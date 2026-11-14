package com.example.wardrobe.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.wardrobe.R;
import com.example.wardrobe.data.OutfitRepository;
import com.example.wardrobe.data.WardrobeRepository;
import com.example.wardrobe.data.WeatherRepository;
import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.WeatherInfo;
import com.example.wardrobe.ui.state.MainUiState;
import com.example.wardrobe.util.AiOutfitCache;
import com.example.wardrobe.util.ApiConfig;
import com.example.wardrobe.util.ItemDescriptionGenerator;
import com.example.wardrobe.util.NetworkUtils;
import com.example.wardrobe.util.RecommendationMode;
import com.example.wardrobe.util.SceneInputStore;
import com.example.wardrobe.util.StyleProfileManager;

import java.util.Collections;
import java.util.List;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 首页 ViewModel。
 *
 * <p>只负责状态合并与业务分发。天气、位置、Prompt、LLM、SQLite 和缓存
 * 分别由 WeatherRepository、OutfitRepository、WardrobeRepository 完成。</p>
 */
public class MainViewModel extends AndroidViewModel {

    private static final long MIN_WEATHER_REFRESH_MS = 1500L;
    private static final long AUTO_AI_DEBOUNCE_MS = 10_000L;

    private final WardrobeRepository wardrobeRepository;
    private final WeatherRepository weatherRepository;
    private final OutfitRepository outfitRepository;
    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());
    private final MutableLiveData<MainUiState> uiState =
            new MutableLiveData<>();
    private final androidx.lifecycle.Observer<List<ClothingItem>>
            wardrobeObserver = ignored -> onWardrobeChanged();

    private String currentMode;
    private String currentGender;
    private boolean weatherRequestInFlight;
    private boolean aiRequestInFlight;
    private long weatherRequestStartedAt;
    private long lastAutoRequestTime;
    private long messageSequence;
    private boolean cacheDecisionPending = true;
    private boolean autoGenerationAttempted;
    private boolean preserveContentAcrossCacheCheck;
    private int loadingStage;
    private final Runnable loadingTicker = new Runnable() {
        @Override
        public void run() {
            if (!getState().isAiLoading()) {
                return;
            }
            loadingStage = (loadingStage + 1) % 3;
            String text = getApplication().getString(
                    loadingStage == 0
                            ? R.string.ai_loading_weather
                            : loadingStage == 1
                            ? R.string.ai_loading_wardrobe
                            : R.string.ai_loading_styling);
            update(state -> state.buildUpon().loadingText(text));
            mainHandler.postDelayed(this, 1400L);
        }
    };
    private final Runnable midnightResetRunnable = () -> {
        cancelAi();
        cacheDecisionPending = false;
        autoGenerationAttempted = false;
        clearInvalidCacheForToday();
        maybeAutoGenerate();
        scheduleMidnightReset();
    };

    public MainViewModel(@NonNull Application application) {
        super(application);
        weatherRepository = new WeatherRepository(application);
        wardrobeRepository = WardrobeRepository.getInstance(application);
        outfitRepository = new OutfitRepository(application);
        currentMode = RecommendationMode.get(application);
        currentGender = StyleProfileManager.getGender(application);
        uiState.setValue(new MainUiState.Builder()
                .mode(currentMode)
                .scene(SceneInputStore.get(application, currentMode))
                .genderSelected(StyleProfileManager.hasGender(application))
                .build());
        wardrobeRepository.getAllItems()
                .observeForever(wardrobeObserver);
        refreshOutfitCount();
        restoreCurrentCache();
        scheduleMidnightReset();
    }

    public LiveData<MainUiState> getUiState() {
        return uiState;
    }

    public void setScene(String scene) {
        update(state -> state.buildUpon()
                .scene(scene == null ? "" : scene));
    }

    public void onSceneInputSaved() {
        SceneInputStore.save(getApplication(), currentMode,
                getState().getScene());
    }

    public void selectMode(String selectedMode) {
        String normalized = RecommendationMode.normalize(selectedMode);
        if (normalized.equals(currentMode)) {
            return;
        }
        String previousMode = currentMode;
        SceneInputStore.save(getApplication(), currentMode,
                getState().getScene());
        currentMode = normalized;
        RecommendationMode.save(getApplication(), currentMode);
        String restored = SceneInputStore.get(
                getApplication(), currentMode);
        MainUiState beforeSwitch = getState();
        boolean hasVisibleContent =
                !beforeSwitch.getAiContent().isEmpty()
                        || !beforeSwitch.getAiPlans().isEmpty()
                        || !beforeSwitch.getPackingItems().isEmpty();
        // 目标旅行模式只有已有输入时才可能有打包缓存，空输入仍按原逻辑处理。
        boolean targetCanShowContent =
                !RecommendationMode.isTravel(currentMode)
                        || !restored.trim().isEmpty();
        boolean preserveContent =
                hasVisibleContent && targetCanShowContent;
        preserveContentAcrossCacheCheck = preserveContent;
        update(state -> {
            MainUiState.Builder builder = state.buildUpon()
                    .mode(currentMode)
                    .scene(restored)
                    .aiLoading(false);
            if (!preserveContent) {
                builder.aiContent("")
                        .aiDate("")
                        .aiPlans(Collections.emptyList())
                        .packingItems(Collections.emptyList())
                        .packingListText("")
                        .contentType(MainUiState.ContentType.IDLE);
            }
            return builder;
        });
        cancelAi();
        cacheDecisionPending = true;
        autoGenerationAttempted = false;
        restoreCurrentCache();
    }

    public void saveGender(String gender) {
        StyleProfileManager.saveGender(getApplication(), gender);
        currentGender = StyleProfileManager.normalizeGender(gender);
        update(state -> state.buildUpon().genderSelected(true));
        updateStateWithMessage(getApplication().getString(
                R.string.gender_saved,
                getApplication().getString(
                        StyleProfileManager.getLabelResId(currentGender))));
        refreshWeather(false);
        cacheDecisionPending = true;
        autoGenerationAttempted = false;
        restoreCurrentCache();
    }

    public void refreshWeather(boolean forceRefresh) {
        if (weatherRequestInFlight) {
            return;
        }
        weatherRequestStartedAt = SystemClock.elapsedRealtime();
        weatherRequestInFlight = true;
        update(state -> state.buildUpon().weatherLoading(true));
        weatherRepository.refresh(getApplication(), forceRefresh,
                new WeatherRepository.Callback() {
                    @Override
                    public void onLocationName(String locationName) {
                        update(state -> state.buildUpon()
                                .locationName(locationName));
                    }

                    @Override
                    public void onWeather(WeatherInfo weather,
                                          String alert) {
                        update(state -> state.buildUpon()
                                .weatherInfo(weather)
                                .weatherAlert(alert));
                    }

                    @Override
                    public void onError(String message) {
                        updateStateWithMessage(message);
                    }

                    @Override
                    public void onFinished() {
                        weatherRequestInFlight = false;
                        long elapsed = SystemClock.elapsedRealtime()
                                - weatherRequestStartedAt;
                        long delay = Math.max(0L,
                                MIN_WEATHER_REFRESH_MS - elapsed);
                        mainHandler.postDelayed(() -> {
                            update(state -> state.buildUpon()
                                    .weatherLoading(false));
                            maybeAutoGenerate();
                        }, delay);
                    }
                });
    }

    /**
     * 进入页面时优先复用 30 分钟持久化缓存，只有缺失或过期才发起网络请求。
     */
    public void ensureWeather() {
        WeatherInfo cached = weatherRepository.getCachedWeather();
        if (cached != null) {
            update(state -> state.buildUpon()
                    .weatherLoading(false)
                    .weatherInfo(cached)
                    .weatherAlert(com.example.wardrobe.util.WeatherAdvisor
                            .buildAlert(getApplication(), cached))
                    .locationName(
                            weatherRepository.getCachedLocationLabel()));
            maybeAutoGenerate();
            return;
        }
        refreshWeather(false);
    }

    public void generateCurrent(boolean manual) {
        if (aiRequestInFlight || !getState().hasGender()) {
            if (!getState().hasGender()) {
                updateStateWithMessage(getApplication().getString(
                        R.string.gender_required));
            }
            return;
        }
        MainUiState state = getState();
        if (!NetworkUtils.isNetworkAvailable(getApplication())) {
            publishAiFailure(getApplication().getString(
                    R.string.network_unavailable));
            return;
        }
        if (!ApiConfig.isLlmConfigured(getApplication())) {
            publishAiFailure(getApplication().getString(
                    R.string.ai_need_llm_key));
            return;
        }
        if (RecommendationMode.isTravel(currentMode)) {
            generateTravel(manual);
            return;
        }
        if (state.getWeatherInfo() == null) {
            publishAiFailure(getApplication().getString(
                    R.string.ai_need_weather));
            return;
        }
        if (state.getWardrobeCount() == 0) {
            publishAiFailure(getApplication().getString(
                    R.string.ai_need_clothes));
            return;
        }
        beginAiRequest(manual);
        outfitRepository.generateDaily(
                getApplication(),
                state.getWeatherInfo(),
                currentMode,
                state.getScene(),
                currentGender,
                manual,
                state.getAiContent(),
                currentSceneOrDefault(state),
                outfitCallback());
    }

    public void setTravelState(ClothingItem item,
                               boolean travel,
                               WardrobeRepository.ResultCallback<Integer> callback) {
        wardrobeRepository.setItemTravel(item.getId(), travel,
                new WardrobeRepository.ResultCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer rows) {
                        if (rows != null && rows > 0) {
                            updateStateWithMessage(getApplication().getString(
                                    travel
                                            ? R.string.travel_item_added
                                            : R.string.travel_item_removed));
                        }
                        callback.onSuccess(rows);
                    }

                    @Override
                    public void onError(String message) {
                        updateStateWithMessage(message);
                        callback.onError(message);
                    }
                });
    }

    public void clearTravel(
            WardrobeRepository.ResultCallback<Integer> callback) {
        wardrobeRepository.clearTravelItems(callback);
    }

    public void onScreenResume() {
        scheduleMidnightReset();
        String savedGender = StyleProfileManager.getGender(getApplication());
        if (!savedGender.equals(currentGender)) {
            currentGender = savedGender;
            cancelAi();
            update(state -> state.buildUpon()
                    .genderSelected(StyleProfileManager.hasGender(getApplication()))
                    .aiContent("")
                    .aiDate("")
                    .aiPlans(Collections.emptyList())
                    .packingItems(Collections.emptyList())
                    .packingListText("")
                    .contentType(MainUiState.ContentType.IDLE));
            cacheDecisionPending = true;
            autoGenerationAttempted = false;
            restoreCurrentCache();
        }
        if (!getState().getAiDate().isEmpty()
                && !AiOutfitCache.today().equals(cacheDate(getState()))) {
            clearInvalidCacheForToday();
            cacheDecisionPending = true;
            autoGenerationAttempted = false;
            restoreCurrentCache();
        }
        refreshOutfitCount();
        wardrobeRepository.refreshAll();
        WeatherInfo current = getState().getWeatherInfo();
        boolean stale = current == null
                || System.currentTimeMillis() - current.getFetchedAt()
                > 30 * 60 * 1000L;
        if (stale) {
            if (NetworkUtils.isNetworkAvailable(getApplication())) {
                ensureWeather();
            }
        } else {
            maybeAutoGenerate();
        }
        maybeBackfillDescriptions();
    }

    public void cancelActiveRequests() {
        cancelAll();
    }

    private void generateTravel(boolean manual) {
        MainUiState state = getState();
        beginAiRequest(manual);
        if (state.getScene().trim().isEmpty()) {
            if (state.getWeatherInfo() == null) {
                finishAiWithError(getApplication().getString(
                        R.string.ai_need_weather));
                return;
            }
            outfitRepository.generateTravelDaily(
                    getApplication(),
                    state.getWeatherInfo(),
                    currentGender,
                    outfitCallback());
        } else {
            outfitRepository.generateTravelPacking(
                    getApplication(),
                    state.getScene(),
                    currentGender,
                    outfitCallback());
        }
    }

    private OutfitRepository.Callback outfitCallback() {
        return new OutfitRepository.Callback() {
            @Override
            public void onSuccess(OutfitRepository.OutfitResult result) {
                aiRequestInFlight = false;
                cacheDecisionPending = false;
                preserveContentAcrossCacheCheck = false;
                stopLoadingTicker();
                MainUiState.Builder builder = getState().buildUpon()
                        .aiLoading(false)
                        .loadingText("")
                        .aiContent(result.getContent())
                        .aiDate(AiOutfitCache.today())
                        .packingListText(result.getPackingListText())
                        .aiPlans(result.getPlans())
                        .packingItems(result.getPackingItems());
                if (RecommendationMode.isTravel(currentMode)) {
                    builder.scene(result.getScene());
                }
                if (result.isPacking()) {
                    builder.contentType(MainUiState.ContentType.PACKING);
                } else if (!result.getPlans().isEmpty()) {
                    builder.contentType(MainUiState.ContentType.PLANS);
                } else {
                    builder.contentType(MainUiState.ContentType.TEXT);
                }
                publish(builder);
                if (!result.getNotice().isEmpty()) {
                    updateStateWithMessage(result.getNotice());
                }
                maybeBackfillDescriptions();
            }

            @Override
            public void onNoCache() {
                aiRequestInFlight = false;
                cacheDecisionPending = false;
                stopLoadingTicker();
                if (preserveContentAcrossCacheCheck) {
                    boolean hasVisibleContent =
                            !getState().getAiContent().isEmpty()
                                    || !getState().getAiPlans().isEmpty()
                                    || !getState().getPackingItems().isEmpty();
                    if (hasVisibleContent
                            && startGenerationAfterModeSwitch()) {
                        preserveContentAcrossCacheCheck = false;
                        return;
                    }
                }
                preserveContentAcrossCacheCheck = false;
                publish(getState().buildUpon()
                        .aiLoading(false)
                        .loadingText("")
                        .contentType(MainUiState.ContentType.IDLE)
                        .aiContent("")
                        .aiDate("")
                        .aiPlans(Collections.emptyList())
                        .packingItems(Collections.emptyList())
                        .packingListText(""));
                maybeAutoGenerate();
            }

    /**
     * 模式切换没有缓存时，在保留旧卡片的前提下直接发起新生成，
     * 避免先清空内容造成闪烁。返回 false 表示前置条件不足。
     */
    private boolean startGenerationAfterModeSwitch() {
        MainUiState state = getState();
        if (!state.isTravelMode()) {
            return startDailyGenerationAfterModeSwitch(state);
        }
        if (state.getScene().trim().isEmpty()) {
            return false;
        }
        if (!state.hasGender()
                || !ApiConfig.isLlmConfigured(getApplication())
                || !NetworkUtils.isNetworkAvailable(getApplication())) {
            return false;
        }
        autoGenerationAttempted = true;
        beginAiRequest(false);
        outfitRepository.generateTravelPacking(
                getApplication(),
                state.getScene(),
                currentGender,
                outfitCallback());
        return true;
    }

    private boolean startDailyGenerationAfterModeSwitch(MainUiState state) {
        if (!state.hasGender()
                || state.getWeatherInfo() == null
                || state.getWardrobeCount() == 0
                || !ApiConfig.isLlmConfigured(getApplication())
                || !NetworkUtils.isNetworkAvailable(getApplication())) {
            return false;
        }
        autoGenerationAttempted = true;
        beginAiRequest(false);
        outfitRepository.generateDaily(
                getApplication(),
                state.getWeatherInfo(),
                currentMode,
                state.getScene(),
                currentGender,
                false,
                state.getAiContent(),
                currentSceneOrDefault(state),
                outfitCallback());
        return true;
    }

            @Override
            public void onError(String message) {
                cacheDecisionPending = false;
                preserveContentAcrossCacheCheck = false;
                finishAiWithError(message);
            }
        };
    }

    private void restoreCurrentCache() {
        if (RecommendationMode.isTravel(currentMode)) {
            outfitRepository.restoreTravel(
                    getApplication(),
                    getState().getScene(),
                    currentGender,
                    outfitCallback());
        } else {
            outfitRepository.restoreDaily(
                    getApplication(),
                    currentMode,
                    getState().getScene(),
                    currentGender,
                    outfitCallback());
        }
    }

    private void maybeAutoGenerate() {
        MainUiState state = getState();
        if (cacheDecisionPending || autoGenerationAttempted
                || state.isAiLoading() || state.getWeatherInfo() == null
                || state.isTravelMode()
                || !state.hasGender()
                || state.getWardrobeCount() == 0
                || !ApiConfig.isLlmConfigured(getApplication())
                || !NetworkUtils.isNetworkAvailable(getApplication())) {
            return;
        }
        if (!state.getAiContent().isEmpty()
                || !state.getAiPlans().isEmpty()
                || !state.getPackingItems().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAutoRequestTime < AUTO_AI_DEBOUNCE_MS) {
            return;
        }
        lastAutoRequestTime = now;
        autoGenerationAttempted = true;
        generateCurrent(false);
    }

    private void beginAiRequest(boolean manual) {
        aiRequestInFlight = true;
        startLoadingTicker();
        MainUiState.Builder builder = getState().buildUpon()
                .aiLoading(true)
                .loadingText(getApplication().getString(
                        R.string.ai_loading_weather));
        if (manual) {
            builder.aiContent("")
                    .aiDate("")
                    .aiPlans(Collections.emptyList())
                    .packingItems(Collections.emptyList())
                    .packingListText("")
                    .contentType(MainUiState.ContentType.IDLE);
        }
        publish(builder);
    }

    private void finishAiWithError(String message) {
        aiRequestInFlight = false;
        cacheDecisionPending = false;
        stopLoadingTicker();
        publishErrorState(message);
        updateStateWithMessage(message);
    }

    private void publishAiFailure(String message) {
        publishErrorState(message);
        updateStateWithMessage(message);
    }

    private void publishErrorState(String message) {
        MainUiState state = getState();
        MainUiState.Builder builder = state.buildUpon()
                .aiLoading(false)
                .loadingText("");
        if (state.getAiPlans().isEmpty()
                && state.getPackingItems().isEmpty()
                && state.getAiContent().trim().isEmpty()) {
            builder.aiContent(message)
                    .contentType(MainUiState.ContentType.TEXT);
        }
        publish(builder);
    }

    private void onWardrobeChanged() {
        List<ClothingItem> items =
                wardrobeRepository.getAllItemsSnapshot();
        update(state -> state.buildUpon().wardrobeCount(items.size()));
        maybeAutoGenerate();
        MainUiState state = getState();
        if (state.getContentType() == MainUiState.ContentType.PACKING
                && !state.getAiContent().isEmpty()) {
            outfitRepository.rebuildPacking(
                    state.getAiContent(),
                    currentSceneOrDefault(state),
                    outfitCallback());
        }
    }

    private void refreshOutfitCount() {
        outfitRepository.loadOutfitCount(count ->
                update(state -> state.buildUpon().outfitCount(count)));
    }

    private void clearInvalidCacheForToday() {
        AiOutfitCache.clear(
                getApplication(), currentMode,
                currentSceneOrDefault(getState()), currentGender);
        if (RecommendationMode.isTravel(currentMode)
                && getState().getScene().trim().isEmpty()) {
            AiOutfitCache.clear(
                    getApplication(), RecommendationMode.TRAVEL,
                    OutfitRepository.TRAVEL_DAILY_SCENE, currentGender);
        }
        update(state -> state.buildUpon()
                .aiContent("")
                .aiDate("")
                .aiPlans(Collections.emptyList())
                .packingItems(Collections.emptyList())
                .packingListText("")
                .contentType(MainUiState.ContentType.IDLE));
    }

    private String cacheDate(MainUiState state) {
        return state.getAiDate();
    }

    private String currentSceneOrDefault(MainUiState state) {
        return getRequestedScene(currentMode, state.getScene());
    }

    private String getRequestedScene(String mode, String scene) {
        return RecommendationMode.isTravel(mode)
                ? (scene == null ? "" : scene.trim())
                : (scene == null || scene.trim().isEmpty())
                ? AiOutfitCache.DEFAULT_SCENE
                : AiOutfitCache.normalizeScene(scene);
    }

    private void maybeBackfillDescriptions() {
        if (!ApiConfig.isLlmConfigured(getApplication())
                || !NetworkUtils.isNetworkAvailable(getApplication())
                || ItemDescriptionGenerator.isBackfillRunning()
                || getState().getWardrobeCount() == 0) {
            return;
        }
        if (ItemDescriptionGenerator.countMissingDescriptions(
                getApplication()) > 0) {
            ItemDescriptionGenerator.backfillMissing(
                    getApplication(), null);
        }
    }

    private void updateStateWithMessage(String message) {
        long id = ++messageSequence;
        update(state -> state.buildUpon().message(message, id));
    }

    private MainUiState getState() {
        MainUiState state = uiState.getValue();
        return state == null ? new MainUiState.Builder().build() : state;
    }

    private void publish(MainUiState.Builder builder) {
        uiState.setValue(builder.build());
    }

    private void update(StateUpdater updater) {
        publish(updater.update(getState()));
    }

    private interface StateUpdater {
        MainUiState.Builder update(MainUiState state);
    }

    private void cancelAi() {
        outfitRepository.cancel();
        aiRequestInFlight = false;
        stopLoadingTicker();
    }

    private void cancelAll() {
        outfitRepository.cancel();
        weatherRepository.cancel();
        aiRequestInFlight = false;
        weatherRequestInFlight = false;
        stopLoadingTicker();
    }

    private void startLoadingTicker() {
        mainHandler.removeCallbacks(loadingTicker);
        loadingStage = 0;
        mainHandler.postDelayed(loadingTicker, 1400L);
    }

    private void stopLoadingTicker() {
        mainHandler.removeCallbacks(loadingTicker);
    }

    private void scheduleMidnightReset() {
        mainHandler.removeCallbacks(midnightResetRunnable);
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime nextMidnight = LocalDate.now(zone)
                .plusDays(1)
                .atStartOfDay(zone);
        long delay = Math.max(1000L,
                Duration.between(now, nextMidnight).toMillis());
        mainHandler.postDelayed(midnightResetRunnable, delay);
    }

    @Override
    protected void onCleared() {
        wardrobeRepository.getAllItems()
                .removeObserver(wardrobeObserver);
        mainHandler.removeCallbacksAndMessages(null);
        cancelAll();
        super.onCleared();
    }
}
