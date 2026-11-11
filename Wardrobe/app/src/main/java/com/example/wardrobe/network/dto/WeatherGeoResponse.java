package com.example.wardrobe.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 和风天气城市查询（逆地理编码）响应。
 *
 * <p>接口返回的 location 是数组，正常查询时取第一项即可。首页将 adm1
 * 作为省份、name 作为城市或区县，例如“宁夏 隆德县”。</p>
 */
public class WeatherGeoResponse {

    @SerializedName("location")
    private List<Location> locations;

    public List<Location> getLocations() {
        return locations;
    }

    public static class Location {

        @SerializedName("id")
        private String id;

        @SerializedName("name")
        private String name;

        @SerializedName("adm1")
        private String adm1;

        @SerializedName("lat")
        private String lat;

        @SerializedName("lon")
        private String lon;

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getAdm1() {
            return adm1;
        }

        public String getLat() {
            return lat;
        }

        public String getLon() {
            return lon;
        }
    }
}
