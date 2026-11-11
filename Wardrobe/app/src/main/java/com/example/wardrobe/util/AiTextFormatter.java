package com.example.wardrobe.util;

/**
 * 清理大模型回答中的 Markdown 和思考过程。
 *
 * <p>普通 TextView 不渲染 Markdown，直接显示 **搭配** 会露出星号；
 * 部分模型还会把 &lt;thought&gt;、分析过程或“需要确保”等自我推演写进正文。
 * 这里统一清洗，只保留用户能直接阅读的最终建议。</p>
 */
public final class AiTextFormatter {

    private AiTextFormatter() {
        // 工具类禁止实例化。
    }

    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        String result = text;

        // 先删除完整的思考标签块，再清理可能没有闭合的残留标签。
        result = result.replaceAll(
                "(?is)<\\s*(thought|thinking|reasoning|analysis|scratchpad)"
                        + "\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>",
                " ");
        result = result.replaceAll(
                "(?is)<\\s*/?\\s*(thought|thinking|reasoning|analysis|scratchpad)"
                        + "\\b[^>]*>",
                " ");

        // 删除常见的自我推演整行。
        result = result.replaceAll(
                "(?m)^\\s*(?:思考过程|推理过程|分析过程|自我检查|校验过程"
                        + "|让我想想|我们需要|需要确保)[:：].*$",
                "");

        // 删除夹杂在正文中的自我推演句子。
        result = result.replaceAll(
                "[^。！？\\r\\n]*(?:需要确保|让我想想|我们需要|自我检查"
                        + "|思考过程|推理过程)[^。！？\\r\\n]*[。！？]?",
                "");

        result = result
                .replace("**", "")
                .replace("__", "")
                .replace("`", "");
        // 去掉 Markdown 标题、引用和行内多余空白。
        result = result.replaceAll("(?m)^\\s*#{1,6}\\s*", "");
        result = result.replaceAll("(?m)^\\s*>\\s?", "");
        result = result.replaceAll("[ \\t]{2,}", " ");
        result = result.replaceAll("\\n{3,}", "\n\n");
        return result.trim();
    }
}
