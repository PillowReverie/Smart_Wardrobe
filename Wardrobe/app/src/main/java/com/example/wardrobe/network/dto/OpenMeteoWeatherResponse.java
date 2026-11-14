package com.example.wardrobe.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Open-Meteo 当前天气响应，用于每日预报失败时的轻量兜底。
 */
public class OpenMeteoWeatherResponse {

    @SerializedName("current")
    private Current current;

    public Current getCurrent() {
        return current;
    }

    public static class Current {

        @SerializedName("temperature_2m")
        private Double temperature;

        @SerializedName("weather_code")
        private Integer weatherCode;

        public Double getTemperature() {
            return temperature;
        }

        public Integer getWeatherCode() {
            return weatherCode;
        }
    }
}
