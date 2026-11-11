package com.example.wardrobe.util;

import android.content.Context;
import android.util.Log;

import com.example.wardrobe.db.WardrobeDao;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.network.LLMService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 衣物隐藏描述的异步生成器。
 *
 * <p>描述仅写入 wardrobe_items.ai_description，并注入后续穿搭 Prompt。
 * 这里使用 Application Context，不绑定添加页生命周期：用户保存后即使马上
 * 离开页面，网络请求仍可在进程存活期间完成数据库回写。</p>
 *
 * <p>旧版本保存的衣物可能没有隐藏描述。本类额外提供批量补全能力：
 * 串行读取所有缺少描述的衣物，逐件请求模型并写库，用户不需要重新
 * 打开编辑页保存。批量任务使用进程级标记防止重复启动。</p>
 */
public final class ItemDescriptionGenerator {

    private static final String TAG = "ItemDescription";
    private static final double REQUEST_TEMPERATURE = 0.35;
    private static final int DESCRIPTION_MAX_TOKENS = 600;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    /**
     * 批量补全同一时间只允许运行一个，避免首页自动补全与设置页手动补全
     * 同时请求，重复消耗模型额度。
     */
    private static final AtomicBoolean BACKFILL_RUNNING =
            new AtomicBoolean(false);

    private ItemDescriptionGenerator() {
        // 工具类禁止实例化。
    }

    /**
     * 单件描述生成完成回调，供批量补全按顺序推进。
     */
    public interface Callback {
        void onFinished(boolean success, String message);
    }

    /**
     * 批量补全进度回调。
     *
     * <p>completed 表示已经处理过的衣物数量（成功和失败都计入），
     * total 是本次开始前统计出的缺失描述衣物总数。</p>
     */
    public interface ProgressCallback {
        void onProgress(int completed, int total);

        void onFinished(int successCount, int failedCount, String firstError);
    }

    /**
     * 批量补全是否正在运行。设置页可据此恢复按钮状态。
     */
    public static boolean isBackfillRunning() {
        return BACKFILL_RUNNING.get();
    }

    /**
     * 统计缺少隐藏描述的衣物数量。
     */
    public static int countMissingDescriptions(Context context) {
        if (context == null) {
            return 0;
        }
        List<ClothingItem> items = new WardrobeDao(
                context.getApplicationContext()).getAllItems();
        int missingCount = 0;
        for (ClothingItem item : items) {
            if (item != null && isBlank(item.getAiDescription())) {
                missingCount++;
            }
        }
        return missingCount;
    }

    /**
     * 为所有缺少隐藏描述的旧衣物补全 AI 资料。
     *
     * <p>任务严格串行执行：上一件写库完成后才发起下一件请求。
     * 单件失败不会中断后续衣物，最后统一返回成功数、失败数和首个错误。</p>
     *
     * @param callback 可为 null，首页自动补全不需要展示进度。
     */
    public static void backfillMissing(Context context,
                                       ProgressCallback callback) {
        if (context == null) {
            notifyBackfillFinished(callback, 0, 0, "衣物数据不可用");
            return;
        }
        Context appContext = context.getApplicationContext();
        if (!BACKFILL_RUNNING.compareAndSet(false, true)) {
            // 已有任务在跑时不重复发起，直接把当前状态告诉调用方。
            notifyBackfillFinished(callback, 0, 0, "补全任务正在进行");
            return;
        }

        final List<Long> missingIds = new ArrayList<>();
        try {
            List<ClothingItem> items =
                    new WardrobeDao(appContext).getAllItems();
            for (ClothingItem item : items) {
                if (item != null && isBlank(item.getAiDescription())) {
                    missingIds.add(item.getId());
                }
            }
        } catch (RuntimeException exception) {
            Log.e(TAG, "读取待补全衣物失败", exception);
            BACKFILL_RUNNING.set(false);
            notifyBackfillFinished(callback, 0, 0, "读取衣物失败");
            return;
        }

        if (missingIds.isEmpty()) {
            BACKFILL_RUNNING.set(false);
            notifyBackfillProgress(callback, 0, 0);
            notifyBackfillFinished(callback, 0, 0, "");
            return;
        }

        if (!ApiConfig.isLlmConfigured(appContext)
                || !NetworkUtils.isNetworkAvailable(appContext)) {
            BACKFILL_RUNNING.set(false);
            notifyBackfillProgress(callback, 0, missingIds.size());
            notifyBackfillFinished(
                    callback, 0, missingIds.size(), "未配置大模型或网络不可用");
            return;
        }

        notifyBackfillProgress(callback, 0, missingIds.size());
        runNext(appContext, missingIds, 0, 0, 0, "", callback);
    }

    /**
     * 递归推进批量任务。Retrofit 的 enqueue 回调在主线程执行，
     * 因此这里不需要再切换线程；每次只保留一个在途请求。
     */
    private static void runNext(Context appContext,
                                List<Long> itemIds,
                                int index,
                                int successCount,
                                int failedCount,
                                String firstError,
                                ProgressCallback callback) {
        if (index >= itemIds.size()) {
            BACKFILL_RUNNING.set(false);
            notifyBackfillFinished(
                    callback, successCount, failedCount, firstError);
            return;
        }

        final int completed = index + 1;
        try {
            generate(appContext, itemIds.get(index), (success, message) -> {
                int nextSuccess = successCount + (success ? 1 : 0);
                int nextFailed = failedCount + (success ? 0 : 1);
                String nextFirstError = firstError;
                if (!success && isBlank(nextFirstError)) {
                    nextFirstError = message;
                }
                notifyBackfillProgress(
                        callback, completed, itemIds.size());
                runNext(appContext, itemIds, completed, nextSuccess,
                        nextFailed, nextFirstError, callback);
            });
        } catch (RuntimeException exception) {
            // 单件同步异常也要继续后续衣物，并确保运行标记最终能释放。
            Log.e(TAG, "批量补全单件任务异常", exception);
            String nextFirstError = isBlank(firstError)
                    ? "生成衣物资料失败" : firstError;
            notifyBackfillProgress(callback, completed, itemIds.size());
            runNext(appContext, itemIds, completed, successCount,
                    failedCount + 1, nextFirstError, callback);
        }
    }

    /**
     * 在衣物新增或编辑成功后调用。未配置模型、断网或记录已变化时安全跳过。
     */
    public static void generate(Context context, long itemId) {
        generate(context, itemId, null);
    }

    /**
     * 带完成回调的版本：批量补全需要知道每件衣物何时结束，
     * 才能在上一件写库完成后再请求下一件，避免并发打满接口。
     */
    public static void generate(Context context,
                                long itemId,
                                Callback callback) {
        if (context == null || itemId <= 0) {
            notifyResult(callback, false, "衣物记录无效");
            return;
        }
        Context appContext = context.getApplicationContext();
        if (!ApiConfig.isLlmConfigured(appContext)
                || !NetworkUtils.isNetworkAvailable(appContext)) {
            notifyResult(callback, false, "未配置大模型或网络不可用");
            return;
        }

        WardrobeDao dao = new WardrobeDao(appContext);
        ClothingItem snapshot = dao.getItemById(itemId);
        if (snapshot == null) {
            notifyResult(callback, false, "衣物已被删除");
            return;
        }
        long expectedUpdatedAt = snapshot.getUpdatedAt();

        LLMService.getInstance().generate(
                appContext,
                PromptBuilder.buildItemDescriptionSystemPrompt(),
                PromptBuilder.buildItemDescriptionPrompt(snapshot),
                REQUEST_TEMPERATURE,
                DESCRIPTION_MAX_TOKENS,
                new LLMService.Callback() {
                    @Override
                    public void onSuccess(String content) {
                        if (content == null || content.trim().isEmpty()) {
                            notifyResult(callback, false, "模型返回空描述");
                            return;
                        }
                        ClothingItem current = dao.getItemById(itemId);
                        // 用户在等待期间再次编辑过衣物时，丢弃旧描述，避免覆盖新资料。
                        if (current == null
                                || current.getUpdatedAt() != expectedUpdatedAt) {
                            notifyResult(callback, false, "衣物资料已变化");
                            return;
                        }
                        String description = content.trim();
                        if (description.length() > MAX_DESCRIPTION_LENGTH) {
                            description = description.substring(
                                    0, MAX_DESCRIPTION_LENGTH);
                        }
                        current.setAiDescription(description);
                        dao.updateItem(current);
                        notifyResult(callback, true, "");
                    }

                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "生成衣物隐藏描述失败：" + message);
                        notifyResult(callback, false, message);
                    }
                });
    }

    private static void notifyResult(Callback callback,
                                     boolean success,
                                     String message) {
        if (callback != null) {
            callback.onFinished(success, message == null ? "" : message);
        }
    }

    /**
     * 回调异常不能中断批量队列，因此统一做保护。
     */
    private static void notifyBackfillProgress(ProgressCallback callback,
                                               int completed,
                                               int total) {
        if (callback == null) {
            return;
        }
        try {
            callback.onProgress(completed, total);
        } catch (RuntimeException exception) {
            Log.e(TAG, "批量补全进度回调异常", exception);
        }
    }

    private static void notifyBackfillFinished(ProgressCallback callback,
                                               int successCount,
                                               int failedCount,
                                               String firstError) {
        if (callback == null) {
            return;
        }
        try {
            callback.onFinished(
                    successCount,
                    failedCount,
                    firstError == null ? "" : firstError);
        } catch (RuntimeException exception) {
            Log.e(TAG, "批量补全完成回调异常", exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
