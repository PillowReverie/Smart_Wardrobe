package com.example.wardrobe.util;

import com.example.wardrobe.model.AiOutfitPlan;
import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.Outfit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将大模型返回的三套穿搭文本解析成可渲染的数据结构。
 *
 * <p>解析分三步：
 * 1. 按 1.、2.、3. 智能拆分方案；
 * 2. 用正则提取【衣物名称】并解析“搭配方案/推荐理由”；
 * 3. 在本机衣橱列表中做不区分空格、标点的包含匹配，找出图片记录。
 * 即使模型漏写方括号或名称不完全一致，也会保留占位项，保证 UI 可展示。</p>
 */
public final class AiOutfitParser {

    private static final String NO_SUITABLE_TEXT =
            "当前衣橱暂无适合该天气的搭配";
    private static final Pattern PLAN_START = Pattern.compile(
            "(?m)^\\s*(?:#{1,6}\\s*)?(?:方案\\s*)?(?:第\\s*)?"
                    + "([1-3一二三])\\s*(?:套)?\\s*[.、:：)）．]\\s*");
    /**
     * 部分模型会把序号放在“搭配方案：”后面，例如：
     * 搭配方案：1. 【经典套装推荐】...
     * 这里单独识别，避免三套方案被合并成一段。
     */
    private static final Pattern PLAN_START_AFTER_LABEL = Pattern.compile(
            "(?m)^\\s*搭配方案\\s*[:：]\\s*(?:方案\\s*)?(?:第\\s*)?"
                    + "([1-3一二三])\\s*(?:套)?\\s*[.、:：)）．]?\\s*");
    private static final Pattern CLOTHING_MARKER = Pattern.compile(
            "【\\s*([^【】\\r\\n]{1,80}?)\\s*】");
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "(?:搭配方案|方案名称|风格名称|风格)\\s*[:：]\\s*([^\\r\\n]+)");
    private static final Pattern REASON_PATTERN = Pattern.compile(
            "推荐理由\\s*[:：]\\s*([\\s\\S]*)");
    private static final Pattern CLASSIC_MARKER = Pattern.compile(
            "【\\s*(?:经典套装推荐|经典套装|经典推荐)\\s*】");
    private static final Pattern INSPIRATION_MARKER = Pattern.compile(
            "【\\s*(?:全新灵感搭配|灵感焕新|全新搭配|灵感搭配)\\s*】");
    private static final Pattern CLASSIC_SOURCE_PATTERN = Pattern.compile(
            "(?:经典来源|经典套装名称|套装名称)\\s*[:：]\\s*"
                    + "([^，,。；;【】\\r\\n]+)");

    private AiOutfitParser() {
        // 工具类禁止实例化。
    }

    /**
     * 解析大模型回答并完成本地衣物匹配。
     */
    public static List<AiOutfitPlan> parse(String rawContent,
                                           List<ClothingItem> wardrobeItems) {
        return parse(rawContent, wardrobeItems, Collections.emptyList());
    }

    /**
     * 解析时额外传入已保存的经典套装，用于在模型漏写“经典来源”时
     * 根据套装名称做一次兜底识别。
     */
    public static List<AiOutfitPlan> parse(String rawContent,
                                           List<ClothingItem> wardrobeItems,
                                           List<Outfit> classicOutfits) {
        String cleaned = AiTextFormatter.clean(rawContent);
        if (isNoSuitableOutfit(cleaned)) {
            AiOutfitPlan refusalPlan = createFallbackPlan(cleaned);
            refusalPlan.setTitle("暂无合适搭配");
            return Collections.singletonList(refusalPlan);
        }
        List<AiOutfitPlan> plans = splitPlans(cleaned);
        if (plans.isEmpty()) {
            plans.add(createFallbackPlan(cleaned));
        }

        for (AiOutfitPlan plan : plans) {
            resolveClassicOutfitTitle(plan, classicOutfits);
            matchVisualItems(plan, wardrobeItems, classicOutfits);
        }
        return plans;
    }

    /**
     * 模型返回“当前衣橱暂无适合该天气的搭配”时，说明衣橱无法组合出
     * 合理穿搭。此时不要再生成占位图片，直接展示这段文字即可。
     */
    private static boolean isNoSuitableOutfit(String content) {
        if (content == null || !content.contains(NO_SUITABLE_TEXT)) {
            return false;
        }
        return !PLAN_START.matcher(content).find()
                && !PLAN_START_AFTER_LABEL.matcher(content).find();
    }

    private static List<AiOutfitPlan> splitPlans(String content) {
        List<AiOutfitPlan> plans = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return plans;
        }

        List<PlanBoundary> boundaries = new ArrayList<>();
        collectBoundaries(content, PLAN_START, boundaries);
        collectBoundaries(content, PLAN_START_AFTER_LABEL, boundaries);
        boundaries.sort((left, right) -> Integer.compare(left.start, right.start));

        if (boundaries.isEmpty()) {
            return plans;
        }
        for (int index = 0; index < boundaries.size() && index < 3; index++) {
            PlanBoundary boundary = boundaries.get(index);
            int end = index + 1 < boundaries.size()
                    ? boundaries.get(index + 1).start
                    : content.length();
            String segment = content.substring(boundary.contentStart, end).trim();
            if (segment.isEmpty()) {
                continue;
            }
            plans.add(createPlan(boundary.sequence, segment));
        }
        return plans;
    }

    private static void collectBoundaries(String content,
                                          Pattern pattern,
                                          List<PlanBoundary> boundaries) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            boundaries.add(new PlanBoundary(
                    matcher.start(),
                    matcher.end(),
                    parseSequence(matcher.group(1))
            ));
        }
    }

    private static AiOutfitPlan createFallbackPlan(String content) {
        AiOutfitPlan plan = createPlan(1, content);
        if (plan.getTitle().isEmpty()) {
            plan.setTitle("今日搭配");
        }
        return plan;
    }

    private static AiOutfitPlan createPlan(int sequence, String segment) {
        AiOutfitPlan plan = new AiOutfitPlan();
        plan.setSequence(sequence);
        plan.setRawContent(segment);
        plan.setRecommendationType(detectRecommendationType(segment));
        plan.setClassicOutfitTitle(extractClassicOutfitTitle(segment));
        String title = extractTitle(segment);
        if (title.isEmpty() && !plan.getClassicOutfitTitle().isEmpty()) {
            title = plan.getClassicOutfitTitle();
        }
        plan.setTitle(title);
        plan.setReason(extractReason(segment));
        return plan;
    }

    /**
     * 提取标题。优先读取“搭配方案：”，没有标签时取段落第一行。
     */
    private static String extractTitle(String segment) {
        String cleaned = stripReservedMarkers(segment);
        Matcher matcher = TITLE_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            String candidate = matcher.group(1).trim();
            if (candidate.isEmpty() || looksLikeClothingDescription(candidate)) {
                return "";
            }
            return limitTitle(candidate);
        }

        String firstLine = cleaned.split("\\R", 2)[0].trim();
        if (firstLine.isEmpty()
                || firstLine.startsWith("上装")
                || firstLine.startsWith("下装")
                || firstLine.startsWith("外套")
                || firstLine.startsWith("鞋履")
                || firstLine.startsWith("配饰")
                || firstLine.startsWith("推荐理由")
                || firstLine.startsWith("经典来源")
                || firstLine.startsWith("搭配方案")
                || looksLikeClothingDescription(firstLine)) {
            return "";
        }
        return limitTitle(firstLine);
    }

    /**
     * 部分模型会把“上装：【...】。下装：...”直接写在搭配方案标签后。
     * 这不是风格名称，交给 UI 显示为“方案 1/2/3”更清晰。
     */
    private static boolean looksLikeClothingDescription(String value) {
        if (value == null) {
            return true;
        }
        String text = value.trim();
        return text.contains("【")
                || text.startsWith("上装")
                || text.startsWith("下装")
                || text.startsWith("外套")
                || text.startsWith("鞋履")
                || text.startsWith("配饰")
                || text.startsWith("推荐理由")
                || text.contains("清单中暂无");
    }

    private static String limitTitle(String title) {
        String cleaned = title == null ? "" : title.trim()
                .replaceAll("[。；;]+$", "");
        if (cleaned.length() > 24) {
            return cleaned.substring(0, 24) + "…";
        }
        return cleaned;
    }

    /**
     * 优先显示“推荐理由：”后的内容；模型漏写标签时退回整段说明，
     * 避免推荐理由区域为空。
     */
    private static String extractReason(String segment) {
        String cleaned = stripReservedMarkers(segment);
        Matcher matcher = REASON_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            String reason = AiTextFormatter.clean(matcher.group(1));
            if (!reason.isEmpty()) {
                return reason;
            }
        }

        String fallback = cleaned
                .replaceAll("(?m)^\\s*(?:搭配方案|方案名称|风格名称|风格)"
                        + "\\s*[:：].*$", "")
                .replaceAll("(?m)^\\s*(?:上装|下装|外套|鞋履|配饰)"
                        + "\\s*[:：].*$", "")
                .replaceAll("清单中暂无", "")
                .trim();
        if (fallback.isEmpty()) {
            return "AI 已根据当前天气从衣橱中挑选了上述单品。";
        }
        return fallback;
    }

    /**
     * 提取所有方括号中的名称，并按本地衣橱做模糊匹配。
     */
    private static void matchVisualItems(AiOutfitPlan plan,
                                         List<ClothingItem> wardrobeItems,
                                         List<Outfit> classicOutfits) {
        LinkedHashSet<String> references = new LinkedHashSet<>();
        Matcher matcher = CLOTHING_MARKER.matcher(plan.getRawContent());
        while (matcher.find()) {
            String reference = matcher.group(1).trim();
            if (!reference.isEmpty() && !isReservedReference(reference)) {
                references.add(reference);
            }
        }

        Set<Long> usedItemIds = new HashSet<>();
        for (String reference : references) {
            ClothingItem match = findBestMatchingItem(reference, wardrobeItems);
            if (match != null && usedItemIds.contains(match.getId())) {
                continue;
            }
            if (match != null) {
                usedItemIds.add(match.getId());
            }
            plan.addVisualItem(new AiOutfitPlan.VisualItem(reference, match));
        }

        // 经典套装推荐如果模型没有完整写【】，直接回填该套装的全部衣物，
        // 既保证图片展示，也保证保存时能拿到真实衣物 ID。
        if (plan.isClassicRecommendation()) {
            Outfit classicOutfit = findClassicOutfit(
                    plan.getClassicOutfitTitle(), classicOutfits);
            if (classicOutfit != null && !classicOutfit.getLinkedItems().isEmpty()) {
                plan.clearVisualItems();
                for (ClothingItem item : classicOutfit.getLinkedItems()) {
                    if (item != null && item.getId() > 0) {
                        plan.addVisualItem(new AiOutfitPlan.VisualItem(
                                item.getName(), item));
                    }
                }
            }
        }

        // 模型漏写【】时没有可匹配名称，仍保留一个占位图片位。
        if (plan.getVisualItems().isEmpty()) {
            plan.addVisualItem(new AiOutfitPlan.VisualItem("未识别衣物", null));
        }
    }

    private static Outfit findClassicOutfit(String title,
                                            List<Outfit> classicOutfits) {
        if (title == null || title.trim().isEmpty()
                || classicOutfits == null || classicOutfits.isEmpty()) {
            return null;
        }
        String titleKey = normalizeForMatch(title);
        for (Outfit outfit : classicOutfits) {
            if (outfit != null
                    && titleKey.equals(normalizeForMatch(outfit.getTitle()))) {
                return outfit;
            }
        }
        return null;
    }

    /**
     * 根据模型输出标签判断方案来源。
     *
     * <p>优先识别明确标签；如果模型只写了“经典来源”，也按经典套装处理。
     * 没有任何标记时默认视为“全新灵感搭配”，避免把普通方案误标为经典。</p>
     */
    private static String detectRecommendationType(String segment) {
        if (segment == null) {
            return AiOutfitPlan.TYPE_INSPIRATION;
        }
        if (CLASSIC_MARKER.matcher(segment).find()
                || segment.contains("经典套装推荐")
                || segment.contains("经典来源")) {
            return AiOutfitPlan.TYPE_CLASSIC;
        }
        if (INSPIRATION_MARKER.matcher(segment).find()) {
            return AiOutfitPlan.TYPE_INSPIRATION;
        }
        return AiOutfitPlan.TYPE_INSPIRATION;
    }

    private static String extractClassicOutfitTitle(String segment) {
        if (segment == null) {
            return "";
        }
        Matcher matcher = CLASSIC_SOURCE_PATTERN.matcher(segment);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1)
                .replace("】", "")
                .replace("【", "")
                .trim();
    }

    /**
     * 模型偶尔会漏写“经典来源”。此时用已保存套装的标题做包含匹配，
     * 例如正文出现“通勤三件套”，就把它补回 plan 上，方便保存关联 ID。
     */
    private static void resolveClassicOutfitTitle(
            AiOutfitPlan plan, List<Outfit> classicOutfits) {
        if (plan == null || !plan.isClassicRecommendation()
                || !plan.getClassicOutfitTitle().isEmpty()
                || classicOutfits == null || classicOutfits.isEmpty()) {
            return;
        }
        String contentKey = normalizeForMatch(plan.getRawContent());
        if (contentKey.isEmpty()) {
            return;
        }
        for (Outfit outfit : classicOutfits) {
            if (outfit == null) {
                continue;
            }
            String titleKey = normalizeForMatch(outfit.getTitle());
            if (!titleKey.isEmpty() && contentKey.contains(titleKey)) {
                plan.setClassicOutfitTitle(outfit.getTitle());
                return;
            }
        }
    }

    /**
     * 去掉输出里的类型标签，避免【经典套装推荐】被误识别成衣物名称，
     * 也避免标题和推荐理由中重复显示标签。
     */
    private static String stripReservedMarkers(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("【\\s*(?:经典套装推荐|经典套装|经典推荐)\\s*】", "")
                .replaceAll("【\\s*(?:全新灵感搭配|灵感焕新|全新搭配|灵感搭配)\\s*】", "")
                .replaceAll("【\\s*经典来源\\s*[:：][^【】\\r\\n]*】", "")
                .replaceAll("(?:经典套装推荐|全新灵感搭配)\\s*", "")
                .trim();
    }

    private static boolean isReservedReference(String reference) {
        String key = normalizeForMatch(reference);
        return key.startsWith(normalizeForMatch("经典套装推荐"))
                || key.startsWith(normalizeForMatch("全新灵感搭配"))
                || key.startsWith(normalizeForMatch("经典套装"))
                || key.startsWith(normalizeForMatch("灵感焕新"))
                || key.startsWith(normalizeForMatch("经典来源"));
    }

    /**
     * 遍历本地衣橱进行包含匹配。
     *
     * <p>比较前会去掉空格和标点，因此“白色 衬衫”仍能匹配“白色衬衫”。
     * 优先完全相等，其次判断衣橱名称是否被模型文本包含，最后兼容模型只写了
     * 衣物名称的一部分的情况。</p>
     */
    public static ClothingItem findBestMatchingItem(
            String reference, List<ClothingItem> wardrobeItems) {
        String referenceKey = normalizeForMatch(reference);
        if (referenceKey.isEmpty() || wardrobeItems == null
                || wardrobeItems.isEmpty()) {
            return null;
        }

        ClothingItem bestItem = null;
        int bestScore = 0;
        for (ClothingItem item : wardrobeItems) {
            if (item == null) {
                continue;
            }
            String itemKey = normalizeForMatch(item.getName());
            if (itemKey.isEmpty()) {
                continue;
            }

            int score = 0;
            if (referenceKey.equals(itemKey)) {
                score = 1000;
            } else if (referenceKey.contains(itemKey)) {
                score = 700 + itemKey.length();
            } else if (itemKey.contains(referenceKey)) {
                score = 500 + referenceKey.length();
            }
            if (score > bestScore) {
                bestScore = score;
                bestItem = item;
            }
        }
        return bestItem;
    }

    /**
     * 提取单套方案实际匹配到的衣物，用于保存 outfit_items 关联。
     */
    public static List<ClothingItem> getMatchedItems(AiOutfitPlan plan) {
        List<ClothingItem> matches = new ArrayList<>();
        if (plan == null) {
            return matches;
        }
        for (AiOutfitPlan.VisualItem visualItem : plan.getVisualItems()) {
            ClothingItem item = visualItem.getClothingItem();
            if (item != null && item.getId() > 0) {
                matches.add(item);
            }
        }
        return matches;
    }

    /**
     * 只保留文字和数字，忽略中英文空格、括号和标点差异。
     */
    private static String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static int parseSequence(String value) {
        if ("二".equals(value)) {
            return 2;
        }
        if ("三".equals(value)) {
            return 3;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static class PlanBoundary {

        private final int start;
        private final int contentStart;
        private final int sequence;

        PlanBoundary(int start, int contentStart, int sequence) {
            this.start = start;
            this.contentStart = contentStart;
            this.sequence = sequence;
        }
    }
}
