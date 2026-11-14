package com.example.wardrobe.util;

/**
 * WMO 天气代码到中文描述的映射。
 *
 * <p>Open-Meteo 使用 WMO Weather interpretation codes。未知代码统一显示
 * “天气未知”，保证解析异常不会破坏首页和 AI 上下文。</p>
 */
public final class OpenMeteoWeatherCodes {

    private OpenMeteoWeatherCodes() {
        // 纯映射工具类。
    }

    public static String toChinese(Integer code) {
        if (code == null) {
            return "天气未知";
        }
        switch (code) {
            case 0:
                return "晴";
            case 1:
                return "大部晴朗";
            case 2:
                return "多云";
            case 3:
                return "阴";
            case 45:
            case 48:
                return "雾";
            case 51:
            case 53:
            case 55:
                return "毛毛雨";
            case 56:
            case 57:
                return "冻毛毛雨";
            case 61:
                return "小雨";
            case 63:
                return "中雨";
            case 65:
                return "大雨";
            case 66:
            case 67:
                return "冻雨";
            case 71:
                return "小雪";
            case 73:
                return "中雪";
            case 75:
                return "大雪";
            case 77:
                return "米雪";
            case 80:
                return "小阵雨";
            case 81:
                return "阵雨";
            case 82:
                return "强阵雨";
            case 85:
                return "小阵雪";
            case 86:
                return "强阵雪";
            case 95:
                return "雷暴";
            case 96:
            case 99:
                return "雷暴伴冰雹";
            default:
                return "天气未知";
        }
    }

    /**
     * 本地生活预警使用的简单天气分类，不依赖和风天气的预警接口。
     */
    public static boolean isRainSnowOrThunder(Integer code) {
        if (code == null) {
            return false;
        }
        return (code >= 51 && code <= 67)
                || (code >= 71 && code <= 77)
                || (code >= 80 && code <= 86)
                || code >= 95;
    }

    public static boolean isFogOrDust(Integer code) {
        return code != null && (code == 45 || code == 48);
    }
}
