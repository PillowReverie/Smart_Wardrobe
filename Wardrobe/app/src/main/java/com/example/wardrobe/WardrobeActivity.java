package com.example.wardrobe;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import com.example.wardrobe.adapter.ClothingAdapter;
import com.example.wardrobe.data.WardrobeRepository;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.ui.state.WardrobeListState;
import com.example.wardrobe.util.GridSpacingItemDecoration;
import com.example.wardrobe.util.UiUtils;
import com.example.wardrobe.viewmodel.WardrobeViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/**
 * 衣橱列表页。
 *
 * <p>页面只消费 WardrobeListState，不再直接操作 SQLite。顶部 Tab 和
 * 类别/搜索条件交给 WardrobeViewModel 合并，旅行衣橱的修改通过共享
 * 仓库同步到首页，返回本页时无需手工拼接数据。</p>
 */
public class WardrobeActivity extends BaseActivity {

    private static final int GRID_SPAN_COUNT = 2;
    private static final int GRID_SPACING_DP = 12;

    private View emptyState;
    private View rootView;
    private TextView emptyTitle;
    private TextView emptyDesc;
    private TextView wardrobeCount;
    private RecyclerView itemList;
    private ChipGroup categoryChipGroup;
    private TextInputEditText searchEditText;
    private ExtendedFloatingActionButton addFab;
    private ExtendedFloatingActionButton clearTravelFab;

    private ClothingAdapter clothingAdapter;
    private WardrobeViewModel viewModel;
    private boolean travelTab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this)
                .get(WardrobeViewModel.class);
        bindViews();
        bindBackToolbar();
        setupRecyclerView();
        setupTabs();
        setupCategoryFilter();
        setupSearch();

        addFab.setOnClickListener(view -> openAddItem());
        clearTravelFab.setOnClickListener(view -> confirmClearTravel());

        observeViewModel();
    }

    @Override
    protected int layoutRes() {
        return R.layout.activity_wardrobe;
    }

    private void bindViews() {
        emptyState = findViewById(R.id.emptyState);
        rootView = findViewById(R.id.root);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyDesc = findViewById(R.id.emptyDesc);
        wardrobeCount = findViewById(R.id.wardrobeCount);
        itemList = findViewById(R.id.itemList);
        categoryChipGroup = findViewById(R.id.categoryChipGroup);
        searchEditText = findViewById(R.id.searchEditText);
        addFab = findViewById(R.id.addFab);
        clearTravelFab = findViewById(R.id.clearTravelFab);
    }

    private void setupRecyclerView() {
        clothingAdapter = new ClothingAdapter(
                this::openItemDetail,
                this::toggleTravelState);
        itemList.setLayoutManager(new GridLayoutManager(this, GRID_SPAN_COUNT));
        itemList.addItemDecoration(new GridSpacingItemDecoration(
                GRID_SPAN_COUNT, dpToPx(GRID_SPACING_DP), false));
        itemList.setAdapter(clothingAdapter);
    }

    private void setupTabs() {
        findViewById(R.id.wardrobeTabAll).setOnClickListener(
                view -> selectWardrobeTab(false));
        findViewById(R.id.wardrobeTabTravel).setOnClickListener(
                view -> selectWardrobeTab(true));
        updateWardrobeTabButtons();
    }

    private void selectWardrobeTab(boolean showTravel) {
        if (travelTab == showTravel) {
            return;
        }
        travelTab = showTravel;
        updateWardrobeTabButtons();
        viewModel.setTravelTab(travelTab);
        updateTabChrome();
    }

    private void updateWardrobeTabButtons() {
        int selectedId = travelTab
                ? R.id.wardrobeTabTravel : R.id.wardrobeTabAll;
        int[] ids = new int[]{
                R.id.wardrobeTabAll,
                R.id.wardrobeTabTravel
        };
        for (int id : ids) {
            MaterialButton button = findViewById(id);
            boolean selected = id == selectedId;
            button.setBackgroundTintList(android.content.res.ColorStateList
                    .valueOf(ContextCompat.getColor(this,
                            selected ? R.color.tab_checked_background
                                    : android.R.color.transparent)));
            button.setTextColor(ContextCompat.getColor(this,
                    selected ? R.color.tab_checked_text
                            : R.color.md_theme_onSurfaceVariant));
        }
    }

    private void setupCategoryFilter() {
        categoryChipGroup.setOnCheckedStateChangeListener(
                (group, checkedIds) -> {
                    if (checkedIds.isEmpty()) {
                        group.check(R.id.chipAll);
                        return;
                    }
                    viewModel.setCategory(getSelectedCategory());
                });
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s,
                                          int start,
                                          int count,
                                          int after) {
                // 搜索防抖由 ViewModel 统一管理。
            }

            @Override
            public void onTextChanged(CharSequence s,
                                      int start,
                                      int before,
                                      int count) {
                // 文本变化时立即把最新值交给 ViewModel 重新计时。
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setKeyword(s == null ? "" : s.toString());
            }
        });
    }

    private void observeViewModel() {
        viewModel.getListState().observe(this, this::renderState);
        viewModel.getMutationInFlight().observe(this, busy -> {
            boolean enabled = !Boolean.TRUE.equals(busy);
            clearTravelFab.setEnabled(enabled);
            addFab.setEnabled(enabled);
        });
    }

    private void renderState(WardrobeListState state) {
        if (state == null) {
            return;
        }
        travelTab = state.isTravelTab();
        updateTabChrome();
        List<ClothingItem> items = state.getItems();
        clothingAdapter.submitList(items);

        if (state.isTravelTab()) {
            wardrobeCount.setText(getString(
                    R.string.travel_wardrobe_count_format,
                    state.getTravelItemCount()));
        } else if (!state.getKeyword().isEmpty()) {
            wardrobeCount.setText(getString(
                    R.string.wardrobe_search_count_format, items.size()));
        } else {
            wardrobeCount.setText(getString(
                    R.string.wardrobe_count_format,
                    state.getAllItemCount()));
        }

        boolean empty = items.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        itemList.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            bindEmptyState(state);
        }
        if (!state.getErrorMessage().isEmpty()) {
            Toast.makeText(this, state.getErrorMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void bindEmptyState(WardrobeListState state) {
        if (!state.getKeyword().isEmpty()) {
            emptyTitle.setText(R.string.wardrobe_search_empty_title);
            emptyDesc.setText(R.string.wardrobe_search_empty_desc);
        } else if (state.isTravelTab()) {
            emptyTitle.setText(R.string.travel_wardrobe_empty_title);
            emptyDesc.setText(R.string.travel_wardrobe_empty_desc);
        } else if (state.getAllItemCount() == 0) {
            emptyTitle.setText(R.string.wardrobe_empty_title);
            emptyDesc.setText(R.string.wardrobe_empty_desc);
        } else {
            emptyTitle.setText(R.string.wardrobe_filter_empty_title);
            emptyDesc.setText(R.string.wardrobe_filter_empty_desc);
        }
    }

    private void updateTabChrome() {
        addFab.setVisibility(travelTab ? View.GONE : View.VISIBLE);
        clearTravelFab.setVisibility(travelTab ? View.VISIBLE : View.GONE);
    }

    private void openItemDetail(ClothingItem item) {
        Intent intent = new Intent(this, ItemDetailActivity.class);
        intent.putExtra(ItemDetailActivity.EXTRA_ITEM_ID, item.getId());
        startActivity(intent);
    }

    private void toggleTravelState(ClothingItem item) {
        viewModel.toggleTravel(item,
                new WardrobeRepository.ResultCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer rows) {
                        if (rows == null || rows <= 0) {
                            Toast.makeText(WardrobeActivity.this,
                                    R.string.travel_wardrobe_toggle_failed,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        UiUtils.showMessage(rootView, getString(
                                item.isTravel()
                                        ? R.string.travel_wardrobe_removed_toast
                                        : R.string.travel_wardrobe_added_toast));
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(WardrobeActivity.this, message,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void confirmClearTravel() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.travel_wardrobe_clear_title)
                .setMessage(R.string.travel_wardrobe_clear_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.travel_wardrobe_clear,
                        (dialog, which) -> clearTravel())
                .show();
    }

    private void clearTravel() {
        clearTravelFab.setEnabled(false);
        viewModel.clearTravel(
                new WardrobeRepository.ResultCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer rows) {
                        Toast.makeText(WardrobeActivity.this,
                                R.string.travel_wardrobe_cleared,
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        clearTravelFab.setEnabled(true);
                        Toast.makeText(WardrobeActivity.this, message,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void openAddItem() {
        startActivity(new Intent(this, AddItemActivity.class));
    }

    /**
     * 返回 null 表示“全部”，否则返回 Chip 上显示的中文类别。
     */
    private String getSelectedCategory() {
        int checkedId = categoryChipGroup.getCheckedChipId();
        if (checkedId == View.NO_ID || checkedId == R.id.chipAll) {
            return null;
        }
        Chip chip = categoryChipGroup.findViewById(checkedId);
        return chip == null ? null : chip.getText().toString();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewModel.refresh();
    }
}
