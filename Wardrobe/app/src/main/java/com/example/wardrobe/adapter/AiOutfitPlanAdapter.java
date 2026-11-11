package com.example.wardrobe.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wardrobe.R;
import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.util.AiTextFormatter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 首页 AI 三套方案的纵向列表。
 *
 * <p>每个方案包含标题、横向衣物图片和推荐理由。列表本身禁用嵌套滚动，
 * 由外层 MaxHeightNestedScrollView 统一滚动，避免页面内出现多层纵向滚动冲突。</p>
 */
public class AiOutfitPlanAdapter
        extends RecyclerView.Adapter<AiOutfitPlanAdapter.PlanViewHolder> {

    public interface OnPlanSaveListener {
        void onSavePlan(AiOutfitPlan plan);
    }

    private final List<AiOutfitPlan> plans = new ArrayList<>();
    private final OnPlanSaveListener saveListener;
    private final Set<String> savedPlanKeys = new HashSet<>();

    public AiOutfitPlanAdapter(OnPlanSaveListener saveListener) {
        this.saveListener = saveListener;
    }

    public void submitList(List<AiOutfitPlan> newPlans) {
        plans.clear();
        if (newPlans != null) {
            plans.addAll(newPlans);
        }
        notifyDataSetChanged();
    }

    /**
     * 保存成功（或数据库中已存在）后，把按钮切换为“已保存”并禁用。
     */
    public void markSaved(AiOutfitPlan plan) {
        if (plan == null) {
            return;
        }
        savedPlanKeys.add(planKey(plan));
        notifyDataSetChanged();
    }

    /**
     * 用户可能在搭配库里删除了某套方案，回到页面时重新允许保存。
     * 真正是否重复仍由数据库层校验。
     */
    public void clearSavedMarks() {
        if (savedPlanKeys.isEmpty()) {
            return;
        }
        savedPlanKeys.clear();
        notifyDataSetChanged();
    }

    private static String planKey(AiOutfitPlan plan) {
        return AiTextFormatter.clean(plan.getRawContent())
                .replaceAll("\\s+", "");
    }

    @NonNull
    @Override
    public PlanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ai_outfit_plan, parent, false);
        return new PlanViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlanViewHolder holder, int position) {
        AiOutfitPlan plan = plans.get(position);
        String title = plan.getTitle();
        holder.title.setText(title.isEmpty()
                ? holder.itemView.getContext().getString(
                R.string.ai_plan_title_format, plan.getSequence())
                : holder.itemView.getContext().getString(
                R.string.ai_plan_title_with_style_format,
                plan.getSequence(),
                title));
        bindRecommendationBadge(holder, plan);
        holder.reason.setText(plan.getReason());
        boolean hasVisualItems = plan.getVisualItems() != null
                && !plan.getVisualItems().isEmpty();
        holder.itemList.setVisibility(hasVisualItems
                ? View.VISIBLE : View.GONE);
        holder.itemsAdapter.submitList(plan.getVisualItems());
        holder.saveButton.setOnClickListener(view -> {
            if (saveListener != null) {
                saveListener.onSavePlan(plan);
            }
        });
        boolean saved = savedPlanKeys.contains(planKey(plan));
        holder.saveButton.setEnabled(!saved);
        holder.saveButton.setText(saved
                ? R.string.ai_saved_plan : R.string.ai_save_plan);
    }

    /**
     * 用不同底色区分“经典套装推荐”和“全新灵感搭配”。
     * 同时展示经典来源，让用户知道这套是从搭配库复用的哪一套。
     */
    private void bindRecommendationBadge(PlanViewHolder holder,
                                         AiOutfitPlan plan) {
        boolean classic = plan.isClassicRecommendation();
        holder.badge.setText(classic
                ? R.string.ai_badge_classic
                : R.string.ai_badge_inspiration);
        int background = classic
                ? R.color.md_primary_container
                : R.color.md_secondary_container;
        int foreground = classic
                ? R.color.md_on_primary_container
                : R.color.md_on_secondary_container;
        holder.badge.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(holder.itemView.getContext(), background)));
        holder.badge.setTextColor(ContextCompat.getColor(
                holder.itemView.getContext(), foreground));

        if (classic && !plan.getClassicOutfitTitle().isEmpty()) {
            holder.classicSource.setText(
                    holder.itemView.getContext().getString(
                            R.string.ai_classic_source_format,
                            plan.getClassicOutfitTitle()));
            holder.classicSource.setVisibility(View.VISIBLE);
        } else {
            holder.classicSource.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return plans.size();
    }

    static class PlanViewHolder extends RecyclerView.ViewHolder {

        final TextView title;
        final TextView badge;
        final TextView classicSource;
        final RecyclerView itemList;
        final TextView reason;
        final Button saveButton;
        final AiClothingThumbnailAdapter itemsAdapter;

        PlanViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.planTitle);
            badge = itemView.findViewById(R.id.planBadge);
            classicSource = itemView.findViewById(R.id.planClassicSource);
            itemList = itemView.findViewById(R.id.planItems);
            reason = itemView.findViewById(R.id.planReason);
            saveButton = itemView.findViewById(R.id.savePlanButton);
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
