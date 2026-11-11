package com.example.wardrobe.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * OpenAI 标准 Chat Completions 请求体。
 *
 * <p>国内主流大模型（DeepSeek、Kimi、智谱等）大多兼容该格式，
 * 因此这里只依赖用户配置的 Base URL 和模型名称，不绑定具体厂商。</p>
 */
public class ChatRequest {

    @SerializedName("model")
    private final String model;

    @SerializedName("messages")
    private final List<ChatMessage> messages;

    @SerializedName("temperature")
    private final double temperature;

    @SerializedName("max_tokens")
    private final int maxTokens;

    @SerializedName("stream")
    private final boolean stream;

    /**
     * DeepSeek 新版接口的思考模式开关。
     *
     * <p>deepseek-flash / deepseek-chat 默认可能开启思考过程，思考内容同样
     * 计入 max_tokens。三套穿搭的 Prompt 较长时，思考会耗尽全部输出额度，
     * 导致 choices[0].message.content 为空、finish_reason=length。</p>
     *
     * <p>该字段只对 DeepSeek 官方接口填写；其他厂商不传 null 字段，
     * 保持 OpenAI 标准请求体的最大兼容性。</p>
     */
    @SerializedName("thinking")
    private final Thinking thinking;

    public ChatRequest(String model,
                       List<ChatMessage> messages,
                       double temperature,
                       int maxTokens) {
        this(model, messages, temperature, maxTokens, null);
    }

    public ChatRequest(String model,
                       List<ChatMessage> messages,
                       double temperature,
                       int maxTokens,
                       Thinking thinking) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.stream = false;
        this.thinking = thinking;
    }

    /**
     * DeepSeek thinking 参数。传 disabled 表示关闭思考，直接输出正文。
     */
    public static class Thinking {

        @SerializedName("type")
        private final String type;

        private Thinking(String type) {
            this.type = type;
        }

        public static Thinking disabled() {
            return new Thinking("disabled");
        }
    }
}
