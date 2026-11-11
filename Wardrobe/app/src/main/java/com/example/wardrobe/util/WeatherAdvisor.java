package com.example.wardrobe.util;

import android.content.Context;

import com.example.wardrobe.R;
import com.example.wardrobe.model.WeatherInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 根据每日天气生成本地提示，并为 Prompt 提供雨雪、防晒和大风上下文。
 */
public final class WeatherAdvisor {

    private static final Pattern FIRST_NUMBER = Pattern.compile("(\\d+)");

    private WeatherAdvisor() {
        // 工具类禁止实例化。
    }

    public static String buildAlert(Context context, WeatherInfo weather) {
        if (weather == null) {
            return "";
        }
        String condition = safeCondition(weather);
        if (hasRainSnowOrThunder(condition)) {
            return context.getString(R.string.weather_alert_rain);
        }
        if (hasSevereWeather(weather)) {
            return context.getString(R.string.weather_alert_severe);
        }
        if (isSunny(condition)
                && (weather.getTempMax() >= 25 || weather.getUvIndex() >= 6)) {
            return context.getString(R.string.weather_alert_sun);
        }
        return context.getString(R.string.weather_alert_pleasant);
    }

    /**
     * 把生活化提醒写进 User Prompt，让 AI 在推荐理由中主动提及雨具或防晒。
     */
    public static String buildPromptContext(WeatherInfo weather) {
        if (weather == null) {
            return "";
        }
        String condition = safeCondition(weather);
        List<String> reminders = new ArrayList<>();
        if (hasRainSnowOrThunder(condition)) {
            reminders.add("今天有雨/雪或雷雨，请建议用户携带雨具并注意路面湿滑");
        }
        if (isSunny(condition)
                && (weather.getTempMax() >= 25 || weather.getUvIndex() >= 6)) {
            reminders.add("今天阳光较强，请建议用户注意防晒或携带遮阳伞");
        }
        if (hasSevereWeather(weather)) {
            reminders.add("今天有大风或空气质量不佳，请建议用户减少户外活动");
        }
        return reminders.isEmpty() ? "" : String.join("；", reminders) + "。";
    }

    private static boolean hasRainSnowOrThunder(String condition) {
        return condition.contains("雨")
                || condition.contains("雪")
                || condition.contains("雷");
    }

    private static boolean isSunny(String condition) {
        return condition.contains("晴");
    }

    private static boolean hasSevereWeather(WeatherInfo weather) {
        String condition = safeCondition(weather);
        boolean airOrDust = condition.contains("霾")
                || condition.contains("沙")
                || condition.contains("尘")
                || condition.contains("雾")
                || condition.contains("大风")
                || condition.contains("强风")
                || condition.contains("台风");
        return airOrDust || getMaxWindScale(weather.getWindScale()) >= 6;
    }

    private static int getMaxWindScale(String windScale) {
        if (windScale == null) {
            return 0;
        }
        Matcher matcher = FIRST_NUMBER.matcher(windScale);
        int maximum = 0;
        while (matcher.find()) {
            try {
                maximum = Math.max(maximum, Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // 非数字风力描述直接忽略。
            }
        }
        return maximum;
    }

    private static String safeCondition(WeatherInfo weather) {
        return weather.getConditionText() == null
                ? "" : weather.getConditionText();
    }
}
