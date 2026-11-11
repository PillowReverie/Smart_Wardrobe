package com.example.wardrobe.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.example.wardrobe.network.dto.DailyWeatherResponse;
import com.google.gson.Gson;

import org.junit.Test;

/**
 * 使用和风天气当前 Host 的新版字段样例验证 DTO，防止把 days 写成 daily、
 * 把 temperatureMax.value 写成字符串等解析回归。
 */
public class DailyWeatherResponseTest {

    @Test
    public void parsesCurrentQWeatherDailyShape() {
        String json = "{\"days\":[{"
                + "\"temperatureMax\":{\"value\":11.63,\"unit\":\"°C\"},"
                + "\"temperatureMin\":{\"value\":4.49,\"unit\":\"°C\"},"
                + "\"uvIndexMax\":3,"
                + "\"daytime\":{\"condition\":{\"text\":\"晴间多云\",\"code\":\"103\"},"
                + "\"wind\":{\"scale\":2}},"
                + "\"nighttime\":{\"condition\":{\"text\":\"小雨\",\"code\":\"305\"},"
                + "\"wind\":{\"scale\":4}}"
                + "}]}";

        DailyWeatherResponse response = new Gson().fromJson(
                json, DailyWeatherResponse.class);

        assertNotNull(response);
        assertEquals(1, response.getDays().size());
        DailyWeatherResponse.Daily day = response.getDays().get(0);
        assertEquals(11.63, day.getTempMaxValue(), 0.01);
        assertEquals(4.49, day.getTempMinValue(), 0.01);
        assertEquals(3, day.getUvIndexMax().intValue());
        assertEquals("小雨",
                day.getNighttime().getCondition().getText());
        assertEquals(4, day.getNighttime().getWind().getScale().intValue());
    }
}
