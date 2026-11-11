package com.example.wardrobe.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 和风天气每日预报响应。
 *
 * <p>当前 Host 返回的新版结构使用 days 数组、temperatureMax/Min 对象，
 * 并把天气现象和风力放在 daytime/nighttime 中。同时保留旧版 daily、
 * tempMax/tempMin、textDay 等字段，兼容不同套餐和版本。</p>
 */
public class DailyWeatherResponse {

    @SerializedName("days")
    private List<Daily> days;

    @SerializedName("daily")
    private List<Daily> legacyDaily;

    public List<Daily> getDays() {
        if (days != null && !days.isEmpty()) {
            return days;
        }
        return legacyDaily;
    }

    public static class Daily {

        @SerializedName("temperatureMax")
        private Temperature temperatureMax;

        @SerializedName("temperatureMin")
        private Temperature temperatureMin;

        @SerializedName("uvIndexMax")
        private Integer uvIndexMax;

        @SerializedName("daytime")
        private DayPeriod daytime;

        @SerializedName("nighttime")
        private DayPeriod nighttime;

        /* 以下字段用于兼容旧版 daily 响应。 */
        @SerializedName("tempMax")
        private String legacyTempMax;

        @SerializedName("tempMin")
        private String legacyTempMin;

        @SerializedName("textDay")
        private String legacyTextDay;

        @SerializedName("textNight")
        private String legacyTextNight;

        @SerializedName("iconDay")
        private String legacyIconDay;

        @SerializedName("iconNight")
        private String legacyIconNight;

        @SerializedName("uvIndex")
        private String legacyUvIndex;

        @SerializedName("windScaleDay")
        private String legacyWindScaleDay;

        public Double getTempMaxValue() {
            return temperatureMax == null ? null : temperatureMax.value;
        }

        public Double getTempMinValue() {
            return temperatureMin == null ? null : temperatureMin.value;
        }

        public Integer getUvIndexMax() {
            return uvIndexMax;
        }

        public DayPeriod getDaytime() {
            return daytime;
        }

        public DayPeriod getNighttime() {
            return nighttime;
        }

        public String getLegacyTempMax() {
            return legacyTempMax;
        }

        public String getLegacyTempMin() {
            return legacyTempMin;
        }

        public String getLegacyTextDay() {
            return legacyTextDay;
        }

        public String getLegacyTextNight() {
            return legacyTextNight;
        }

        public String getLegacyIconDay() {
            return legacyIconDay;
        }

        public String getLegacyIconNight() {
            return legacyIconNight;
        }

        public String getLegacyUvIndex() {
            return legacyUvIndex;
        }

        public String getLegacyWindScaleDay() {
            return legacyWindScaleDay;
        }
    }

    public static class Temperature {

        @SerializedName("value")
        private Double value;

        public Double getValue() {
            return value;
        }
    }

    public static class DayPeriod {

        @SerializedName("condition")
        private Condition condition;

        @SerializedName("wind")
        private Wind wind;

        public Condition getCondition() {
            return condition;
        }

        public Wind getWind() {
            return wind;
        }
    }

    public static class Condition {

        @SerializedName("text")
        private String text;

        @SerializedName("code")
        private String code;

        public String getText() {
            return text;
        }

        public String getCode() {
            return code;
        }
    }

    public static class Wind {

        @SerializedName("scale")
        private Integer scale;

        public Integer getScale() {
            return scale;
        }
    }
}
