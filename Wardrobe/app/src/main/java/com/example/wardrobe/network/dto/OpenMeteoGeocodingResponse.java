package com.example.wardrobe.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Open-Meteo 城市搜索响应。
 *
 * <p>结果中的 name、country、admin1 用于展示，latitude/longitude 用于查询
 * 目的地天气；没有 results 字段表示未找到城市。</p>
 */
public class OpenMeteoGeocodingResponse {

    @SerializedName("results")
    private List<Result> results;

    public List<Result> getResults() {
        return results;
    }

    public static class Result {

        @SerializedName("name")
        private String name;

        @SerializedName("country")
        private String country;

        @SerializedName("admin1")
        private String admin1;

        @SerializedName("latitude")
        private Double latitude;

        @SerializedName("longitude")
        private Double longitude;

        @SerializedName("timezone")
        private String timezone;

        public String getName() {
            return name;
        }

        public String getCountry() {
            return country;
        }

        public String getAdmin1() {
            return admin1;
        }

        public Double getLatitude() {
            return latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public String getTimezone() {
            return timezone;
        }
    }
}
