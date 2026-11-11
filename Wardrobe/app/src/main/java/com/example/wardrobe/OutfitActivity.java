package com.example.wardrobe;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wardrobe.adapter.OutfitAdapter;
import com.example.wardrobe.db.WardrobeDao;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.Outfit;
import com.example.wardrobe.util.AiTextFormatter;
import com.example.wardrobe.util.UiUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * 搭配库页面。
 *
 * <p>展示保存的 AI 搭配卡片，包括横向衣物图片、标题、天气和正文摘要；
 * 点击卡片可查看完整文本与关联衣物。</p>
 */
public class OutfitActivity extends BaseActivity {

    private View emptyState;
    private RecyclerView outfitList;
    private TextView outfitCount;
    private WardrobeDao wardrobeDao;
    private OutfitAdapter outfitAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wardrobeDao = new WardrobeDao(this);
        outfitCount = findViewById(R.id.outfitCount);
        emptyState = findViewById(R.id.emptyState);
        outfitList = findViewById(R.id.outfitList);

        bindBackToolbar();

        outfitAdapter = new OutfitAdapter(
                this::showOutfitDetail, this::confirmDeleteOutfit);
        outfitList.setLayoutManager(new LinearLayoutManager(this));
        outfitList.setAdapter(outfitAdapter);
    }

    @Override
    protected int layoutRes() {
        return R.layout.activity_outfit;
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOutfits();
    }

    private void loadOutfits() {
        List<Outfit> outfits = wardrobeDao.getAllOutfitsWithItems();
        outfitCount.setText(getString(
                R.string.outfit_count_format, outfits.size()));
        outfitAdapter.submitList(outfits);

        boolean empty = outfits.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        outfitList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /**
     * 删除搭配前二次确认，避免误触。
     */
    private void confirmDeleteOutfit(Outfit outfit) {
        if (outfit == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.outfit_delete_title)
                .setMessage(getString(
                        R.string.outfit_delete_message, outfit.getTitle()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.outfit_delete,
                        (dialog, which) -> deleteOutfit(outfit))
                .show();
    }

    private void deleteOutfit(Outfit outfit) {
        try {
            int rows = wardrobeDao.deleteOutfit(outfit.getId());
            if (rows > 0) {
                UiUtils.showMessage(findViewById(R.id.root),
                        getString(R.string.outfit_deleted));
                loadOutfits();
            } else {
                Toast.makeText(this, R.string.outfit_delete_failed,
                        Toast.LENGTH_SHORT).show();
            }
        } catch (RuntimeException exception) {
            Toast.makeText(this, R.string.outfit_delete_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showOutfitDetail(Outfit outfit) {
        List<ClothingItem> items = outfit.getLinkedItems();
        if (items == null || items.isEmpty()) {
            items = wardrobeDao.getItemsForOutfit(outfit.getId());
        }
        StringBuilder names = new StringBuilder();
        for (ClothingItem item : items) {
            if (names.length() > 0) {
                names.append("、");
            }
            names.append(item.getName());
        }
        String linkedText = names.length() == 0
                ? getString(R.string.outfit_no_items)
                : getString(R.string.outfit_linked_items_format, names.toString());

        new MaterialAlertDialogBuilder(this)
                .setTitle(outfit.getTitle())
                .setMessage(AiTextFormatter.clean(outfit.getContent())
                        + "\n\n" + linkedText)
                .setNeutralButton(R.string.outfit_delete,
                        (dialog, which) -> confirmDeleteOutfit(outfit))
                .setPositiveButton(R.string.action_confirm, null)
                .show();
    }
}
