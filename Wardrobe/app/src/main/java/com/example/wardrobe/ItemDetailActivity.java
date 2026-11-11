package com.example.wardrobe;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wardrobe.adapter.AiOutfitPlanAdapter;
import com.example.wardrobe.db.WardrobeDao;
import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.Outfit;
import com.example.wardrobe.model.WeatherInfo;
import com.example.wardrobe.network.LLMService;
import com.example.wardrobe.network.dto.ChatResponse;
import com.example.wardrobe.util.ApiConfig;
import com.example.wardrobe.util.AiOutfitParser;
import com.example.wardrobe.util.ImageStore;
import com.example.wardrobe.util.NetworkUtils;
import com.example.wardrobe.util.OutfitSaver;
import com.example.wardrobe.util.PromptBuilder;
import com.example.wardrobe.util.StyleProfileManager;
import com.example.wardrobe.util.UiUtils;
import com.example.wardrobe.util.WeatherManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;

/**
 * 衣物详情页。
 *
 * <p>展示衣物信息、天气适配警告，并支持单品 AI 搭配。AI 结果直接展示在
 * 当前页面下方，可重试或保存到搭配库。页面销毁时会取消未完成的 AI Call。</p>
 */
public class ItemDetailActivity extends BaseActivity {

    public static final String EXTRA_ITEM_ID = "extra_item_id";

    private ImageView itemImage;
    private ImageView itemImagePlaceholder;
    private TextView itemName;
    private TextView itemColor;
    private TextView itemNote;
    private View aiDescriptionContainer;
    private TextView itemAiDescription;
    private Chip itemCategory;

    private View aiOutfitButton;
    private View aiResultCard;
    private View aiResultProgress;
    private TextView aiResultText;
    private RecyclerView aiPlanList;
    private AiOutfitPlanAdapter aiPlanAdapter;

    private WardrobeDao wardrobeDao;
    private ClothingItem currentItem;
    private long itemId;
    private WeatherInfo currentWeather;
    private String currentAiContent;
    private Call<ChatResponse> aiCall;
    private boolean aiRequestInFlight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wardrobeDao = new WardrobeDao(this);
        itemId = getIntent().getLongExtra(EXTRA_ITEM_ID, -1L);

        bindViews();
        setupToolbar();
        setupActions();
    }

    @Override
    protected int layoutRes() {
        return R.layout.activity_item_detail;
    }

    private void bindViews() {
        itemImage = findViewById(R.id.itemImage);
        itemImagePlaceholder = findViewById(R.id.itemImagePlaceholder);
        itemName = findViewById(R.id.itemName);
        itemColor = findViewById(R.id.itemColor);
        itemNote = findViewById(R.id.itemNote);
        aiDescriptionContainer = findViewById(R.id.aiDescriptionContainer);
        itemAiDescription = findViewById(R.id.itemAiDescription);
        itemCategory = findViewById(R.id.itemCategory);

        aiOutfitButton = findViewById(R.id.aiOutfitButton);
        aiResultCard = findViewById(R.id.aiResultCard);
        aiResultProgress = findViewById(R.id.aiResultProgress);
        aiResultText = findViewById(R.id.aiResultText);
        aiPlanList = findViewById(R.id.aiPlanList);
        aiResultCard.setVisibility(View.GONE);

        aiPlanAdapter = new AiOutfitPlanAdapter(this::saveSingleItemPlan);
        aiPlanList.setLayoutManager(new LinearLayoutManager(this));
        aiPlanList.setAdapter(aiPlanAdapter);
        aiPlanList.setItemAnimator(null);
        aiPlanList.setNestedScrollingEnabled(false);
    }

    private void setupToolbar() {
        bindBackToolbar();
    }

    private void setupActions() {
        findViewById(R.id.editButton).setOnClickListener(view -> openEditPage());
        findViewById(R.id.deleteButton).setOnClickListener(view -> confirmDelete());
        aiOutfitButton.setOnClickListener(view -> generateItemOutfit());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (aiPlanAdapter != null) {
            aiPlanAdapter.clearSavedMarks();
        }
        loadItem();
    }

    private void loadItem() {
        if (itemId <= 0) {
            showToast(R.string.item_not_found);
            finish();
            return;
        }
        currentItem = wardrobeDao.getItemById(itemId);
        if (currentItem == null) {
            showToast(R.string.item_not_found);
            finish();
            return;
        }
        bindItem(currentItem);
    }

    private void bindItem(ClothingItem item) {
        itemName.setText(item.getName());
        itemCategory.setText(item.getCategory());
        itemColor.setText(isBlank(item.getColor())
                ? getString(R.string.detail_not_set) : item.getColor());
        itemNote.setText(isBlank(item.getNote())
                ? getString(R.string.detail_not_set) : item.getNote());
        bindAiDescription(item);

        File imageFile = ImageStore.getImageFile(this, item.getImagePath());
        if (imageFile != null && imageFile.exists()) {
            Glide.with(this)
                    .load(imageFile)
                    .centerCrop()
                    .placeholder(R.drawable.bg_item_image_placeholder)
                    .error(R.drawable.bg_item_image_placeholder)
                    .into(itemImage);
            itemImage.setVisibility(View.VISIBLE);
            itemImagePlaceholder.setVisibility(View.GONE);
        } else {
            Glide.with(this).clear(itemImage);
            itemImage.setVisibility(View.GONE);
            itemImagePlaceholder.setVisibility(View.VISIBLE);
        }
        itemImage.setContentDescription(
                getString(R.string.cd_item_image) + "：" + item.getName());
    }

    /**
     * Release 包完全隐藏隐藏描述；Beta 包展示实际存入数据库的模型文本。
     */
    private void bindAiDescription(ClothingItem item) {
        if (!BuildConfig.SHOW_AI_DESCRIPTION) {
            aiDescriptionContainer.setVisibility(View.GONE);
            return;
        }
        aiDescriptionContainer.setVisibility(View.VISIBLE);
        itemAiDescription.setText(isBlank(item.getAiDescription())
                ? getString(R.string.detail_ai_description_empty)
                : item.getAiDescription());
    }

    /**
     * 单品 AI 入口。先校验配置和天气，再组装核心单品与其他衣物。
     */
    private void generateItemOutfit() {
        if (currentItem == null || aiRequestInFlight) {
            return;
        }
        if (!NetworkUtils.isNetworkAvailable(this)) {
            handleItemAiFailure(getString(R.string.network_unavailable));
            return;
        }
        if (!ApiConfig.isLlmConfigured(this)) {
            handleItemAiFailure(getString(R.string.ai_need_llm_key));
            return;
        }
        if (currentWeather == null) {
            fetchWeatherThenGenerate();
            return;
        }
        generateItemOutfitWithWeather();
    }

    /**
     * 详情页可能直接打开而没有首页缓存，这里补一次天气请求。
     */
    private void fetchWeatherThenGenerate() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            handleItemAiFailure(getString(R.string.network_unavailable));
            return;
        }
        if (!ApiConfig.isWeatherConfigured(this)) {
            handleItemAiFailure(getString(R.string.ai_need_weather));
            return;
        }

        setItemAiLoading(hasCurrentItemAiContent());
        aiRequestInFlight = true;
        WeatherManager.getInstance().getWeather(this,
                new WeatherManager.Callback() {
                    @Override
                    public void onSuccess(WeatherInfo weatherInfo,
                                          boolean fromCache) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        aiRequestInFlight = false;
                        currentWeather = weatherInfo;
                        generateItemOutfitWithWeather();
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        aiRequestInFlight = false;
                        handleItemAiFailure(message);
                    }
                });
    }

    private void generateItemOutfitWithWeather() {
        if (currentItem == null || currentWeather == null || aiRequestInFlight) {
            return;
        }
        List<ClothingItem> otherItems = getOtherItems();
        final String previousContent = currentAiContent;
        setItemAiLoading(hasCurrentItemAiContent());
        aiRequestInFlight = true;
        String gender = StyleProfileManager.getGender(this);
        String userPrompt = PromptBuilder.buildItemOutfitPrompt(
                currentWeather, currentItem, otherItems, gender);
        aiCall = LLMService.getInstance().generate(
                this,
                PromptBuilder.buildItemSystemPrompt(gender),
                userPrompt,
                new LLMService.Callback() {
                    @Override
                    public void onSuccess(String content) {
                        aiRequestInFlight = false;
                        aiCall = null;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        currentAiContent = content;
                        setItemAiSuccess(content);
                    }

                    @Override
                    public void onError(String message) {
                        aiRequestInFlight = false;
                        aiCall = null;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (previousContent != null
                                && !previousContent.trim().isEmpty()) {
                            currentAiContent = previousContent;
                            handleItemAiFailure(message);
                            return;
                        }
                        handleItemAiFailure(message);
                    }
                });
    }

    private List<ClothingItem> getOtherItems() {
        List<ClothingItem> others = new ArrayList<>();
        for (ClothingItem item : wardrobeDao.getAllItems()) {
            if (item.getId() != currentItem.getId()) {
                others.add(item);
            }
        }
        return others;
    }

    /**
     * 单品页同样按方案独立保存，并只关联该方案实际匹配到的衣物。
     */
    private void saveSingleItemPlan(AiOutfitPlan plan) {
        if (plan == null || plan.getRawContent().trim().isEmpty()) {
            return;
        }
        String baseName = getString(
                R.string.outfit_default_item,
                UiUtils.formatMonthDay(System.currentTimeMillis()));
        String defaultName = baseName + " · 方案" + plan.getSequence();
        OutfitSaver.showSaveDialog(
                this,
                defaultName,
                plan.getRawContent(),
                Outfit.SOURCE_ITEM,
                currentWeather,
                AiOutfitParser.getMatchedItems(plan),
                saved -> {
                    if (saved) {
                        aiPlanAdapter.markSaved(plan);
                    }
                });
    }

    private void setItemAiLoading(boolean preserveCurrentContent) {
        aiResultCard.setVisibility(View.VISIBLE);
        aiResultProgress.setVisibility(View.VISIBLE);
        if (!preserveCurrentContent) {
            aiResultText.setVisibility(View.VISIBLE);
            aiResultText.setText(R.string.ai_thinking);
            aiPlanList.setVisibility(View.GONE);
        }
        aiOutfitButton.setEnabled(false);
    }

    private void setItemAiSuccess(String content) {
        // 图片匹配以当前 SQLite 衣橱为准，缓存或旧请求对象不会造成脏引用。
        List<ClothingItem> sourceItems = wardrobeDao.getAllItems();
        List<AiOutfitPlan> plans = AiOutfitParser.parse(content, sourceItems);
        aiResultCard.setVisibility(View.VISIBLE);
        aiResultProgress.setVisibility(View.GONE);
        aiResultText.setVisibility(View.GONE);
        aiPlanAdapter.submitList(plans);
        aiPlanList.setVisibility(plans.isEmpty() ? View.GONE : View.VISIBLE);
        aiOutfitButton.setEnabled(true);
    }

    private void showItemAiError(String message) {
        currentAiContent = null;
        aiResultCard.setVisibility(View.VISIBLE);
        aiResultProgress.setVisibility(View.GONE);
        aiResultText.setVisibility(View.VISIBLE);
        aiResultText.setText(message);
        aiPlanList.setVisibility(View.GONE);
        aiOutfitButton.setEnabled(true);
    }

    private boolean hasCurrentItemAiContent() {
        return currentAiContent != null
                && !currentAiContent.trim().isEmpty();
    }

    private void handleItemAiFailure(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        if (hasCurrentItemAiContent()) {
            setItemAiSuccess(currentAiContent);
            return;
        }
        showItemAiError(message);
    }

    private void openEditPage() {
        if (currentItem == null) {
            return;
        }
        Intent intent = new Intent(this, AddItemActivity.class);
        intent.putExtra(AddItemActivity.EXTRA_ITEM_ID, currentItem.getId());
        startActivity(intent);
    }

    private void confirmDelete() {
        if (currentItem == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) ->
                        deleteCurrentItem())
                .show();
    }

    private void deleteCurrentItem() {
        if (currentItem == null) {
            return;
        }
        try {
            int rows = wardrobeDao.deleteItem(currentItem.getId());
            if (rows > 0) {
                ImageStore.deleteImage(this, currentItem.getImagePath());
                showToast(R.string.item_deleted);
                setResult(RESULT_OK);
                finish();
            } else {
                showToast(R.string.delete_failed);
            }
        } catch (Exception exception) {
            showToast(R.string.delete_failed);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void showToast(int stringResId) {
        Toast.makeText(this, stringResId, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (aiCall != null) {
            aiCall.cancel();
            aiCall = null;
        }
        WeatherManager.getInstance().cancelActiveCalls();
        super.onDestroy();
    }
}
