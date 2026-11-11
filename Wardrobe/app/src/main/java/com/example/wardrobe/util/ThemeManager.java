package com.example.wardrobe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * 深色模式偏好管理。
 *
 * <p>AppCompatDelegate 的夜间模式是进程级设置，必须在 Activity 创建前
 * 或用户切换时调用。这里把选择保存到 SharedPreferences，应用启动时由
 * WardrobeApplication 恢复，避免每次进入页面都重置。</p>
 */
public final class ThemeManager {

    public static final int MODE_FOLLOW_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;

    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_MODE = "night_mode";

    private ThemeManager() {
        // 工具类禁止实例化。
    }

    public static int getSavedMode(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_MODE, MODE_FOLLOW_SYSTEM);
    }

    public static void saveMode(Context context, int mode) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_MODE, mode)
                .apply();
    }

    /**
     * 立即应用夜间模式，AppCompat 会自动重建当前 Activity。
     */
    public static void applyMode(int mode) {
        AppCompatDelegate.setDefaultNightMode(toDelegateMode(mode));
    }

    /**
     * 应用启动时恢复用户上次选择。
     */
    public static void applySavedMode(Context context) {
        applyMode(getSavedMode(context));
    }

    private static int toDelegateMode(int mode) {
        if (mode == MODE_LIGHT) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (mode == MODE_DARK) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }
}
