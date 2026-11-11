package com.example.wardrobe.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.wardrobe.R;
import com.example.wardrobe.data.WardrobeRepository;
import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.TravelPackingItem;
import com.example.wardrobe.model.WeatherInfo;
import com.example.wardrobe.network.LLMService;
import com.example.wardrobe.network.dto.ChatResponse;
import com.example.wardrobe.ui.state.TravelUiState;
import com.example.wardrobe.util.AiOutfitCache;
import com.example.wardrobe.util.ApiConfig;
import com.example.wardrobe.util.NetworkUtils;
import com.example.wardrobe.util.PromptBuilder;
import com.example.wardrobe.util.TravelPlanner;
import com.example.wardrobe.util.TravelResultCache;

import java.util.List;

import retrofit2.Call;

/**
 * 首页 ViewModel。
 *
 * <p>共享衣橱 LiveData，并把“异地游玩”的全部业务状态收敛为
 * TravelUiState：输入非空生成打包清单，输入为空使用旅行衣橱生成
 * 当天穿搭。网络 Call 在 ViewModel 清理时统一取消。</p>
 */
public class MainViewModel extends AndroidViewModel {

    /**
     * 空输入场景用于旅行衣橱的“当天穿搭”缓存，不会回填到输入框。
     */
    public static final String TRAVEL_DAILY_SCENE = "旅行衣橱今日穿搭";

    private final WardrobeRepository repository;
    private final TravelPlanner travelPlanner = new TravelPlanner();
    private final MutableLiveData<TravelUiState> travelUiState =
            new MutableLiveData<>(TravelUiState.idle());
    private final MutableLiveData<Boolean> travelRequestInFlight =
            new MutableLiveData<>(false);

    private Call<ChatResponse> travelCall;
    private int requestToken;
    private boolean requestInFlight;
    private TravelUiState lastSuccessState = TravelUiState.idle();

    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = WardrobeRepository.getInstance(application);
    }

    public LiveData<List<ClothingItem>> getAllItems() {
        return repository.getAllItems();
    }

    public LiveData<List<ClothingItem>> getTravelItems() {
        return repository.getTravelItems();
    }

    public LiveData<Boolean> getTravelMutationInFlight() {
        return repository.getTravelMutationInFlight();
    }

    public LiveData<TravelUiState> getTravelUiState() {
        return travelUiState;
    }

    public LiveData<Boolean> getTravelRequestInFlight() {
        return travelRequestInFlight;
    }

    /**
     * 输入框非空：解析城市/日期/天数，查询目的地七天天气并生成打包清单。
     */
    public void generateTravelPacking(String rawInput, String gender) {
        if (requestInFlight) {
            return;
        }
        if (!NetworkUtils.isNetworkAvailable(getApplication())) {
            publishError(getApplication().getString(
                    R.string.network_unavailable));
            return;
        }
        if (!ApiConfig.isLlmConfigured(getApplication())) {
            publishError(getApplication().getString(
                    R.string.ai_need_llm_key));
            return;
        }
        if (!ApiConfig.isWeatherConfigured(getApplication())) {
            publishError(getApplication().getString(
                    R.string.weather_not_configured));
            return;
        }
        List<ClothingItem> allItems = repository.getAllItemsSnapshot();
        if (allItems.isEmpty()) {
            publishError(getApplication().getString(
                    R.string.ai_need_clothes));
            return;
        }

        int token = beginRequest(rawInput);
        travelPlanner.plan(
                getApplication(),
                rawInput,
                gender,
                allItems,
                new TravelPlanner.Callback() {
                    @Override
                    public void onInvalidInput(String message) {
                        finishRequestWithError(token, message);
                    }

                    @Override
                    public void onSuccess(String content, String scene) {
                        if (!isCurrent(token)) {
                            return;
                        }
                        AiOutfitCache.save(
                                getApplication(),
                                com.example.wardrobe.util.RecommendationMode.TRAVEL,
                                scene,
                                gender,
                                content);
                        TravelResultCache.save(
                                getApplication(), scene, gender, content);
                        publishPacking(token, content, scene);
                    }

                    @Override
                    public void onError(String message) {
                        finishRequestWithError(token, message);
                    }
                });
    }

    /**
     * 输入框为空：只使用 is_travel = 1 的衣物生成当天穿搭。
     */
    public void generateTravelDailyOutfit(WeatherInfo weather,
                                          String gender) {
        if (requestInFlight) {
            return;
        }
        List<ClothingItem> travelItems =
                repository.getTravelItemsSnapshot();
        if (travelItems.isEmpty()) {
            publishError(getApplication().getString(
                    R.string.travel_wardrobe_need_items));
            return;
        }
        if (weather == null) {
            publishError(getApplication().getString(
                    R.string.ai_need_weather));
            return;
        }
        if (!ApiConfig.isLlmConfigured(getApplication())) {
            publishError(getApplication().getString(
                    R.string.ai_need_llm_key));
            return;
        }
        if (!NetworkUtils.isNetworkAvailable(getApplication())) {
            publishError(getApplication().getString(
                    R.string.network_unavailable));
            return;
        }

        int token = beginRequest(TRAVEL_DAILY_SCENE);
        String userPrompt = PromptBuilder.buildTravelWardrobeOutfitPrompt(
                weather, travelItems, gender);
        String systemPrompt =
                PromptBuilder.buildTravelOutfitSystemPrompt(gender);
        travelCall = LLMService.getInstance().generate(
                getApplication(),
                systemPrompt,
                userPrompt,
                new LLMService.Callback() {
                    @Override
                    public void onSuccess(String content) {
                        if (!isCurrent(token)) {
                            return;
                        }
                        travelCall = null;
                        AiOutfitCache.save(
                                getApplication(),
                                com.example.wardrobe.util.RecommendationMode.TRAVEL,
                                TRAVEL_DAILY_SCENE,
                                gender,
                                content);
                        publishOutfit(token, content, TRAVEL_DAILY_SCENE);
                    }

                    @Override
                    public void onError(String message) {
                        finishRequestWithError(token, message);
                    }
                });
    }

    /**
     * 恢复当天缓存。输入非空恢复打包清单；输入为空恢复旅行中穿搭。
     * 输入框内容本身由 Activity/SceneInputStore 管理，这里不回写。
     */
    public void restoreTravelCache(String rawInput, String gender) {
        String normalizedInput = rawInput == null ? "" : rawInput.trim();
        if (normalizedInput.isEmpty()) {
            String cached = AiOutfitCache.loadTodayContent(
                    getApplication(),
                    com.example.wardrobe.util.RecommendationMode.TRAVEL,
                    TRAVEL_DAILY_SCENE,
                    gender);
            if (cached != null) {
                publishOutfitWithoutRequest(
                        cached, TRAVEL_DAILY_SCENE, gender);
            } else {
                travelUiState.setValue(TravelUiState.idle());
            }
            return;
        }

        String cached = AiOutfitCache.loadTodayContent(
                getApplication(),
                com.example.wardrobe.util.RecommendationMode.TRAVEL,
                normalizedInput,
                gender);
        if (cached == null) {
            TravelResultCache.Result last =
                    TravelResultCache.loadToday(getApplication(), gender);
            if (last != null && normalizedInput.equals(last.getScene())) {
                cached = last.getContent();
            }
        }
        if (cached != null) {
            publishPackingWithoutRequest(cached, normalizedInput);
        } else {
            travelUiState.setValue(TravelUiState.idle());
        }
    }

    /**
     * 全局衣物快照变化后重新绑定打包清单图片和按钮状态。
     * 例如用户在衣橱页清空旅行箱，返回首页时“已加入”会立即恢复为可添加。
     */
    public void refreshTravelPackingBindings() {
        TravelUiState current = travelUiState.getValue();
        if (current == null
                || current.getKind() != TravelUiState.Kind.PACKING
                || current.getContent().trim().isEmpty()) {
            return;
        }
        int token = requestToken;
        repository.parseTravelPacking(current.getContent(),
                new WardrobeRepository.ResultCallback<List<TravelPackingItem>>() {
                    @Override
                    public void onSuccess(List<TravelPackingItem> items) {
                        if (!isCurrent(token)) {
                            return;
                        }
                        travelUiState.setValue(TravelUiState.packing(
                                current.getContent(),
                                current.getScene(),
                                items));
                    }

                    @Override
                    public void onError(String message) {
                        // 原图文继续展示，图片绑定刷新失败不打断主流程。
                    }
                });
    }

    /**
     * 打包卡片的双向旅行状态更新。
     */
    public void setTravelState(
            ClothingItem item,
            boolean travel,
            WardrobeRepository.ResultCallback<Integer> callback) {
        if (item == null || item.getId() <= 0) {
            return;
        }
        repository.setItemTravel(item.getId(), travel, callback);
    }

    public void clearTravel(
            WardrobeRepository.ResultCallback<Integer> callback) {
        repository.clearTravelItems(callback);
    }

    public TravelUiState getLastSuccessState() {
        return lastSuccessState;
    }

    private int beginRequest(String scene) {
        requestInFlight = true;
        travelRequestInFlight.setValue(true);
        travelUiState.setValue(TravelUiState.loading(scene));
        return ++requestToken;
    }

    private boolean isCurrent(int token) {
        return token == requestToken;
    }

    private void publishPacking(int token,
                                String content,
                                String scene) {
        repository.parseTravelPacking(content,
                new WardrobeRepository.ResultCallback<List<TravelPackingItem>>() {
                    @Override
                    public void onSuccess(List<TravelPackingItem> items) {
                        if (!isCurrent(token)) {
                            return;
                        }
                        finishLoading();
                        TravelUiState state = TravelUiState.packing(
                                content, scene, items);
                        lastSuccessState = state;
                        travelUiState.setValue(state);
                    }

                    @Override
                    public void onError(String message) {
                        finishRequestWithError(token, message);
                    }
                });
    }

    private void publishOutfit(int token,
                               String content,
                               String scene) {
        repository.parseOutfitPlans(content,
                new WardrobeRepository.ResultCallback<List<AiOutfitPlan>>() {
                    @Override
                    public void onSuccess(List<AiOutfitPlan> plans) {
                        if (!isCurrent(token)) {
                            return;
                        }
                        finishLoading();
                        TravelUiState state = TravelUiState.outfit(
                                content, scene, plans);
                        lastSuccessState = state;
                        travelUiState.setValue(state);
                    }

                    @Override
                    public void onError(String message) {
                        finishRequestWithError(token, message);
                    }
                });
    }

    private void publishPackingWithoutRequest(String content, String scene) {
        repository.parseTravelPacking(content,
                new WardrobeRepository.ResultCallback<List<TravelPackingItem>>() {
                    @Override
                    public void onSuccess(List<TravelPackingItem> items) {
                        TravelUiState state = TravelUiState.packing(
                                content, scene, items);
                        lastSuccessState = state;
                        travelUiState.setValue(state);
                    }

                    @Override
                    public void onError(String message) {
                        travelUiState.setValue(
                                TravelUiState.error(message, scene));
                    }
                });
    }

    private void publishOutfitWithoutRequest(String content,
                                             String scene,
                                             String gender) {
        repository.parseOutfitPlans(content,
                new WardrobeRepository.ResultCallback<List<AiOutfitPlan>>() {
                    @Override
                    public void onSuccess(List<AiOutfitPlan> plans) {
                        TravelUiState state = TravelUiState.outfit(
                                content, scene, plans);
                        lastSuccessState = state;
                        travelUiState.setValue(state);
                    }

                    @Override
                    public void onError(String message) {
                        travelUiState.setValue(
                                TravelUiState.error(message, scene));
                    }
                });
    }

    private void finishRequestWithError(int token, String message) {
        if (!isCurrent(token)) {
            return;
        }
        finishLoading();
        travelUiState.setValue(TravelUiState.error(
                message == null ? "" : message,
                travelUiState.getValue() == null
                        ? "" : travelUiState.getValue().getScene()));
    }

    private void publishError(String message) {
        travelUiState.setValue(TravelUiState.error(message, ""));
    }

    private void finishLoading() {
        requestInFlight = false;
        travelCall = null;
        travelRequestInFlight.setValue(false);
    }

    public void cancelActiveRequests() {
        requestToken++;
        requestInFlight = false;
        travelRequestInFlight.setValue(false);
        if (travelCall != null) {
            travelCall.cancel();
            travelCall = null;
        }
        travelPlanner.cancel();
    }

    @Override
    protected void onCleared() {
        cancelActiveRequests();
        super.onCleared();
    }
}
