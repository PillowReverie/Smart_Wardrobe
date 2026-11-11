package com.example.wardrobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 验证性别偏好只接受三个合法值，并生成对应的 Prompt 约束。
 */
public class StyleProfileManagerTest {

    @Test
    public void genderValuesAreNormalized() {
        assertEquals(StyleProfileManager.GENDER_FEMALE,
                StyleProfileManager.normalizeGender("female"));
        assertEquals(StyleProfileManager.GENDER_MALE,
                StyleProfileManager.normalizeGender("male"));
        assertEquals(StyleProfileManager.GENDER_NEUTRAL,
                StyleProfileManager.normalizeGender("unknown"));
        assertEquals(StyleProfileManager.GENDER_NEUTRAL,
                StyleProfileManager.normalizeGender(null));
    }

    @Test
    public void promptInstructionsKeepWardrobeAsSourceOfTruth() {
        String female = StyleProfileManager.getPromptInstruction(
                StyleProfileManager.GENDER_FEMALE);
        String male = StyleProfileManager.getPromptInstruction(
                StyleProfileManager.GENDER_MALE);
        String neutral = StyleProfileManager.getPromptInstruction(
                StyleProfileManager.GENDER_NEUTRAL);

        assertTrue(female.contains("女性"));
        assertTrue(female.contains("只能使用衣物清单"));
        assertTrue(male.contains("男性"));
        assertTrue(male.contains("只能使用衣物清单"));
        assertTrue(neutral.contains("不限性别风格"));
    }
}
