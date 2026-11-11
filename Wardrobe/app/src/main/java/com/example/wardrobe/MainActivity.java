package com.example.wardrobe;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.wardrobe.adapter.AiOutfitPlanAdapter;
import com.example.wardrobe.adapter.TravelPackingItemAdapter;
import com.example.wardrobe.data.WardrobeRepository;
import com.example.wardrobe.db.WardrobeDao;
import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.Outfit;
import com.example.wardrobe.model.WeatherInfo;
import com.example.wardrobe.network.LLMService;
import com.example.wardrobe.network.dto.ChatResponse;
import com.example.wardrobe.util.ApiConfig;
import com.example.wardrobe.util.AiOutfitCache;
import com.example.wardrobe.util.AiOutfitParser;
import com.example.wardrobe.util.ItemDescriptionGenerator;
import com.example.wardrobe.util.LocationHelper;
import com.example.wardrobe.util.NetworkUtils;
import com.example.wardrobe.util.OutfitSaver;
import com.example.wardrobe.util.PromptBuilder;
import com.example.wardrobe.util.RecommendationMode;
import com.example.wardrobe.util.SceneInputStore;
import com.example.wardrobe.util.StyleProfileManager;
import com.example.wardrobe.util.TravelPackingParser;
import com.example.wardrobe.util.UiUtils;
import com.example.wardrobe.util.WeatherManager;
import com.example.wardrobe.util.WeatherAdvisor;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.example.wardrobe.ui.state.TravelUiState;
import com.example.wardrobe.viewmodel.MainViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;

/**
 * 首页：天气感知与主动式今日穿搭。
 *
 * <p>先异步获取定位和天气，再读取衣橱并调用通用大模型。AI 请求全部通过
 * enqueue 执行，Activity 销毁时会取消当前 Call。首页同时管理加载、成功、
 * 失败和重试四种 UI 状态。</p>
 */
public class MainActivity extends BaseActivity {

    private View rootView;
    private TextView weatherLocation;
    private TextView weatherCondition;
    private TextView weatherAlertText;
    private View weatherProgress;
    private SwipeRefreshLayout swipeRefresh;
    private TextView wardrobeSummary;
    private TextView overviewHint;

    private View aiLoadingPanel;
    private TextView aiLoadingText;
    private TextView aiTitle;
    private TextView aiContent;
    private RecyclerView aiPlanList;
    private AiOutfitPlanAdapter aiPlanAdapter;
    private TextView travelPackingImagesTitle;
    private RecyclerView travelPackingItemList;
    private TravelPackingItemAdapter travelPackingItemAdapter;
    private TextView travelPackingTextTitle;
    private TextView travelPackingText;
    private View aiGenerateButton;
    private TextInputLayout sceneInputLayout;
    private TextInputEditText sceneInput;

    private WardrobeDao wardrobeDao;
    private MainViewModel mainViewModel;
    private WeatherInfo currentWeather;
    private String currentAiContent;
    private String currentAiDate;
    private String currentAiScene = AiOutfitCache.DEFAULT_SCENE;
    private String currentMode = RecommendationMode.INSPIRATION;
    private String currentGender = StyleProfileManager.GENDER_NEUTRAL;
    private Call<ChatResponse> aiCall;
    private int aiRequestToken;
    private boolean weatherRequestInFlight;
    private boolean forceWeatherRefresh;
    private long weatherRequestStartedAt;
    private long lastAutoRequestTime = 0L;
    private final Handler refreshHandler =
            new Handler(Looper.getMainLooper());
    private boolean aiRequestInFlight;
    private boolean initializingModeSelection;
    private AlertDialog genderDialog;
    private final Handler loadingHandler =
            new Handler(Looper.getMainLooper());
    private int loadingStage;

    /**
     * 圆形加载指示器下方的三段动态文案。
     */
    private final Runnable loadingTextRunnable = new Runnable() {
        @Override
        public void run() {
            if (aiLoadingText == null
                    || aiLoadingPanel.getVisibility() != View.VISIBLE) {
                return;
            }
            loadingStage = (loadingStage + 1) % 3;
            aiLoadingText.setText(loadingStage == 0
                    ? R.string.ai_loading_weather
                    : loadingStage == 1
                    ? R.string.ai_loading_wardrobe
                    : R.string.ai_loading_styling);
            loadingHandler.postDelayed(this, 1400L);
        }
    };

    private void showAiLoading() {
        loadingStage = 0;
        loadingHandler.removeCallbacks(loadingTextRunnable);
        aiLoadingPanel.setVisibility(View.VISIBLE);
        aiLoadingText.setText(R.string.ai_loading_weather);
        loadingHandler.postDelayed(loadingTextRunnable, 1400L);
    }

    private void hideAiLoading() {
        loadingHandler.removeCallbacks(loadingTextRunnable);
        aiLoadingPanel.setVisibility(View.GONE);
    }

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean fineGranted = Boolean.TRUE.equals(
                                result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                        boolean coarseGranted = Boolean.TRUE.equals(
                                result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                        if (fineGranted || coarseGranted) {
                            loadLocationAndWeather();
                        } else {
                            weatherRequestInFlight = false;
                            Toast.makeText(this,
                                    R.string.permission_location_denied,
                                    Toast.LENGTH_SHORT).show();
                            requestLocationAndWeather(
                                    LocationHelper.DEFAULT_LATITUDE,
                                    LocationHelper.DEFAULT_LONGITUDE,
                                    true);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootView = findViewById(R.id.root);
        wardrobeDao = new WardrobeDao(this);
        mainViewModel = new ViewModelProvider(this)
                .get(MainViewModel.class);
        currentGender = StyleProfileManager.getGender(this);
        bindViews();
        setupAiResultList();
        setupNavigation();
        setupModeSwitch();
        setupSceneInput();
        observeMainViewModel();
        if (StyleProfileManager.hasGender(this)) {
            loadTodayCachedOutfit();
            startWeatherFlow();
        } else {
            showInitialGenderDialog();
        }
    }

    @Override
    protected int layoutRes() {
        return R.layout.activity_main;
    }

    private void bindViews() {
        weatherLocation = findViewById(R.id.weatherLocation);
        weatherCondition = findViewById(R.id.weatherCondition);
        weatherAlertText = findViewById(R.id.weatherAlertText);
        weatherProgress = findViewById(R.id.weatherProgress);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        wardrobeSummary = findViewById(R.id.wardrobeSummary);
        overviewHint = findViewById(R.id.overviewHint);

        aiLoadingPanel = findViewById(R.id.aiLoadingPanel);
        aiLoadingText = findViewById(R.id.aiLoadingText);
        aiTitle = findViewById(R.id.aiTitle);
        aiContent = findViewById(R.id.aiContent);
        aiPlanList = findViewById(R.id.aiPlanList);
        travelPackingImagesTitle =
                findViewById(R.id.travelPackingImagesTitle);
        travelPackingItemList =
                findViewById(R.id.travelPackingItemList);
        travelPackingTextTitle =
                findViewById(R.id.travelPackingTextTitle);
        travelPackingText = findViewById(R.id.travelPackingText);
        aiGenerateButton = findViewById(R.id.aiGenerateButton);
        sceneInputLayout = findViewById(R.id.sceneInputLayout);
        sceneInput = findViewById(R.id.sceneInput);
    }

    private void setupAiResultList() {
        aiPlanAdapter = new AiOutfitPlanAdapter(this::saveSingleAiPlan);
        aiPlanList.setLayoutManager(new LinearLayoutManager(this));
        aiPlanList.setAdapter(aiPlanAdapter);
        aiPlanList.setItemAnimator(null);
        aiPlanList.setNestedScrollingEnabled(false);

        travelPackingItemAdapter = new TravelPackingItemAdapter(
                this::toggleTravelPackingItem);
        travelPackingItemList.setLayoutManager(
                new LinearLayoutManager(
                        this, LinearLayoutManager.HORIZONTAL, false));
        travelPackingItemList.setAdapter(travelPackingItemAdapter);
        travelPackingItemList.setItemAnimator(null);
        travelPackingItemList.setNestedScrollingEnabled(false);
    }

    private void setupNavigation() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(this::handleMenuItemClick);

        findViewById(R.id.openWardrobeButton).setOnClickListener(view ->
                openActivity(WardrobeActivity.class));
        findViewById(R.id.openOutfitsButton).setOnClickListener(view ->
                openActivity(OutfitActivity.class));
        swipeRefresh.setColorSchemeResources(R.color.md_theme_primary);
        swipeRefresh.setProgressBackgroundColorSchemeResource(
                R.color.md_theme_surface);
        swipeRefresh.setOnRefreshListener(
                this::refreshWeatherFromPull);

        aiGenerateButton.setOnClickListener(view -> submitSceneOutfit());
    }

    /**
     * 场景输入支持按钮和键盘“完成/回车”两种提交方式。
     * 提交入口统一收敛到 submitSceneOutfit，避免两套逻辑产生不同缓存。
     */
    private void setupSceneInput() {
        sceneInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s,
                                          int start,
                                          int count,
                                          int after) {
                // 只需要在内容变化后更新旅行模式按钮。
            }

            @Override
            public void onTextChanged(CharSequence s,
                                      int start,
                                      int before,
                                      int count) {
                if (RecommendationMode.isTravel(currentMode)) {
                    updateSceneHint();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 输入内容由 SceneInputStore 在页面切出时统一保存。
            }
        });
        sceneInput.setOnEditorActionListener((view, actionId, event) -> {
            boolean doneAction = actionId == EditorInfo.IME_ACTION_DONE;
            boolean enterKey = event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (!doneAction && !enterKey) {
                return false;
            }
            submitSceneOutfit();
            return true;
        });
    }

    /**
     * 订阅共享衣橱和旅行状态。Activity 不直接决定业务结果，只把
     * TravelUiState 映射到对应视图。
     */
    private void observeMainViewModel() {
        mainViewModel.getTravelUiState().observe(
                this, this::renderTravelState);
        mainViewModel.getTravelRequestInFlight().observe(this, loading -> {
            boolean inFlight = Boolean.TRUE.equals(loading);
            aiRequestInFlight = inFlight;
            if (inFlight) {
                aiGenerateButton.setEnabled(false);
            }
        });
        mainViewModel.getAllItems().observe(this, ignored -> {
            refreshWardrobeSummary();
            if (RecommendationMode.isTravel(currentMode)) {
                mainViewModel.refreshTravelPackingBindings();
            }
        });
    }

    private void renderTravelState(TravelUiState state) {
        if (state == null || !RecommendationMode.isTravel(currentMode)) {
            return;
        }
        switch (state.getKind()) {
            case LOADING:
                aiRequestInFlight = true;
                setAiLoading(false);
                break;
            case PACKING:
                aiRequestInFlight = false;
                showTravelPacking(state);
                break;
            case OUTFIT:
                aiRequestInFlight = false;
                showTravelOutfit(state);
                break;
            case ERROR:
                aiRequestInFlight = false;
                hideAiLoading();
                aiGenerateButton.setEnabled(true);
                Toast.makeText(this, state.getMessage(),
                        Toast.LENGTH_SHORT).show();
                if (!hasCurrentAiContent()) {
                    showAiError(state.getMessage());
                }
                break;
            case IDLE:
            default:
                aiRequestInFlight = false;
                break;
        }
    }

    /**
     * 首次启动时引导用户选择性别偏好。
     *
     * <p>选项写入 SharedPreferences 后才会允许 AI 请求。对话框不可取消，
     * 避免用户在没有完成选择时进入半初始状态；后续可在设置页修改。</p>
     */
    private void showInitialGenderDialog() {
        if (genderDialog != null && genderDialog.isShowing()) {
            return;
        }
        final String[] genderValues = new String[]{
                StyleProfileManager.GENDER_FEMALE,
                StyleProfileManager.GENDER_MALE,
                StyleProfileManager.GENDER_NEUTRAL
        };
        final String[] labels = new String[]{
                getString(R.string.gender_female),
                getString(R.string.gender_male),
                getString(R.string.gender_neutral)
        };
        int checkedIndex = -1;
        if (StyleProfileManager.hasGender(this)) {
            for (int i = 0; i < genderValues.length; i++) {
                if (genderValues[i].equals(currentGender)) {
                    checkedIndex = i;
                    break;
                }
            }
        }
        final String[] selectedGender = new String[]{
                checkedIndex >= 0 ? genderValues[checkedIndex] : null
        };

        genderDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.gender_dialog_title)
                .setSingleChoiceItems(labels, checkedIndex,
                        (dialog, which) ->
                                selectedGender[0] = genderValues[which])
                .setPositiveButton(R.string.gender_dialog_confirm, null)
                .setCancelable(false)
                .create();
        genderDialog.setOnShowListener(ignored ->
                genderDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            if (selectedGender[0] == null) {
                                Toast.makeText(MainActivity.this,
                                        R.string.gender_required,
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            StyleProfileManager.saveGender(
                                    MainActivity.this, selectedGender[0]);
                            currentGender = selectedGender[0];
                            genderDialog.dismiss();
                            String label = getString(
                                    StyleProfileManager.getLabelResId(
                                            currentGender));
                            Toast.makeText(MainActivity.this,
                                    getString(R.string.gender_saved, label),
                                    Toast.LENGTH_SHORT).show();
                            startWeatherFlow();
                            loadTodayCachedOutfit();
                            maybeGenerateTodayOutfit();
                        }));
        genderDialog.setOnDismissListener(dialog -> genderDialog = null);
        genderDialog.show();
    }

    /**
     * 初始化“灵感焕新 / 经典套装”切换。
     *
     * <p>先恢复上次选择，再注册监听，避免程序化 check() 触发一次
     * 无意义的清缓存和网络请求。</p>
     */
    private void setupModeSwitch() {
        currentMode = RecommendationMode.get(this);
        restoreSceneInputForMode();
        initializingModeSelection = true;
        initializingModeSelection = false;
        updateModeButtons(getSelectedModeButtonId());
        updateSceneHint();
        showAiIdle();

        findViewById(R.id.aiModeInspirationButton).setOnClickListener(
                view -> selectRecommendationMode(
                        RecommendationMode.INSPIRATION,
                        R.id.aiModeInspirationButton));
        findViewById(R.id.aiModeClassicButton).setOnClickListener(
                view -> selectRecommendationMode(
                        RecommendationMode.CLASSIC,
                        R.id.aiModeClassicButton));
        findViewById(R.id.aiModeTravelButton).setOnClickListener(
                view -> selectRecommendationMode(
                        RecommendationMode.TRAVEL,
                        R.id.aiModeTravelButton));
    }

    private int getSelectedModeButtonId() {
        if (RecommendationMode.isTravel(currentMode)) {
            return R.id.aiModeTravelButton;
        }
        if (RecommendationMode.isClassic(currentMode)) {
            return R.id.aiModeClassicButton;
        }
        return R.id.aiModeInspirationButton;
    }

    private void selectRecommendationMode(String selectedMode,
                                          int selectedButtonId) {
        if (initializingModeSelection || selectedMode.equals(currentMode)) {
            return;
        }
        String oldInput = getRawSceneInput();
        boolean wasTravel = RecommendationMode.isTravel(currentMode);
        currentMode = selectedMode;
        RecommendationMode.save(this, currentMode);
        updateModeButtons(selectedButtonId);
        if (wasTravel != RecommendationMode.isTravel(currentMode)) {
            SceneInputStore.save(this,
                    wasTravel ? RecommendationMode.TRAVEL
                            : RecommendationMode.INSPIRATION,
                    oldInput);
            restoreSceneInputForMode();
        }
        onRecommendationModeChanged();
    }

    private void updateModeButtons(int selectedButtonId) {
        int[] ids = new int[]{
                R.id.aiModeInspirationButton,
                R.id.aiModeClassicButton,
                R.id.aiModeTravelButton
        };
        for (int id : ids) {
            MaterialButton button = findViewById(id);
            boolean selected = id == selectedButtonId;
            button.setSelected(selected);
            button.setBackgroundTintList(android.content.res.ColorStateList
                    .valueOf(androidx.core.content.ContextCompat.getColor(
                            this,
                            selected
                                    ? R.color.tab_checked_background
                                    : android.R.color.transparent)));
            button.setTextColor(androidx.core.content.ContextCompat.getColor(
                    this,
                    selected
                            ? R.color.tab_checked_text
                            : R.color.md_theme_onSurfaceVariant));
        }
    }

    /**
     * 恢复当前分组的输入：日常穿搭共用一份，异地游玩单独一份。
     */
    private void restoreSceneInputForMode() {
        if (sceneInput == null) {
            return;
        }
        String saved = SceneInputStore.get(this, currentMode);
        sceneInput.setText(saved);
        sceneInput.setSelection(saved.length());
    }

    /**
     * 切换模式后取消旧请求，优先显示该模式今天已有的缓存；
     * 没有缓存且条件满足时自动生成一批。
     */
    private void onRecommendationModeChanged() {
        if (aiCall != null) {
            aiCall.cancel();
            aiCall = null;
        }
        aiRequestToken++;
        aiRequestInFlight = false;
        currentAiContent = null;
        currentAiDate = null;
        updateSceneHint();
        String scene = RecommendationMode.isTravel(currentMode)
                ? getRawSceneInput()
                : getRequestedScene();
        currentAiScene = scene;
        aiPlanAdapter.submitList(Collections.emptyList());
        showAiIdle();

        if (RecommendationMode.isTravel(currentMode)) {
            loadTodayCachedOutfit();
            return;
        }

        String cachedContent = AiOutfitCache.loadTodayContent(
                this, currentMode, scene, currentGender);
        if (cachedContent != null) {
            currentAiContent = cachedContent;
            currentAiDate = AiOutfitCache.today();
            setAiSuccess(cachedContent);
            return;
        }

        if (!NetworkUtils.isNetworkAvailable(this)) {
            interceptAiRequest(getString(R.string.network_unavailable));
            return;
        }
        if (currentWeather == null) {
            // 天气还没回来时由 requestWeather 的 onSuccess 自动触发生成。
            return;
        }
        if (wardrobeDao.getItemCount() == 0) {
            interceptAiRequest(getString(R.string.ai_need_clothes));
            return;
        }
        if (!ApiConfig.isLlmConfigured(this)) {
            interceptAiRequest(getString(R.string.ai_need_llm_key));
            return;
        }
        requestTodayOutfit(scene);
    }

    /**
     * 当天缓存直接展示，不等待天气或网络。跨天时 loadTodayContent 会自动
     * 删除旧值，后续由天气流程触发今日重新生成。
     */
    private boolean loadTodayCachedOutfit() {
        if (RecommendationMode.isTravel(currentMode)) {
            // 旅行缓存由 ViewModel 根据“输入是否为空”恢复：
            // 非空是打包清单，空输入是旅行衣橱当天穿搭。
            mainViewModel.restoreTravelCache(
                    getRawSceneInput(), currentGender);
            return false;
        }
        String scene = RecommendationMode.isTravel(currentMode)
                ? getRawSceneInput()
                : getRequestedScene();
        if (scene.isEmpty()) {
            return false;
        }
        currentAiScene = scene;
        String cachedContent = AiOutfitCache.loadTodayContent(
                this, currentMode, scene, currentGender);
        if (cachedContent == null) {
            return false;
        }
        applyLoadedAi(scene, cachedContent);
        return true;
    }

    private void applyLoadedAi(String scene, String content) {
        currentAiScene = scene;
        currentAiContent = content;
        currentAiDate = AiOutfitCache.today();
        setAiSuccess(content);
    }

    private void startWeatherFlow() {
        startWeatherFlow(false);
    }

    /**
     * 下拉刷新先清除天气内存缓存，再重新定位和请求。
     */
    private void refreshWeatherFromPull() {
        if (weatherRequestInFlight) {
            return;
        }
        WeatherManager.getInstance().invalidateMemoryCache();
        startWeatherFlow(true);
    }

    private void startWeatherFlow(boolean forceRefresh) {
        weatherRequestStartedAt = SystemClock.elapsedRealtime();
        if (weatherRequestInFlight) {
            return;
        }
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.network_unavailable,
                    Toast.LENGTH_SHORT).show();
            setWeatherLoading(false);
            finishWeatherRefreshAnimation();
            return;
        }
        weatherRequestInFlight = true;
        forceWeatherRefresh = forceRefresh;
        setWeatherLoading(true);

        if (LocationHelper.hasLocationPermission(this)) {
            loadLocationAndWeather();
            return;
        }
        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void loadLocationAndWeather() {
        weatherLocation.setText(R.string.home_weather_locating);
        LocationHelper.requestCurrentLocation(this,
                (latitude, longitude, usedDefault) -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    weatherLocation.setText(usedDefault
                            ? R.string.home_weather_default_location
                            : R.string.home_weather_location_loading);
                    requestLocationAndWeather(latitude, longitude, usedDefault);
                });
    }

    /**
     * 地名和天气并行请求。任一接口失败都不会覆盖另一个接口已拿到的结果。
     */
    private void requestLocationAndWeather(double latitude,
                                           double longitude,
                                           boolean usedDefault) {
        requestLocationName(latitude, longitude, usedDefault);
        requestWeather(latitude, longitude, usedDefault);
    }

    private void requestLocationName(double latitude,
                                     double longitude,
                                     boolean usedDefault) {
        WeatherManager.getInstance().fetchLocationName(
                this,
                latitude,
                longitude,
                new WeatherManager.LocationCallback() {
                    @Override
                    public void onSuccess(String locationName,
                                          boolean fromCache) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        weatherLocation.setText(locationName);
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        weatherLocation.setText(usedDefault
                                ? R.string.home_weather_default_location
                                : R.string.home_weather_location);
                    }
                });
    }

    private void requestWeather(double latitude,
                                double longitude,
                                boolean usedDefault) {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            weatherRequestInFlight = false;
            forceWeatherRefresh = false;
            setWeatherLoading(false);
            finishWeatherRefreshAnimation();
            Toast.makeText(this, R.string.network_unavailable,
                    Toast.LENGTH_SHORT).show();
            if (currentAiContent == null && !aiRequestInFlight) {
                showAiError(getString(R.string.network_unavailable));
            }
            return;
        }
        if (!ApiConfig.isWeatherConfigured(this)) {
            weatherRequestInFlight = false;
            forceWeatherRefresh = false;
            setWeatherLoading(false);
            finishWeatherRefreshAnimation();
            weatherCondition.setText(R.string.weather_not_configured);
            handleAiFailure(getString(R.string.weather_not_configured));
            return;
        }

        WeatherManager.getInstance().fetchWeather(
                this,
                latitude,
                longitude,
                forceWeatherRefresh,
                new WeatherManager.Callback() {
                    @Override
                    public void onSuccess(WeatherInfo weatherInfo,
                                          boolean fromCache) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        weatherRequestInFlight = false;
                        forceWeatherRefresh = false;
                        currentWeather = weatherInfo;
                        setWeatherLoading(false);
                        weatherCondition.setText(getString(
                                R.string.weather_day_summary_format,
                                weatherInfo.getTempMin(),
                                weatherInfo.getTempMax(),
                                weatherInfo.getConditionText()));
                        updateWeatherAlert(weatherInfo);
                        finishWeatherRefreshAnimation();
                        maybeGenerateTodayOutfit();
                        // 天气请求成功说明网络已就绪；冷启动时 ConnectivityManager
                        // 可能短暂返回不可用，这里补一次自动描述补全检查。
                        if (hasCurrentAiContent() && !aiRequestInFlight) {
                            maybeBackfillItemDescriptions();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        weatherRequestInFlight = false;
                        forceWeatherRefresh = false;
                        setWeatherLoading(false);
                        if (currentWeather == null) {
                            weatherCondition.setText(
                                    R.string.home_weather_unavailable);
                            weatherAlertText.setVisibility(View.GONE);
                        }
                        finishWeatherRefreshAnimation();
                        Toast.makeText(MainActivity.this, message,
                                Toast.LENGTH_SHORT).show();
                        if (currentAiContent == null
                                && !aiRequestInFlight) {
                            showAiError(getString(R.string.ai_need_weather));
                        }
                    }
                });
    }

    private void setWeatherLoading(boolean loading) {
        weatherProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        swipeRefresh.setEnabled(!loading);
        if (loading) {
            weatherCondition.setText(R.string.home_weather_loading);
        }
    }

    /**
     * 天气 UI 更新完毕后再结束下拉动画，且保证至少展示 1.5 秒。
     */
    private void finishWeatherRefreshAnimation() {
        long elapsed = SystemClock.elapsedRealtime()
                - weatherRequestStartedAt;
        long delay = Math.max(0L, 1500L - elapsed);
        refreshHandler.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                swipeRefresh.setRefreshing(false);
            }
        }, delay);
    }

    private void updateWeatherAlert(WeatherInfo weatherInfo) {
        String alert = WeatherAdvisor.buildAlert(this, weatherInfo);
        if (alert.isEmpty()) {
            weatherAlertText.setVisibility(View.GONE);
            return;
        }
        weatherAlertText.setText(alert);
        weatherAlertText.setVisibility(View.VISIBLE);
    }

    /**
     * 用户主动点击“按场景生成”或右上角刷新时调用。
     *
     * <p>手动生成始终发起新请求，成功后覆盖同日期、同模式和同场景缓存；
     * 请求期间清空旧结果并展示思考状态，失败时再把旧结果恢复回来。</p>
     */
    private void submitSceneOutfit() {
        if (isFinishing() || isDestroyed() || aiRequestInFlight) {
            return;
        }
        if (RecommendationMode.isTravel(currentMode)) {
            submitTravelOutfit();
            return;
        }
        if (!StyleProfileManager.hasGender(this)) {
            showInitialGenderDialog();
            return;
        }
        hideKeyboardAndClearFocus();
        SceneInputStore.save(this, currentMode, getRawSceneInput());
        String scene = getRequestedScene();
        // 输入框为空时显式补上默认场景，避免 Prompt 和界面状态不一致。
        CharSequence rawScene = sceneInput.getText();
        if (rawScene == null || rawScene.toString().trim().isEmpty()) {
            sceneInput.setText(scene);
            sceneInput.setSelection(scene.length());
        }
        if (!NetworkUtils.isNetworkAvailable(this)) {
            interceptAiRequest(getString(R.string.network_unavailable));
            return;
        }
        if (!hasGenerationPreconditions()) {
            interceptAiRequest(getPreconditionMessage());
            return;
        }
        generateTodayOutfit(scene, true);
    }

    /**
     * 异地游玩：解析城市和天数 → 查询目的地天气 → 生成旅行打包清单。
     */
    private void submitTravelOutfit() {
        if (!StyleProfileManager.hasGender(this)) {
            showInitialGenderDialog();
            return;
        }
        String rawInput = getRawSceneInput();
        sceneInputLayout.setError(null);
        hideKeyboardAndClearFocus();
        SceneInputStore.save(this, currentMode, getRawSceneInput());

        if (!NetworkUtils.isNetworkAvailable(this)) {
            interceptAiRequest(getString(R.string.network_unavailable));
            return;
        }
        if (!ApiConfig.isLlmConfigured(this)) {
            interceptAiRequest(getString(R.string.ai_need_llm_key));
            return;
        }
        final String requestGender = StyleProfileManager.getGender(this);
        currentGender = requestGender;
        if (rawInput.isEmpty()) {
            submitTravelDailyOutfit(requestGender);
            return;
        }

        if (mainViewModel.getAllItems().getValue() == null
                || mainViewModel.getAllItems().getValue().isEmpty()) {
            interceptAiRequest(getString(R.string.ai_need_clothes));
            return;
        }
        if (!ApiConfig.isWeatherConfigured(this)) {
            interceptAiRequest(getString(R.string.weather_not_configured));
            return;
        }
        aiRequestInFlight = true;
        setAiLoading(false);
        mainViewModel.generateTravelPacking(rawInput, requestGender);
    }

    /**
     * 旅行模式输入为空时，仅从旅行衣橱生成当天穿搭。
     */
    private void submitTravelDailyOutfit(String requestGender) {
        if (mainViewModel.getTravelItems().getValue() == null
                || mainViewModel.getTravelItems().getValue().isEmpty()) {
            interceptAiRequest(getString(
                    R.string.travel_wardrobe_need_items));
            return;
        }
        if (currentWeather == null) {
            interceptAiRequest(getString(R.string.ai_need_weather));
            return;
        }
        aiRequestInFlight = true;
        setAiLoading(false);
        mainViewModel.generateTravelDailyOutfit(
                currentWeather, requestGender);
    }

    /**
     * 当天已有结果时直接保持，不因天气刷新或页面返回而重复消耗模型额度。
     */
    private void maybeGenerateTodayOutfit() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        // 已有当天结果时保持缓存，天气刷新或页面返回都不应重复生成。
        if (hasCurrentAiContent()) {
            return;
        }
        if (RecommendationMode.isTravel(currentMode)) {
            // 异地游玩涉及付费天气和模型额度，只响应用户主动点击，
            // 不因天气刷新、页面返回而自动重复生成。
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAutoRequestTime < 10_000L) {
            return;
        }
        lastAutoRequestTime = now;
        if (!StyleProfileManager.hasGender(this) || currentWeather == null) {
            // 首次性别选择或天气尚未返回时，由对应回调重新触发。
            return;
        }
        requestTodayOutfit(getRequestedScene());
    }

    private void requestTodayOutfit(String scene) {
        if (!StyleProfileManager.hasGender(this)) {
            interceptAiRequest(getString(R.string.gender_required));
            return;
        }
        if (!NetworkUtils.isNetworkAvailable(this)) {
            interceptAiRequest(getString(R.string.network_unavailable));
            return;
        }
        if (currentWeather == null) {
            interceptAiRequest(getString(R.string.ai_need_weather));
            return;
        }
        if (wardrobeDao.getItemCount() == 0) {
            interceptAiRequest(getString(R.string.ai_need_clothes));
            return;
        }
        if (!ApiConfig.isLlmConfigured(this)) {
            interceptAiRequest(getString(R.string.ai_need_llm_key));
            return;
        }
        generateTodayOutfit(scene, false);
    }

    /**
     * 前置条件不满足时统一弹 Toast，并保持旧内容不消失。
     */
    private void interceptAiRequest(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        if (!hasCurrentAiContent()) {
            showAiError(message);
        }
    }

    private String getPreconditionMessage() {
        if (!StyleProfileManager.hasGender(this)) {
            return getString(R.string.gender_required);
        }
        if (currentWeather == null) {
            return getString(R.string.ai_need_weather);
        }
        if (wardrobeDao.getItemCount() == 0) {
            return getString(R.string.ai_need_clothes);
        }
        return getString(R.string.ai_need_llm_key);
    }

    private boolean hasGenerationPreconditions() {
        return StyleProfileManager.hasGender(this)
                && currentWeather != null
                && wardrobeDao.getItemCount() > 0
                && ApiConfig.isLlmConfigured(this)
                && NetworkUtils.isNetworkAvailable(this);
    }

    private void generateTodayOutfit(String scene, boolean manualRequest) {
        if (aiRequestInFlight || currentWeather == null) {
            return;
        }
        List<ClothingItem> items = wardrobeDao.getAllItems();
        if (items.isEmpty()) {
            handleAiFailure(getString(R.string.ai_need_clothes));
            return;
        }

        final List<Outfit> classicOutfits = wardrobeDao.getAllOutfitsWithItems();
        final boolean hasClassicOutfits = !classicOutfits.isEmpty();
        if (RecommendationMode.isClassic(currentMode) && !hasClassicOutfits) {
            Toast.makeText(this, R.string.ai_classic_empty_fallback,
                    Toast.LENGTH_SHORT).show();
        }

        final String previousContent = currentAiContent;
        final String previousDate = currentAiDate;
        final String previousScene = currentAiScene;
        final String requestMode = currentMode;
        final String requestScene = AiOutfitCache.normalizeScene(scene);
        final String requestGender = StyleProfileManager.getGender(this);
        final List<ClothingItem> previousItems =
                resolvePreviousRecommendedItems(
                        previousContent,
                        previousScene,
                        requestScene,
                        items,
                        classicOutfits);
        final int requestToken = ++aiRequestToken;
        currentAiScene = requestScene;
        currentGender = requestGender;
        // 手动生成先清空旧展示；自动请求仅在已有内容时保留当前画面。
        setAiLoading(!manualRequest && hasCurrentAiContent());
        aiRequestInFlight = true;
        String userPrompt = PromptBuilder.buildTodayOutfitPrompt(
                currentWeather,
                items,
                classicOutfits,
                requestScene,
                requestMode,
                requestGender,
                previousItems);
        String systemPrompt = PromptBuilder.buildTodaySystemPrompt(
                requestMode, hasClassicOutfits, requestGender);
        aiCall = LLMService.getInstance().generate(
                this,
                systemPrompt,
                userPrompt,
                new LLMService.Callback() {
                    @Override
                    public void onSuccess(String content) {
                        if (requestToken != aiRequestToken) {
                            return;
                        }
                        aiRequestInFlight = false;
                        aiCall = null;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        currentAiContent = content;
                        currentAiDate = AiOutfitCache.today();
                        currentAiScene = requestScene;
                        currentGender = requestGender;
                        AiOutfitCache.save(
                                MainActivity.this,
                                requestMode,
                                requestScene,
                                requestGender,
                                content);
                        setAiSuccess(content);
                    }

                    @Override
                    public void onError(String message) {
                        if (requestToken != aiRequestToken) {
                            return;
                        }
                        aiRequestInFlight = false;
                        aiCall = null;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        restorePreviousAiIfAvailable(
                                previousContent, previousDate, previousScene);
                        handleAiFailure(message);
                    }
                });
    }

    /**
     * 主动刷新失败时恢复旧缓存，避免杀进程后旧内容也一起消失。
     */
    private void restorePreviousAiIfAvailable(String previousContent,
                                              String previousDate,
                                              String previousScene) {
        if (previousContent == null || previousContent.trim().isEmpty()) {
            return;
        }
        currentAiContent = previousContent;
        currentAiDate = AiOutfitCache.today().equals(previousDate)
                ? previousDate : null;
        currentAiScene = previousScene == null
                ? AiOutfitCache.DEFAULT_SCENE : previousScene;
    }

    /**
     * 每套方案独立保存，关联衣物和正文均来自该方案，而不是合并后的三套文本。
     */
    private void saveSingleAiPlan(AiOutfitPlan plan) {
        if (plan == null || plan.getRawContent().trim().isEmpty()) {
            return;
        }
        String baseName = getString(
                R.string.outfit_default_today,
                UiUtils.formatMonthDay(System.currentTimeMillis()));
        String defaultName = baseName + " · 方案" + plan.getSequence();
        OutfitSaver.showSaveDialog(
                this,
                defaultName,
                plan.getRawContent(),
                Outfit.SOURCE_TODAY,
                currentWeather,
                resolvePlanItems(plan),
                saved -> {
                    if (saved) {
                        aiPlanAdapter.markSaved(plan);
                    }
                });
    }

    /**
     * 保存前汇总这一套实际关联的衣物 ID。
     *
     * <p>普通方案直接使用 AI 文本里【】匹配到的单品；经典套装方案
     * 还会按“经典来源”找到搭配库记录，把该套装的衣物全部补进来，
     * 防止模型漏写某一两个【】导致 outfit_items 关联不完整。</p>
     */
    private List<ClothingItem> resolvePlanItems(AiOutfitPlan plan) {
        return resolvePlanItems(
                plan, wardrobeDao.getAllOutfitsWithItems());
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

    /**
     * 只在上一次结果属于同一场景时提取衣物，避免用户换场景后仍被旧结果限制。
     */
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

    /**
     * 打包清单按“横向衣物图片 + 下方纯文本清单”展示。
     */
    private void showTravelPacking(TravelUiState state) {
        currentAiContent = state.getContent();
        currentAiDate = AiOutfitCache.today();
        currentAiScene = state.getScene();

        hideAiLoading();
        aiContent.setVisibility(View.GONE);
        aiPlanList.setVisibility(View.GONE);
        aiPlanAdapter.submitList(Collections.emptyList());

        String otherChecklist = TravelPackingParser
                .extractNonClothingSections(state.getContent());
        travelPackingImagesTitle.setVisibility(View.VISIBLE);
        travelPackingItemList.setVisibility(View.VISIBLE);
        travelPackingTextTitle.setVisibility(
                otherChecklist.isEmpty() ? View.GONE : View.VISIBLE);
        travelPackingText.setVisibility(
                otherChecklist.isEmpty() ? View.GONE : View.VISIBLE);
        travelPackingItemAdapter.submitList(state.getPackingItems());
        travelPackingText.setText(otherChecklist);

        findViewById(R.id.aiContentScroll).scrollTo(0, 0);
        aiGenerateButton.setVisibility(View.VISIBLE);
        aiGenerateButton.setEnabled(true);
    }

    /**
     * 旅行中穿搭复用首页三套图文方案，并允许逐套保存。
     */
    private void showTravelOutfit(TravelUiState state) {
        currentAiContent = state.getContent();
        currentAiDate = AiOutfitCache.today();
        currentAiScene = state.getScene();
        hideAiLoading();
        hideTravelPacking();

        List<AiOutfitPlan> plans = state.getOutfitPlans();
        aiPlanAdapter.submitList(plans);
        if (plans.isEmpty()) {
            aiContent.setVisibility(View.VISIBLE);
            aiContent.setText(state.getContent());
            aiPlanList.setVisibility(View.GONE);
        } else {
            aiContent.setVisibility(View.GONE);
            aiPlanList.setVisibility(View.VISIBLE);
        }
        findViewById(R.id.aiContentScroll).scrollTo(0, 0);
        aiGenerateButton.setVisibility(View.VISIBLE);
        aiGenerateButton.setEnabled(true);
    }

    private void hideTravelPacking() {
        if (travelPackingImagesTitle != null) {
            travelPackingImagesTitle.setVisibility(View.GONE);
        }
        if (travelPackingItemList != null) {
            travelPackingItemList.setVisibility(View.GONE);
        }
        if (travelPackingTextTitle != null) {
            travelPackingTextTitle.setVisibility(View.GONE);
        }
        if (travelPackingText != null) {
            travelPackingText.setVisibility(View.GONE);
        }
    }

    /**
     * 打包卡按钮支持双向切换：未加入时加入旅行衣橱，
     * 已加入时再次点击移出旅行衣橱。
     */
    private void toggleTravelPackingItem(ClothingItem item,
                                         boolean addToTravel) {
        if (item == null) {
            return;
        }
        travelPackingItemAdapter.markPending(item.getId());
        mainViewModel.setTravelState(item, addToTravel,
                new WardrobeRepository.ResultCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer rows) {
                        if (rows == null || rows <= 0) {
                            travelPackingItemAdapter.clearPending(item.getId());
                            Toast.makeText(MainActivity.this,
                                    R.string.travel_operation_in_progress,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (addToTravel) {
                            travelPackingItemAdapter.markAdded(item.getId());
                        } else {
                            travelPackingItemAdapter.markRemoved(item.getId());
                        }
                        UiUtils.showMessage(rootView, getString(
                                addToTravel
                                        ? R.string.travel_item_added
                                        : R.string.travel_item_removed));
                    }

                    @Override
                    public void onError(String message) {
                        travelPackingItemAdapter.clearPending(item.getId());
                        UiUtils.showMessage(rootView, message);
                    }
                });
    }

    private void setAiLoading(boolean preserveCurrentContent) {
        showAiLoading();
        if (!preserveCurrentContent) {
            hideTravelPacking();
            aiContent.setVisibility(View.VISIBLE);
            aiContent.setText(R.string.ai_thinking);
            aiPlanList.setVisibility(View.GONE);
            findViewById(R.id.aiContentScroll).scrollTo(0, 0);
        }
        aiGenerateButton.setVisibility(View.VISIBLE);
        aiGenerateButton.setEnabled(false);
    }

    private void setAiSuccess(String content) {
        if (RecommendationMode.isTravel(currentMode)) {
            hideAiLoading();
            aiPlanAdapter.submitList(Collections.emptyList());
            hideTravelPacking();
            aiContent.setVisibility(View.VISIBLE);
            aiContent.setText(content);
            aiPlanList.setVisibility(View.GONE);
            findViewById(R.id.aiContentScroll).scrollTo(0, 0);
            aiGenerateButton.setVisibility(View.VISIBLE);
            aiGenerateButton.setEnabled(true);
            return;
        }
        // AI 文本只负责数据来源，首页展示使用解析后的结构化图文方案。
        // 始终使用当前 SQLite 数据解析图片，避免缓存生成后衣物被删除仍引用旧对象。
        List<ClothingItem> sourceItems = wardrobeDao.getAllItems();
        List<Outfit> classicOutfits = wardrobeDao.getAllOutfitsWithItems();
        List<AiOutfitPlan> plans = AiOutfitParser.parse(
                content, sourceItems, classicOutfits);

        hideAiLoading();
        aiPlanAdapter.submitList(plans);
        if (plans.isEmpty()) {
            // 极端情况下模型未按【】格式输出，至少保留正文，避免卡片空白。
            aiContent.setVisibility(View.VISIBLE);
            aiContent.setText(content);
            aiPlanList.setVisibility(View.GONE);
        } else {
            aiContent.setVisibility(View.GONE);
            aiPlanList.setVisibility(View.VISIBLE);
        }
        findViewById(R.id.aiContentScroll).scrollTo(0, 0);
        aiGenerateButton.setVisibility(View.VISIBLE);
        aiGenerateButton.setEnabled(true);
        // 今天已有可见结果后再补全旧衣物描述，避免与本次穿搭请求抢并发额度。
        maybeBackfillItemDescriptions();
    }

    /**
     * 请求失败时若屏幕上已有当天结果，保持原内容并只提示错误。
     */
    private void handleAiFailure(String message) {
        hideAiLoading();
        aiGenerateButton.setEnabled(true);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        if (hasCurrentAiContent()) {
            aiContent.setVisibility(View.GONE);
            aiPlanList.setVisibility(View.VISIBLE);
            aiGenerateButton.setVisibility(View.VISIBLE);
            return;
        }
        showAiError(message);
    }

    private boolean hasCurrentAiContent() {
        return currentAiContent != null
                && !currentAiContent.trim().isEmpty();
    }

    private void showAiError(String message) {
        hideAiLoading();
        hideTravelPacking();
        aiContent.setVisibility(View.VISIBLE);
        aiContent.setText(message);
        aiPlanList.setVisibility(View.GONE);
        findViewById(R.id.aiContentScroll).scrollTo(0, 0);
        aiGenerateButton.setVisibility(View.VISIBLE);
        aiGenerateButton.setEnabled(true);
    }

    private boolean handleMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_about) {
            openActivity(AboutActivity.class);
            return true;
        }
        return false;
    }

    @Override
    protected void onPause() {
        SceneInputStore.save(this, currentMode, getRawSceneInput());
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (aiPlanAdapter != null) {
            aiPlanAdapter.clearSavedMarks();
        }
        refreshWardrobeSummary();
        refreshGenderProfileIfChanged();
        // 用户可能刚在设置页填好或修正 API 配置后返回首页。
        // 之前天气为空时自动补一次请求，避免页面停留在“请先配置天气 API”。
        if (currentWeather == null
                && ApiConfig.isWeatherConfigured(this)
                && NetworkUtils.isNetworkAvailable(this)) {
            startWeatherFlow();
        }
        // App 若停留过夜，回到前台时立即作废旧日期的内存结果。
        if (hasCurrentAiContent()
                && (currentAiDate == null
                || !currentAiDate.equals(AiOutfitCache.today()))) {
            currentAiContent = null;
            currentAiDate = null;
            AiOutfitCache.clear(
                    this, currentMode, currentAiScene, currentGender);
            currentAiScene = RecommendationMode.isTravel(currentMode)
                    ? getRawSceneInput() : getRequestedScene();
            showAiIdle();
        }
        // 从设置页或衣橱页返回后，如果前置条件已满足，自动补一次生成。
        if (currentAiContent == null
                && !aiRequestInFlight
                && StyleProfileManager.hasGender(this)
                && ApiConfig.isLlmConfigured(this)
                && wardrobeDao.getItemCount() > 0
                && (RecommendationMode.isTravel(currentMode)
                || currentWeather != null)) {
            maybeGenerateTodayOutfit();
        }
        // 首页已有结果时，顺带为旧版本遗留的无描述衣物补全 AI 资料。
        if (hasCurrentAiContent()) {
            maybeBackfillItemDescriptions();
        }
    }

    /**
     * 自动补全旧衣物缺失的隐藏描述。
     *
     * <p>只在配置完整、网络可用且确实存在缺失记录时启动。批量任务本身是
     * 串行的，且全局只允许一个任务运行；用户不需要重新进入编辑页保存。</p>
     */
    private void maybeBackfillItemDescriptions() {
        if (isFinishing() || isDestroyed()
                || !StyleProfileManager.hasGender(this)
                || !ApiConfig.isLlmConfigured(this)
                || !NetworkUtils.isNetworkAvailable(this)
                || wardrobeDao.getItemCount() == 0
                || ItemDescriptionGenerator.isBackfillRunning()) {
            return;
        }
        if (ItemDescriptionGenerator.countMissingDescriptions(this) == 0) {
            return;
        }
        // 首页自动补全不展示进度，完成后下次生成穿搭即可使用新描述。
        ItemDescriptionGenerator.backfillMissing(this, null);
    }

    /**
     * 设置页修改性别后，旧结果立即失效。先尝试读取新性别今天的缓存，
     * 没有缓存时由 onResume 末尾的自动生成逻辑补一次请求。
     */
    private void refreshGenderProfileIfChanged() {
        String savedGender = StyleProfileManager.getGender(this);
        if (savedGender.equals(currentGender)) {
            return;
        }
        currentGender = savedGender;
        if (aiCall != null) {
            aiCall.cancel();
            aiCall = null;
        }
        aiRequestToken++;
        aiRequestInFlight = false;
        currentAiContent = null;
        currentAiDate = null;
        currentAiScene = getRequestedScene();
        aiPlanAdapter.submitList(Collections.emptyList());
        showAiIdle();
        loadTodayCachedOutfit();
    }

    private void showAiIdle() {
        hideAiLoading();
        hideTravelPacking();
        if (RecommendationMode.isTravel(currentMode)) {
            aiContent.setVisibility(View.GONE);
        } else {
            aiContent.setVisibility(View.VISIBLE);
            aiContent.setText(R.string.home_ai_desc);
        }
        aiPlanList.setVisibility(View.GONE);
        aiGenerateButton.setVisibility(View.VISIBLE);
        aiGenerateButton.setEnabled(true);
    }

    /**
     * 输入框为空时使用“日常通勤”，否则使用去除首尾空白后的用户场景。
     */
    private String getRequestedScene() {
        CharSequence text = sceneInput == null ? null : sceneInput.getText();
        return AiOutfitCache.normalizeScene(text == null ? "" : text.toString());
    }

    /**
     * 旅行模式保留用户原始输入，例如“洛阳 3天”，不做默认场景替换。
     */
    private String getRawSceneInput() {
        CharSequence text = sceneInput == null ? null : sceneInput.getText();
        return text == null ? "" : text.toString().trim();
    }

    /**
     * 灵感/经典模式提示“今天想去哪”，旅行模式提示“想去哪里玩”。
     */
    private void updateSceneHint() {
        if (sceneInputLayout == null) {
            return;
        }
        boolean travel = RecommendationMode.isTravel(currentMode);
        boolean travelInputEmpty = travel
                && getRawSceneInput().isEmpty();
        sceneInputLayout.setHint(getString(travel
                ? R.string.home_travel_scene_hint
                : R.string.home_ai_scene_hint));
        sceneInputLayout.setError(null);
        if (aiTitle != null) {
            aiTitle.setText(travel
                    ? travelInputEmpty
                    ? R.string.home_travel_daily_title
                    : R.string.home_travel_title
                    : R.string.home_ai_title);
        }
        if (aiGenerateButton instanceof MaterialButton) {
            ((MaterialButton) aiGenerateButton).setText(travel
                    ? travelInputEmpty
                    ? R.string.home_travel_generate_daily
                    : R.string.home_travel_generate
                    : R.string.home_ai_generate);
        }
    }

    /**
     * 提交场景后收起键盘，避免键盘遮挡刚出现的 AI 结果。
     */
    private void hideKeyboardAndClearFocus() {
        if (sceneInput == null) {
            return;
        }
        sceneInput.clearFocus();
        InputMethodManager manager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(sceneInput.getWindowToken(), 0);
        }
    }

    private void refreshWardrobeSummary() {
        List<ClothingItem> liveItems =
                mainViewModel.getAllItems().getValue();
        int itemCount = liveItems == null
                ? wardrobeDao.getItemCount() : liveItems.size();
        int outfitCount = wardrobeDao.getOutfitCount();
        wardrobeSummary.setText(
                getString(R.string.home_summary_format, itemCount, outfitCount));
        overviewHint.setText(itemCount == 0
                ? R.string.home_overview_empty
                : R.string.home_overview_ready);
    }

    private void openActivity(Class<?> targetActivity) {
        startActivity(new Intent(this, targetActivity));
    }

    @Override
    protected void onDestroy() {
        loadingHandler.removeCallbacks(loadingTextRunnable);
        refreshHandler.removeCallbacksAndMessages(null);
        if (genderDialog != null) {
            genderDialog.dismiss();
            genderDialog = null;
        }
        aiRequestToken++;
        if (aiCall != null) {
            aiCall.cancel();
            aiCall = null;
        }
        WeatherManager.getInstance().cancelActiveCalls();
        if (isFinishing()) {
            mainViewModel.cancelActiveRequests();
        }
        super.onDestroy();
    }
}
