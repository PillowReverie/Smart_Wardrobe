package com.example.wardrobe.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页 AI 三套方案中的一套。
 *
 * <p>visualItems 保存图片区要展示的单品：匹配成功时持有数据库衣物对象，
 * 匹配失败时 clothingItem 为 null，适配器会显示默认占位图。</p>
 */
public class AiOutfitPlan {

    public static final String TYPE_CLASSIC = "CLASSIC";
    public static final String TYPE_INSPIRATION = "INSPIRATION";

    private int sequence;
    private String title = "";
    private String reason = "";
    private String rawContent = "";
    private String recommendationType = TYPE_INSPIRATION;
    private String classicOutfitTitle = "";
    private final List<VisualItem> visualItems = new ArrayList<>();

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? "" : reason;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent == null ? "" : rawContent;
    }

    public String getRecommendationType() {
        return recommendationType;
    }

    public void setRecommendationType(String recommendationType) {
        this.recommendationType = TYPE_CLASSIC.equals(recommendationType)
                ? TYPE_CLASSIC : TYPE_INSPIRATION;
    }

    public boolean isClassicRecommendation() {
        return TYPE_CLASSIC.equals(recommendationType);
    }

    public String getClassicOutfitTitle() {
        return classicOutfitTitle;
    }

    public void setClassicOutfitTitle(String classicOutfitTitle) {
        this.classicOutfitTitle = classicOutfitTitle == null
                ? "" : classicOutfitTitle.trim();
    }

    public List<VisualItem> getVisualItems() {
        return visualItems;
    }

    public void addVisualItem(VisualItem item) {
        if (item != null) {
            visualItems.add(item);
        }
    }

    public void clearVisualItems() {
        visualItems.clear();
    }

    /**
     * 一个图片卡需要的数据。referencedName 是模型在【】中写出的原始名称。
     */
    public static class VisualItem {

        private final String referencedName;
        private final ClothingItem clothingItem;

        public VisualItem(String referencedName, ClothingItem clothingItem) {
            this.referencedName = referencedName == null ? "" : referencedName;
            this.clothingItem = clothingItem;
        }

        public String getReferencedName() {
            return referencedName;
        }

        public ClothingItem getClothingItem() {
            return clothingItem;
        }

        public boolean isMatched() {
            return clothingItem != null;
        }
    }
}
