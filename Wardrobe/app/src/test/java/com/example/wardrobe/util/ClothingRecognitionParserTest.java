package com.example.wardrobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.example.wardrobe.model.ClothingRecognition;

import org.junit.Test;

/**
 * 验证视觉模型返回值的容错解析和类别归一化。
 */
public class ClothingRecognitionParserTest {

    @Test
    public void parsesPlainJson() {
        ClothingRecognition result = ClothingRecognitionParser.parse(
                "{\"name\":\"米白色高领毛衣\",\"category\":\"上装\","
                        + "\"color\":\"米白色\"}");

        assertNotNull(result);
        assertEquals("米白色高领毛衣", result.getName());
        assertEquals("上装", result.getCategory());
        assertEquals("米白色", result.getColor());
    }

    @Test
    public void parsesJsonInsideMarkdownFence() {
        ClothingRecognition result = ClothingRecognitionParser.parse(
                "识别结果如下：\n```json\n"
                        + "{\"name\":\"黑色半身裙\",\"category\":\"裙装\","
                        + "\"color\":\"黑色\"}\n```\n希望有帮助。");

        assertNotNull(result);
        assertEquals("黑色半身裙", result.getName());
        assertEquals("裙装", result.getCategory());
    }

    @Test
    public void normalizesFreeFormCategory() {
        assertEquals("鞋履",
                ClothingRecognitionParser.normalizeCategory("高跟鞋"));
        assertEquals("外套",
                ClothingRecognitionParser.normalizeCategory("针织开衫"));
        assertEquals("下装",
                ClothingRecognitionParser.normalizeCategory("牛仔裤"));
        assertEquals("", ClothingRecognitionParser.normalizeCategory("未知分类"));
    }

    @Test
    public void rejectsMissingJson() {
        assertNull(ClothingRecognitionParser.parse("没有返回 JSON"));
        assertNull(ClothingRecognitionParser.parse(""));
        assertNull(ClothingRecognitionParser.parse(null));
    }
}
