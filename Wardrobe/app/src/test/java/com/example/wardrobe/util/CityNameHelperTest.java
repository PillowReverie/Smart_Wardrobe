package com.example.wardrobe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 中文城市查询词转换测试。城市名必须连续、无声调，英文输入不得被修改。
 */
public class CityNameHelperTest {

    @Test
    public void convertsCommonChineseCities() {
        assertEquals("Xianyang", CityNameHelper.convertCityNameToQuery("咸阳"));
        assertEquals("Beijing", CityNameHelper.convertCityNameToQuery("北京市"));
        assertEquals("Luoyang", CityNameHelper.convertCityNameToQuery("洛阳"));
        assertEquals("Chongqing", CityNameHelper.convertCityNameToQuery("重庆"));
    }

    @Test
    public void usesPinyinForOrdinaryChineseName() {
        assertEquals("Kaifeng",
                CityNameHelper.convertCityNameToQuery("开封"));
        assertEquals("Zhengzhou",
                CityNameHelper.convertCityNameToQuery("郑州"));
    }

    @Test
    public void keepsEnglishAndPinyinInput() {
        assertEquals("Beijing",
                CityNameHelper.convertCityNameToQuery("Beijing"));
        assertEquals("Xi'an",
                CityNameHelper.convertCityNameToQuery("Xi'an"));
    }

    @Test
    public void detectsChineseCharacters() {
        assertTrue(CityNameHelper.containsChinese("咸阳"));
        assertTrue(!CityNameHelper.containsChinese("Beijing"));
    }
}
