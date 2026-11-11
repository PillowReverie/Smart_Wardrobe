package com.example.wardrobe.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.wardrobe.network.dto.ChatRequest;
import com.example.wardrobe.network.dto.VisionChatMessage;
import com.example.wardrobe.network.dto.VisionChatRequest;
import com.example.wardrobe.network.dto.VisionContentPart;
import com.google.gson.Gson;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * 验证视觉请求符合 OpenAI Vision 兼容格式。
 */
public class VisionChatRequestTest {

    private final Gson gson = new Gson();

    @Test
    public void serializesImagePartAndDeepSeekThinkingFlag() {
        VisionChatRequest request = new VisionChatRequest(
                "deepseek-flash",
                Collections.singletonList(new VisionChatMessage(
                        "user",
                        Arrays.asList(
                                VisionContentPart.text("识别衣物"),
                                VisionContentPart.image(
                                        "data:image/jpeg;base64,AAAA")))),
                0.1,
                300,
                ChatRequest.Thinking.disabled());

        String json = gson.toJson(request);

        assertTrue(json.contains("\"type\":\"image_url\""));
        assertTrue(json.contains(
                "\"url\":\"data:image/jpeg;base64,AAAA\""));
        assertTrue(json.contains("\"thinking\":{\"type\":\"disabled\"}"));
    }

    @Test
    public void omitsThinkingForOtherVisionProviders() {
        VisionChatRequest request = new VisionChatRequest(
                "qwen-vl-plus",
                Collections.singletonList(new VisionChatMessage(
                        "user",
                        Collections.singletonList(
                                VisionContentPart.text("识别衣物")))),
                0.1,
                300,
                null);

        String json = gson.toJson(request);

        assertFalse(json.contains("\"thinking\""));
    }
}
