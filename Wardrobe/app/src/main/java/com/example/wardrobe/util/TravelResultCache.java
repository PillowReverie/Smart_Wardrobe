package com.example.wardrobe.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 最近一次异地游玩结果的兜底缓存。
 *
 * <p>按日期和性别保存，重启 App 后即使场景输入尚未恢复，也能直接展示
 * 当天最后一次旅行清单；跨天后自动失效，由页面重新生成。</p>
 */
public final class TravelResultCache {

    private static final String PREFS = "travel_last_result";
    private static final String KEY_DATE = "date";
    private static final String KEY_SCENE = "scene";
    private static final String KEY_GENDER = "gender";
    private static final String KEY_CONTENT = "content";

    public static final class Result {
        private final String scene;
        private final String content;

        Result(String scene, String content) {
            this.scene = scene;
            this.content = content;
        }

        public String getScene() {
            return scene;
        }

        public String getContent() {
            return content;
        }
    }

    private TravelResultCache() {
    }

    public static void save(Context context,
                            String scene,
                            String gender,
                            String content) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        preferences(context).edit()
                .putString(KEY_DATE, AiOutfitCache.today())
                .putString(KEY_SCENE, AiOutfitCache.normalizeScene(scene))
                .putString(KEY_GENDER,
                        StyleProfileManager.normalizeGender(gender))
                .putString(KEY_CONTENT, content)
                .apply();
    }

    public static Result loadToday(Context context, String gender) {
        SharedPreferences preferences = preferences(context);
        String date = preferences.getString(KEY_DATE, "");
        String scene = preferences.getString(KEY_SCENE, "");
        String savedGender = preferences.getString(KEY_GENDER, "");
        String content = preferences.getString(KEY_CONTENT, "");
        boolean valid = AiOutfitCache.today().equals(date)
                && !scene.trim().isEmpty()
                && !content.trim().isEmpty()
                && StyleProfileManager.normalizeGender(gender)
                .equals(savedGender);
        if (!valid) {
            preferences.edit().clear().apply();
            return null;
        }
        return new Result(scene, content);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
