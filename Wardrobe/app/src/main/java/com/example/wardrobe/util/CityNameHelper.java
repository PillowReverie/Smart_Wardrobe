package com.example.wardrobe.util;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.Locale;

/**
 * 中文城市名到 Open-Meteo 查询词的转换。
 *
 * <p>pinyin4j 对多音字使用“先出现的读音”，因此北京、重庆、厦门、西安等
 * 城市需要在自动转换前使用城市读音修正。其他中文地名走通用转换；结果
 * 去掉空格和声调，例如“咸阳市” -> “Xianyang”。</p>
 */
public final class CityNameHelper {

    private static final String[] CITY_SUFFIXES = {
            "特别行政区", "自治区", "自治州", "地区", "城市", "省", "市", "县", "区"
    };

    private static final HanyuPinyinOutputFormat PINYIN_FORMAT =
            new HanyuPinyinOutputFormat();

    static {
        PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        PINYIN_FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    private CityNameHelper() {
        // 纯工具类。
    }

    /**
     * 纯中文输入转成连续拼音；英文或拼音输入保持原样。
     */
    public static String convertCityNameToQuery(String input) {
        if (input == null) {
            return "";
        }
        String value = input.trim();
        if (value.isEmpty() || !containsChinese(value)) {
            return value;
        }

        String city = stripCitySuffix(value);
        String corrected = correctPolyphonicCity(city);
        if (corrected != null) {
            return corrected;
        }

        StringBuilder result = new StringBuilder();
        for (int index = 0; index < city.length(); index++) {
            char character = city.charAt(index);
            if (isChinese(character)) {
                try {
                    String[] readings = PinyinHelper.toHanyuPinyinStringArray(
                            character, PINYIN_FORMAT);
                    if (readings == null || readings.length == 0) {
                        return value;
                    }
                    result.append(readings[0]);
                } catch (BadHanyuPinyinOutputFormatCombination exception) {
                    return value;
                }
            } else if (Character.isLetterOrDigit(character)
                    || character == '\'' || character == '-') {
                result.append(character);
            }
        }
        String converted = result.toString().trim();
        return converted.isEmpty() ? value : capitalize(converted);
    }

    public static boolean containsChinese(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (isChinese(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 多音字城市的完整读音修正。返回 null 表示交给 pinyin4j 自动转换。
     */
    private static String correctPolyphonicCity(String city) {
        switch (city) {
            case "北京":
                return "Beijing";
            case "重庆":
                return "Chongqing";
            case "厦门":
                return "Xiamen";
            case "西安":
                return "Xian";
            case "长沙":
                return "Changsha";
            case "长春":
                return "Changchun";
            case "成都":
                return "Chengdu";
            case "广州":
                return "Guangzhou";
            case "上海":
                return "Shanghai";
            case "深圳":
                return "Shenzhen";
            case "南京":
                return "Nanjing";
            case "杭州":
                return "Hangzhou";
            case "武汉":
                return "Wuhan";
            case "天津":
                return "Tianjin";
            case "苏州":
                return "Suzhou";
            case "青岛":
                return "Qingdao";
            case "郑州":
                return "Zhengzhou";
            case "济南":
                return "Jinan";
            case "昆明":
                return "Kunming";
            case "大连":
                return "Dalian";
            case "沈阳":
                return "Shenyang";
            case "哈尔滨":
                return "Harbin";
            case "福州":
                return "Fuzhou";
            case "合肥":
                return "Hefei";
            case "南昌":
                return "Nanchang";
            case "南宁":
                return "Nanning";
            case "贵阳":
                return "Guiyang";
            case "太原":
                return "Taiyuan";
            case "石家庄":
                return "Shijiazhuang";
            case "兰州":
                return "Lanzhou";
            case "银川":
                return "Yinchuan";
            case "西宁":
                return "Xining";
            case "乌鲁木齐":
                return "Urumqi";
            case "拉萨":
                return "Lhasa";
            case "呼和浩特":
                return "Hohhot";
            case "洛阳":
                return "Luoyang";
            case "咸阳":
                return "Xianyang";
            case "桂林":
                return "Guilin";
            case "三亚":
                return "Sanya";
            case "丽江":
                return "Lijiang";
            case "张家界":
                return "Zhangjiajie";
            case "隆德":
                return "Longde";
            case "昌平":
                return "Changping";
            default:
                return null;
        }
    }

    private static String stripCitySuffix(String value) {
        String city = value;
        boolean removed;
        do {
            removed = false;
            for (String suffix : CITY_SUFFIXES) {
                if (city.length() > suffix.length()
                        && city.endsWith(suffix)) {
                    city = city.substring(0, city.length() - suffix.length());
                    removed = true;
                    break;
                }
            }
        } while (removed);
        return city;
    }

    private static boolean isChinese(char value) {
        return value >= '\u4E00' && value <= '\u9FFF';
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0))
                + value.substring(1).toLowerCase(Locale.ROOT);
    }
}
