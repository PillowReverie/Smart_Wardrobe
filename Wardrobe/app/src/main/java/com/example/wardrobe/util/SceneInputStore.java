package com.example.wardrobe.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 按“日常穿搭 / 异地游玩”两组保存输入框内容。
 *
 * <p>灵感焕新和经典套装共用一份输入；异地游玩单独一份。
 * 退出后重新进入时恢复对应分组的输入，便于重新读取当天缓存。</p>
 */
public final class SceneInputStore {

    private static final String PREFS_NAME = "scene_inputs";
    private static final String KEY_NORMAL = "normal";
    private static final String KEY_TRAVEL = "travel";

    private SceneInputStore() {
    }

    public static String get(Context context, String mode) {
        String value = preferences(context).getString(
                key(mode), "");
        if (!RecommendationMode.isTravel(mode)
                && AiOutfitCache.DEFAULT_SCENE.equals(
                value == null ? "" : value.trim())) {
            return "";
        }
        return value == null ? "" : value;
    }

    public static void save(Context context, String mode, String input) {
        String value = input == null ? "" : input.trim();
        if (!RecommendationMode.isTravel(mode)
                && AiOutfitCache.DEFAULT_SCENE.equals(value)) {
            value = "";
        }
        preferences(context).edit()
                .putString(key(mode), value)
                .apply();
    }

    private static String key(String mode) {
        return RecommendationMode.isTravel(mode) ? KEY_TRAVEL : KEY_NORMAL;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
