package com.example.wardrobe.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.wardrobe.R;
import com.example.wardrobe.db.WardrobeDao;
import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.Outfit;
import com.example.wardrobe.model.TravelPackingItem;
import com.example.wardrobe.model.WeatherInfo;
import com.example.wardrobe.network.LLMService;
import com.example.wardrobe.network.dto.ChatResponse;
import com.example.wardrobe.util.AiOutfitCache;
import com.example.wardrobe.util.AiOutfitParser;
import com.example.wardrobe.util.ApiConfig;
import com.example.wardrobe.util.NetworkUtils;
import com.example.wardrobe.util.PromptBuilder;
import com.example.wardrobe.util.RecommendationMode;
import com.example.wardrobe.util.StyleProfileManager;
import com.example.wardrobe.util.TravelPackingParser;
import com.example.wardrobe.util.TravelPlanner;
import com.example.wardrobe.util.TravelResultCache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;

/**
 * 穿搭推荐业务仓库。
 *
 * <p>统一负责读取 SQLite、构建 Prompt、调用大模型、解析结构化方案和读写
 * 当日缓存。ViewModel 不再直接依赖 DAO、PromptBuilder 或 LLMService。</p>
 */
public final class OutfitRepository {

    public static final String TRAVEL_DAILY_SCENE = "旅行衣橱今日穿搭";

    public static final class OutfitResult {
        private final String content;
        private final String scene;
        private final List<AiOutfitPlan> plans;
        private final List<TravelPackingItem> packingItems;
        private final String packingListText;
        private final boolean fromCache;
        private final String notice;

        private OutfitResult(String content,
                             String scene,
                             List<AiOutfitPlan> plans,
                             List<TravelPackingItem> packingItems,
                             String packingListText,
                             boolean fromCache,
                             String notice) {
            this.content = content == null ? "" : content;
            this.scene = scene == null ? "" : scene;
            this.plans = plans == null
                    ? Collections.emptyList() : new ArrayList<>(plans);
            this.packingItems = packingItems == null
                    ? Collections.emptyList() : new ArrayList<>(packingItems);
            this.packingListText = packingListText == null
                    ? "" : packingListText;
            this.fromCache = fromCache;
            this.notice = notice == null ? "" : notice;
        }

        public String getContent() {
            return content;
        }

        public String getScene() {
            return scene;
        }

        public List<AiOutfitPlan> getPlans() {
            return new ArrayList<>(plans);
        }

        public List<TravelPackingItem> getPackingItems() {
            return new ArrayList<>(packingItems);
        }

        public String getPackingListText() {
            return packingListText;
        }

        public boolean isFromCache() {
            return fromCache;
        }

        public String getNotice() {
            return notice;
        }

        public boolean isPacking() {
            return !packingItems.isEmpty();
        }
    }

    public interface Callback {
        void onSuccess(OutfitResult result);

        void onNoCache();

        void onError(String message);
    }

    public interface CountCallback {
        void onResult(int count);
    }

    private final WardrobeDao wardrobeDao;
    private final TravelPlanner travelPlanner = new TravelPlanner();
    private final ExecutorService ioExecutor =
            Executors.newSingleThreadExecutor();
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());
    private Call<ChatResponse> aiCall;
    private int requestToken;

    public OutfitRepository(Context context) {
        wardrobeDao = new WardrobeDao(context.getApplicationContext());
    }

    /**
     * 恢复首页今日缓存；没有缓存时回调 onNoCache。
     */
    public void restoreDaily(Context context,
                             String mode,
                             String scene,
                             String gender,
                             Callback callback) {
        final int token = ++requestToken;
        final String normalizedScene = AiOutfitCache.normalizeScene(scene);
        ioExecutor.execute(() -> {
            String cached = AiOutfitCache.loadTodayContent(
                    context, mode, normalizedScene, gender);
            if (cached == null) {
                postNoCache(token, callback);
                return;
            }
            List<ClothingItem> items = wardrobeDao.getAllItems();
            List<Outfit> outfits = wardrobeDao.getAllOutfitsWithItems();
            List<AiOutfitPlan> plans =
                    AiOutfitParser.parse(cached, items, outfits);
            postSuccess(token, callback, new OutfitResult(
                    cached, normalizedScene, plans, null, "",
                    true, ""));
        });
    }

    /**
     * 生成灵感/经典模式的三套穿搭。
     */
    public void generateDaily(Context context,
                              WeatherInfo weather,
                              String mode,
                              String scene,
                              String gender,
                              boolean force,
                              String previousContent,
                              String previousScene,
                              Callback callback) {
        final int token = ++requestToken;
        final String normalizedScene = AiOutfitCache.normalizeScene(scene);
        ioExecutor.execute(() -> {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                postError(token, callback, context.getString(
                        R.string.network_unavailable));
                return;
            }
            if (!ApiConfig.isLlmConfigured(context)) {
                postError(token, callback, context.getString(
                        R.string.ai_need_llm_key));
                return;
            }
            if (weather == null) {
                postError(token, callback, context.getString(
                        R.string.ai_need_weather));
                return;
            }
            List<ClothingItem> items = wardrobeDao.getAllItems();
            if (items.isEmpty()) {
                postError(token, callback, context.getString(
                        R.string.ai_need_clothes));
                return;
            }
            List<Outfit> classicOutfits =
                    wardrobeDao.getAllOutfitsWithItems();
            String notice = RecommendationMode.isClassic(mode)
                    && classicOutfits.isEmpty()
                    ? context.getString(R.string.ai_classic_empty_fallback)
                    : "";
            if (!force) {
                String cached = AiOutfitCache.loadTodayContent(
                        context, mode, normalizedScene, gender);
                if (cached != null) {
                    List<AiOutfitPlan> plans = AiOutfitParser.parse(
                            cached, items, classicOutfits);
                    postSuccess(token, callback, new OutfitResult(
                            cached, normalizedScene, plans, null, "",
                            true, notice));
                    return;
                }
            }

            List<ClothingItem> previousItems =
                    resolvePreviousRecommendedItems(
                            previousContent,
                            previousScene,
                            normalizedScene,
                            items,
                            classicOutfits);
            String userPrompt = PromptBuilder.buildTodayOutfitPrompt(
                    weather,
                    items,
                    classicOutfits,
                    normalizedScene,
                    mode,
                    gender,
                    previousItems);
            String systemPrompt = PromptBuilder.buildTodaySystemPrompt(
                    mode, !classicOutfits.isEmpty(), gender);
            requestAi(context, token, systemPrompt, userPrompt,
                    new AiResultHandler() {
                        @Override
                        public void onSuccess(String content) {
                            AiOutfitCache.save(context, mode,
                                    normalizedScene, gender, content);
                            List<AiOutfitPlan> plans = AiOutfitParser.parse(
                                    content, items, classicOutfits);
                            postSuccess(token, callback, new OutfitResult(
                                    content, normalizedScene, plans, null,
                                    "", false, notice));
                        }

                        @Override
                        public void onError(String message) {
                            postError(token, callback, message);
                        }
                    });
        });
    }

    /**
     * 异地游玩打包清单。
     */
    public void generateTravelPacking(Context context,
                                      String rawInput,
                                      String gender,
                                      Callback callback) {
        final int token = ++requestToken;
        ioExecutor.execute(() -> {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                postError(token, callback, context.getString(
                        R.string.network_unavailable));
                return;
            }
            // 天气默认由 Open-Meteo 免费源兜底，不再要求配置和风天气。
            if (!ApiConfig.isLlmConfigured(context)) {
                postError(token, callback, context.getString(
                        R.string.api_not_configured));
                return;
            }
            List<ClothingItem> items = wardrobeDao.getAllItems();
            if (items.isEmpty()) {
                postError(token, callback, context.getString(
                        R.string.ai_need_clothes));
                return;
            }
            mainHandler.post(() -> {
                if (token != requestToken) {
                    return;
                }
                travelPlanner.plan(context, rawInput, gender, items,
                        new TravelPlanner.Callback() {
                            @Override
                            public void onInvalidInput(String message) {
                                postError(token, callback, message);
                            }

                            @Override
                            public void onSuccess(String content,
                                                  String scene) {
                                AiOutfitCache.save(context,
                                        RecommendationMode.TRAVEL,
                                        scene, gender, content);
                                TravelResultCache.save(
                                        context, scene, gender, content);
                                parsePacking(token, context, content, scene,
                                        callback);
                            }

                            @Override
                            public void onError(String message) {
                                postError(token, callback, message);
                            }
                        });
            });
        });
    }

    /**
     * 旅行模式输入为空时，仅使用旅行衣橱生成当天穿搭。
     */
    public void generateTravelDaily(Context context,
                                    WeatherInfo weather,
                                    String gender,
                                    Callback callback) {
        final int token = ++requestToken;
        ioExecutor.execute(() -> {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                postError(token, callback, context.getString(
                        R.string.network_unavailable));
                return;
            }
            if (!ApiConfig.isLlmConfigured(context)) {
                postError(token, callback, context.getString(
                        R.string.ai_need_llm_key));
                return;
            }
            if (weather == null) {
                postError(token, callback, context.getString(
                        R.string.ai_need_weather));
                return;
            }
            List<ClothingItem> travelItems = wardrobeDao.getTravelItems();
            if (travelItems.isEmpty()) {
                postError(token, callback, context.getString(
                        R.string.travel_wardrobe_need_items));
                return;
            }
            String userPrompt =
                    PromptBuilder.buildTravelWardrobeOutfitPrompt(
                            weather, travelItems, gender);
            String systemPrompt =
                    PromptBuilder.buildTravelOutfitSystemPrompt(gender);
            requestAi(context, token, systemPrompt, userPrompt,
                    new AiResultHandler() {
                        @Override
                        public void onSuccess(String content) {
                            AiOutfitCache.save(context,
                                    RecommendationMode.TRAVEL,
                                    TRAVEL_DAILY_SCENE,
                                    gender,
                                    content);
                            List<AiOutfitPlan> plans =
                                    AiOutfitParser.parse(
                                            content,
                                            wardrobeDao.getAllItems(),
                                            wardrobeDao.getAllOutfitsWithItems());
                            postSuccess(token, callback, new OutfitResult(
                                    content, TRAVEL_DAILY_SCENE, plans,
                                    null, "", false, ""));
                        }

                        @Override
                        public void onError(String message) {
                            postError(token, callback, message);
                        }
                    });
        });
    }

    /**
     * 详情页单品 AI 穿搭。
     */
    public void generateItem(Context context,
                             ClothingItem coreItem,
                             WeatherInfo weather,
                             String gender,
                             Callback callback) {
        final int token = ++requestToken;
        ioExecutor.execute(() -> {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                postError(token, callback, context.getString(
                        R.string.network_unavailable));
                return;
            }
            if (!ApiConfig.isLlmConfigured(context)) {
                postError(token, callback, context.getString(
                        R.string.ai_need_llm_key));
                return;
            }
            if (coreItem == null || weather == null) {
                postError(token, callback, context.getString(
                        R.string.ai_need_weather));
                return;
            }
            List<ClothingItem> others = new ArrayList<>();
            for (ClothingItem item : wardrobeDao.getAllItems()) {
                if (item.getId() != coreItem.getId()) {
                    others.add(item);
                }
            }
            String userPrompt = PromptBuilder.buildItemOutfitPrompt(
                    weather, coreItem, others, gender);
            String systemPrompt =
                    PromptBuilder.buildItemSystemPrompt(gender);
            requestAi(context, token, systemPrompt, userPrompt,
                    new AiResultHandler() {
                        @Override
                        public void onSuccess(String content) {
                            List<AiOutfitPlan> plans =
                                    AiOutfitParser.parse(
                                            content,
                                            wardrobeDao.getAllItems(),
                                            Collections.emptyList());
                            postSuccess(token, callback, new OutfitResult(
                                    content, "", plans, null, "",
                                    false, ""));
                        }

                        @Override
                        public void onError(String message) {
                            postError(token, callback, message);
                        }
                    });
        });
    }

    /**
     * 恢复旅行缓存：空输入恢复旅行穿搭，非空恢复打包清单。
     */
    public void restoreTravel(Context context,
                              String rawInput,
                              String gender,
                              Callback callback) {
        final int token = ++requestToken;
        final String scene = rawInput == null ? "" : rawInput.trim();
        ioExecutor.execute(() -> {
            if (scene.isEmpty()) {
                String cached = AiOutfitCache.loadTodayContent(
                        context, RecommendationMode.TRAVEL,
                        TRAVEL_DAILY_SCENE, gender);
                if (cached == null) {
                    postNoCache(token, callback);
                    return;
                }
                List<AiOutfitPlan> plans = AiOutfitParser.parse(
                        cached,
                        wardrobeDao.getAllItems(),
                        wardrobeDao.getAllOutfitsWithItems());
                postSuccess(token, callback, new OutfitResult(
                        cached, TRAVEL_DAILY_SCENE, plans, null,
                        "", true, ""));
                return;
            }
            String cached = AiOutfitCache.loadTodayContent(
                    context, RecommendationMode.TRAVEL, scene, gender);
            if (cached == null) {
                TravelResultCache.Result last =
                        TravelResultCache.loadToday(context, gender);
                if (last != null && scene.equals(last.getScene())) {
                    cached = last.getContent();
                }
            }
            if (cached == null) {
                postNoCache(token, callback);
                return;
            }
            List<TravelPackingItem> packingItems =
                    TravelPackingParser.parse(
                            cached, wardrobeDao.getAllItems());
            String checklist = TravelPackingParser
                    .extractNonClothingSections(cached);
            postSuccess(token, callback, new OutfitResult(
                    cached, scene, null, packingItems, checklist,
                    true, ""));
        });
    }

    /**
     * 衣橱变化后重新匹配旅行清单图片。
     */
    public void rebuildPacking(String content,
                               String scene,
                               Callback callback) {
        final int token = ++requestToken;
        ioExecutor.execute(() -> {
            List<TravelPackingItem> packingItems =
                    TravelPackingParser.parse(
                            content, wardrobeDao.getAllItems());
            String checklist = TravelPackingParser
                    .extractNonClothingSections(content);
            postSuccess(token, callback, new OutfitResult(
                    content, scene, null, packingItems, checklist,
                    true, ""));
        });
    }

    private void parsePacking(int token,
                              Context context,
                              String content,
                              String scene,
                              Callback callback) {
        ioExecutor.execute(() -> {
            List<TravelPackingItem> items = TravelPackingParser.parse(
                    content, wardrobeDao.getAllItems());
            String checklist = TravelPackingParser
                    .extractNonClothingSections(content);
            postSuccess(token, callback, new OutfitResult(
                    content, scene, null, items, checklist,
                    false, ""));
        });
    }

    private interface AiResultHandler {
        void onSuccess(String content);

        void onError(String message);
    }

    private void requestAi(Context context,
                           int token,
                           String systemPrompt,
                           String userPrompt,
                           AiResultHandler handler) {
        mainHandler.post(() -> {
            if (token != requestToken) {
                return;
            }
            aiCall = LLMService.getInstance().generate(
                    context, systemPrompt, userPrompt,
                    new LLMService.Callback() {
                        @Override
                        public void onSuccess(String content) {
                            if (token == requestToken) {
                                aiCall = null;
                                handler.onSuccess(content);
                            }
                        }

                        @Override
                        public void onError(String message) {
                            if (token == requestToken) {
                                aiCall = null;
                                handler.onError(message);
                            }
                        }
                    });
        });
    }

    private void postSuccess(int token,
                             Callback callback,
                             OutfitResult result) {
        mainHandler.post(() -> {
            if (token == requestToken) {
                callback.onSuccess(result);
            }
        });
    }

    private void postNoCache(int token, Callback callback) {
        mainHandler.post(() -> {
            if (token == requestToken) {
                callback.onNoCache();
            }
        });
    }

    private void postError(int token,
                           Callback callback,
                           String message) {
        mainHandler.post(() -> {
            if (token == requestToken) {
                callback.onError(message);
            }
        });
    }

    private List<ClothingItem> resolvePreviousRecommendedItems(
            String previousContent,
            String previousScene,
            String requestScene,
            List<ClothingItem> items,
            List<Outfit> classicOutfits) {
        if (previousContent == null || previousContent.trim().isEmpty()
                || !requestScene.equals(
                AiOutfitCache.normalizeScene(previousScene))) {
            return Collections.emptyList();
        }
        List<ClothingItem> previousItems = new ArrayList<>();
        List<AiOutfitPlan> previousPlans = AiOutfitParser.parse(
                previousContent, items, classicOutfits);
        for (AiOutfitPlan plan : previousPlans) {
            for (ClothingItem item : resolvePlanItems(plan, classicOutfits)) {
                if (item != null && item.getId() > 0
                        && !containsItemId(previousItems, item.getId())) {
                    previousItems.add(item);
                }
            }
        }
        return previousItems;
    }

    private List<ClothingItem> resolvePlanItems(
            AiOutfitPlan plan,
            List<Outfit> classicOutfits) {
        List<ClothingItem> resolved = new ArrayList<>(
                AiOutfitParser.getMatchedItems(plan));
        if (plan == null || !plan.isClassicRecommendation()
                || plan.getClassicOutfitTitle().isEmpty()
                || classicOutfits == null || classicOutfits.isEmpty()) {
            return resolved;
        }
        String targetTitle = normalizeTitle(plan.getClassicOutfitTitle());
        for (Outfit outfit : classicOutfits) {
            if (!targetTitle.equals(normalizeTitle(outfit.getTitle()))) {
                continue;
            }
            for (ClothingItem item : outfit.getLinkedItems()) {
                if (item != null && item.getId() > 0
                        && !containsItemId(resolved, item.getId())) {
                    resolved.add(item);
                }
            }
            break;
        }
        return resolved;
    }

    private boolean containsItemId(List<ClothingItem> items, long itemId) {
        for (ClothingItem item : items) {
            if (item != null && item.getId() == itemId) {
                return true;
            }
        }
        return false;
    }

    private String normalizeTitle(String value) {
        return value == null ? "" : value.trim()
                .replaceAll("[\\s\\p{Punct}，。；：、“”‘’！？【】《》]+", "")
                .toLowerCase(Locale.ROOT);
    }

    public void cancel() {
        requestToken++;
        if (aiCall != null) {
            aiCall.cancel();
            aiCall = null;
        }
        travelPlanner.cancel();
    }

    public void loadOutfitCount(CountCallback callback) {
        ioExecutor.execute(() -> {
            int count = wardrobeDao.getOutfitCount();
            mainHandler.post(() -> callback.onResult(count));
        });
    }
}
