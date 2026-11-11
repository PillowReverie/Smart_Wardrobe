package com.example.wardrobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 验证模型思考标签、自我推演和 Markdown 加粗不会进入最终展示文本。
 */
public class AiTextFormatterTest {

    @Test
    public void removesThoughtBlockAndMarkdownBold() {
        String raw = "<thought>我需要确保用户看不到这段。</thought>\n"
                + "## 搭配方案\n"
                + "上装：**白色衬衫**\n"
                + "推荐理由：干净利落。";

        String cleaned = AiTextFormatter.clean(raw);

        assertFalse(cleaned.contains("我需要确保"));
        assertFalse(cleaned.contains("**"));
        assertFalse(cleaned.contains("##"));
        assertTrue(cleaned.contains("上装：白色衬衫"));
        assertTrue(cleaned.contains("推荐理由：干净利落"));
    }

    @Test
    public void removesSelfReasoningSentence() {
        String raw = "上装：【白色衬衫】。需要确保不提及清单外衣物。"
                + "推荐理由：适合今天的温度。";

        String cleaned = AiTextFormatter.clean(raw);

        assertFalse(cleaned.contains("需要确保"));
        assertTrue(cleaned.contains("推荐理由：适合今天的温度"));
    }

    @Test
    public void nullBecomesEmptyText() {
        assertEquals("", AiTextFormatter.clean(null));
    }
}
