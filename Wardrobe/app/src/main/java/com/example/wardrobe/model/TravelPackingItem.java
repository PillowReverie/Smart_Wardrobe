package com.example.wardrobe.model;

/**
 * 旅行打包结果中的一件衣服。
 *
 * <p>clothingItem 为空表示模型写了【名称】但本地衣橱没有匹配到，
 * UI 必须展示占位图且禁止加入旅行衣橱。</p>
 */
public final class TravelPackingItem {

    private final String referencedName;
    private final ClothingItem clothingItem;

    public TravelPackingItem(String referencedName, ClothingItem clothingItem) {
        this.referencedName = referencedName == null
                ? "" : referencedName.trim();
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
