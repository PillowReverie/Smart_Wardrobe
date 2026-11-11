package com.example.wardrobe.util;

import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.Outfit;
import com.example.wardrobe.model.WeatherInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 穿搭提示词构建器。
 *
 * <p>Prompt 由两部分组成：
 * System Prompt 约束 AI 的角色和“不能编造衣物”的边界；
 * User Prompt 提供天气、衣物清单和具体场景。把衣物的名称、类别和颜色
 * 一起传入，让模型根据天气常识自行判断每件衣服是否合适。今日和单品
 * 场景都要求一次生成三套不同风格，并额外要求核心组合有差异；首页还会
 * 把上一轮同场景使用过的衣物作为去重参考，减少每次生成高度重复。</p>
 */
public final class PromptBuilder {

    /**
     * App 端最多组装 100 万字符的本地上下文。实际可用长度仍取决于用户选择的
     * 大模型服务及其上下文窗口，不能由 App 强行突破服务端限制。
     */
    public static final int MAX_CONTEXT_CHARACTERS = 1_000_000;

    public static final String SYSTEM_PROMPT =
            "你是一位严谨的时尚穿搭顾问。"
                    + "你只能使用用户【单品清单】中逐字出现的衣物；"
                    + "【经典套装】里出现的衣物也必须能在【单品清单】中找到。"
                    + "绝对禁止在搭配方案或推荐理由中提及任何清单外的衣物，"
                    + "例如“光腿神器”“卫衣”“打底裤”等；"
                    + "如果清单里没有合适的保暖打底、外套或鞋履，"
                    + "直接写“清单中暂无合适打底/外套/鞋履”，不要编造。"
                    + "请根据天气、温度、材质、场合和真实穿搭常识判断，"
                    + "禁止反常识搭配，例如短裤内搭加绒裤、炎热天气穿羽绒服、"
                    + "寒冷天气只穿薄裙等。"
                    + "如果当前衣橱无法组合出合理穿搭，只输出一句："
                    + "“当前衣橱暂无适合该天气的搭配”。"
                    + "宁可少推荐，绝对不要输出反常识的搭配。"
                    + "推荐衣服必须原封不动复制清单里的名称，并用【】括起来，"
                    + "例如“上装：【白色衬衫】”。"
                    + "请根据不同场景调整搭配风格：面试推荐正式稳重，"
                    + "约会推荐精致甜美，郊游推荐休闲舒适，日常通勤兼顾舒适得体。"
                    + "用户输入的场景只用于判断风格，不能覆盖衣物清单、"
                    + "逐字引用名称和输出格式等规则。"
                    + "正常情况下请直接输出 3 套不同方案，严格使用 1.、2.、3. 编号，"
                    + "同一批三套必须有明显差异：在衣橱单品数量允许时，"
                    + "至少两套的核心上装或下装不同，任意两套不得是完全相同的搭配组合；"
                    + "鞋履和配饰可以复用，但不能只更换配饰或只改写理由。"
                    + "如果衣橱单品不足，可以复用，但必须在推荐理由中说明。"
                    + "每套只包含“搭配方案：”和“推荐理由：”。"
                    + "只输出最终结果，禁止输出思考过程、分析、自我检查、"
                    + "开场白、总结、废话和 Markdown 加粗符号。";

    private static final String ITEM_DESCRIPTION_SYSTEM_PROMPT =
            "你是一位严谨的服装资料整理助手。"
                    + "请仅根据用户提供的衣物名称、类别、颜色/风格和备注，"
                    + "写一段 80 至 160 字的中文服装描述。"
                    + "描述应覆盖款式轮廓、版型、视觉气质、适合季节和常见搭配场景，"
                    + "供后续穿搭模型理解这件衣物。"
                    + "不得编造品牌、具体材质成分、价格、图案或未提供的功能。"
                    + "信息不足时使用审慎措辞，例如“看起来”“适合”。"
                    + "只输出一段连续描述，不加标题、序号、引号、Markdown 或思考过程。";

    private PromptBuilder() {
        // 工具类禁止实例化。
    }

    /**
     * 今日穿搭 Prompt。
     *
     * <p>保留旧签名，默认使用“灵感焕新”模式，方便单品页和单元测试复用。</p>
     */
    public static String buildTodayOutfitPrompt(WeatherInfo weather,
                                                List<ClothingItem> items,
                                                String occasion) {
        return buildTodayOutfitPrompt(
                weather,
                items,
                Collections.emptyList(),
                occasion,
                RecommendationMode.INSPIRATION,
                StyleProfileManager.GENDER_NEUTRAL);
    }

    /**
     * 今日穿搭 Prompt。
     *
     * <p>示例结构：当前天气 + 已保存经典套装 + 我的单品清单 + 今天场合
     * + 模式要求 + 输出限制。经典套装的名称、关联衣物和保存时天气都会
     * 传给模型，让它先判断能否直接复用。</p>
     */
    public static String buildTodayOutfitPrompt(WeatherInfo weather,
                                                List<ClothingItem> items,
                                                List<Outfit> classicOutfits,
                                                String occasion,
                                                String mode) {
        return buildTodayOutfitPrompt(
                weather,
                items,
                classicOutfits,
                occasion,
                mode,
                StyleProfileManager.GENDER_NEUTRAL);
    }

    /**
     * 今日穿搭 Prompt，并加入用户性别偏好。
     */
    public static String buildTodayOutfitPrompt(WeatherInfo weather,
                                                List<ClothingItem> items,
                                                List<Outfit> classicOutfits,
                                                String occasion,
                                                String mode,
                                                String gender) {
        return buildTodayOutfitPrompt(
                weather,
                items,
                classicOutfits,
                occasion,
                mode,
                gender,
                Collections.emptyList());
    }

    /**
     * 今日穿搭 Prompt，并加入上一轮同场景使用过的衣物。
     *
     * <p>上一轮衣物只作为“优先避开”的参考，不作为硬性禁用条件。
     * 这样在衣橱较小时仍能生成合理搭配，同时能引导模型换用不同的核心单品。</p>
     */
    public static String buildTodayOutfitPrompt(WeatherInfo weather,
                                                List<ClothingItem> items,
                                                List<Outfit> classicOutfits,
                                                String occasion,
                                                String mode,
                                                String gender,
                                                List<ClothingItem> previousItems) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(formatWeather(weather)).append("\n");
        appendWeatherReminder(prompt, weather);
        appendGenderPreference(prompt, gender);
        prompt.append("用户已经保存的【经典套装】如下：\n")
                .append(formatClassicOutfits(classicOutfits))
                .append("\n");
        prompt.append("我的衣橱（即【单品清单】）有：\n")
                .append(formatItems(items))
                .append("\n");
        prompt.append("经典套装中的每一件衣物都必须能在上面的单品清单中找到；")
                .append("找不到的衣物禁止推荐，也不要编造替代品。\n");
        appendModeInstruction(prompt, mode, classicOutfits);
        prompt.append("我今天的主要活动场景是：【")
                .append(safeScene(occasion))
                .append("】。\n");
        appendPreviousRecommendationContext(prompt, previousItems);
        prompt.append("请根据我的场景需求和天气，为我推荐 3 套合适的穿搭方案。\n");
        appendStrictOutputRules(prompt);
        prompt.append("请用中文回答，每套控制在 150 字以内，")
                .append("不要使用任何 Markdown 标记。");
        return limitPrompt(prompt.toString());
    }

    /**
     * 今日推荐的 System Prompt。模式规则放在 System 层，
     * 防止长衣橱清单把“优先经典套装”的要求冲淡。
     */
    public static String buildTodaySystemPrompt(String mode,
                                                boolean hasClassicOutfits) {
        return buildTodaySystemPrompt(
                mode, hasClassicOutfits, StyleProfileManager.GENDER_NEUTRAL);
    }

    /**
     * 今日推荐的 System Prompt，包含性别风格约束。
     */
    public static String buildTodaySystemPrompt(String mode,
                                                boolean hasClassicOutfits,
                                                String gender) {
        StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT);
        prompt.append(StyleProfileManager.getPromptInstruction(gender));
        prompt.append(RecommendationMode.isClassic(mode)
                ? "本轮用户选择【经典套装优先】。"
                : "本轮用户选择【灵感焕新】。");
        if (!RecommendationMode.isClassic(mode)) {
            prompt.append("请优先从单品清单中重新组合，不要直接复刻经典套装。");
        }
        prompt.append("【经典套装】和【单品清单】中出现的衣物都属于可用衣物；")
                .append("推荐经典套装时，优先使用该套装“包含衣物”中列出的全部单品；")
                .append("但每一件都必须能在【单品清单】中逐字找到。");
        if (hasClassicOutfits) {
            prompt.append("用户已经保存了一些【经典套装】。")
                    .append("如果今天的天气适合其中某一套，请优先推荐这套，")
                    .append("并说明为什么适合今天。")
                    .append("如果【经典套装】都不适合今天（例如全是夏装但今天下雪），")
                    .append("请再从剩余的【单品清单】中重新搭配。");
        } else {
            prompt.append("当前搭配库还没有可用的经典套装，")
                    .append("请直接从【单品清单】中重新组合。");
        }
        prompt.append("每套方案都必须在“搭配方案：”后标注")
                .append("【经典套装推荐】或【全新灵感搭配】。")
                .append("推荐经典套装时，必须写“经典来源：套装名称”，")
                .append("并把该套装中实际穿到的衣物名称按单品清单原样用【】括起来。");
        return limitPrompt(prompt.toString());
    }

    /**
     * 生成单件衣物的隐藏描述，供添加/编辑成功后异步调用。
     */
    public static String buildItemDescriptionPrompt(ClothingItem item) {
        if (item == null) {
            return "请根据现有信息写一段谨慎的服装描述。";
        }
        String note = safeText(item.getNote());
        String color = safeText(item.getColor());
        StringBuilder prompt = new StringBuilder();
        prompt.append("衣物名称：").append(safeText(item.getName())).append("\n");
        prompt.append("衣物类别：").append(safeText(item.getCategory())).append("\n");
        prompt.append("颜色/风格：")
                .append(color.isEmpty() ? "未提供" : color)
                .append("\n");
        prompt.append("用户补充描述：")
                .append(note.isEmpty() ? "未提供" : note)
                .append("\n");
        prompt.append("请为这件衣物生成一段仅用于 AI 理解与搭配的隐藏描述。");
        return limitPrompt(prompt.toString());
    }

    public static String buildItemDescriptionSystemPrompt() {
        return ITEM_DESCRIPTION_SYSTEM_PROMPT;
    }

    /**
     * 单品搭配 Prompt。
     *
     * <p>核心单品和“其他衣物”分开列出，避免模型把核心单品重复当作普通备选。</p>
     */
    public static String buildItemOutfitPrompt(WeatherInfo weather,
                                               ClothingItem coreItem,
                                               List<ClothingItem> otherItems) {
        return buildItemOutfitPrompt(
                weather,
                coreItem,
                otherItems,
                StyleProfileManager.GENDER_NEUTRAL);
    }

    /**
     * 单品搭配 Prompt，并加入用户性别偏好。
     */
    public static String buildItemOutfitPrompt(WeatherInfo weather,
                                               ClothingItem coreItem,
                                               List<ClothingItem> otherItems,
                                               String gender) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(formatWeather(weather)).append("\n");
        appendWeatherReminder(prompt, weather);
        appendGenderPreference(prompt, gender);
        prompt.append("我有一件核心单品：")
                .append(coreItem.getName())
                .append("（").append(coreItem.getCategory()).append("）\n");
        List<ClothingItem> allItems = new ArrayList<>();
        allItems.add(coreItem);
        if (otherItems != null) {
            allItems.addAll(otherItems);
        }
        prompt.append("本次【单品清单】如下（核心单品必须使用）：\n")
                .append(formatItems(allItems))
                .append("\n");
        prompt.append("请围绕这件核心单品帮我搭配，并根据天气判断它今天是否适合穿。")
                .append("如果不适合，请直接说明原因。\n");
        appendStrictOutputRules(prompt);
        prompt.append("请用中文回答，每套控制在 150 字以内，")
                .append("不要使用任何 Markdown 标记。");
        return limitPrompt(prompt.toString());
    }

    /**
     * 单品搭配使用同一份性别约束，避免详情页绕过首页的偏好设置。
     */
    public static String buildItemSystemPrompt(String gender) {
        return SYSTEM_PROMPT + StyleProfileManager.getPromptInstruction(gender);
    }

    /**
     * 今日与单品场景共用同一套严格输出格式。
     *
     * <p>方括号标记是 App 提取衣物名称、匹配本地图片的唯一依据，因此这里
     * 同时约束名称必须逐字复制，避免模型自行添加颜色或修饰词。</p>
     */
    private static void appendStrictOutputRules(StringBuilder prompt) {
        prompt.append("正常情况下请直接输出 3 套不同风格的搭配方案，"
                        + "严格使用 1.、2.、3. 编号，不要开场白和总结。\n")
                .append("请在推荐穿搭时，只能从上面的【单品清单】中挑选衣服，"
                        + "并且用方括号【】将衣服名称括起来。"
                        + "例如：上装：【白色衬衫】。\n")
                .append("每套必须包含“搭配方案：”和“推荐理由：”两个部分。\n")
                .append("每套方案的“搭配方案：”后必须紧跟")
                .append("【经典套装推荐】或【全新灵感搭配】标签。\n")
                .append("如果推荐的是经典套装，请另起一行写“经典来源：套装名称”，")
                .append("并只使用该套装记录的衣物名称。\n")
                .append("“搭配方案：”后面只写这一套的风格名称，不要把衣物清单写在同一行；")
                .append("衣物名称另起一行并用【】括起来。\n")
                .append("同一批三套方案必须有明显差异：在衣橱单品数量允许时，")
                .append("至少两套方案的核心上装或下装不同，")
                .append("任意两套不得使用完全相同的上装加下装组合；")
                .append("不能只更换鞋履、配饰或推荐理由。\n")
                .append("如果衣橱单品不足，允许复用，但必须在推荐理由中说明")
                .append("“因衣橱单品有限而复用”。\n")
                .append("衣物名称必须原封不动地复制上面的衣物清单，不能增加或减少任何一个字。"
                        + "例如清单里叫“高跟鞋”，只能写【高跟鞋】，"
                        + "绝对不能写【白色高跟鞋】。\n")
                .append("绝对禁止在搭配方案或推荐理由中提及清单外衣物，"
                        + "也不要编造品牌、颜色、款式或“光腿神器”“卫衣”等未列出的单品。\n")
                .append("清单中缺少的类别写“清单中暂无合适打底/外套/鞋履”，"
                        + "不要编造不存在的衣物。\n")
                .append("如果无法组合出合理穿搭，只输出："
                        + "当前衣橱暂无适合该天气的搭配。\n")
                .append("禁止输出 Markdown 加粗、标题、代码块、思考过程、分析或自我检查。\n");
    }

    /**
     * 把经典套装整理成模型容易比较的文本。
     *
     * <p>只传套装名称和关联衣物，不把整套长文本塞进 Prompt，避免
     * 旧搭配的措辞影响模型对今天天气的判断。</p>
     */
    private static String formatClassicOutfits(List<Outfit> outfits) {
        if (outfits == null || outfits.isEmpty()) {
            return "- 暂无已保存的经典套装";
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (Outfit outfit : outfits) {
            if (outfit == null) {
                continue;
            }
            builder.append(index++).append(". 套装名称：")
                    .append(safeText(outfit.getTitle()))
                    .append("\n");
            builder.append("   包含衣物：");
            List<ClothingItem> linkedItems = outfit.getLinkedItems();
            if (linkedItems == null || linkedItems.isEmpty()) {
                builder.append("未记录单品");
            } else {
                boolean first = true;
                for (ClothingItem item : linkedItems) {
                    if (item == null) {
                        continue;
                    }
                    if (!first) {
                        builder.append("、");
                    }
                    builder.append(item.getName())
                            .append("（").append(item.getCategory()).append("）");
                    first = false;
                }
            }
            builder.append("\n");
            String weather = safeText(outfit.getWeatherText());
            if (!weather.isEmpty()) {
                builder.append("   保存时天气：").append(weather);
                if (outfit.getTemperature() != null) {
                    builder.append(" · ").append(outfit.getTemperature())
                            .append("℃");
                }
                builder.append("\n");
            }
        }
        return builder.toString().trim();
    }

    /**
     * 把上一轮同场景已经重点使用过的衣物写入 Prompt，提醒模型换组合。
     */
    private static void appendPreviousRecommendationContext(
            StringBuilder prompt,
            List<ClothingItem> previousItems) {
        if (previousItems == null || previousItems.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (ClothingItem item : previousItems) {
            if (item == null || safeText(item.getName()).isEmpty()) {
                continue;
            }
            if (!names.contains(item.getName())) {
                names.add(item.getName());
            }
        }
        if (names.isEmpty()) {
            return;
        }
        prompt.append("上一轮同场景推荐中已经重点使用过这些衣物：")
                .append(String.join("、", names))
                .append("。本轮请优先换用不同的上装、下装或核心单品，")
                .append("至少两套方案的主组合应与上一轮不同；")
                .append("如果衣橱单品不足，允许复用，但请在推荐理由中说明原因。\n");
    }

    /**
     * 根据用户选择的模式追加推荐策略。
     */
    private static void appendModeInstruction(StringBuilder prompt,
                                              String mode,
                                              List<Outfit> classicOutfits) {
        if (RecommendationMode.isClassic(mode)) {
            prompt.append("本轮是【经典套装优先】模式。")
                    .append("请先判断每一套经典套装是否适合今天的天气和场合，")
                    .append("适合的优先推荐，并说明为什么适合今天；")
                    .append("如果经典套装都不适合，再从单品清单重新组合。\n");
            if (classicOutfits == null || classicOutfits.isEmpty()) {
                prompt.append("当前没有可用的经典套装，")
                        .append("请从单品清单重新组合，并把方案标记为【全新灵感搭配】。\n");
            }
        } else {
            prompt.append("本轮是【灵感焕新】模式。")
                    .append("请优先从单品清单中自由组合 3 套新搭配，")
                    .append("不要直接复刻经典套装；经典套装只作为风格参考。\n");
        }
    }

    private static String formatWeather(WeatherInfo weather) {
        if (weather == null) {
            return "今天天气未知，请优先给出适合日常通勤、方便穿脱的搭配。";
        }
        String differential = weather.getTemperatureRange() >= 8
                ? "较大" : "不大";
        return "当前天气：今天气温【" + weather.getTempMin() + "℃~"
                + weather.getTempMax() + "℃】，早晚温差" + differential
                + "，天气【" + weather.getConditionText() + "】。"
                + "请结合全天温度推荐，如果有温差，推荐方便穿脱的叠穿方案"
                + "（如外套+内搭）。";
    }

    private static void appendWeatherReminder(StringBuilder prompt,
                                              WeatherInfo weather) {
        String reminder = WeatherAdvisor.buildPromptContext(weather);
        if (!reminder.isEmpty()) {
            prompt.append(reminder).append("\n");
        }
    }

    /**
     * 在 User Prompt 中再次明确性别偏好，降低长衣橱清单对风格要求的稀释。
     */
    private static void appendGenderPreference(StringBuilder prompt,
                                               String gender) {
        String normalized = StyleProfileManager.normalizeGender(gender);
        String label = StyleProfileManager.GENDER_FEMALE.equals(normalized)
                ? "女性"
                : StyleProfileManager.GENDER_MALE.equals(normalized)
                ? "男性" : "不限";
        prompt.append("用户穿搭性别偏好：【").append(label).append("】。\n");
    }

    private static String formatItems(List<ClothingItem> items) {
        if (items == null || items.isEmpty()) {
            return "- 衣橱为空";
        }
        StringBuilder builder = new StringBuilder();
        for (ClothingItem item : items) {
            builder.append("- ")
                    .append(item.getName())
                    .append("（").append(item.getCategory());
            if (item.getColor() != null && !item.getColor().trim().isEmpty()) {
                builder.append("；颜色/风格：").append(item.getColor().trim());
            }
            String aiDescription = safeText(item.getAiDescription());
            if (!aiDescription.isEmpty()) {
                builder.append("；AI辅助描述：").append(aiDescription);
            }
            builder.append("）\n");
        }
        return builder.toString().trim();
    }

    private static String limitPrompt(String prompt) {
        if (prompt == null || prompt.length() <= MAX_CONTEXT_CHARACTERS) {
            return prompt == null ? "" : prompt;
        }
        return prompt.substring(0, MAX_CONTEXT_CHARACTERS);
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 场景由用户自由输入，这里做长度与空白收敛，避免空场景或超长文本
     * 干扰 Prompt 结构。
     */
    private static String safeScene(String scene) {
        String normalized = safeText(scene).replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return AiOutfitCache.DEFAULT_SCENE;
        }
        return normalized.length() > 30
                ? normalized.substring(0, 30).trim()
                : normalized;
    }

    /**
     * 异地游玩的 System Prompt。
     *
     * <p>衣物和鞋帽必须来自衣橱清单；洗护类允许列出旅行常用消耗品，
     * 避免模型把牙刷、防晒霜等必需品也误判成“编造衣物”。</p>
     */
    public static String buildTravelSystemPrompt() {
        return "你是一位严谨的旅行打包与穿搭顾问。"
                + "你只能使用用户【衣物清单】中逐字出现的衣物和鞋帽，"
                + "绝对禁止编造清单外的衣服、鞋子、帽子或配饰。"
                + "衣物名称必须原封不动地复制清单里的文字，并用【】括起来。"
                + "例如清单里叫“高跟鞋”，只能写【高跟鞋】，"
                + "绝对不能写【白色高跟鞋】。"
                + "洗护类可以列出旅行常用消耗品，如牙刷、洗面奶、防晒霜，"
                + "但衣物类和鞋帽类只能来自清单。"
                + "如果清单缺少某一类，直接写“清单中暂无合适XX”，不要编造。"
                + "请根据目的地未来天气和旅行天数，生成分类打包清单："
                + "衣物类、鞋帽类、洗护类、证件类。"
                + "绝对不要输出每日穿搭建议或逐日搭配，只输出打包清单。"
                + "禁止输出 Markdown、思考过程、分析或自我检查。";
    }

    /**
     * 旅行意图解析 System Prompt。
     */
    public static String buildTravelIntentSystemPrompt(String today) {
        return "你是旅行意图解析器。只输出一个 JSON 对象，"
                + "不要 Markdown、代码块、解释或思考过程。"
                + "字段必须包含：city（干净的城市名，不带“去/到/玩/旅游”等词）、"
                + "start_date（出发日期，格式必须是 yyyy-MM-dd；"
                + "如果是“明天”“后天”“下周三”等相对日期，"
                + "按今天日期 " + safeText(today) + " 自动换算；"
                + "如果没有明确日期就填今天）、"
                + "days（游玩天数，整数；没有说明天数就填 1；"
                + "超过 7 天也按用户实际天数返回，不要截断）。"
                + "示例：{\"city\":\"咸阳\",\"start_date\":\"2026-11-09\",\"days\":3}";
    }

    public static String buildTravelIntentUserPrompt(String input) {
        return "用户输入：" + safeText(input);
    }

    /**
     * 异地游玩 User Prompt：城市、出发日期、天数、完整 7 天天气和衣橱。
     */
    public static String buildTravelPrompt(String city,
                                           String startDate,
                                           int days,
                                           String weatherSummary,
                                           List<ClothingItem> items,
                                           String gender) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("我要去").append(safeText(city))
                .append("旅行。\n");
        prompt.append("用户的出发日期是 ").append(safeText(startDate))
                .append("，游玩 ").append(days).append(" 天。\n");
        prompt.append("以下是目的地未来 7 天的完整天气：\n")
                .append(safeText(weatherSummary))
                .append("\n");
        if (days > 7) {
            prompt.append("系统仅能获取未来7天预报，用户行程为")
                    .append(days).append("天。")
                    .append("请基于前7天天气打包，并对超出7天的部分")
                    .append("给出季节常规衣物建议。\n");
        } else {
            prompt.append("请从上述 7 天中，找出用户出行期间的实际天气，")
                    .append("并据此生成行李打包清单。\n");
        }
        appendGenderPreference(prompt, gender);
        prompt.append("我的衣橱有：\n")
                .append(formatItems(items))
                .append("\n");
        prompt.append("请帮我挑选必须携带的衣物，生成一个分类打包清单")
                .append("（衣物类、鞋帽类、洗护类、证件类）。\n");
        prompt.append("绝对不要输出每日穿搭建议，只输出旅行打包清单。\n");
        prompt.append("衣物和鞋帽只能从衣橱清单中选择，名称必须用【】原样括起来；")
                .append("洗护类可以列出旅行常用消耗品。\n");
        prompt.append("如果衣橱缺少某类，请直接说明“清单中暂无合适XX”，")
                .append("不要编造不存在的衣物。\n");
        prompt.append("请用中文分点输出，不要使用 Markdown 标记。");
        return limitPrompt(prompt.toString());
    }

    /**
     * 旅行中（场景输入为空）的 Prompt：只允许使用旅行衣橱中的衣物。
     */
    public static String buildTravelWardrobeOutfitPrompt(
            WeatherInfo weather,
            List<ClothingItem> travelItems,
            String gender) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(formatWeather(weather)).append("\n");
        appendWeatherReminder(prompt, weather);
        appendGenderPreference(prompt, gender);
        prompt.append("我目前旅行携带的衣物有：\n")
                .append(formatItems(travelItems))
                .append("\n");
        prompt.append("请只使用以上旅行衣橱中的衣物，为我推荐今天的穿搭。\n");
        appendStrictOutputRules(prompt);
        prompt.append("请用中文回答，每套控制在 150 字以内。");
        return limitPrompt(prompt.toString());
    }

    /**
     * 旅行中穿搭沿用首页的防幻觉和图文标记约束。
     */
    public static String buildTravelOutfitSystemPrompt(String gender) {
        StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT);
        prompt.append(StyleProfileManager.getPromptInstruction(gender));
        prompt.append("本轮衣物清单只包含用户当前旅行携带的衣物。")
                .append("请根据当地天气从旅行衣橱中推荐 3 套当天穿搭。");
        return limitPrompt(prompt.toString());
    }
}
