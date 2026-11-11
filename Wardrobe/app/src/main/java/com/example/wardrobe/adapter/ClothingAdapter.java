package com.example.wardrobe.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wardrobe.R;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.util.ImageStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 衣橱双列卡片适配器。
 *
 * <p>图片通过 Glide 从 App 私有目录加载，数据库里只有文件名。
 * 使用 DiffUtil 做局部刷新，筛选或编辑后不会整屏闪烁。</p>
 */
public class ClothingAdapter extends RecyclerView.Adapter<ClothingAdapter.ItemViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(ClothingItem item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(ClothingItem item);
    }

    private final List<ClothingItem> items = new ArrayList<>();
    private final OnItemClickListener clickListener;
    private final OnItemLongClickListener longClickListener;

    public ClothingAdapter(OnItemClickListener clickListener,
                           OnItemLongClickListener longClickListener) {
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        setHasStableIds(true);
    }

    public void submitList(List<ClothingItem> newItems) {
        List<ClothingItem> oldItems = new ArrayList<>(items);
        List<ClothingItem> incoming = new ArrayList<>(newItems);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return incoming.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return oldItems.get(oldPosition).getId()
                        == incoming.get(newPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                ClothingItem oldItem = oldItems.get(oldPosition);
                ClothingItem newItem = incoming.get(newPosition);
                return oldItem.getUpdatedAt() == newItem.getUpdatedAt()
                        && Objects.equals(oldItem.getName(), newItem.getName())
                        && Objects.equals(oldItem.getCategory(), newItem.getCategory())
                        && Objects.equals(oldItem.getImagePath(), newItem.getImagePath())
                        && Objects.equals(oldItem.getColor(), newItem.getColor())
                        && oldItem.isTravel() == newItem.isTravel()
                        && oldItem.getMinTemp() == newItem.getMinTemp()
                        && oldItem.getMaxTemp() == newItem.getMaxTemp();
            }
        });

        items.clear();
        items.addAll(incoming);
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getId();
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_clothing, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        ClothingItem item = items.get(position);
        holder.name.setText(item.getName());
        String category = item.getCategory();
        if (item.getColor() != null && !item.getColor().trim().isEmpty()) {
            category = category + " · " + item.getColor().trim();
        }
        holder.category.setText(category);
        holder.travelBadge.setVisibility(
                item.isTravel() ? View.VISIBLE : View.GONE);

        File imageFile = ImageStore.getImageFile(
                holder.itemView.getContext(), item.getImagePath());
        Object imageModel = imageFile != null && imageFile.exists()
                ? imageFile : R.drawable.bg_item_image_placeholder;
        Glide.with(holder.image)
                .load(imageModel)
                .centerCrop()
                .placeholder(R.drawable.bg_item_image_placeholder)
                .error(R.drawable.bg_item_image_placeholder)
                .into(holder.image);
        holder.image.setContentDescription(
                holder.itemView.getContext().getString(R.string.cd_item_image)
                        + "：" + item.getName());
        holder.itemView.setOnClickListener(view -> {
            if (clickListener != null) {
                clickListener.onItemClick(item);
            }
        });
        holder.itemView.setOnLongClickListener(view -> {
            if (longClickListener == null) {
                return false;
            }
            longClickListener.onItemLongClick(item);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {

        final ImageView image;
        final TextView name;
        final TextView category;
        final TextView travelBadge;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.itemImage);
            name = itemView.findViewById(R.id.itemName);
            category = itemView.findViewById(R.id.itemCategory);
            travelBadge = itemView.findViewById(R.id.travelBadge);
        }
    }
}
