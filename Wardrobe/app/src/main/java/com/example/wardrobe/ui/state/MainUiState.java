package com.example.wardrobe.ui.state;

import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.model.TravelPackingItem;
import com.example.wardrobe.model.WeatherInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 首页唯一的不可变 UI 状态。
 *
 * <p>Activity 只读取该对象完成渲染，不再自行维护天气请求锁、AI 请求锁、
 * 当前天气、缓存日期等业务字段。</p>
 */
public final class MainUiState {

    public enum ContentType {
        IDLE,
        PLANS,
        PACKING,
        TEXT
    }

    private final boolean weatherLoading;
    private final boolean aiLoading;
    private final String loadingText;
    private final WeatherInfo weatherInfo;
    private final String locationName;
    private final String weatherAlert;
    private final String mode;
    private final String scene;
    private final String aiContent;
    private final String aiDate;
    private final String packingListText;
    private final List<AiOutfitPlan> aiPlans;
    private final List<TravelPackingItem> packingItems;
    private final ContentType contentType;
    private final boolean genderSelected;
    private final int wardrobeCount;
    private final int outfitCount;
    private final String message;
    private final long messageId;

    private MainUiState(Builder builder) {
        weatherLoading = builder.weatherLoading;
        aiLoading = builder.aiLoading;
        loadingText = safe(builder.loadingText);
        weatherInfo = builder.weatherInfo;
        locationName = safe(builder.locationName);
        weatherAlert = safe(builder.weatherAlert);
        mode = safe(builder.mode);
        scene = safe(builder.scene);
        aiContent = safe(builder.aiContent);
        aiDate = safe(builder.aiDate);
        packingListText = safe(builder.packingListText);
        aiPlans = immutable(builder.aiPlans);
        packingItems = immutable(builder.packingItems);
        contentType = builder.contentType == null
                ? ContentType.IDLE : builder.contentType;
        genderSelected = builder.genderSelected;
        wardrobeCount = Math.max(0, builder.wardrobeCount);
        outfitCount = Math.max(0, builder.outfitCount);
        message = safe(builder.message);
        messageId = builder.messageId;
    }

    public boolean isWeatherLoading() {
        return weatherLoading;
    }

    public boolean isAiLoading() {
        return aiLoading;
    }

    public String getLoadingText() {
        return loadingText;
    }

    public WeatherInfo getWeatherInfo() {
        return weatherInfo;
    }

    public String getLocationName() {
        return locationName;
    }

    public String getWeatherAlert() {
        return weatherAlert;
    }

    public String getMode() {
        return mode;
    }

    public String getScene() {
        return scene;
    }

    public String getAiContent() {
        return aiContent;
    }

    public String getAiDate() {
        return aiDate;
    }

    public String getPackingListText() {
        return packingListText;
    }

    public List<AiOutfitPlan> getAiPlans() {
        return new ArrayList<>(aiPlans);
    }

    public List<TravelPackingItem> getPackingItems() {
        return new ArrayList<>(packingItems);
    }

    public ContentType getContentType() {
        return contentType;
    }

    public boolean hasGender() {
        return genderSelected;
    }

    public int getWardrobeCount() {
        return wardrobeCount;
    }

    public int getOutfitCount() {
        return outfitCount;
    }

    public String getMessage() {
        return message;
    }

    public long getMessageId() {
        return messageId;
    }

    public boolean isTravelMode() {
        return "TRAVEL".equals(mode);
    }

    public boolean isTravelInputEmpty() {
        return isTravelMode() && scene.trim().isEmpty();
    }

    public Builder buildUpon() {
        return new Builder(this);
    }

    private static <T> List<T> immutable(List<T> source) {
        return source == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class Builder {

        private boolean weatherLoading;
        private boolean aiLoading;
        private String loadingText = "";
        private WeatherInfo weatherInfo;
        private String locationName = "";
        private String weatherAlert = "";
        private String mode = "INSPIRATION";
        private String scene = "";
        private String aiContent = "";
        private String aiDate = "";
        private String packingListText = "";
        private List<AiOutfitPlan> aiPlans = Collections.emptyList();
        private List<TravelPackingItem> packingItems = Collections.emptyList();
        private ContentType contentType = ContentType.IDLE;
        private boolean genderSelected;
        private int wardrobeCount;
        private int outfitCount;
        private String message = "";
        private long messageId;

        public Builder() {
        }

        private Builder(MainUiState state) {
            weatherLoading = state.weatherLoading;
            aiLoading = state.aiLoading;
            loadingText = state.loadingText;
            weatherInfo = state.weatherInfo;
            locationName = state.locationName;
            weatherAlert = state.weatherAlert;
            mode = state.mode;
            scene = state.scene;
            aiContent = state.aiContent;
            aiDate = state.aiDate;
            packingListText = state.packingListText;
            aiPlans = state.aiPlans;
            packingItems = state.packingItems;
            contentType = state.contentType;
            genderSelected = state.genderSelected;
            wardrobeCount = state.wardrobeCount;
            outfitCount = state.outfitCount;
            message = state.message;
            messageId = state.messageId;
        }

        public Builder weatherLoading(boolean value) {
            weatherLoading = value;
            return this;
        }

        public Builder aiLoading(boolean value) {
            aiLoading = value;
            return this;
        }

        public Builder loadingText(String value) {
            loadingText = value;
            return this;
        }

        public Builder weatherInfo(WeatherInfo value) {
            weatherInfo = value;
            return this;
        }

        public Builder locationName(String value) {
            locationName = value;
            return this;
        }

        public Builder weatherAlert(String value) {
            weatherAlert = value;
            return this;
        }

        public Builder mode(String value) {
            mode = value;
            return this;
        }

        public Builder scene(String value) {
            scene = value;
            return this;
        }

        public Builder aiContent(String value) {
            aiContent = value;
            return this;
        }

        public Builder aiDate(String value) {
            aiDate = value;
            return this;
        }

        public Builder packingListText(String value) {
            packingListText = value;
            return this;
        }

        public Builder aiPlans(List<AiOutfitPlan> value) {
            aiPlans = value;
            return this;
        }

        public Builder packingItems(List<TravelPackingItem> value) {
            packingItems = value;
            return this;
        }

        public Builder contentType(ContentType value) {
            contentType = value;
            return this;
        }

        public Builder genderSelected(boolean value) {
            genderSelected = value;
            return this;
        }

        public Builder wardrobeCount(int value) {
            wardrobeCount = value;
            return this;
        }

        public Builder outfitCount(int value) {
            outfitCount = value;
            return this;
        }

        public Builder message(String value, long id) {
            message = value;
            messageId = id;
            return this;
        }

        public MainUiState build() {
            return new MainUiState(this);
        }
    }
}
