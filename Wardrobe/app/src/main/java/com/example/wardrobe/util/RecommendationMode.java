package com.example.wardrobe.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 首页 AI 推荐模式。
 *
 * <p>“灵感焕新”从单品清单自由组合；“经典套装”优先复用搭配库中
 * 已保存的套装。模式保存在 SharedPreferences 中，重启 App 后仍然生效。</p>
 */
public final class RecommendationMode {

    public static final String INSPIRATION = "INSPIRATION";
    public static final String CLASSIC = "CLASSIC";
    public static final String TRAVEL = "TRAVEL";

    private static final String PREFS_NAME = "recommendation_mode";
    private static final String KEY_MODE = "mode";

    private RecommendationMode() {
        // 工具类禁止实例化。
    }

    public static String get(Context context) {
        String value = preferences(context).getString(KEY_MODE, INSPIRATION);
        return normalize(value);
    }

    public static void save(Context context, String mode) {
        preferences(context)
                .edit()
                .putString(KEY_MODE, normalize(mode))
                .apply();
    }

    public static boolean isClassic(String mode) {
        return CLASSIC.equals(mode);
    }

    public static boolean isTravel(String mode) {
        return TRAVEL.equals(mode);
    }

    public static String normalize(String mode) {
        if (CLASSIC.equals(mode)) {
            return CLASSIC;
        }
        return TRAVEL.equals(mode) ? TRAVEL : INSPIRATION;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
