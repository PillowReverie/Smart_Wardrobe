package com.example.wardrobe.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.wardrobe.R;

/**
 * 穿搭偏好管理器。
 *
 * <p>性别偏好只影响 AI 对版型、剪裁、配色和风格措辞的判断，不会改变衣橱
 * 数据，也不允许模型借此编造清单外衣物。偏好保存在 SharedPreferences 中，
 * 首次启动由首页引导选择，之后可以在设置页修改。</p>
 */
public final class StyleProfileManager {

    public static final String GENDER_FEMALE = "female";
    public static final String GENDER_MALE = "male";
    public static final String GENDER_NEUTRAL = "neutral";

    private static final String PREFS_NAME = "style_profile";
    private static final String KEY_GENDER = "gender";

    private StyleProfileManager() {
        // 工具类禁止实例化。
    }

    /**
     * 是否已经完成首次性别偏好选择。
     */
    public static boolean hasGender(Context context) {
        String value = preferences(context).getString(KEY_GENDER, "");
        return GENDER_FEMALE.equals(value)
                || GENDER_MALE.equals(value)
                || GENDER_NEUTRAL.equals(value);
    }

    /**
     * 读取性别偏好；未设置或值异常时回退为“不限”，保证页面仍可运行。
     */
    public static String getGender(Context context) {
        return normalizeGender(
                preferences(context).getString(KEY_GENDER, GENDER_NEUTRAL));
    }

    public static void saveGender(Context context, String gender) {
        preferences(context).edit()
                .putString(KEY_GENDER, normalizeGender(gender))
                .apply();
    }

    /**
     * 把 SharedPreferences 中的稳定值转换为界面显示文案。
     */
    public static int getLabelResId(String gender) {
        String normalized = normalizeGender(gender);
        if (GENDER_FEMALE.equals(normalized)) {
            return R.string.gender_female;
        }
        if (GENDER_MALE.equals(normalized)) {
            return R.string.gender_male;
        }
        return R.string.gender_neutral;
    }

    /**
     * 生成 System Prompt 的性别偏好片段。
     *
     * <p>这里特别强调“只调整风格、不改变衣物清单”，避免模型为了迎合性别
     * 标签而推荐衣橱中不存在的衣服。</p>
     */
    public static String getPromptInstruction(String gender) {
        String normalized = normalizeGender(gender);
        if (GENDER_FEMALE.equals(normalized)) {
            return "用户穿搭性别偏好为女性。"
                    + "请优先按照女性常见版型、剪裁、配色和风格进行搭配，"
                    + "避免推荐明显偏男装版型的组合；"
                    + "但仍然只能使用衣物清单中的单品，"
                    + "不得为了体现女性风格而编造裙装、鞋履或配饰。";
        }
        if (GENDER_MALE.equals(normalized)) {
            return "用户穿搭性别偏好为男性。"
                    + "请优先按照男性常见版型、剪裁、配色和风格进行搭配，"
                    + "避免推荐明显偏女装版型的组合；"
                    + "但仍然只能使用衣物清单中的单品，"
                    + "不得为了体现男性风格而编造衬衫、外套或鞋履。";
        }
        return "用户选择不限性别风格。"
                + "请优先根据衣物本身的版型、颜色、材质和场景推荐中性、"
                + "简洁且得体的组合，不要强行套用性别刻板印象。";
    }

    /**
     * 把外部传入的值收敛到三个合法选项，防止缓存键或 Prompt 被脏数据污染。
     */
    public static String normalizeGender(String gender) {
        if (GENDER_FEMALE.equals(gender)) {
            return GENDER_FEMALE;
        }
        if (GENDER_MALE.equals(gender)) {
            return GENDER_MALE;
        }
        return GENDER_NEUTRAL;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
