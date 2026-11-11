package com.example.wardrobe.util;

import com.example.wardrobe.model.ClothingRecognition;
import com.google.gson.Gson;

import java.util.Locale;

/**
 * 解析视觉模型返回的衣物 JSON。
 *
 * <p>不同模型可能额外输出 Markdown 代码块或前后说明，这里先截取
 * 第一个 { 到最后一个 }，再交给 Gson 解析，避免严格解析失败。</p>
 */
public final class ClothingRecognitionParser {

    private ClothingRecognitionParser() {
        // 工具类禁止实例化。
    }

    public static ClothingRecognition parse(String rawContent) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            return null;
        }
        int start = rawContent.indexOf('{');
        int end = rawContent.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        String json = rawContent.substring(start, end + 1);
        ClothingRecognition recognition;
        try {
            recognition = new Gson()
                    .fromJson(json, ClothingRecognition.class);
        } catch (RuntimeException exception) {
            return null;
        }
        if (recognition == null) {
            return null;
        }
        recognition.setName(clean(recognition.getName()));
        recognition.setColor(clean(recognition.getColor()));
        recognition.setCategory(normalizeCategory(recognition.getCategory()));
        return recognition;
    }

    /**
     * 把模型可能返回的长类别名收敛到应用固定的六个分类。
     */
    static String normalizeCategory(String category) {
        String value = clean(category);
        if (value.isEmpty()) {
            return "";
        }
        if (isOneOf(value, "上装", "下装", "外套", "裙装", "鞋履", "配饰")) {
            return value;
        }
        if (value.contains("裙")) {
            return "裙装";
        }
        if (value.contains("鞋") || value.contains("靴")) {
            return "鞋履";
        }
        if (value.contains("裤") || value.contains("下装")) {
            return "下装";
        }
        if (value.contains("外套") || value.contains("夹克")
                || value.contains("风衣") || value.contains("大衣")
                || value.contains("羽绒") || value.contains("开衫")) {
            return "外套";
        }
        if (value.contains("包") || value.contains("帽")
                || value.contains("围巾") || value.contains("腰带")
                || value.contains("配饰") || value.contains("饰品")) {
            return "配饰";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("衬衫") || lower.contains("t恤")
                || lower.contains("毛衣") || lower.contains("针织")
                || lower.contains("卫衣") || lower.contains("背心")
                || lower.contains("上衣") || lower.contains("上装")) {
            return "上装";
        }
        return "";
    }

    private static boolean isOneOf(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
