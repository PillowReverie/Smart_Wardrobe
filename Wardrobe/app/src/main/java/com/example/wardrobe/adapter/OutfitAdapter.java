package com.example.wardrobe.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

import com.example.wardrobe.R;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.Outfit;
import com.example.wardrobe.util.AiTextFormatter;
import com.example.wardrobe.util.UiUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 搭配库卡片适配器。
 *
 * <p>展示保存的 AI 穿搭标题、来源、天气和正文摘要；点击卡片由 Activity
 * 弹出完整内容与关联衣物。</p>
 */
public class OutfitAdapter extends RecyclerView.Adapter<OutfitAdapter.OutfitViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Outfit outfit);
    }

    public interface OnItemDeleteListener {
        void onDeleteClick(Outfit outfit);
    }

    private final List<Outfit> items = new ArrayList<>();
    private final OnItemClickListener clickListener;
    private final OnItemDeleteListener deleteListener;

    public OutfitAdapter(OnItemClickListener clickListener) {
        this(clickListener, null);
    }

    public OutfitAdapter(OnItemClickListener clickListener,
                         OnItemDeleteListener deleteListener) {
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
        setHasStableIds(true);
    }

    public void submitList(List<Outfit> newItems) {
        List<Outfit> oldItems = new ArrayList<>(items);
        List<Outfit> incoming = new ArrayList<>(newItems);
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
                Outfit oldItem = oldItems.get(oldPosition);
                Outfit newItem = incoming.get(newPosition);
                return Objects.equals(oldItem.getTitle(), newItem.getTitle())
                        && Objects.equals(oldItem.getContent(), newItem.getContent())
                        && Objects.equals(oldItem.getSource(), newItem.getSource())
                        && Objects.equals(oldItem.getWeatherText(), newItem.getWeatherText())
                        && Objects.equals(oldItem.getTemperature(), newItem.getTemperature())
                        && buildLinkedItemKey(oldItem).equals(
                        buildLinkedItemKey(newItem));
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
    public OutfitViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_outfit, parent, false);
        return new OutfitViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OutfitViewHolder holder, int position) {
        Outfit outfit = items.get(position);
        holder.title.setText(outfit.getTitle());
        holder.source.setText(Outfit.SOURCE_ITEM.equals(outfit.getSource())
                ? R.string.outfit_source_item
                : R.string.outfit_source_today);
        holder.meta.setText(buildMeta(holder.itemView.getContext(), outfit));
        holder.content.setText(AiTextFormatter.clean(outfit.getContent()));
        List<ClothingItem> linkedItems = outfit.getLinkedItems();
        boolean hasLinkedItems = linkedItems != null && !linkedItems.isEmpty();
        holder.itemList.setVisibility(hasLinkedItems ? View.VISIBLE : View.GONE);
        holder.itemsAdapter.submitClothingItems(linkedItems);
        holder.itemView.setOnClickListener(view -> {
            if (clickListener != null) {
                clickListener.onItemClick(outfit);
            }
        });
        holder.deleteButton.setOnClickListener(view -> {
            if (deleteListener != null) {
                deleteListener.onDeleteClick(outfit);
            }
        });
    }

    /**
     * 把关联衣物 ID 拼成稳定字符串，供 DiffUtil 判断图片区域是否变化。
     * 例如删除衣物导致级联关联减少时，卡片必须重新绑定。
     */
    private String buildLinkedItemKey(Outfit outfit) {
        List<ClothingItem> linkedItems = outfit.getLinkedItems();
        if (linkedItems == null || linkedItems.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ClothingItem item : linkedItems) {
            if (item == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(item.getId());
        }
        return builder.toString();
    }

    private String buildMeta(Context context, Outfit outfit) {
        String weather;
        if (outfit.getWeatherText() == null || outfit.getWeatherText().trim().isEmpty()) {
            weather = context.getString(R.string.outfit_weather_unknown);
        } else if (outfit.getTemperature() == null) {
            weather = outfit.getWeatherText();
        } else {
            weather = context.getString(R.string.outfit_weather_format,
                    outfit.getWeatherText(), outfit.getTemperature());
        }
        return context.getString(R.string.outfit_meta_format,
                UiUtils.formatDateTime(outfit.getCreatedAt()), weather);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class OutfitViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView source;
        final TextView meta;
        final TextView content;
        final RecyclerView itemList;
        final View deleteButton;
        final AiClothingThumbnailAdapter itemsAdapter;

        OutfitViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.outfitTitle);
            source = itemView.findViewById(R.id.outfitSource);
            meta = itemView.findViewById(R.id.outfitMeta);
            content = itemView.findViewById(R.id.outfitContent);
            itemList = itemView.findViewById(R.id.outfitItems);
            deleteButton = itemView.findViewById(R.id.deleteOutfitButton);
            itemsAdapter = new AiClothingThumbnailAdapter();
            itemList.setLayoutManager(new LinearLayoutManager(
                    itemView.getContext(),
                    LinearLayoutManager.HORIZONTAL,
                    false));
            itemList.setAdapter(itemsAdapter);
            itemList.setItemAnimator(null);
            itemList.setNestedScrollingEnabled(false);
        }
    }
}
