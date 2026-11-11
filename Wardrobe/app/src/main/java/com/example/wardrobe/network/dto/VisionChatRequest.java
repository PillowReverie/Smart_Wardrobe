package com.example.wardrobe.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * OpenAI Vision 兼容请求体。
 *
 * <p>与普通 ChatRequest 的区别只在 messages[].content：视觉请求使用
 * 数组同时承载文本和图片。response 仍复用 ChatResponse。</p>
 */
public class VisionChatRequest {

    @SerializedName("model")
    private final String model;

    @SerializedName("messages")
    private final List<VisionChatMessage> messages;

    @SerializedName("temperature")
    private final double temperature;

    @SerializedName("max_tokens")
    private final int maxTokens;

    @SerializedName("stream")
    private final boolean stream;

    @SerializedName("thinking")
    private final ChatRequest.Thinking thinking;

    public VisionChatRequest(String model,
                             List<VisionChatMessage> messages,
                             double temperature,
                             int maxTokens,
                             ChatRequest.Thinking thinking) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.stream = false;
        this.thinking = thinking;
    }
}
