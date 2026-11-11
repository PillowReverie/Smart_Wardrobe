package com.example.wardrobe.ui.state;

import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.model.TravelPackingItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 首页“异地游玩”单一状态源。
 *
 * <p>Activity 只根据 kind 渲染；加载、成功和错误之间不会同时存在，
 * 避免旧代码多个布尔值互相覆盖。</p>
 */
public final class TravelUiState {

    public enum Kind {
        IDLE,
        LOADING,
        PACKING,
        OUTFIT,
        ERROR
    }

    private final Kind kind;
    private final String content;
    private final String scene;
    private final String message;
    private final List<TravelPackingItem> packingItems;
    private final List<AiOutfitPlan> outfitPlans;

    private TravelUiState(Kind kind,
                          String content,
                          String scene,
                          String message,
                          List<TravelPackingItem> packingItems,
                          List<AiOutfitPlan> outfitPlans) {
        this.kind = kind;
        this.content = content == null ? "" : content;
        this.scene = scene == null ? "" : scene;
        this.message = message == null ? "" : message;
        this.packingItems = packingItems == null
                ? Collections.emptyList() : new ArrayList<>(packingItems);
        this.outfitPlans = outfitPlans == null
                ? Collections.emptyList() : new ArrayList<>(outfitPlans);
    }

    public static TravelUiState idle() {
        return new TravelUiState(
                Kind.IDLE, "", "", "", null, null);
    }

    public static TravelUiState loading(String scene) {
        return new TravelUiState(
                Kind.LOADING, "", scene, "", null, null);
    }

    public static TravelUiState packing(String content,
                                        String scene,
                                        List<TravelPackingItem> items) {
        return new TravelUiState(
                Kind.PACKING, content, scene, "", items, null);
    }

    public static TravelUiState outfit(String content,
                                       String scene,
                                       List<AiOutfitPlan> plans) {
        return new TravelUiState(
                Kind.OUTFIT, content, scene, "", null, plans);
    }

    public static TravelUiState error(String message, String scene) {
        return new TravelUiState(
                Kind.ERROR, "", scene, message, null, null);
    }

    public Kind getKind() {
        return kind;
    }

    public String getContent() {
        return content;
    }

    public String getScene() {
        return scene;
    }

    public String getMessage() {
        return message;
    }

    public List<TravelPackingItem> getPackingItems() {
        return new ArrayList<>(packingItems);
    }

    public List<AiOutfitPlan> getOutfitPlans() {
        return new ArrayList<>(outfitPlans);
    }

    public boolean isLoading() {
        return kind == Kind.LOADING;
    }
}
