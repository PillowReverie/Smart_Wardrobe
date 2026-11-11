package com.example.wardrobe.network.dto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Locale;

/**
 * Chat Completions 响应。
 *
 * <p>只读取 choices[0].message.content。reasoning_content 是模型内部思考，
 * 必须直接忽略，绝不能展示给用户。content 兼容字符串和多段数组两种结构；
 * 失败时部分兼容服务会在 error.message 中返回具体原因。</p>
 */
public class ChatResponse {

    @SerializedName("choices")
    private List<Choice> choices;

    @SerializedName("error")
    private ApiError error;

    @SerializedName("usage")
    private Usage usage;

    /**
     * 兼容不同 OpenAI 兼容服务的返回结构：
     * 1. 标准 content 字符串；
     * 2. 多段 content 数组；
     * 3. 数组中的 text 类型片段。
     *
     * <p>reasoning_content、reasoning/thought 类型的片段都会被忽略。</p>
     */
    public String getFirstContent() {
        if (choices == null || choices.isEmpty() || choices.get(0) == null) {
            return "";
        }
        Choice choice = choices.get(0);
        ResponseMessage message = choice.message;
        return extractContent(message == null ? null : message.content);
    }

    public String getErrorMessage() {
        return error == null || error.message == null
                ? "" : error.message.trim();
    }

    public String getFinishReason() {
        if (choices == null || choices.isEmpty() || choices.get(0) == null) {
            return "";
        }
        String reason = choices.get(0).finishReason;
        return reason == null ? "" : reason.trim();
    }

    /**
     * 推理模型会把内部思考放在 reasoning_content，该内容仅用于诊断。
     * 页面永远不能展示它，只能记录长度，判断正文为空是否由思考截断导致。
     */
    public int getReasoningLength() {
        if (choices == null || choices.isEmpty() || choices.get(0) == null) {
            return 0;
        }
        ResponseMessage message = choices.get(0).message;
        if (message == null || message.reasoningContent == null) {
            return 0;
        }
        return message.reasoningContent.length();
    }

    /**
     * 输出 token 统计，便于定位 max_tokens 截断问题。
     */
    public String getUsageSummary() {
        if (usage == null) {
            return "";
        }
        return "completion=" + usage.completionTokens
                + "，reasoning=" + usage.getReasoningTokens();
    }

    private String extractContent(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString().trim();
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            StringBuilder builder = new StringBuilder();
            for (JsonElement part : array) {
                String text = extractContentPart(part);
                if (!text.isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(text);
                }
            }
            return builder.toString().trim();
        }
        return extractContentPart(element);
    }

    /**
     * 多段 content 常见结构为 {"type":"text","text":"..."}，
     * 也有服务使用 content/value 字段，这里按常见键名依次兜底。
     */
    private String extractContentPart(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString().trim();
        }
        if (!element.isJsonObject()) {
            return "";
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement typeElement = object.get("type");
        if (typeElement != null && typeElement.isJsonPrimitive()) {
            String type = typeElement.getAsString().toLowerCase(Locale.ROOT);
            if (type.contains("reason") || type.contains("thought")) {
                return "";
            }
        }
        String[] keys = {"text", "content", "value"};
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonPrimitive()) {
                String text = value.getAsString().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    public static class Choice {

        @SerializedName("message")
        private ResponseMessage message;

        @SerializedName("finish_reason")
        private String finishReason;
    }

    public static class ResponseMessage {

        @SerializedName("content")
        private JsonElement content;

        @SerializedName("reasoning_content")
        private String reasoningContent;
    }

    public static class Usage {

        @SerializedName("completion_tokens")
        private int completionTokens;

        @SerializedName("completion_tokens_details")
        private CompletionTokensDetails completionTokensDetails;

        /**
         * 不同服务对 reasoning token 的嵌套层级略有差异，
         * 这里在 Gson 完成赋值后做一次兼容读取。
         */
        public int getReasoningTokens() {
            return completionTokensDetails == null
                    ? 0 : completionTokensDetails.reasoningTokens;
        }
    }

    public static class CompletionTokensDetails {

        @SerializedName("reasoning_tokens")
        private int reasoningTokens;
    }

    public static class ApiError {

        @SerializedName("message")
        private String message;
    }
}
