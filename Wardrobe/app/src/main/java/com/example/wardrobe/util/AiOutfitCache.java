package com.example.wardrobe.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 首页今日 AI 穿搭的本地缓存。
 *
 * <p>缓存按“日期 + 推荐模式 + 场景 + 性别偏好”隔离。用户上午生成“去面试”的方案，
 * 下午输入“周末约会”时不会错误复用上午的结果；读取时只要发现日期不是今天，
 * 就立即删除旧缓存并返回 null，确保每天 0 点后一定重新生成。</p>
 */
public final class AiOutfitCache {

    private static final String PREFS_NAME = "ai_outfit_cache";
    private static final String KEY_CONTENT_PREFIX = "scene_content_";
    private static final String KEY_DATE_PREFIX = "scene_date_";
    // 旧版本只按推荐模式缓存，升级后仅在默认场景下兼容读取。
    private static final String LEGACY_MODE_CONTENT_PREFIX = "content_";
    private static final String LEGACY_MODE_DATE_PREFIX = "date_";
    // v1.1 及以前只缓存一份结果，读取灵感模式时兼容迁移。
    private static final String LEGACY_KEY_CONTENT = "content";
    private static final String LEGACY_KEY_DATE = "date";
    // 默认场景在缓存键中使用短名称，避免把较长中文写入键名。
    private static final String DEFAULT_SCENE_KEY = "默认";
    /** 场景为空时统一使用的默认场景，必须与 strings.xml 中的文案保持一致。 */
    public static final String DEFAULT_SCENE = "日常通勤";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private AiOutfitCache() {
        // 工具类禁止实例化。
    }

    public static String today() {
        return LocalDate.now().format(DATE_FORMATTER);
    }

    /**
     * 保留旧调用方式，默认读取“日常通勤”场景。
     */
    public static String loadTodayContent(Context context, String mode) {
        return loadTodayContent(
                context, mode, DEFAULT_SCENE, StyleProfileManager.GENDER_NEUTRAL);
    }

    /**
     * 读取指定模式和场景的当天缓存；跨天时自动清理。
     */
    public static String loadTodayContent(Context context,
                                          String mode,
                                          String scene) {
        return loadTodayContent(
                context, mode, scene, StyleProfileManager.GENDER_NEUTRAL);
    }

    /**
     * 读取指定模式、场景和性别偏好的当天缓存；跨天时自动清理。
     */
    public static String loadTodayContent(Context context,
                                          String mode,
                                          String scene,
                                          String gender) {
        String normalizedMode = RecommendationMode.normalize(mode);
        String normalizedScene = normalizeScene(scene);
        String normalizedGender = StyleProfileManager.normalizeGender(gender);
        SharedPreferences preferences = preferences(context);
        String cachedDate = preferences.getString(
                dateKey(normalizedMode, normalizedScene, normalizedGender), "");
        String content = preferences.getString(
                contentKey(normalizedMode, normalizedScene, normalizedGender), "");
        if (!isSameDate(cachedDate, today()) || content == null
                || content.trim().isEmpty()) {
            clear(context, normalizedMode, normalizedScene, normalizedGender);
            // 旧缓存没有性别维度，只能安全迁移到“默认场景 + 不限性别”。
            if (!DEFAULT_SCENE.equals(normalizedScene)
                    || !StyleProfileManager.GENDER_NEUTRAL.equals(
                    normalizedGender)) {
                return null;
            }
            String legacyModeContent = loadLegacyModeCache(
                    preferences, normalizedMode);
            return legacyModeContent != null
                    ? legacyModeContent
                    : loadLegacyInspirationCache(preferences, normalizedMode);
        }
        return content;
    }

    /**
     * 保留旧调用方式，默认保存到“日常通勤”场景。
     */
    public static void save(Context context, String mode, String content) {
        save(context, mode, DEFAULT_SCENE,
                StyleProfileManager.GENDER_NEUTRAL, content);
    }

    /**
     * 保存某个场景当天的生成结果。手动重新生成成功后调用本方法，
     * 会自然覆盖同日期、同模式和同场景的旧内容。
     */
    public static void save(Context context,
                            String mode,
                            String scene,
                            String content) {
        save(context, mode, scene,
                StyleProfileManager.GENDER_NEUTRAL, content);
    }

    /**
     * 保存指定性别偏好的场景结果。
     */
    public static void save(Context context,
                            String mode,
                            String scene,
                            String gender,
                            String content) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        String normalizedMode = RecommendationMode.normalize(mode);
        String normalizedScene = normalizeScene(scene);
        String normalizedGender = StyleProfileManager.normalizeGender(gender);
        preferences(context)
                .edit()
                .putString(contentKey(
                        normalizedMode, normalizedScene, normalizedGender),
                        content)
                .putString(dateKey(
                        normalizedMode, normalizedScene, normalizedGender),
                        today())
                .apply();
    }

    /**
     * 保留旧调用方式，默认清理“日常通勤”场景。
     */
    public static void clear(Context context, String mode) {
        clear(context, mode, DEFAULT_SCENE,
                StyleProfileManager.GENDER_NEUTRAL);
    }

    /**
     * 只清理指定模式和场景的缓存，不影响用户为其他场景生成的结果。
     */
    public static void clear(Context context, String mode, String scene) {
        clear(context, mode, scene, StyleProfileManager.GENDER_NEUTRAL);
    }

    /**
     * 清理指定模式、场景和性别偏好的缓存。
     */
    public static void clear(Context context,
                             String mode,
                             String scene,
                             String gender) {
        String normalizedMode = RecommendationMode.normalize(mode);
        String normalizedScene = normalizeScene(scene);
        String normalizedGender = StyleProfileManager.normalizeGender(gender);
        preferences(context)
                .edit()
                .remove(contentKey(
                        normalizedMode, normalizedScene, normalizedGender))
                .remove(dateKey(
                        normalizedMode, normalizedScene, normalizedGender))
                .apply();
    }

    /**
     * 数据迁移完成后清空全部 AI 结果缓存，避免旧方案引用导入前的衣物。
     */
    public static void clearAll(Context context) {
        preferences(context).edit().clear().apply();
    }

    /**
     * 规范化场景文本，保证“ 去面试 ”和“去面试”命中同一份缓存。
     */
    public static String normalizeScene(String scene) {
        if (scene == null) {
            return DEFAULT_SCENE;
        }
        String normalized = scene.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return DEFAULT_SCENE;
        }
        if (normalized.length() > 30) {
            normalized = normalized.substring(0, 30).trim();
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    /**
     * 读取上一版按模式保存的缓存，并迁移到默认场景的新键。
     */
    private static String loadLegacyModeCache(
            SharedPreferences preferences, String mode) {
        String legacyDate = preferences.getString(
                LEGACY_MODE_DATE_PREFIX + mode, "");
        String legacyContent = preferences.getString(
                LEGACY_MODE_CONTENT_PREFIX + mode, "");
        if (!isSameDate(legacyDate, today()) || legacyContent == null
                || legacyContent.trim().isEmpty()) {
            preferences.edit()
                    .remove(LEGACY_MODE_CONTENT_PREFIX + mode)
                    .remove(LEGACY_MODE_DATE_PREFIX + mode)
                    .apply();
            return null;
        }
        save(preferences, mode, DEFAULT_SCENE,
                StyleProfileManager.GENDER_NEUTRAL, legacyContent);
        preferences.edit()
                .remove(LEGACY_MODE_CONTENT_PREFIX + mode)
                .remove(LEGACY_MODE_DATE_PREFIX + mode)
                .apply();
        return legacyContent;
    }

    /**
     * 升级前只有一份缓存，且当时只有“灵感焕新”一种模式。
     * 若旧缓存仍是今天生成的，就迁移到灵感模式，避免升级当天内容消失。
     */
    private static String loadLegacyInspirationCache(
            SharedPreferences preferences, String mode) {
        if (!RecommendationMode.INSPIRATION.equals(mode)) {
            return null;
        }
        String legacyDate = preferences.getString(LEGACY_KEY_DATE, "");
        String legacyContent = preferences.getString(LEGACY_KEY_CONTENT, "");
        if (!isSameDate(legacyDate, today()) || legacyContent == null
                || legacyContent.trim().isEmpty()) {
            preferences.edit()
                    .remove(LEGACY_KEY_CONTENT)
                    .remove(LEGACY_KEY_DATE)
                    .apply();
            return null;
        }
        save(preferences, mode, DEFAULT_SCENE,
                StyleProfileManager.GENDER_NEUTRAL, legacyContent);
        preferences.edit()
                .remove(LEGACY_KEY_CONTENT)
                .remove(LEGACY_KEY_DATE)
                .apply();
        return legacyContent;
    }

    private static void save(SharedPreferences preferences,
                             String mode,
                             String scene,
                             String gender,
                             String content) {
        String normalizedGender = StyleProfileManager.normalizeGender(gender);
        preferences.edit()
                .putString(contentKey(
                        mode, normalizeScene(scene), normalizedGender), content)
                .putString(dateKey(
                        mode, normalizeScene(scene), normalizedGender), today())
                .apply();
    }

    /**
     * 日期比较单独抽成纯函数，便于单元测试覆盖跨天失效规则。
     */
    static boolean isSameDate(String cachedDate, String currentDate) {
        return cachedDate != null && cachedDate.equals(currentDate);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String sceneSuffix(String mode,
                                      String scene,
                                      String gender) {
        String normalizedScene = normalizeScene(scene);
        String sceneKey = DEFAULT_SCENE.equals(normalizedScene)
                ? DEFAULT_SCENE_KEY : normalizedScene;
        return mode + "_" + sceneKey + "_"
                + StyleProfileManager.normalizeGender(gender);
    }

    private static String contentKey(String mode,
                                     String scene,
                                     String gender) {
        return KEY_CONTENT_PREFIX + sceneSuffix(mode, scene, gender);
    }

    private static String dateKey(String mode,
                                  String scene,
                                  String gender) {
        return KEY_DATE_PREFIX + sceneSuffix(mode, scene, gender);
    }
}
