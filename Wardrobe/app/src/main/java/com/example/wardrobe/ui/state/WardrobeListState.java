package com.example.wardrobe.ui.state;

import com.example.wardrobe.model.ClothingItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 衣橱列表页的不可变 UI 状态。
 */
public final class WardrobeListState {

    private final List<ClothingItem> items;
    private final int allItemCount;
    private final int travelItemCount;
    private final boolean travelTab;
    private final String keyword;
    private final boolean loading;
    private final String errorMessage;

    public WardrobeListState(List<ClothingItem> items,
                             int allItemCount,
                             int travelItemCount,
                             boolean travelTab,
                             String keyword,
                             boolean loading,
                             String errorMessage) {
        this.items = items == null
                ? Collections.emptyList() : new ArrayList<>(items);
        this.allItemCount = allItemCount;
        this.travelItemCount = travelItemCount;
        this.travelTab = travelTab;
        this.keyword = keyword == null ? "" : keyword;
        this.loading = loading;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public List<ClothingItem> getItems() {
        return new ArrayList<>(items);
    }

    public int getAllItemCount() {
        return allItemCount;
    }

    public int getTravelItemCount() {
        return travelItemCount;
    }

    public boolean isTravelTab() {
        return travelTab;
    }

    public String getKeyword() {
        return keyword;
    }

    public boolean isLoading() {
        return loading;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
