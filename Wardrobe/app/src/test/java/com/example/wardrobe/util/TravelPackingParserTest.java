package com.example.wardrobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.TravelPackingItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 验证旅行清单【】名称的模糊匹配、去重和占位兜底。
 */
public class TravelPackingParserTest {

    @Test
    public void matchesClothingNamesFuzzilyAndKeepsUnknownAsPlaceholder() {
        ClothingItem jacket = item(1, "米白冲锋衣", "外套");
        ClothingItem shoes = item(2, "防滑登山鞋", "鞋履");

        String content = "衣物类：【米白 冲锋衣】【防滑登山鞋】\n"
                + "鞋帽类：【白色登山鞋】\n"
                + "洗护类：牙刷、防晒霜\n"
                + "证件类：身份证";

        List<TravelPackingItem> result = TravelPackingParser.parse(
                content, Arrays.asList(jacket, shoes));

        assertEquals(3, result.size());
        assertTrue(result.get(0).isMatched());
        assertEquals(jacket.getId(),
                result.get(0).getClothingItem().getId());
        assertTrue(result.get(1).isMatched());
        assertEquals(shoes.getId(),
                result.get(1).getClothingItem().getId());
        assertFalse(result.get(2).isMatched());
    }

    @Test
    public void missingMarkersStillProducesOnePlaceholderCard() {
        List<TravelPackingItem> result = TravelPackingParser.parse(
                "模型忘记输出方括号", Arrays.asList(item(1, "衬衫", "上装")));

        assertEquals(1, result.size());
        assertFalse(result.get(0).isMatched());
    }

    @Test
    public void removesClothingSectionsButKeepsTravelEssentials() {
        String content = "完整打包清单\n"
                + "衣物类：\n"
                + "【黑色羽绒服】\n"
                + "【黑色毛衣】\n"
                + "鞋帽类：\n"
                + "【白色运动鞋】\n"
                + "清单中暂无合适帽子\n"
                + "洗护类：\n"
                + "牙刷、洗面奶、防晒霜\n"
                + "证件类：\n"
                + "身份证、充电器";

        String result = TravelPackingParser
                .extractNonClothingSections(content);

        assertFalse(result.contains("黑色羽绒服"));
        assertFalse(result.contains("黑色毛衣"));
        assertFalse(result.contains("白色运动鞋"));
        assertFalse(result.contains("完整打包清单"));
        assertFalse(result.contains("清单中暂无合适帽子"));
        assertTrue(result.contains("洗护类"));
        assertTrue(result.contains("牙刷"));
        assertTrue(result.contains("证件类"));
        assertTrue(result.contains("身份证"));
        assertTrue(result.contains("\n"));
        assertEquals(
                "洗护类：牙刷、洗面奶、防晒霜\n证件类：身份证、充电器",
                result);
    }

    private ClothingItem item(long id, String name, String category) {
        ClothingItem item = new ClothingItem();
        item.setId(id);
        item.setName(name);
        item.setCategory(category);
        return item;
    }
}
