package com.example.wardrobe.util;

import static org.junit.Assert.assertTrue;

import com.example.wardrobe.network.dto.OpenMeteoForecastResponse;
import com.google.gson.Gson;

import org.junit.Test;

/**
 * Open-Meteo 七日天气摘要测试，确保数组按日期对齐且空字段不会崩溃。
 */
public class OpenMeteoTravelClientTest {

    @Test
    public void formatsSevenDayForecast() {
        String json = "{"
                + "\"daily\":{"
                + "\"time\":[\"2026-11-14\",\"2026-11-15\"],"
                + "\"weather_code\":[0,61],"
                + "\"temperature_2m_max\":[20.2,16.8],"
                + "\"temperature_2m_min\":[8.1,7.4]"
                + "}}";
        OpenMeteoForecastResponse response =
                new Gson().fromJson(json, OpenMeteoForecastResponse.class);
        String summary = OpenMeteoTravelClient.formatForecast(response);
        assertTrue(summary.contains("11月14日 晴 8~20℃"));
        assertTrue(summary.contains("11月15日 小雨 7~17℃"));
    }

    @Test
    public void malformedForecastReturnsEmptySummary() {
        assertEqualsEmpty(OpenMeteoTravelClient.formatForecast(null));
    }

    private void assertEqualsEmpty(String value) {
        assertTrue(value == null || value.isEmpty());
    }
}
