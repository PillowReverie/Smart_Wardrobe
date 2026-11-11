package com.example.wardrobe.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从“洛阳 3天”“去洛阳玩三天”这类自然语言中提取城市和天数。
 */
public final class TravelRequestParser {

    private static final Pattern DAYS_PATTERN =
            Pattern.compile("([0-9]+|[一二两三四五六七八九十]+)\\s*天");
    private static final Pattern PLAIN_DAYS_PATTERN =
            Pattern.compile("(?<![0-9])([0-9]{1,2})(?![0-9])");
    private static final Pattern CITY_PATTERN =
            Pattern.compile("[\\u4e00-\\u9fa5A-Za-z]{2,10}");

    private TravelRequestParser() {
    }

    public static final class Request {
        private final String city;
        private final int days;

        Request(String city, int days) {
            this.city = city;
            this.days = days;
        }

        public String getCity() {
            return city;
        }

        public int getDays() {
            return days;
        }
    }

    public static Request parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }

        Matcher dayMatcher = DAYS_PATTERN.matcher(text);
        if (!dayMatcher.find()) {
            // 兼容“Beijing 3”这种省略“天”字的写法。
            dayMatcher = PLAIN_DAYS_PATTERN.matcher(text);
            if (!dayMatcher.find()) {
                return null;
            }
        }
        int days = parseNumber(dayMatcher.group(1));
        if (days <= 0) {
            return null;
        }
        // 超过七天的行程仍保留原天数，由 Prompt 说明天气只覆盖前七天。
        days = Math.min(days, 30);
        text = text.substring(0, dayMatcher.start()) + " "
                + text.substring(dayMatcher.end());

        String cleaned = text
                .replaceAll("(去|到|在|旅游|旅行|游玩|玩|逛|待|住|的|计划|行程|"
                        + "帮我|请|安排|一下|几天)", " ")
                .replaceAll("[\\s,，。;；:：!！?？、]+", " ")
                .trim();

        Matcher cityMatcher = CITY_PATTERN.matcher(cleaned);
        if (!cityMatcher.find()) {
            return null;
        }
        String city = cityMatcher.group();
        if (city.length() < 2) {
            return null;
        }
        return new Request(city, days);
    }

    private static int parseNumber(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            // 继续按中文数字解析。
        }
        int result = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '十') {
                result = Math.max(1, result) * 10;
                continue;
            }
            int digit = "零一二三四五六七八九".indexOf(ch);
            if (ch == '两') {
                digit = 2;
            }
            if (digit >= 0) {
                result = result * 10 + digit;
            }
        }
        return result;
    }
}
