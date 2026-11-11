package com.example.wardrobe.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.wardrobe.model.WeatherInfo;

import org.junit.Test;

/**
 * 验证雨雪、防晒和恶劣天气判断，不依赖 Android Context 的 Prompt 上下文
 * 部分可以直接进行 JVM 单元测试。
 */
public class WeatherAdvisorTest {

    @Test
    public void rainyDayAddsUmbrellaContext() {
        WeatherInfo weather = new WeatherInfo(10, 16, "中雨", 1, "4-5", 0L);
        assertTrue(WeatherAdvisor.buildPromptContext(weather)
                .contains("携带雨具"));
    }

    @Test
    public void hotSunnyDayAddsSunProtectionContext() {
        WeatherInfo weather = new WeatherInfo(24, 30, "晴", 7, "2-3", 0L);
        assertTrue(WeatherAdvisor.buildPromptContext(weather)
                .contains("注意防晒"));
    }

    @Test
    public void normalDayHasNoExtraPromptReminder() {
        WeatherInfo weather = new WeatherInfo(18, 23, "多云", 3, "2-3", 0L);
        assertFalse(WeatherAdvisor.buildPromptContext(weather)
                .contains("雨具"));
    }
}
