package com.example.wardrobe.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.Outfit;
import com.example.wardrobe.model.WeatherInfo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * 验证最终版 Prompt 的核心约束，避免后续改文案时意外退回单套方案，
 * 或重新把本地温度区间传给大模型。
 */
public class PromptBuilderTest {

    @Test
    public void todayPromptRequestsThreeNumberedLooks() {
        ClothingItem shirt = item("白色衬衫", "上装", "米白");
        ClothingItem trousers = item("黑色直筒裤", "下装", "黑色");
        WeatherInfo weather = new WeatherInfo(14, 25, "晴", 8, "3-4", 0L);

        String prompt = PromptBuilder.buildTodayOutfitPrompt(
                weather, Arrays.asList(shirt, trousers), "日常通勤");

        assertTrue(prompt.contains("3 套不同风格"));
        assertTrue(prompt.contains("1.、2.、3."));
        assertTrue(prompt.contains("白色衬衫"));
        assertTrue(prompt.contains("黑色直筒裤"));
        assertTrue(prompt.contains("14℃~25℃"));
        assertTrue(prompt.contains("早晚温差较大"));
        assertTrue(prompt.contains("今天阳光较强"));
        assertTrue(prompt.contains("我今天的主要活动场景是：【日常通勤】"));
        assertTrue(prompt.contains("请根据我的场景需求和天气"));
        assertTrue(prompt.contains("绝对禁止"));
        assertTrue(prompt.contains("清单外衣物"));
        assertTrue(prompt.contains("当前衣橱暂无适合该天气的搭配"));
        assertFalse(prompt.contains("请主动从衣橱中寻找是否有"));
        // 新版本应由 AI 判断季节适配，不能把旧版“适合温度”字段传给模型。
        assertFalse(prompt.contains("适合温度"));
        assertFalse(prompt.contains("40℃"));
    }

    @Test
    public void itemPromptKeepsCoreItemAndRequestsThreeLooks() {
        ClothingItem coat = item("卡其风衣", "外套", "卡其");
        ClothingItem shoes = item("白色运动鞋", "鞋履", "白色");
        WeatherInfo weather = new WeatherInfo(16, "多云", 0L);

        String prompt = PromptBuilder.buildItemOutfitPrompt(
                weather, coat, Collections.singletonList(shoes));

        assertTrue(prompt.contains("核心单品：卡其风衣"));
        assertTrue(prompt.contains("白色运动鞋"));
        assertTrue(prompt.contains("3 套不同风格"));
        assertTrue(prompt.contains("1.、2.、3."));
    }

    @Test
    public void rainyPromptAsksForUmbrellaAndLayering() {
        ClothingItem coat = item("防风外套", "外套", "黑色");
        WeatherInfo weather = new WeatherInfo(8, 18, "小雨", 1, "5-6", 0L);

        String prompt = PromptBuilder.buildTodayOutfitPrompt(
                weather, Collections.singletonList(coat), "日常通勤");

        assertTrue(prompt.contains("今天有雨"));
        assertTrue(prompt.contains("叠穿方案"));
        assertTrue(prompt.contains("早晚温差较大"));
    }

    @Test
    public void systemPromptRequiresThreeNumberedLooks() {
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("3 套不同方案"));
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("1.、2.、3."));
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("不要编造"));
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("禁止反常识搭配"));
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("禁止输出思考过程"));
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("Markdown 加粗"));
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("面试推荐正式稳重"));
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("约会推荐精致甜美"));
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("郊游推荐休闲舒适"));
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("不能覆盖衣物清单"));
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("核心上装或下装不同"));
        assertTrue(PromptBuilder.SYSTEM_PROMPT.contains("不能只更换配饰"));
    }

    @Test
    public void customSceneIsPassedToUserPrompt() {
        ClothingItem shirt = item("白色衬衫", "上装", "米白");
        WeatherInfo weather = new WeatherInfo(18, 26, "晴", 8, "2-3", 0L);

        String prompt = PromptBuilder.buildTodayOutfitPrompt(
                weather,
                Collections.singletonList(shirt),
                "周末约会");

        assertTrue(prompt.contains("【周末约会】"));
        assertTrue(prompt.contains("推荐 3 套合适的穿搭方案"));
    }

    @Test
    public void genderPreferenceIsPassedToTodayAndItemPrompts() {
        ClothingItem shirt = item("白色衬衫", "上装", "米白");
        WeatherInfo weather = new WeatherInfo(18, 26, "晴", 8, "2-3", 0L);

        String todayPrompt = PromptBuilder.buildTodayOutfitPrompt(
                weather,
                Collections.singletonList(shirt),
                Collections.emptyList(),
                "去面试",
                RecommendationMode.INSPIRATION,
                StyleProfileManager.GENDER_FEMALE);
        String todaySystem = PromptBuilder.buildTodaySystemPrompt(
                RecommendationMode.INSPIRATION,
                false,
                StyleProfileManager.GENDER_FEMALE);
        String itemPrompt = PromptBuilder.buildItemOutfitPrompt(
                weather,
                shirt,
                Collections.emptyList(),
                StyleProfileManager.GENDER_MALE);

        assertTrue(todayPrompt.contains("用户穿搭性别偏好：【女性】"));
        assertTrue(todaySystem.contains("女性常见版型"));
        assertTrue(itemPrompt.contains("用户穿搭性别偏好：【男性】"));
        assertTrue(PromptBuilder.buildItemSystemPrompt(
                StyleProfileManager.GENDER_MALE).contains("男性常见版型"));
    }

    @Test
    public void classicOutfitsAreIncludedInPrompt() {
        ClothingItem shirt = item("白色衬衫", "上装", "米白");
        ClothingItem trousers = item("黑色直筒裤", "下装", "黑色");
        WeatherInfo weather = new WeatherInfo(14, 25, "晴", 8, "3-4", 0L);
        Outfit classic = new Outfit();
        classic.setTitle("通勤三件套");
        classic.setWeatherText("晴");
        classic.setTemperature(22);
        classic.setLinkedItems(Arrays.asList(shirt, trousers));

        String prompt = PromptBuilder.buildTodayOutfitPrompt(
                weather,
                Arrays.asList(shirt, trousers),
                Collections.singletonList(classic),
                "日常通勤",
                RecommendationMode.CLASSIC);

        assertTrue(prompt.contains("经典套装"));
        assertTrue(prompt.contains("通勤三件套"));
        assertTrue(prompt.contains("白色衬衫"));
        assertTrue(prompt.contains("优先推荐"));
        assertTrue(prompt.contains("经典来源"));
    }

    @Test
    public void todaySystemPromptRequiresSourceLabels() {
        String classicSystem = PromptBuilder.buildTodaySystemPrompt(
                RecommendationMode.CLASSIC, true);
        String inspirationSystem = PromptBuilder.buildTodaySystemPrompt(
                RecommendationMode.INSPIRATION, true);

        assertTrue(classicSystem.contains("优先推荐"));
        assertTrue(classicSystem.contains("经典套装推荐"));
        assertTrue(classicSystem.contains("全新灵感搭配"));
        assertTrue(inspirationSystem.contains("不要直接复刻"));
    }

    @Test
    public void todayPromptAddsDiversityAndPreviousRecommendationContext() {
        ClothingItem shirt = item("白色衬衫", "上装", "米白");
        ClothingItem trousers = item("黑色直筒裤", "下装", "黑色");
        WeatherInfo weather = new WeatherInfo(14, 25, "晴", 8, "3-4", 0L);

        String prompt = PromptBuilder.buildTodayOutfitPrompt(
                weather,
                Arrays.asList(shirt, trousers),
                Collections.emptyList(),
                "日常通勤",
                RecommendationMode.INSPIRATION,
                StyleProfileManager.GENDER_FEMALE,
                Arrays.asList(shirt, trousers));

        assertTrue(prompt.contains("上一轮同场景推荐中已经重点使用过这些衣物"));
        assertTrue(prompt.contains("白色衬衫、黑色直筒裤"));
        assertTrue(prompt.contains("至少两套方案的核心上装或下装不同"));
        assertTrue(prompt.contains("因衣橱单品有限而复用"));
        assertTrue(prompt.contains("至少两套方案的主组合应与上一轮不同"));
    }

    @Test
    public void itemDescriptionPromptUsesVisibleMetadataAndUserNote() {
        ClothingItem coat = item("米白针织开衫", "上装", "米白");
        coat.setNote("柔软针织，适合春秋通勤");

        String system = PromptBuilder.buildItemDescriptionSystemPrompt();
        String prompt = PromptBuilder.buildItemDescriptionPrompt(coat);

        assertTrue(system.contains("80 至 160 字"));
        assertTrue(system.contains("不得编造品牌"));
        assertTrue(prompt.contains("衣物名称：米白针织开衫"));
        assertTrue(prompt.contains("衣物类别：上装"));
        assertTrue(prompt.contains("柔软针织，适合春秋通勤"));
        assertTrue(prompt.contains("仅用于 AI 理解与搭配的隐藏描述"));
    }

    @Test
    public void hiddenAiDescriptionIsInjectedIntoOutfitPrompt() {
        ClothingItem coat = item("米白针织开衫", "上装", "米白");
        coat.setAiDescription("细针织短款版型，视觉柔和，适合春秋叠穿。");
        WeatherInfo weather = new WeatherInfo(14, 25, "晴", 8, "3-4", 0L);

        String prompt = PromptBuilder.buildTodayOutfitPrompt(
                weather,
                Collections.singletonList(coat),
                "日常通勤");

        assertTrue(prompt.contains("AI辅助描述"));
        assertTrue(prompt.contains("细针织短款版型"));
        assertTrue(PromptBuilder.MAX_CONTEXT_CHARACTERS >= 1_000_000);
    }

    @Test
    public void travelPackingForbidsDailyOutfitAdvice() {
        ClothingItem jacket = item("米白冲锋衣", "外套", "米白");

        String system = PromptBuilder.buildTravelSystemPrompt();
        String prompt = PromptBuilder.buildTravelPrompt(
                "洛阳",
                "2026-11-10",
                3,
                "11月10日 晴 8~18℃",
                Collections.singletonList(jacket),
                StyleProfileManager.GENDER_NEUTRAL);

        assertTrue(system.contains("绝对不要输出每日穿搭建议"));
        assertTrue(prompt.contains("绝对不要输出每日穿搭建议"));
        assertTrue(prompt.contains("证件类"));
        assertTrue(prompt.contains("米白冲锋衣"));
    }

    @Test
    public void travelWardrobeOutfitUsesOnlyCarriedItems() {
        ClothingItem jacket = item("米白冲锋衣", "外套", "米白");
        WeatherInfo weather = new WeatherInfo(8, 18, "晴", 10, "2-3", 0L);

        String prompt = PromptBuilder.buildTravelWardrobeOutfitPrompt(
                weather,
                Collections.singletonList(jacket),
                StyleProfileManager.GENDER_FEMALE);
        String system = PromptBuilder.buildTravelOutfitSystemPrompt(
                StyleProfileManager.GENDER_FEMALE);

        assertTrue(prompt.contains("我目前旅行携带的衣物有"));
        assertTrue(prompt.contains("米白冲锋衣"));
        assertTrue(prompt.contains("用户穿搭性别偏好：【女性】"));
        assertTrue(system.contains("只包含用户当前旅行携带的衣物"));
    }

    private ClothingItem item(String name, String category, String color) {
        ClothingItem item = new ClothingItem();
        item.setName(name);
        item.setCategory(category);
        item.setColor(color);
        return item;
    }
}
