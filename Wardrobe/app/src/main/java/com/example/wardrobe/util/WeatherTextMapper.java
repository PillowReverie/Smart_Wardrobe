package com.example.wardrobe.util;

import java.util.Locale;

/**
 * 将和风天气返回的英文 condition.text 或天气代码转换为中文。
 *
 * <p>接口在没有额外语言参数时可能返回 Sunny、Cloudy 等英文描述，
 * 页面需要保持中文，因此优先使用数字天气代码做分类映射。</p>
 */
public final class WeatherTextMapper {

    private WeatherTextMapper() {
        // 工具类禁止实例化。
    }

    public static String toChinese(String rawText, String rawCode) {
        if (containsChinese(rawText)) {
            return rawText;
        }

        int code = parseCode(rawCode);
        if (code == 100 || code == 150) {
            return "晴";
        }
        if (code == 101 || code == 151) {
            return "多云";
        }
        if (code == 102 || code == 152) {
            return "少云";
        }
        if (code == 103 || code == 153) {
            return "晴间多云";
        }
        if (code == 104) {
            return "阴";
        }
        if (code >= 200 && code <= 213) {
            return "沙尘";
        }
        if (code >= 300 && code <= 304) {
            return "阵雨";
        }
        if (code >= 305 && code <= 312) {
            return "雨";
        }
        if (code >= 313 && code <= 317) {
            return "冻雨";
        }
        if (code >= 318 && code <= 399) {
            return "雨雪";
        }
        if (code >= 400 && code <= 499) {
            return "雪";
        }
        if (code >= 500 && code <= 515) {
            return "雾";
        }

        String text = rawText == null ? "" : rawText.trim();
        String lower = text.toLowerCase(Locale.US);
        if (lower.contains("sunny") || lower.contains("clear")) {
            return "晴";
        }
        if (lower.contains("partly")) {
            return "晴间多云";
        }
        if (lower.contains("cloudy")) {
            return "多云";
        }
        if (lower.contains("overcast")) {
            return "阴";
        }
        if (lower.contains("thunder")) {
            return "雷阵雨";
        }
        if (lower.contains("snow")) {
            return "雪";
        }
        if (lower.contains("rain")) {
            return "雨";
        }
        if (lower.contains("fog") || lower.contains("mist")
                || lower.contains("haze")) {
            return "雾";
        }
        if (lower.contains("wind")) {
            return "大风";
        }
        return text.isEmpty() ? "未知" : text;
    }

    private static int parseCode(String rawCode) {
        if (rawCode == null) {
            return -1;
        }
        try {
            return Integer.parseInt(rawCode.trim());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static boolean containsChinese(String text) {
        if (text == null) {
            return false;
        }
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            if (Character.UnicodeScript.of(codePoint)
                    == Character.UnicodeScript.HAN) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }
}
