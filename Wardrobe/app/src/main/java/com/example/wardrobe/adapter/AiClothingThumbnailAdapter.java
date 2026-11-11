package com.example.wardrobe.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.wardrobe.R;
import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.util.ImageStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 单套 AI 方案里的横向衣物图片列表。
 *
 * <p>匹配成功且私有图片存在时使用 Glide 加载实物图；匹配失败或文件丢失时
 * 统一使用 R.drawable.bg_item_image_placeholder，避免出现空白视图。</p>
 */
public class AiClothingThumbnailAdapter
        extends RecyclerView.Adapter<AiClothingThumbnailAdapter.ItemViewHolder> {

    private final List<AiOutfitPlan.VisualItem> items = new ArrayList<>();

    public void submitList(List<AiOutfitPlan.VisualItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    /**
     * 搭配库的关联衣物是数据库实体，没有“AI 提取名称”这一层，
     * 这里转换成 VisualItem 复用同一套图片加载和占位逻辑。
     */
    public void submitClothingItems(List<ClothingItem> clothingItems) {
        List<AiOutfitPlan.VisualItem> visualItems = new ArrayList<>();
        if (clothingItems != null) {
            for (ClothingItem item : clothingItems) {
                if (item != null) {
                    visualItems.add(new AiOutfitPlan.VisualItem(
                            item.getName(), item));
                }
            }
        }
        submitList(visualItems);
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ai_clothing_thumb, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        AiOutfitPlan.VisualItem visualItem = items.get(position);
        if (visualItem == null) {
            showPlaceholder(holder.image);
            holder.name.setText("未识别衣物");
            return;
        }
        ClothingItem clothingItem = visualItem.getClothingItem();

        if (clothingItem == null) {
            showPlaceholder(holder.image);
            holder.name.setText(visualItem.getReferencedName());
        } else {
            holder.name.setText(clothingItem.getName());
            File imageFile = ImageStore.getImageFile(
                    holder.itemView.getContext(), clothingItem.getImagePath());
            if (imageFile != null && imageFile.exists()) {
                holder.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Glide.with(holder.image)
                        .load(imageFile)
                        .centerCrop()
                        .placeholder(R.drawable.bg_item_image_placeholder)
                        .error(R.drawable.bg_item_image_placeholder)
                        .into(holder.image);
            } else {
                showPlaceholder(holder.image);
            }
        }

        holder.image.setContentDescription(
                holder.itemView.getContext().getString(R.string.cd_item_image)
                        + "：" + holder.name.getText());
    }

    private void showPlaceholder(ImageView imageView) {
        Glide.with(imageView).clear(imageView);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setImageResource(R.drawable.bg_item_image_placeholder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {

        final ImageView image;
        final TextView name;

        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.thumbImage);
            name = itemView.findViewById(R.id.thumbName);
        }
    }
}
