package com.example.wardrobe;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.wardrobe.db.WardrobeDao;
import com.example.wardrobe.util.ApiConfig;
import com.example.wardrobe.util.DataMigrationManager;
import com.example.wardrobe.util.StyleProfileManager;
import com.example.wardrobe.util.ThemeManager;
import com.example.wardrobe.util.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 设置页。
 *
 * <p>提供和风天气 Host/Key 与 LLM Base URL/Key/Model Name 五项配置。
 * 源码中没有任何真实 API Key，所有值只在用户保存后写入 SharedPreferences。
 * 后续网络请求阶段会通过 ApiConfig 的 isXxxConfigured() 做前置拦截。
 * 同时提供跟随系统、浅色、深色三种主题选择；为满足六 Activity 硬性要求，
 * 本页面继续保留 AboutActivity 类名，仅将用户可见标题改为“设置”。</p>
 */
public class AboutActivity extends BaseActivity {

    private View rootView;
    private TextInputLayout weatherHostInput;
    private TextInputLayout weatherKeyInput;
    private TextInputLayout llmBaseUrlInput;
    private TextInputLayout llmApiKeyInput;
    private TextInputLayout llmModelNameInput;
    private TextView configStatusText;
    private TextView versionText;
    private TextView dbStatusText;
    private View dataMigrationProgress;
    private TextView dataMigrationStatusText;
    private View exportDataButton;
    private View importDataButton;
    private View themeModeGroup;
    private View genderProfileGroup;
    private WardrobeDao wardrobeDao;
    private boolean initializingThemeSelection;
    private boolean initializingGenderSelection;
    private boolean migrationInProgress;

    private final ExecutorService migrationExecutor =
            Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> createBackupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/zip"),
                    uri -> {
                        if (uri != null) {
                            startExport(uri);
                        }
                    });

    private final ActivityResultLauncher<String[]> openBackupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> {
                        if (uri != null) {
                            confirmImport(uri);
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootView = findViewById(R.id.root);
        wardrobeDao = new WardrobeDao(this);

        weatherHostInput = findViewById(R.id.weatherHostInput);
        weatherKeyInput = findViewById(R.id.weatherKeyInput);
        llmBaseUrlInput = findViewById(R.id.llmBaseUrlInput);
        llmApiKeyInput = findViewById(R.id.llmApiKeyInput);
        llmModelNameInput = findViewById(R.id.llmModelNameInput);
        configStatusText = findViewById(R.id.configStatusText);
        versionText = findViewById(R.id.versionText);
        dbStatusText = findViewById(R.id.dbStatusText);
        dataMigrationProgress = findViewById(R.id.dataMigrationProgress);
        dataMigrationStatusText = findViewById(
                R.id.dataMigrationStatusText);
        exportDataButton = findViewById(R.id.exportDataButton);
        importDataButton = findViewById(R.id.importDataButton);
        themeModeGroup = findViewById(R.id.themeModeGroup);
        genderProfileGroup = findViewById(R.id.genderProfileGroup);

        bindBackToolbar();

        loadApiConfig();
        showAppVersion();
        setupThemeMode();
        setupGenderProfile();
        setupApiConfigActions();
        setupDataMigrationActions();

        findViewById(R.id.refreshDbButton).setOnClickListener(view -> {
            refreshDatabaseStatus();
            UiUtils.showMessage(rootView, getString(R.string.db_refreshed));
        });
    }

    @Override
    protected int layoutRes() {
        return R.layout.activity_about;
    }

    /**
     * 通过系统文件选择器导出/导入，不申请存储权限，也不依赖固定路径。
     */
    private void setupDataMigrationActions() {
        exportDataButton.setOnClickListener(view -> {
            if (migrationInProgress) {
                return;
            }
            String timestamp = new SimpleDateFormat(
                    "yyyyMMdd-HHmmss", Locale.US).format(new Date());
            createBackupLauncher.launch(
                    "SmartWardrobe-backup-" + timestamp + ".zip");
        });

        importDataButton.setOnClickListener(view -> {
            if (migrationInProgress) {
                return;
            }
            openBackupLauncher.launch(new String[]{
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream"
            });
        });
    }

    /**
     * 导入会覆盖本机数据，因此必须先明确确认。
     */
    private void confirmImport(Uri backupUri) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.data_migration_import_confirm_title)
                .setMessage(R.string.data_migration_import_confirm_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(
                        R.string.data_migration_import_confirm_action,
                        (dialog, which) -> startImport(backupUri))
                .show();
    }

    private void startExport(Uri destination) {
        if (migrationInProgress) {
            return;
        }
        setMigrationBusy(true, getString(
                R.string.data_migration_exporting));
        migrationExecutor.execute(() -> {
            try {
                DataMigrationManager.MigrationResult result =
                        DataMigrationManager.exportData(this, destination);
                postToUi(() -> finishExport(result));
            } catch (Exception exception) {
                postToUi(() -> showMigrationError(exception));
            }
        });
    }

    private void finishExport(DataMigrationManager.MigrationResult result) {
        wardrobeDao = new WardrobeDao(this);
        setMigrationBusy(false, getString(
                R.string.data_migration_export_success,
                result.itemCount,
                result.outfitCount,
                result.imageCount));
        refreshDatabaseStatus();
        UiUtils.showMessage(rootView, getString(
                R.string.data_migration_export_success,
                result.itemCount,
                result.outfitCount,
                result.imageCount));
    }

    private void startImport(Uri source) {
        if (migrationInProgress) {
            return;
        }
        setMigrationBusy(true, getString(
                R.string.data_migration_importing));
        migrationExecutor.execute(() -> {
            try {
                DataMigrationManager.MigrationResult result =
                        DataMigrationManager.importData(this, source);
                postToUi(() -> finishImport(result));
            } catch (Exception exception) {
                postToUi(() -> showMigrationError(exception));
            }
        });
    }

    private void finishImport(DataMigrationManager.MigrationResult result) {
        setMigrationBusy(false, getString(
                R.string.data_migration_import_success,
                result.itemCount,
                result.outfitCount,
                result.imageCount));
        // 清空内存图片缓存，避免进程未退出时继续显示导入前的缩略图。
        Glide.get(this).clearMemory();
        UiUtils.showMessage(rootView, getString(
                R.string.data_migration_restarting));
        mainHandler.postDelayed(this::restartIntoHome, 550L);
    }

    private void showMigrationError(Exception exception) {
        String detail = exception.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            detail = getString(R.string.data_migration_unknown_error);
        }
        String message = getString(
                R.string.data_migration_failed, detail);
        setMigrationBusy(false, message);
        UiUtils.showMessage(rootView, message);
    }

    /**
     * 迁移期间禁用两个入口，防止并发读写数据库或图片目录。
     */
    private void setMigrationBusy(boolean busy, String status) {
        migrationInProgress = busy;
        exportDataButton.setEnabled(!busy);
        importDataButton.setEnabled(!busy);
        dataMigrationProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        dataMigrationStatusText.setText(status);
    }

    private void postToUi(Runnable action) {
        mainHandler.post(() -> {
            if (!isFinishing() && !isDestroyed()) {
                action.run();
            }
        });
    }

    /**
     * 清空任务栈并从首页重新进入，让所有页面重新读取导入后的数据库。
     */
    private void restartIntoHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        // 已提交的任务会继续完成；避免用 shutdownNow 中断数据库替换。
        migrationExecutor.shutdown();
        super.onDestroy();
    }

    /**
     * 初始化深色模式选择器。
     *
     * <p>先按 SharedPreferences 中的值选中按钮，再注册监听器；布尔标记用于
     * 防止程序化 check() 触发一次无意义的主题重建。</p>
     */
    private void setupThemeMode() {
        initializingThemeSelection = true;
        updateSelectorButtons(themeModeGroup,
                getThemeButtonId(ThemeManager.getSavedMode(this)));
        initializingThemeSelection = false;

        bindSelector(themeModeGroup, checkedId -> {
                    int selectedMode = getThemeMode(checkedId);
                    if (selectedMode == ThemeManager.MODE_FOLLOW_SYSTEM
                            && checkedId == View.NO_ID) {
                        return;
                    }
                    if (selectedMode == ThemeManager.getSavedMode(this)) {
                        return;
                    }

                    // 先持久化选择，再交给 AppCompatDelegate 重建并应用主题。
                    ThemeManager.saveMode(this, selectedMode);
                    ThemeManager.applyMode(selectedMode);
                });
    }

    private int getThemeButtonId(int mode) {
        if (mode == ThemeManager.MODE_LIGHT) {
            return R.id.themeLightButton;
        }
        if (mode == ThemeManager.MODE_DARK) {
            return R.id.themeDarkButton;
        }
        return R.id.themeFollowSystemButton;
    }

    private int getThemeMode(int checkedId) {
        if (checkedId == R.id.themeLightButton) {
            return ThemeManager.MODE_LIGHT;
        }
        if (checkedId == R.id.themeDarkButton) {
            return ThemeManager.MODE_DARK;
        }
        return ThemeManager.MODE_FOLLOW_SYSTEM;
    }

    /**
     * 性别偏好由首页首次启动时引导选择，本页负责后续修改。
     * 程序化 check() 期间不触发保存，避免进入设置页就覆盖原值。
     */
    private void setupGenderProfile() {
        String savedGender = StyleProfileManager.getGender(this);
        initializingGenderSelection = true;
        updateSelectorButtons(genderProfileGroup,
                getGenderButtonId(savedGender));
        initializingGenderSelection = false;

        bindSelector(genderProfileGroup, checkedId -> {
                    String selectedGender = getGender(checkedId);
                    if (selectedGender.equals(
                            StyleProfileManager.getGender(this))) {
                        return;
                    }
                    StyleProfileManager.saveGender(this, selectedGender);
                    String label = getString(
                            StyleProfileManager.getLabelResId(selectedGender));
                    UiUtils.showMessage(rootView, getString(
                            R.string.gender_saved, label));
                });
    }

    private interface OnSelectorButtonClicked {
        void onSelected(int buttonId);
    }

    /**
     * 用独立胶囊按钮替代 MaterialButtonToggleGroup，避免首尾自动裁切成鱼尾。
     */
    private void bindSelector(View group,
                              OnSelectorButtonClicked callback) {
        if (!(group instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup viewGroup =
                (android.view.ViewGroup) group;
        for (int index = 0; index < viewGroup.getChildCount(); index++) {
            View child = viewGroup.getChildAt(index);
            if (!(child instanceof MaterialButton)) {
                continue;
            }
            child.setOnClickListener(view -> {
                if (group == themeModeGroup && initializingThemeSelection
                        || group == genderProfileGroup
                        && initializingGenderSelection) {
                    return;
                }
                updateSelectorButtons(group, view.getId());
                callback.onSelected(view.getId());
            });
        }
    }

    private void updateSelectorButtons(View group, int selectedId) {
        if (!(group instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup viewGroup =
                (android.view.ViewGroup) group;
        for (int index = 0; index < viewGroup.getChildCount(); index++) {
            View child = viewGroup.getChildAt(index);
            if (!(child instanceof MaterialButton)) {
                continue;
            }
            boolean selected = child.getId() == selectedId;
            MaterialButton button = (MaterialButton) child;
            button.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this,
                                    selected
                                            ? R.color.tab_checked_background
                                            : android.R.color.transparent)));
            button.setTextColor(ContextCompat.getColor(this,
                    selected ? R.color.tab_checked_text
                            : R.color.md_theme_onSurfaceVariant));
        }
    }

    private int getGenderButtonId(String gender) {
        if (StyleProfileManager.GENDER_FEMALE.equals(gender)) {
            return R.id.genderFemaleButton;
        }
        if (StyleProfileManager.GENDER_MALE.equals(gender)) {
            return R.id.genderMaleButton;
        }
        return R.id.genderNeutralButton;
    }

    private String getGender(int checkedId) {
        if (checkedId == R.id.genderFemaleButton) {
            return StyleProfileManager.GENDER_FEMALE;
        }
        if (checkedId == R.id.genderMaleButton) {
            return StyleProfileManager.GENDER_MALE;
        }
        return StyleProfileManager.GENDER_NEUTRAL;
    }

    private void setupApiConfigActions() {
        findViewById(R.id.saveLlmButton).setOnClickListener(view ->
                saveApiConfig());
        findViewById(R.id.resetLlmButton).setOnClickListener(view ->
                confirmClearApiConfig());

        addClearErrorWatcher(weatherHostInput);
        addClearErrorWatcher(weatherKeyInput);
        addClearErrorWatcher(llmBaseUrlInput);
        addClearErrorWatcher(llmApiKeyInput);
        addClearErrorWatcher(llmModelNameInput);
    }

    private void addClearErrorWatcher(TextInputLayout layout) {
        TextInputEditText editText = (TextInputEditText) layout.getEditText();
        if (editText == null) {
            return;
        }
        editText.addTextChangedListener(new SimpleTextWatcher(
                () -> layout.setError(null)));
    }

    private void loadApiConfig() {
        setText(weatherHostInput, ApiConfig.getWeatherBaseUrl(this));
        setText(weatherKeyInput, ApiConfig.getWeatherApiKey(this));
        setText(llmBaseUrlInput, ApiConfig.getLlmBaseUrl(this));
        setText(llmApiKeyInput, ApiConfig.getLlmApiKey(this));
        setText(llmModelNameInput, ApiConfig.getLlmModelName(this));
        updateConfigStatus();
    }

    private void saveApiConfig() {
        String weatherHost = readText(weatherHostInput);
        String weatherKey = readText(weatherKeyInput);
        String llmBaseUrl = readText(llmBaseUrlInput);
        String llmApiKey = readText(llmApiKeyInput);
        String llmModelName = readText(llmModelNameInput);

        clearAllErrors();
        boolean weatherAny = !weatherHost.isEmpty() || !weatherKey.isEmpty();
        boolean llmAny = !llmBaseUrl.isEmpty()
                || !llmApiKey.isEmpty()
                || !llmModelName.isEmpty();

        // 允许全部留空表示清空配置，但不允许只填半个服务。
        if (!weatherAny && !llmAny) {
            ApiConfig.clearApiConfig(this);
            loadApiConfig();
            UiUtils.showMessage(rootView, getString(R.string.llm_reset_done));
            return;
        }

        boolean valid = true;
        if (weatherAny) {
            if (weatherHost.isEmpty()) {
                weatherHostInput.setError(getString(R.string.llm_required));
                valid = false;
            }
            if (weatherKey.isEmpty()) {
                weatherKeyInput.setError(getString(R.string.llm_required));
                valid = false;
            }
        }
        if (llmAny) {
            if (llmBaseUrl.isEmpty()) {
                llmBaseUrlInput.setError(getString(R.string.llm_required));
                valid = false;
            }
            if (llmApiKey.isEmpty()) {
                llmApiKeyInput.setError(getString(R.string.llm_required));
                valid = false;
            }
            if (llmModelName.isEmpty()) {
                llmModelNameInput.setError(getString(R.string.llm_required));
                valid = false;
            }
        }
        if (!valid) {
            UiUtils.showMessage(rootView, getString(R.string.llm_required));
            return;
        }

        if (!weatherHost.isEmpty() && !isHttpUrl(weatherHost)) {
            weatherHostInput.setError(getString(R.string.llm_invalid_url));
            return;
        }
        if (!llmBaseUrl.isEmpty() && !isHttpUrl(llmBaseUrl)) {
            llmBaseUrlInput.setError(getString(R.string.llm_invalid_url));
            return;
        }

        ApiConfig.saveApiConfig(
                this, weatherHost, weatherKey, llmBaseUrl, llmApiKey, llmModelName);
        loadApiConfig();
        UiUtils.showMessage(rootView, getString(R.string.llm_saved));
    }

    private void confirmClearApiConfig() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.llm_reset_title)
                .setMessage(R.string.llm_reset_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> {
                    ApiConfig.clearApiConfig(this);
                    loadApiConfig();
                    UiUtils.showMessage(rootView,
                            getString(R.string.llm_reset_done));
                })
                .show();
    }

    private void updateConfigStatus() {
        String configured = getString(R.string.settings_configured);
        String missing = getString(R.string.settings_missing);
        String weatherStatus = ApiConfig.isWeatherConfigured(this)
                ? configured : missing;
        String llmStatus = ApiConfig.isLlmConfigured(this)
                ? configured : missing;
        configStatusText.setText(getString(
                R.string.settings_status_format, weatherStatus, llmStatus));
    }

    private boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private void clearAllErrors() {
        weatherHostInput.setError(null);
        weatherKeyInput.setError(null);
        llmBaseUrlInput.setError(null);
        llmApiKeyInput.setError(null);
        llmModelNameInput.setError(null);
    }

    private void showAppVersion() {
        String versionName = "?";
        try {
            String packageVersion = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            if (packageVersion != null && !packageVersion.isEmpty()) {
                versionName = packageVersion;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // 正常安装的应用不会走到这里，保留“?”作为兜底。
        }
        versionText.setText(getString(R.string.about_version, versionName));
    }

    private void refreshDatabaseStatus() {
        boolean schemaReady = wardrobeDao.isSchemaReady();
        int version = wardrobeDao.getDatabaseVersion();
        int itemCount = wardrobeDao.getItemCount();
        int outfitCount = wardrobeDao.getOutfitCount();

        String schemaText = getString(schemaReady
                ? R.string.db_ok : R.string.db_error);
        dbStatusText.setText(getString(
                R.string.db_status_format,
                version,
                schemaText,
                itemCount,
                outfitCount));
        dbStatusText.setTextColor(ContextCompat.getColor(
                this,
                schemaReady ? R.color.md_on_surface_variant : R.color.md_error));
    }

    private String readText(TextInputLayout layout) {
        TextInputEditText editText = (TextInputEditText) layout.getEditText();
        if (editText == null || editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    private void setText(TextInputLayout layout, String value) {
        TextInputEditText editText = (TextInputEditText) layout.getEditText();
        if (editText != null) {
            editText.setText(value == null ? "" : value);
            editText.setSelection(editText.length());
        }
        layout.setError(null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDatabaseStatus();
        updateConfigStatus();
    }

    private static class SimpleTextWatcher implements android.text.TextWatcher {

        private final Runnable onChanged;

        SimpleTextWatcher(Runnable onChanged) {
            this.onChanged = onChanged;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            onChanged.run();
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
        }
    }
}
