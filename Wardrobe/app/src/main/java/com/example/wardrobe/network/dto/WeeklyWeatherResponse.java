package com.example.wardrobe.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 和风天气七天预报响应（v7 格式）。
 */
public class WeeklyWeatherResponse {

    @SerializedName("code")
    private String code;

    @SerializedName("daily")
    private List<Day> daily;

    public String getCode() {
        return code;
    }

    public List<Day> getDaily() {
        return daily;
    }

    public static class Day {

        @SerializedName("fxDate")
        private String fxDate;

        @SerializedName("textDay")
        private String textDay;

        @SerializedName("textNight")
        private String textNight;

        @SerializedName("tempMax")
        private String tempMax;

        @SerializedName("tempMin")
        private String tempMin;

        @SerializedName("windScaleDay")
        private String windScaleDay;

        public String getFxDate() {
            return fxDate;
        }

        public String getTextDay() {
            return textDay;
        }

        public String getTextNight() {
            return textNight;
        }

        public String getTempMax() {
            return tempMax;
        }

        public String getTempMin() {
            return tempMin;
        }

        public String getWindScaleDay() {
            return windScaleDay;
        }
    }
}
