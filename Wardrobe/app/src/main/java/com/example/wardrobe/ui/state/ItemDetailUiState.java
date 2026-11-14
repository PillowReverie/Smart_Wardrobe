package com.example.wardrobe.ui.state;

import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.WeatherInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 衣物详情页的不可变 UI 状态。
 */
public final class ItemDetailUiState {

    private final ClothingItem item;
    private final WeatherInfo weather;
    private final boolean aiLoading;
    private final String aiContent;
    private final List<AiOutfitPlan> plans;
    private final String message;
    private final long messageId;

    private ItemDetailUiState(Builder builder) {
        item = builder.item;
        weather = builder.weather;
        aiLoading = builder.aiLoading;
        aiContent = safe(builder.aiContent);
        plans = builder.plans == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(
                new ArrayList<>(builder.plans));
        message = safe(builder.message);
        messageId = builder.messageId;
    }

    public ClothingItem getItem() {
        return item;
    }

    public WeatherInfo getWeather() {
        return weather;
    }

    public boolean isAiLoading() {
        return aiLoading;
    }

    public String getAiContent() {
        return aiContent;
    }

    public List<AiOutfitPlan> getPlans() {
        return new ArrayList<>(plans);
    }

    public String getMessage() {
        return message;
    }

    public long getMessageId() {
        return messageId;
    }

    public Builder buildUpon() {
        return new Builder(this);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class Builder {
        private ClothingItem item;
        private WeatherInfo weather;
        private boolean aiLoading;
        private String aiContent = "";
        private List<AiOutfitPlan> plans = Collections.emptyList();
        private String message = "";
        private long messageId;

        public Builder() {
        }

        private Builder(ItemDetailUiState state) {
            item = state.item;
            weather = state.weather;
            aiLoading = state.aiLoading;
            aiContent = state.aiContent;
            plans = state.plans;
            message = state.message;
            messageId = state.messageId;
        }

        public Builder item(ClothingItem value) {
            item = value;
            return this;
        }

        public Builder weather(WeatherInfo value) {
            weather = value;
            return this;
        }

        public Builder aiLoading(boolean value) {
            aiLoading = value;
            return this;
        }

        public Builder aiContent(String value) {
            aiContent = value;
            return this;
        }

        public Builder plans(List<AiOutfitPlan> value) {
            plans = value;
            return this;
        }

        public Builder message(String value, long id) {
            message = value;
            messageId = id;
            return this;
        }

        public ItemDetailUiState build() {
            return new ItemDetailUiState(this);
        }
    }
}
