package com.example.wardrobe.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * 第三方接口配置中心。
 *
 * <p>源码中不保存任何真实 API Key。天气默认使用无需 Key 的 Open-Meteo；
 * 用户填写和风天气配置后，天气层自动优先使用和风天气。大模型仍由用户
 * 在设置页填写并保存到本机 SharedPreferences。</p>
 */
public final class ApiConfig {

    private ApiConfig() {
        // 纯配置类，禁止实例化。
    }

    /* ------------------------------ 源码默认值 ------------------------------ */

    /**
     * 不再在源码中提供真实服务地址和密钥，避免提交仓库时泄露。
     * 以下常量仅作为“未配置”的默认值保留。
     */
    public static final String WEATHER_BASE_URL = "";
    public static final String WEATHER_API_KEY = "";
    public static final String LLM_BASE_URL = "";
    public static final String LLM_API_KEY = "";
    public static final String LLM_MODEL_NAME = "";

    /* ------------------------------ 固定接口约定 ------------------------------ */

    public static final String WEATHER_API_KEY_HEADER = "X-QW-Api-Key";

    public static final String OPEN_METEO_FORECAST_URL =
            "https://api.open-meteo.com/v1/forecast";
    public static final String OPEN_METEO_CURRENT_URL =
            "https://api.open-meteo.com/v1/current";
    public static final String OPEN_METEO_GEOCODING_URL =
            "https://geocoding-api.open-meteo.com/v1/search";

    /**
     * 注意：和风天气接口要求纬度在前、经度在后。
     */
    public static final String WEATHER_CURRENT_PATH = "weather/v1/current/{lat}/{lon}";
    public static final String WEATHER_DAILY_PATH = "weather/v1/daily/{lat}/{lon}";

    /**
     * 逆地理编码接口规定 location 参数为“经度,纬度”。
     */
    public static final String WEATHER_GEO_CITY_PATH = "geo/v2/city/lookup";

    public static final String LLM_CHAT_PATH = "chat/completions";

    /* ------------------------------ SharedPreferences ------------------------------ */

    private static final String PREFS_NAME = "api_config";
    private static final String KEY_WEATHER_HOST = "weather_host";
    private static final String KEY_WEATHER_API_KEY = "weather_api_key";
    private static final String KEY_LLM_BASE_URL = "llm_base_url";
    private static final String KEY_LLM_API_KEY = "llm_api_key";
    private static final String KEY_LLM_MODEL_NAME = "llm_model_name";

    public static String getWeatherBaseUrl(Context context) {
        String value = preferences(context).getString(
                KEY_WEATHER_HOST, WEATHER_BASE_URL);
        return normalizeBaseUrl(value);
    }

    public static String getWeatherApiKey(Context context) {
        String value = preferences(context).getString(
                KEY_WEATHER_API_KEY, WEATHER_API_KEY);
        return sanitizeValue(value);
    }

    public static String getLlmBaseUrl(Context context) {
        String value = preferences(context).getString(
                KEY_LLM_BASE_URL, LLM_BASE_URL);
        return normalizeBaseUrl(value);
    }

    public static String getLlmApiKey(Context context) {
        String value = preferences(context).getString(
                KEY_LLM_API_KEY, LLM_API_KEY);
        return sanitizeValue(value);
    }

    public static String getLlmModelName(Context context) {
        String value = preferences(context).getString(
                KEY_LLM_MODEL_NAME, LLM_MODEL_NAME);
        // 模型 ID 不允许包含空格。用户从网页复制时可能带入全角空格、
        // 不换行空格或零宽字符，这里统一清掉，避免接口返回 400。
        return sanitizeValue(value).replace(" ", "");
    }

    /**
     * 和风天气 Host 与 Key 都填写后，天气路由才优先使用和风天气。
     * 未配置不再阻止天气请求，而是自动使用 Open-Meteo 免费源。
     */
    public static boolean isWeatherConfigured(Context context) {
        return !getWeatherBaseUrl(context).isEmpty()
                && !getWeatherApiKey(context).isEmpty();
    }

    /**
     * 大模型三个字段都填写后才算配置完成。
     */
    public static boolean isLlmConfigured(Context context) {
        return !getLlmBaseUrl(context).isEmpty()
                && !getLlmApiKey(context).isEmpty()
                && !getLlmModelName(context).isEmpty();
    }

    /**
     * 将用户填写的 Base URL 转换为完整的 chat/completions 地址。
     *
     * <p>兼容以下输入：
     * https://api.deepseek.com/
     * https://api.deepseek.com
     * https://api.deepseek.com/chat/completions
     * </p>
     */
    public static String getLlmChatUrl(Context context) {
        String base = getLlmBaseUrl(context);
        if (base.isEmpty()) {
            return "";
        }

        String normalized = base;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String lower = normalized.toLowerCase(Locale.US);
        if (lower.endsWith(LLM_CHAT_PATH)) {
            return normalized;
        }
        return normalized + "/" + LLM_CHAT_PATH;
    }

    /**
     * 保存五项 API 配置。空字符串表示该项未配置。
     */
    public static void saveApiConfig(Context context,
                                     String weatherHost,
                                     String weatherKey,
                                     String llmBaseUrl,
                                     String llmApiKey,
                                     String llmModelName) {
        preferences(context)
                .edit()
                .putString(KEY_WEATHER_HOST,
                        normalizeBaseUrl(sanitizeValue(weatherHost)))
                .putString(KEY_WEATHER_API_KEY, sanitizeValue(weatherKey))
                .putString(KEY_LLM_BASE_URL,
                        normalizeBaseUrl(sanitizeValue(llmBaseUrl)))
                .putString(KEY_LLM_API_KEY, sanitizeValue(llmApiKey))
                .putString(KEY_LLM_MODEL_NAME, sanitizeValue(llmModelName))
                .apply();
    }

    /**
     * 清空本机保存的全部 API 配置。
     */
    public static void clearApiConfig(Context context) {
        preferences(context).edit().clear().apply();
    }

    /**
     * Retrofit 的 BaseUrl 必须以“/”结尾，否则相对路径可能拼接错误。
     * 空配置保持为空，避免把示例地址误当成真实配置。
     */
    public static String normalizeBaseUrl(String baseUrl) {
        String value = sanitizeValue(baseUrl);
        if (value.isEmpty()) {
            return "";
        }
        return value.endsWith("/") ? value : value + "/";
    }

    /**
     * 清理复制粘贴带来的不可见字符。
     *
     * <p>中文网页和输入法常带入全角空格 U+3000、不换行空格 U+00A0、
     * 零宽空格 U+200B 以及 BOM U+FEFF。{@link String#trim()} 只处理
     * U+0020 及以下字符，这些字符会原样留在模型名里，导致大模型接口
     * 以“模型不存在”返回 400。</p>
     */
    public static String sanitizeValue(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\u200B'
                    || character == '\u200C'
                    || character == '\u200D'
                    || character == '\uFEFF') {
                continue;
            }
            if (Character.isWhitespace(character)
                    || Character.isSpaceChar(character)) {
                builder.append(' ');
            } else {
                builder.append(character);
            }
        }
        return builder.toString().trim();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
