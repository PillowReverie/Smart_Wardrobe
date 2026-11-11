package com.example.wardrobe;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wardrobe.adapter.AiOutfitPlanAdapter;
import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.Outfit;
import com.example.wardrobe.ui.state.ItemDetailUiState;
import com.example.wardrobe.util.ImageStore;
import com.example.wardrobe.util.OutfitSaver;
import com.example.wardrobe.util.UiUtils;
import com.example.wardrobe.viewmodel.ItemDetailViewModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 衣物详情 View。
 *
 * <p>只负责状态渲染、保存对话框、编辑跳转和删除确认。单品天气与 AI
 * 请求全部由 ItemDetailViewModel 完成。</p>
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

    private ItemDetailViewModel viewModel;
    private ItemDetailUiState lastState =
            new ItemDetailUiState.Builder().build();
    private long itemId;
    private long lastMessageId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        itemId = getIntent().getLongExtra(EXTRA_ITEM_ID, -1L);
        viewModel = new ViewModelProvider(this)
                .get(ItemDetailViewModel.class);
        bindViews();
        bindBackToolbar();
        setupActions();
        viewModel.getUiState().observe(this, this::renderUiState);
        viewModel.loadItem(itemId);
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

        aiPlanAdapter = new AiOutfitPlanAdapter(this::saveSinglePlan);
        aiPlanList.setLayoutManager(new LinearLayoutManager(this));
        aiPlanList.setAdapter(aiPlanAdapter);
        aiPlanList.setItemAnimator(null);
        aiPlanList.setNestedScrollingEnabled(false);
    }

    private void setupActions() {
        findViewById(R.id.editButton).setOnClickListener(
                view -> openEditPage());
        findViewById(R.id.deleteButton).setOnClickListener(
                view -> confirmDelete());
        aiOutfitButton.setOnClickListener(
                view -> viewModel.generateOutfit());
    }

    private void renderUiState(ItemDetailUiState state) {
        lastState = state;
        ClothingItem item = state.getItem();
        if (item != null) {
            renderItem(item);
        }
        renderAi(state);
        if (state.getMessageId() > lastMessageId
                && !state.getMessage().isEmpty()) {
            lastMessageId = state.getMessageId();
            UiUtils.showMessage(findViewById(R.id.root),
                    state.getMessage());
        }
    }

    private void renderItem(ClothingItem item) {
        itemName.setText(item.getName());
        itemCategory.setText(item.getCategory());
        itemColor.setText(isBlank(item.getColor())
                ? getString(R.string.detail_not_set) : item.getColor());
        itemNote.setText(isBlank(item.getNote())
                ? getString(R.string.detail_not_set) : item.getNote());
        renderAiDescription(item);

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

    private void renderAiDescription(ClothingItem item) {
        if (!BuildConfig.SHOW_AI_DESCRIPTION) {
            aiDescriptionContainer.setVisibility(View.GONE);
            return;
        }
        aiDescriptionContainer.setVisibility(View.VISIBLE);
        itemAiDescription.setText(isBlank(item.getAiDescription())
                ? getString(R.string.detail_ai_description_empty)
                : item.getAiDescription());
    }

    private void renderAi(ItemDetailUiState state) {
        aiOutfitButton.setEnabled(!state.isAiLoading());
        boolean hasPlans = !state.getPlans().isEmpty();
        boolean hasText = !state.getAiContent().isEmpty();
        aiResultCard.setVisibility(
                state.isAiLoading() || hasPlans || hasText
                        ? View.VISIBLE : View.GONE);
        aiResultProgress.setVisibility(
                state.isAiLoading() ? View.VISIBLE : View.GONE);
        if (state.isAiLoading()) {
            aiResultText.setVisibility(
                    hasPlans || hasText ? View.GONE : View.VISIBLE);
            aiResultText.setText(R.string.ai_thinking);
        } else if (hasPlans) {
            aiResultText.setVisibility(View.GONE);
            aiPlanAdapter.submitList(state.getPlans());
            aiPlanList.setVisibility(View.VISIBLE);
            return;
        } else if (hasText) {
            aiResultText.setVisibility(View.VISIBLE);
            aiResultText.setText(state.getAiContent());
        }
        aiPlanAdapter.submitList(
                hasPlans ? state.getPlans() : new ArrayList<>());
        aiPlanList.setVisibility(
                hasPlans ? View.VISIBLE : View.GONE);
    }

    private void saveSinglePlan(AiOutfitPlan plan) {
        if (plan == null || plan.getRawContent().trim().isEmpty()) {
            return;
        }
        String defaultName = getString(
                R.string.outfit_default_item,
                UiUtils.formatMonthDay(System.currentTimeMillis()))
                + " · 方案" + plan.getSequence();
        OutfitSaver.showSaveDialog(
                this,
                defaultName,
                plan.getRawContent(),
                Outfit.SOURCE_ITEM,
                lastState.getWeather(),
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

    private void openEditPage() {
        ClothingItem item = lastState.getItem();
        if (item == null) {
            return;
        }
        Intent intent = new Intent(this, AddItemActivity.class);
        intent.putExtra(AddItemActivity.EXTRA_ITEM_ID, item.getId());
        startActivity(intent);
    }

    private void confirmDelete() {
        ClothingItem item = lastState.getItem();
        if (item == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete,
                        (dialog, which) -> deleteCurrentItem())
                .show();
    }

    private void deleteCurrentItem() {
        viewModel.deleteCurrent(new ItemDetailViewModel.DeleteCallback() {
            @Override
            public void onDeleted() {
                UiUtils.showMessage(findViewById(R.id.root),
                        getString(R.string.item_deleted));
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(String message) {
                UiUtils.showMessage(findViewById(R.id.root), message);
            }
        });
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    protected void onResume() {
        super.onResume();
        aiPlanAdapter.clearSavedMarks();
        if (lastState.getItem() == null) {
            viewModel.loadItem(itemId);
        }
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            viewModel.cancelActiveRequests();
        }
        super.onDestroy();
    }
}
