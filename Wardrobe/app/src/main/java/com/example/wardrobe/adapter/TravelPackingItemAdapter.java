package com.example.wardrobe.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wardrobe.R;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.TravelPackingItem;
import com.example.wardrobe.util.ImageStore;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 旅行打包清单的横向衣物列表。
 *
 * <p>每张卡下方直接提供“加入旅行衣橱”按钮。按钮状态由数据库快照
 * 和本地 pending 集合共同决定：未加入时显示“加入旅行衣橱”，
 * 加入后显示可再次点击的“已加入”，用于移出旅行衣橱。</p>
 */
public class TravelPackingItemAdapter
        extends RecyclerView.Adapter<TravelPackingItemAdapter.ItemViewHolder> {

    public interface OnTravelToggleListener {
        void onTravelToggle(ClothingItem item, boolean addToTravel);
    }

    private final List<TravelPackingItem> items = new ArrayList<>();
    private final Set<Long> pendingItemIds = new HashSet<>();
    private final Set<Long> addedItemIds = new HashSet<>();
    private final Set<Long> removedItemIds = new HashSet<>();
    private final OnTravelToggleListener toggleListener;

    public TravelPackingItemAdapter(OnTravelToggleListener toggleListener) {
        this.toggleListener = toggleListener;
    }

    public void submitList(List<TravelPackingItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        pendingItemIds.clear();
        addedItemIds.clear();
        removedItemIds.clear();
        notifyDataSetChanged();
    }

    public void markPending(long itemId) {
        pendingItemIds.add(itemId);
        notifyDataSetChanged();
    }

    public void clearPending(long itemId) {
        pendingItemIds.remove(itemId);
        notifyDataSetChanged();
    }

    /**
     * SQLite 写入成功后立即把按钮锁为“已加入”，不必等待整张 AI 结果重解析。
     */
    public void markAdded(long itemId) {
        pendingItemIds.remove(itemId);
        removedItemIds.remove(itemId);
        addedItemIds.add(itemId);
        notifyDataSetChanged();
    }

    /**
     * 移出旅行衣橱成功后恢复为可添加状态。
     */
    public void markRemoved(long itemId) {
        pendingItemIds.remove(itemId);
        addedItemIds.remove(itemId);
        removedItemIds.add(itemId);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                             int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_travel_packing, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        TravelPackingItem packingItem = items.get(position);
        ClothingItem clothingItem = packingItem.getClothingItem();
        holder.name.setText(clothingItem == null
                ? packingItem.getReferencedName() : clothingItem.getName());
        bindImage(holder.image, clothingItem);

        if (clothingItem == null) {
            holder.addButton.setText(R.string.travel_packing_unmatched);
            holder.addButton.setEnabled(false);
            return;
        }
        boolean added = (clothingItem.isTravel()
                && !removedItemIds.contains(clothingItem.getId()))
                || addedItemIds.contains(clothingItem.getId());
        boolean pending = pendingItemIds.contains(clothingItem.getId());
        holder.addButton.setEnabled(!pending);
        holder.addButton.setText(added
                ? R.string.travel_packing_added
                : pending
                ? R.string.travel_packing_adding
                : R.string.travel_packing_add);
        int background = added
                ? R.color.md_theme_surfaceVariant
                : R.color.md_theme_secondaryContainer;
        int foreground = added
                ? R.color.md_theme_onSurfaceVariant
                : R.color.md_theme_onSecondaryContainer;
        holder.addButton.setBackgroundTintList(
                ContextCompat.getColorStateList(
                        holder.itemView.getContext(), background));
        holder.addButton.setTextColor(ContextCompat.getColor(
                holder.itemView.getContext(), foreground));
        holder.addButton.setOnClickListener(view -> {
            if (toggleListener != null) {
                toggleListener.onTravelToggle(clothingItem, !added);
            }
        });
    }

    private void bindImage(ImageView imageView, ClothingItem item) {
        File imageFile = item == null ? null
                : ImageStore.getImageFile(
                imageView.getContext(), item.getImagePath());
        Object source = imageFile != null && imageFile.exists()
                ? imageFile : R.drawable.bg_item_image_placeholder;
        Glide.with(imageView)
                .load(source)
                .centerCrop()
                .placeholder(R.drawable.bg_item_image_placeholder)
                .error(R.drawable.bg_item_image_placeholder)
                .into(imageView);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {

        final ImageView image;
        final TextView name;
        final MaterialButton addButton;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.travelItemImage);
            name = itemView.findViewById(R.id.travelItemName);
            addButton = itemView.findViewById(R.id.addTravelItemButton);
        }
    }
}
