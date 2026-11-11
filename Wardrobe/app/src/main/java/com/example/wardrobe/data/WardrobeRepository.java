package com.example.wardrobe.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.wardrobe.db.WardrobeDao;
import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.Outfit;
import com.example.wardrobe.model.TravelPackingItem;
import com.example.wardrobe.util.AiOutfitParser;
import com.example.wardrobe.util.TravelPackingParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 应用级衣橱数据仓库。
 *
 * <p>SQLite 仍由 WardrobeDao 负责，这里只解决三个跨页面问题：
 * 1. 所有数据库 I/O 统一切到单线程后台执行器；
 * 2. 写入完成后主动刷新全局 LiveData，首页和衣橱页立即收到同一份数据；
 * 3. 旅行衣橱写操作使用原子开关防止重复点击造成并发覆盖。</p>
 */
public final class WardrobeRepository {

    public interface ResultCallback<T> {
        void onSuccess(T result);

        void onError(String message);
    }

    private static volatile WardrobeRepository instance;

    private final WardrobeDao wardrobeDao;
    private final ExecutorService ioExecutor =
            Executors.newSingleThreadExecutor();
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());
    private final MutableLiveData<List<ClothingItem>> allItems =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<ClothingItem>> travelItems =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> travelMutationInFlight =
            new MutableLiveData<>(false);
    private final AtomicBoolean travelWriteBusy = new AtomicBoolean(false);
    private int refreshGeneration;

    private WardrobeRepository(Context context) {
        wardrobeDao = new WardrobeDao(context.getApplicationContext());
        refreshAll();
    }

    public static WardrobeRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (WardrobeRepository.class) {
                if (instance == null) {
                    instance = new WardrobeRepository(context);
                }
            }
        }
        return instance;
    }

    public LiveData<List<ClothingItem>> getAllItems() {
        return allItems;
    }

    public LiveData<List<ClothingItem>> getTravelItems() {
        return travelItems;
    }

    public LiveData<Boolean> getTravelMutationInFlight() {
        return travelMutationInFlight;
    }

    /**
     * 立即返回当前内存快照。调用方不得修改返回列表。
     */
    public List<ClothingItem> getAllItemsSnapshot() {
        return safeCopy(allItems.getValue());
    }

    public List<ClothingItem> getTravelItemsSnapshot() {
        return safeCopy(travelItems.getValue());
    }

    /**
     * 后台重新读取完整衣橱。旅行列表由同一快照派生，避免两次查询间
     * 数据发生变化导致角标和旅行箱列表不一致。
     */
    public void refreshAll() {
        final int generation = ++refreshGeneration;
        ioExecutor.execute(() -> {
            try {
                List<ClothingItem> loaded = wardrobeDao.getAllItems();
                postSnapshotIfCurrent(generation, loaded);
            } catch (RuntimeException exception) {
                // 读取失败时保留上一份成功快照，页面不会突然空白。
            }
        });
    }

    /**
     * SQLite LIKE 组合查询。查询结果通过主线程回调，不跨线程直接更新 UI。
     */
    public void searchItems(String category,
                            String keyword,
                            boolean travelOnly,
                            ResultCallback<List<ClothingItem>> callback) {
        ioExecutor.execute(() -> {
            try {
                List<ClothingItem> result = wardrobeDao.searchItems(
                        category, keyword, travelOnly);
                postResult(callback, result);
            } catch (RuntimeException exception) {
                postError(callback, "衣橱查询失败，请稍后重试");
            }
        });
    }

    /**
     * 单件衣物加入或移出旅行箱。
     */
    public void setItemTravel(long itemId,
                              boolean travel,
                              ResultCallback<Integer> callback) {
        if (!travelWriteBusy.compareAndSet(false, true)) {
            postResult(callback, 0);
            return;
        }
        postTravelBusy(true);
        ioExecutor.execute(() -> {
            try {
                int rows = wardrobeDao.setItemTravel(itemId, travel);
                List<ClothingItem> loaded = wardrobeDao.getAllItems();
                postSnapshot(loaded);
                postResult(callback, rows);
            } catch (RuntimeException exception) {
                postError(callback, "旅行衣橱更新失败，请稍后重试");
            } finally {
                travelWriteBusy.set(false);
                postTravelBusy(false);
            }
        });
    }

    /**
     * 只清空旅行标记，不删除主衣橱中的任何衣物或图片。
     */
    public void clearTravelItems(ResultCallback<Integer> callback) {
        if (!travelWriteBusy.compareAndSet(false, true)) {
            postResult(callback, 0);
            return;
        }
        postTravelBusy(true);
        ioExecutor.execute(() -> {
            try {
                int rows = wardrobeDao.clearTravelItems();
                List<ClothingItem> loaded = wardrobeDao.getAllItems();
                postSnapshot(loaded);
                postResult(callback, rows);
            } catch (RuntimeException exception) {
                postError(callback, "清空旅行衣橱失败，请稍后重试");
            } finally {
                travelWriteBusy.set(false);
                postTravelBusy(false);
            }
        });
    }

    /**
     * 在后台把 AI 文本解析成三套图文方案，避免在主线程读取搭配关联表。
     */
    public void parseOutfitPlans(
            String content,
            ResultCallback<List<AiOutfitPlan>> callback) {
        ioExecutor.execute(() -> {
            try {
                List<ClothingItem> items = wardrobeDao.getAllItems();
                List<Outfit> outfits =
                        wardrobeDao.getAllOutfitsWithItems();
                List<AiOutfitPlan> plans =
                        AiOutfitParser.parse(content, items, outfits);
                postResult(callback, plans);
            } catch (RuntimeException exception) {
                postError(callback, "AI 结果解析失败，请稍后重试");
            }
        });
    }

    /**
     * 在后台匹配打包清单中的【衣物名称】与本地图片。
     */
    public void parseTravelPacking(
            String content,
            ResultCallback<List<TravelPackingItem>> callback) {
        ioExecutor.execute(() -> {
            try {
                List<ClothingItem> items = wardrobeDao.getAllItems();
                postResult(callback,
                        TravelPackingParser.parse(content, items));
            } catch (RuntimeException exception) {
                postError(callback, "旅行清单解析失败，请稍后重试");
            }
        });
    }

    private void postSnapshotIfCurrent(int generation,
                                       List<ClothingItem> loaded) {
        mainHandler.post(() -> {
            if (generation == refreshGeneration) {
                publishSnapshot(loaded);
            }
        });
    }

    private void postSnapshot(List<ClothingItem> loaded) {
        mainHandler.post(() -> publishSnapshot(loaded));
    }

    private void publishSnapshot(List<ClothingItem> loaded) {
        List<ClothingItem> all = safeCopy(loaded);
        List<ClothingItem> travel = new ArrayList<>();
        for (ClothingItem item : all) {
            if (item != null && item.isTravel()) {
                travel.add(item);
            }
        }
        allItems.setValue(all);
        travelItems.setValue(travel);
    }

    private void postTravelBusy(boolean busy) {
        mainHandler.post(() -> travelMutationInFlight.setValue(busy));
    }

    private <T> void postResult(ResultCallback<T> callback, T result) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onSuccess(result));
    }

    private <T> void postError(ResultCallback<T> callback, String message) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onError(message));
    }

    private List<ClothingItem> safeCopy(List<ClothingItem> source) {
        return source == null
                ? Collections.emptyList()
                : new ArrayList<>(source);
    }
}
