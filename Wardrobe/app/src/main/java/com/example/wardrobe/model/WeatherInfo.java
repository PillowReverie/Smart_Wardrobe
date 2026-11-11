package com.example.wardrobe.model;

/**
 * 天气展示与 AI 上下文模型。
 *
 * <p>每日预报接口提供最低温、最高温、天气现象、紫外线指数和风力等级。
 * 旧构造方法保留单温度，供历史搭配记录和测试兼容使用。</p>
 */
public class WeatherInfo {

    private final int tempMin;
    private final int tempMax;
    private final String conditionText;
    private final int uvIndex;
    private final String windScale;
    private final long fetchedAt;

    public WeatherInfo(int temperature, String conditionText, long fetchedAt) {
        this(temperature, temperature, conditionText, 0, "", fetchedAt);
    }

    public WeatherInfo(int tempMin,
                       int tempMax,
                       String conditionText,
                       int uvIndex,
                       String windScale,
                       long fetchedAt) {
        this.tempMin = Math.min(tempMin, tempMax);
        this.tempMax = Math.max(tempMin, tempMax);
        this.conditionText = conditionText;
        this.uvIndex = uvIndex;
        this.windScale = windScale == null ? "" : windScale;
        this.fetchedAt = fetchedAt;
    }

    /**
     * 兼容 outfits 表只存单个温度的旧结构，保存时使用全天平均温度。
     */
    public int getTemperature() {
        return Math.round((tempMin + tempMax) / 2f);
    }

    public int getTempMin() {
        return tempMin;
    }

    public int getTempMax() {
        return tempMax;
    }

    public int getTemperatureRange() {
        return tempMax - tempMin;
    }

    public String getConditionText() {
        return conditionText;
    }

    public int getUvIndex() {
        return uvIndex;
    }

    public String getWindScale() {
        return windScale;
    }

    public long getFetchedAt() {
        return fetchedAt;
    }
}
