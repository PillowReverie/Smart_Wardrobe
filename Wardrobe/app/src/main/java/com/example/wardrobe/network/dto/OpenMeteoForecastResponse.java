package com.example.wardrobe.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Open-Meteo 每日天气预报响应。
 *
 * <p>请求使用 timezone=auto，返回的日期是目的地当地日期。daily 中的各数组
 * 按下标一一对应；Gson 在字段缺失时会保留为 null，解析端必须自行兜底。</p>
 */
public class OpenMeteoForecastResponse {

    @SerializedName("latitude")
    private Double latitude;

    @SerializedName("longitude")
    private Double longitude;

    @SerializedName("timezone")
    private String timezone;

    @SerializedName("daily")
    private Daily daily;

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getTimezone() {
        return timezone;
    }

    public Daily getDaily() {
        return daily;
    }

    public static class Daily {

        @SerializedName("time")
        private List<String> dates;

        @SerializedName("weather_code")
        private List<Integer> weatherCodes;

        @SerializedName("temperature_2m_max")
        private List<Double> temperatureMax;

        @SerializedName("temperature_2m_min")
        private List<Double> temperatureMin;

        public List<String> getDates() {
            return dates;
        }

        public List<Integer> getWeatherCodes() {
            return weatherCodes;
        }

        public List<Double> getTemperatureMax() {
            return temperatureMax;
        }

        public List<Double> getTemperatureMin() {
            return temperatureMin;
        }
    }
}
