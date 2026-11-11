package com.example.wardrobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.Outfit;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 验证三套方案拆分、推荐理由提取和【】衣物的包含匹配。
 */
public class AiOutfitParserTest {

    @Test
    public void parsesThreePlansAndMatchesImagesFuzzily() {
        ClothingItem shirt = item(1, "白色衬衫");
        ClothingItem trousers = item(2, "黑色直筒裤");
        ClothingItem heels = item(3, "高跟鞋");

        String content = "1. 搭配方案：通勤简约\n"
                + "上装：【白色 衬衫】\n"
                + "下装：【黑色直筒裤】\n"
                + "鞋履：【高跟鞋】\n"
                + "推荐理由：清爽利落，适合早晚温差。\n"
                + "2. 搭配方案：轻复古\n"
                + "上装：【白色衬衫】\n"
                + "推荐理由：用基础款突出质感。\n"
                + "3. 搭配方案：周末休闲\n"
                + "上装：【不存在的单品】\n"
                + "推荐理由：缺少图片时使用占位图。\n";

        List<AiOutfitPlan> plans = AiOutfitParser.parse(
                content, Arrays.asList(shirt, trousers, heels));

        assertEquals(3, plans.size());
        assertEquals("通勤简约", plans.get(0).getTitle());
        assertEquals(3, plans.get(0).getVisualItems().size());
        assertTrue(plans.get(0).getReason().contains("清爽利落"));
        assertNotNull(plans.get(0).getVisualItems().get(0).getClothingItem());
        assertNotNull(plans.get(0).getVisualItems().get(2).getClothingItem());

        // 未匹配名称必须保留占位项，由适配器显示默认图片。
        assertFalse(plans.get(2).getVisualItems().get(0).isMatched());
    }

    @Test
    public void missingBracketsStillCreatesPlaceholderPlan() {
        List<AiOutfitPlan> plans = AiOutfitParser.parse(
                "1. 搭配方案：没有标记\n推荐理由：模型忘了写方括号。",
                Arrays.asList(item(1, "白色衬衫")));

        assertEquals(1, plans.size());
        assertEquals(1, plans.get(0).getVisualItems().size());
        assertNull(plans.get(0).getVisualItems().get(0).getClothingItem());
    }

    @Test
    public void clothingDescriptionIsNotUsedAsStyleTitle() {
        List<AiOutfitPlan> plans = AiOutfitParser.parse(
                "1. 搭配方案：上装：【白色衬衫】。下装：清单中暂无。"
                        + "推荐理由：适合通勤。",
                Arrays.asList(item(1, "白色衬衫")));

        assertEquals("", plans.get(0).getTitle());
    }

    @Test
    public void classicMarkerIsParsedAndReservedLabelIsNotAClothing() {
        ClothingItem shirt = item(1, "白色衬衫");
        ClothingItem trousers = item(2, "黑色直筒裤");
        Outfit classic = new Outfit();
        classic.setTitle("通勤三件套");
        classic.setLinkedItems(Arrays.asList(shirt, trousers));

        String content = "1. 搭配方案：【经典套装推荐】通勤简约\n"
                + "经典来源：通勤三件套\n"
                + "上装：【白色衬衫】\n"
                + "下装：【黑色直筒裤】\n"
                + "推荐理由：经典套装适合今天的温度。\n"
                + "2. 搭配方案：【全新灵感搭配】周末休闲\n"
                + "上装：【白色衬衫】\n"
                + "推荐理由：换一种轻松的组合。\n";

        List<AiOutfitPlan> plans = AiOutfitParser.parse(
                content,
                Arrays.asList(shirt, trousers),
                Collections.singletonList(classic));

        assertEquals(2, plans.size());
        assertTrue(plans.get(0).isClassicRecommendation());
        assertEquals("通勤三件套", plans.get(0).getClassicOutfitTitle());
        assertEquals("通勤简约", plans.get(0).getTitle());
        assertEquals(2, plans.get(0).getVisualItems().size());
        assertFalse(plans.get(1).isClassicRecommendation());
        assertEquals(AiOutfitPlan.TYPE_INSPIRATION,
                plans.get(1).getRecommendationType());
    }

    @Test
    public void parsesPlanNumberAfterLabelAndTruncatesClassicSource() {
        ClothingItem shirt = item(1, "白色衬衫");
        ClothingItem trousers = item(2, "黑色直筒裤");
        ClothingItem shoes = item(3, "黑色乐福鞋");
        Outfit classic = new Outfit();
        classic.setTitle("通勤三件套");
        classic.setLinkedItems(Arrays.asList(shirt, trousers, shoes));

        String content = "搭配方案：1. 【经典套装推荐】经典来源：通勤三件套，"
                + "【白色衬衫】【黑色直筒裤】【黑色乐福鞋】。\n"
                + "推荐理由：这套适合今天的温度。\n\n"
                + "搭配方案：2. 【全新灵感搭配】【白色衬衫】【黑色直筒裤】。\n"
                + "推荐理由：换一种轻松的组合。\n";

        List<AiOutfitPlan> plans = AiOutfitParser.parse(
                content,
                Arrays.asList(shirt, trousers, shoes),
                Collections.singletonList(classic));

        assertEquals(2, plans.size());
        assertTrue(plans.get(0).isClassicRecommendation());
        assertEquals("通勤三件套", plans.get(0).getClassicOutfitTitle());
        assertEquals(3, plans.get(0).getVisualItems().size());
        assertFalse(plans.get(1).isClassicRecommendation());
    }

    @Test
    public void noSuitableOutfitTextHasNoPlaceholderImage() {
        List<AiOutfitPlan> plans = AiOutfitParser.parse(
                "当前衣橱暂无适合该天气的搭配",
                Collections.singletonList(item(1, "白色衬衫")));

        assertEquals(1, plans.size());
        assertTrue(plans.get(0).getReason().contains("当前衣橱暂无适合该天气的搭配"));
        assertTrue(plans.get(0).getVisualItems().isEmpty());
    }

    @Test
    public void fewerThanThreePlansAreDisplayedNormally() {
        List<AiOutfitPlan> plans = AiOutfitParser.parse(
                "1. 搭配方案：通勤\n上装：【白色衬衫】\n推荐理由：简单利落。",
                Collections.singletonList(item(1, "白色衬衫")));

        assertEquals(1, plans.size());
        assertEquals("通勤", plans.get(0).getTitle());
    }

    private ClothingItem item(long id, String name) {
        ClothingItem item = new ClothingItem();
        item.setId(id);
        item.setName(name);
        item.setCategory("上装");
        item.setImagePath("test-" + id + ".jpg");
        return item;
    }
}
