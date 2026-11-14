package com.example.wardrobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Open-Meteo 的 WMO 代码与本地天气提示分类测试。
 */
public class OpenMeteoWeatherCodesTest {

    @Test
    public void mapsCommonWeatherCodesToChinese() {
        assertEquals("晴", OpenMeteoWeatherCodes.toChinese(0));
        assertEquals("阴", OpenMeteoWeatherCodes.toChinese(3));
        assertEquals("小雨", OpenMeteoWeatherCodes.toChinese(61));
        assertEquals("雷暴伴冰雹", OpenMeteoWeatherCodes.toChinese(99));
    }

    @Test
    public void unknownCodeHasSafeFallback() {
        assertEquals("天气未知", OpenMeteoWeatherCodes.toChinese(999));
        assertEquals("天气未知", OpenMeteoWeatherCodes.toChinese(null));
    }

    @Test
    public void identifiesRainSnowThunderAndFog() {
        assertTrue(OpenMeteoWeatherCodes.isRainSnowOrThunder(61));
        assertTrue(OpenMeteoWeatherCodes.isRainSnowOrThunder(73));
        assertTrue(OpenMeteoWeatherCodes.isRainSnowOrThunder(95));
        assertFalse(OpenMeteoWeatherCodes.isRainSnowOrThunder(0));
        assertTrue(OpenMeteoWeatherCodes.isFogOrDust(45));
        assertFalse(OpenMeteoWeatherCodes.isFogOrDust(3));
    }
}
