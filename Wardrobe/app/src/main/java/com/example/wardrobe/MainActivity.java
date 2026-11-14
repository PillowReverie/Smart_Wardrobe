package com.example.wardrobe;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.wardrobe.adapter.AiOutfitPlanAdapter;
import com.example.wardrobe.adapter.TravelPackingItemAdapter;
import com.example.wardrobe.data.WardrobeRepository;
import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.Outfit;
import com.example.wardrobe.model.WeatherInfo;
import com.example.wardrobe.ui.state.MainUiState;
import com.example.wardrobe.util.OutfitSaver;
import com.example.wardrobe.util.RecommendationMode;
import com.example.wardrobe.util.StyleProfileManager;
import com.example.wardrobe.util.UiUtils;
import com.example.wardrobe.viewmodel.MainViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 首页 View。
 *
 * <p>只处理权限、输入、导航、Adapter 和保存对话框。天气、缓存、Prompt、
 * 大模型与 SQLite 业务全部由 MainViewModel 和 Repository 完成。</p>
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
    private MaterialButton aiGenerateButton;
    private TextInputLayout sceneInputLayout;
    private TextInputEditText sceneInput;

    private MainViewModel mainViewModel;
    private AlertDialog genderDialog;
    private MainUiState lastState = new MainUiState.Builder().build();
    private MainUiState.ContentType lastContentType =
            MainUiState.ContentType.IDLE;
    private long lastMessageId;
    private boolean initializingSceneInput;


    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean granted =
                                Boolean.TRUE.equals(result.get(
                                        Manifest.permission.ACCESS_FINE_LOCATION))
                                        || Boolean.TRUE.equals(result.get(
                                        Manifest.permission.ACCESS_COARSE_LOCATION));
                        if (!granted) {
                            UiUtils.showMessage(rootView, getString(
                                    R.string.permission_location_denied));
                        }
                        mainViewModel.refreshWeather(false);
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootView = findViewById(R.id.root);
        mainViewModel = new ViewModelProvider(this)
                .get(MainViewModel.class);
        bindViews();
        setupResultLists();
        setupActions();
        setupSceneInput();
        mainViewModel.getUiState().observe(this, this::renderUiState);
        ensureLocationPermissionThenLoad();
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

    private void setupResultLists() {
        aiPlanAdapter = new AiOutfitPlanAdapter(this::saveSinglePlan);
        aiPlanList.setLayoutManager(new LinearLayoutManager(this));
        aiPlanList.setAdapter(aiPlanAdapter);
        aiPlanList.setItemAnimator(null);
        aiPlanList.setNestedScrollingEnabled(false);

        travelPackingItemAdapter = new TravelPackingItemAdapter(
                this::toggleTravelItem);
        travelPackingItemList.setLayoutManager(
                new LinearLayoutManager(
                        this, LinearLayoutManager.HORIZONTAL, false));
        travelPackingItemList.setAdapter(travelPackingItemAdapter);
        travelPackingItemList.setItemAnimator(null);
        travelPackingItemList.setNestedScrollingEnabled(false);
    }

    private void setupActions() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(this::handleMenuItemClick);
        findViewById(R.id.openWardrobeButton).setOnClickListener(
                view -> openActivity(WardrobeActivity.class));
        findViewById(R.id.openOutfitsButton).setOnClickListener(
                view -> openActivity(OutfitActivity.class));
        findViewById(R.id.aiModeInspirationButton).setOnClickListener(
                view -> mainViewModel.selectMode(
                        RecommendationMode.INSPIRATION));
        findViewById(R.id.aiModeClassicButton).setOnClickListener(
                view -> mainViewModel.selectMode(
                        RecommendationMode.CLASSIC));
        findViewById(R.id.aiModeTravelButton).setOnClickListener(
                view -> mainViewModel.selectMode(
                        RecommendationMode.TRAVEL));
        aiGenerateButton.setOnClickListener(
                view -> submitGeneration());
        swipeRefresh.setColorSchemeResources(R.color.md_theme_primary);
        swipeRefresh.setProgressBackgroundColorSchemeResource(
                R.color.md_theme_surface);
        swipeRefresh.setOnRefreshListener(
                () -> mainViewModel.refreshWeather(true));
    }

    private void setupSceneInput() {
        sceneInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s,
                                          int start,
                                          int count,
                                          int after) {
            }

            @Override
            public void onTextChanged(CharSequence s,
                                      int start,
                                      int before,
                                      int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!initializingSceneInput) {
                    mainViewModel.setScene(
                            s == null ? "" : s.toString());
                }
            }
        });
        sceneInput.setOnEditorActionListener((view, actionId, event) -> {
            boolean done = actionId == EditorInfo.IME_ACTION_DONE;
            boolean enter = event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (done || enter) {
                submitGeneration();
                return true;
            }
            return false;
        });
    }

    private void ensureLocationPermissionThenLoad() {
        boolean granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            mainViewModel.refreshWeather(false);
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void submitGeneration() {
        hideKeyboardAndClearFocus();
        mainViewModel.onSceneInputSaved();
        mainViewModel.generateCurrent(true);
    }

    /**
     * 全页唯一的状态渲染入口。
     */
    private void renderUiState(MainUiState state) {
        lastState = state;
        renderWeather(state);
        renderModeChrome(state);
        renderSceneInput(state);
        renderAiContent(state);
        renderSummary(state);
        renderMessage(state);
        renderGenderRequirement(state);

        boolean showLoading = state.isAiLoading();
        aiLoadingPanel.setVisibility(
                showLoading ? View.VISIBLE : View.GONE);
        aiLoadingText.setText(showLoading
                ? state.getLoadingText()
                : "");
        aiGenerateButton.setEnabled(
                !showLoading && state.hasGender());
    }

    private void renderWeather(MainUiState state) {
        weatherProgress.setVisibility(
                state.isWeatherLoading() ? View.VISIBLE : View.GONE);
        swipeRefresh.setRefreshing(state.isWeatherLoading());
        swipeRefresh.setEnabled(!state.isWeatherLoading());
        if (!state.getLocationName().isEmpty()) {
            weatherLocation.setText(state.getLocationName());
        } else {
            weatherLocation.setText(state.isWeatherLoading()
                    ? R.string.home_weather_locating
                    : R.string.home_weather_location);
        }

        WeatherInfo weather = state.getWeatherInfo();
        if (state.isWeatherLoading()) {
            weatherCondition.setText(R.string.home_weather_loading);
        } else if (weather != null) {
            weatherCondition.setText(getString(
                    R.string.weather_day_summary_format,
                    weather.getTempMin(),
                    weather.getTempMax(),
                    weather.getConditionText()));
        } else {
            weatherCondition.setText(R.string.home_weather_unavailable);
        }

        boolean showAlert = !state.isWeatherLoading()
                && !state.getWeatherAlert().isEmpty();
        weatherAlertText.setVisibility(showAlert ? View.VISIBLE : View.GONE);
        weatherAlertText.setText(state.getWeatherAlert());
    }

    private void renderModeChrome(MainUiState state) {
        boolean travel = state.isTravelMode();
        sceneInputLayout.setHint(getString(travel
                ? R.string.home_travel_scene_hint
                : R.string.home_ai_scene_hint));
        sceneInputLayout.setError(null);
        aiTitle.setText(travel
                ? state.isTravelInputEmpty()
                ? R.string.home_travel_daily_title
                : R.string.home_travel_title
                : R.string.home_ai_title);
        aiGenerateButton.setText(travel
                ? state.isTravelInputEmpty()
                ? R.string.home_travel_generate_daily
                : R.string.home_travel_generate
                : R.string.home_ai_generate);
        updateModeButton(
                R.id.aiModeInspirationButton,
                RecommendationMode.INSPIRATION.equals(state.getMode()));
        updateModeButton(
                R.id.aiModeClassicButton,
                RecommendationMode.CLASSIC.equals(state.getMode()));
        updateModeButton(
                R.id.aiModeTravelButton,
                RecommendationMode.TRAVEL.equals(state.getMode()));
    }

    private void updateModeButton(int id, boolean selected) {
        MaterialButton button = findViewById(id);
        button.setSelected(selected);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this,
                                selected
                                        ? R.color.tab_checked_background
                                        : android.R.color.transparent)));
        button.setTextColor(ContextCompat.getColor(this,
                selected
                        ? R.color.tab_checked_text
                        : R.color.md_theme_onSurfaceVariant));
    }

    private void renderSceneInput(MainUiState state) {
        if (sceneInput.hasFocus()) {
            return;
        }
        CharSequence current = sceneInput.getText();
        String text = current == null ? "" : current.toString();
        if (!Objects.equals(text, state.getScene())) {
            initializingSceneInput = true;
            sceneInput.setText(state.getScene());
            sceneInput.setSelection(state.getScene().length());
            initializingSceneInput = false;
        }
    }

    private void renderAiContent(MainUiState state) {
        boolean typeChanged = lastContentType != state.getContentType();
        switch (state.getContentType()) {
            case PLANS:
                hidePacking();
                aiContent.setVisibility(View.GONE);
                aiPlanAdapter.submitList(state.getAiPlans());
                aiPlanList.setVisibility(View.VISIBLE);
                break;
            case PACKING:
                aiPlanAdapter.submitList(Collections.emptyList());
                aiContent.setVisibility(View.GONE);
                aiPlanList.setVisibility(View.GONE);
                travelPackingImagesTitle.setVisibility(View.VISIBLE);
                travelPackingItemList.setVisibility(View.VISIBLE);
                travelPackingItemAdapter.submitList(
                        state.getPackingItems());
                boolean hasChecklist =
                        !state.getPackingListText().isEmpty();
                travelPackingTextTitle.setVisibility(
                        hasChecklist ? View.VISIBLE : View.GONE);
                travelPackingText.setVisibility(
                        hasChecklist ? View.VISIBLE : View.GONE);
                travelPackingText.setText(state.getPackingListText());
                break;
            case TEXT:
                hidePacking();
                aiPlanAdapter.submitList(Collections.emptyList());
                aiPlanList.setVisibility(View.GONE);
                aiContent.setVisibility(View.VISIBLE);
                aiContent.setText(state.getAiContent());
                break;
            case IDLE:
            default:
                hidePacking();
                aiPlanAdapter.submitList(Collections.emptyList());
                aiPlanList.setVisibility(View.GONE);
                boolean hasAiText =
                        !state.getAiContent().isEmpty();
                aiContent.setVisibility(
                        hasAiText || !state.isTravelMode()
                                ? View.VISIBLE : View.GONE);
                if (state.isAiLoading() && !hasAiText) {
                    aiContent.setText(R.string.ai_thinking);
                } else if (hasAiText) {
                    aiContent.setText(state.getAiContent());
                } else {
                    aiContent.setText(R.string.home_ai_desc);
                }
                break;
        }
        if (typeChanged) {
            findViewById(R.id.aiContentScroll).scrollTo(0, 0);
            lastContentType = state.getContentType();
        }
    }

    private void hidePacking() {
        travelPackingImagesTitle.setVisibility(View.GONE);
        travelPackingItemList.setVisibility(View.GONE);
        travelPackingTextTitle.setVisibility(View.GONE);
        travelPackingText.setVisibility(View.GONE);
    }

    private void renderSummary(MainUiState state) {
        wardrobeSummary.setText(getString(
                R.string.home_summary_format,
                state.getWardrobeCount(),
                state.getOutfitCount()));
        overviewHint.setText(state.getWardrobeCount() == 0
                ? R.string.home_overview_empty
                : R.string.home_overview_ready);
    }

    private void renderMessage(MainUiState state) {
        if (state.getMessageId() > lastMessageId
                && !state.getMessage().isEmpty()) {
            lastMessageId = state.getMessageId();
            UiUtils.showMessage(rootView, state.getMessage());
        }
    }

    private void renderGenderRequirement(MainUiState state) {
        if (!state.hasGender()) {
            showGenderDialog();
        }
    }

    private void showGenderDialog() {
        if (genderDialog != null && genderDialog.isShowing()) {
            return;
        }
        final String[] values = new String[]{
                StyleProfileManager.GENDER_FEMALE,
                StyleProfileManager.GENDER_MALE,
                StyleProfileManager.GENDER_NEUTRAL
        };
        final String[] labels = new String[]{
                getString(R.string.gender_female),
                getString(R.string.gender_male),
                getString(R.string.gender_neutral)
        };
        final String[] selected = new String[]{null};
        genderDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.gender_dialog_title)
                .setSingleChoiceItems(labels, -1,
                        (dialog, which) -> selected[0] = values[which])
                .setPositiveButton(R.string.gender_dialog_confirm, null)
                .setCancelable(false)
                .create();
        genderDialog.setOnShowListener(ignored ->
                genderDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(view -> {
                            if (selected[0] == null) {
                                UiUtils.showMessage(rootView, getString(
                                        R.string.gender_required));
                                return;
                            }
                            mainViewModel.saveGender(selected[0]);
                            genderDialog.dismiss();
                        }));
        genderDialog.setOnDismissListener(dialog -> genderDialog = null);
        genderDialog.show();
    }

    private void saveSinglePlan(AiOutfitPlan plan) {
        if (plan == null || plan.getRawContent().trim().isEmpty()) {
            return;
        }
        String defaultName = getString(
                R.string.outfit_default_today,
                UiUtils.formatMonthDay(System.currentTimeMillis()))
                + " · 方案" + plan.getSequence();
        OutfitSaver.showSaveDialog(
                this,
                defaultName,
                plan.getRawContent(),
                Outfit.SOURCE_TODAY,
                lastState.getWeatherInfo(),
                matchedItems(plan),
                saved -> {
                    if (saved) {
                        aiPlanAdapter.markSaved(plan);
                    }
                });
    }

    private List<ClothingItem> matchedItems(AiOutfitPlan plan) {
        List<ClothingItem> result = new ArrayList<>();
        for (AiOutfitPlan.VisualItem visual : plan.getVisualItems()) {
            ClothingItem item = visual.getClothingItem();
            if (item != null && item.getId() > 0) {
                result.add(item);
            }
        }
        return result;
    }

    private void toggleTravelItem(ClothingItem item, boolean add) {
        travelPackingItemAdapter.markPending(item.getId());
        mainViewModel.setTravelState(item, add,
                new WardrobeRepository.ResultCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer rows) {
                        if (rows == null || rows <= 0) {
                            travelPackingItemAdapter.clearPending(
                                    item.getId());
                            return;
                        }
                        if (add) {
                            travelPackingItemAdapter.markAdded(item.getId());
                        } else {
                            travelPackingItemAdapter.markRemoved(item.getId());
                        }
                    }

                    @Override
                    public void onError(String message) {
                        travelPackingItemAdapter.clearPending(item.getId());
                    }
                });
    }

    private void hideKeyboardAndClearFocus() {
        sceneInput.clearFocus();
        InputMethodManager manager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(
                    sceneInput.getWindowToken(), 0);
        }
    }

    private boolean handleMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            openActivity(AboutActivity.class);
            return true;
        }
        return false;
    }

    private void openActivity(Class<?> target) {
        startActivity(new Intent(this, target));
    }

    @Override
    protected void onPause() {
        mainViewModel.onSceneInputSaved();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        aiPlanAdapter.clearSavedMarks();
        mainViewModel.onScreenResume();
    }

    @Override
    protected void onDestroy() {
        if (genderDialog != null) {
            genderDialog.dismiss();
            genderDialog = null;
        }
        if (isFinishing()) {
            mainViewModel.cancelActiveRequests();
        }
        super.onDestroy();
    }
}
