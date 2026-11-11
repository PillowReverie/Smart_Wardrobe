package com.example.wardrobe.util;

import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.TravelPackingItem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 旅行打包清单解析器。
 *
 * <p>只把【】里的内容当作潜在衣物名称，并使用包含匹配关联 SQLite 衣物。
 * 类别标题和旅行用品不展示为衣物图片；如果模型完全漏写【】，
 * 仍生成一张占位卡，保证界面结构稳定。</p>
 */
public final class TravelPackingParser {

    private static final Pattern MARKER_PATTERN =
            Pattern.compile("【([^【】\\r\\n]{1,60})】");
    private static final Set<String> RESERVED_NAMES = Set.of(
            "衣物类", "鞋帽类", "洗护类", "证件类", "电子类",
            "电子设备", "其他", "旅行清单", "打包清单"
    );

    private TravelPackingParser() {
    }

    public static List<TravelPackingItem> parse(
            String content,
            List<ClothingItem> wardrobeItems) {
        LinkedHashSet<String> references = extractReferences(content);
        List<TravelPackingItem> result = new ArrayList<>();
        for (String reference : references) {
            if (RESERVED_NAMES.contains(reference)) {
                continue;
            }
            ClothingItem matched = AiOutfitParser.findBestMatchingItem(
                    reference, wardrobeItems);
            result.add(new TravelPackingItem(reference, matched));
        }
        if (result.isEmpty()) {
            result.add(new TravelPackingItem("未识别衣物", null));
        }
        return result;
    }

    /**
     * 从完整打包清单中移除“衣物类/鞋帽类”等已经由图片卡展示的内容，
     * 只保留洗护、证件、电子设备等无法通过衣物图片表达的条目。
     */
    public static String extractNonClothingSections(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        List<String> compactSections = new ArrayList<>();
        String currentSection = "";
        StringBuilder currentContent = new StringBuilder();
        boolean skipClothingSection = false;
        String[] lines = content.replace("\r\n", "\n")
                .replace('\r', '\n')
                .split("\n");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (isRedundantSummaryTitle(line)) {
                continue;
            }
            String section = detectSection(line);
            if (!section.isEmpty()) {
                appendCompactSection(
                        compactSections, currentSection, currentContent);
                currentSection = "";
                currentContent.setLength(0);
                skipClothingSection = isClothingSection(section);
                if (!skipClothingSection) {
                    currentSection = section;
                    currentContent.append(extractInlineContent(line));
                }
                continue;
            }
            if (skipClothingSection || isClothingMarkerLine(line)) {
                continue;
            }
            if (!currentSection.isEmpty() && !line.isEmpty()) {
                if (currentContent.length() > 0
                        && !endsWithSeparator(currentContent)) {
                    currentContent.append("、");
                }
                currentContent.append(line);
            }
        }
        appendCompactSection(
                compactSections, currentSection, currentContent);
        return String.join("\n", compactSections);
    }

    private static void appendCompactSection(List<String> sections,
                                             String title,
                                             StringBuilder content) {
        if (title == null || title.isEmpty()
                || content == null || content.length() == 0) {
            return;
        }
        String normalizedContent = content.toString()
                .replaceAll("\\s+", "")
                .replaceAll("^[、，,；;]+|[、，,；;]+$", "");
        if (!normalizedContent.isEmpty()) {
            sections.add(title + "：" + normalizedContent);
        }
    }

    private static boolean endsWithSeparator(StringBuilder content) {
        if (content.length() == 0) {
            return false;
        }
        char last = content.charAt(content.length() - 1);
        return last == '、' || last == '，' || last == ','
                || last == '；' || last == ';';
    }

    private static String extractInlineContent(String line) {
        int chineseColon = line.indexOf('：');
        int englishColon = line.indexOf(':');
        int colon = chineseColon >= 0 ? chineseColon : englishColon;
        if (colon < 0 || colon + 1 >= line.length()) {
            return "";
        }
        return line.substring(colon + 1).trim();
    }

    private static boolean isRedundantSummaryTitle(String line) {
        String compact = line.replaceAll("[\\s:：]", "");
        return "完整打包清单".equals(compact)
                || "旅行打包清单".equals(compact)
                || "分类打包清单".equals(compact);
    }

    private static String detectSection(String line) {
        String compact = line.replaceAll("[\\s:：]", "");
        if (compact.contains("衣物类") || compact.contains("服装类")
                || compact.contains("穿搭类")) {
            return "衣物类";
        }
        if (compact.contains("鞋帽类") || compact.contains("鞋包类")) {
            return "鞋帽类";
        }
        if (compact.contains("洗护类") || compact.contains("洗漱类")
                || compact.contains("证件类") || compact.contains("电子类")
                || compact.contains("电子产品") || compact.contains("其他类")) {
            if (compact.contains("洗护类") || compact.contains("洗漱类")) {
                return "洗护类";
            }
            if (compact.contains("证件类")) {
                return "证件类";
            }
            if (compact.contains("电子类") || compact.contains("电子产品")) {
                return "电子设备";
            }
            return "其他用品";
        }
        return "";
    }

    private static boolean isClothingSection(String section) {
        return "衣物类".equals(section) || "鞋帽类".equals(section);
    }

    private static boolean isClothingMarkerLine(String line) {
        if (line.isEmpty() || !line.contains("【") || !line.contains("】")) {
            return false;
        }
        String remainder = MARKER_PATTERN.matcher(line).replaceAll("")
                .replaceAll("[、，,；;\\s]", "");
        return remainder.isEmpty();
    }

    private static LinkedHashSet<String> extractReferences(String content) {
        LinkedHashSet<String> references = new LinkedHashSet<>();
        if (content == null) {
            return references;
        }
        Matcher matcher = MARKER_PATTERN.matcher(content);
        while (matcher.find()) {
            String value = matcher.group(1).trim();
            if (!value.isEmpty()) {
                references.add(value);
            }
        }
        return references;
    }
}
