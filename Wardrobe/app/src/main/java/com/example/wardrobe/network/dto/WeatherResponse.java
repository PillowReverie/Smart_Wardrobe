package com.example.wardrobe.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * 和风天气 current 接口响应。
 *
 * <p>真实响应把 condition 和 temperature 放在顶层，例如：
 * {"condition":{"text":"Sunny","code":"100"},
 *  "temperature":{"value":15.76,"unit":"°C"}}。</p>
 */
public class WeatherResponse {

    @SerializedName("condition")
    private Condition condition;

    @SerializedName("temperature")
    private Temperature temperature;

    public Condition getCondition() {
        return condition;
    }

    public Temperature getTemperature() {
        return temperature;
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

    public static class Temperature {

        @SerializedName("value")
        private double value;

        @SerializedName("unit")
        private String unit;

        public double getValue() {
            return value;
        }

        public String getUnit() {
            return unit;
        }
    }
}
