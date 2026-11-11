package com.example.wardrobe.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 验证 AI 缓存只在生成日期等于当天时有效，跨天后必须失效。
 */
public class AiOutfitCacheTest {

    @Test
    public void sameDateIsValid() {
        assertTrue(AiOutfitCache.isSameDate("2026-10-31", "2026-10-31"));
    }

    @Test
    public void previousDateIsExpired() {
        assertFalse(AiOutfitCache.isSameDate("2026-10-30", "2026-10-31"));
    }

    @Test
    public void blankSceneUsesDefaultScene() {
        assertEquals("日常通勤", AiOutfitCache.DEFAULT_SCENE);
        assertEquals(AiOutfitCache.DEFAULT_SCENE,
                AiOutfitCache.normalizeScene("   "));
        assertEquals(AiOutfitCache.DEFAULT_SCENE,
                AiOutfitCache.normalizeScene(null));
    }

    @Test
    public void sceneWhitespaceAndCaseAreNormalized() {
        assertEquals("weekend date",
                AiOutfitCache.normalizeScene("  Weekend   Date  "));
        assertEquals("去面试", AiOutfitCache.normalizeScene(" 去面试 "));
    }
}
