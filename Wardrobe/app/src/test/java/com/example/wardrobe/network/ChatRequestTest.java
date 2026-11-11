package com.example.wardrobe.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.wardrobe.network.dto.ChatMessage;
import com.example.wardrobe.network.dto.ChatRequest;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.Collections;

/**
 * 验证 DeepSeek thinking 开关只在需要时进入请求体。
 */
public class ChatRequestTest {

    private final Gson gson = new Gson();

    @Test
    public void writesDisabledThinkingForDeepSeek() {
        ChatRequest request = new ChatRequest(
                "deepseek-flash",
                Collections.singletonList(ChatMessage.user("测试")),
                0.9,
                3072,
                ChatRequest.Thinking.disabled());

        String json = gson.toJson(request);

        assertTrue(json.contains("\"thinking\":{\"type\":\"disabled\"}"));
    }

    @Test
    public void omitsThinkingForOtherProviders() {
        ChatRequest request = new ChatRequest(
                "qwen-plus",
                Collections.singletonList(ChatMessage.user("测试")),
                0.9,
                3072);

        String json = gson.toJson(request);

        assertFalse(json.contains("\"thinking\""));
    }
}
