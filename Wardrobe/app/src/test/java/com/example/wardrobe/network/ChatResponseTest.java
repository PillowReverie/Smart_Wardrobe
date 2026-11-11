package com.example.wardrobe.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.wardrobe.network.dto.ChatResponse;
import com.google.gson.Gson;

import org.junit.Test;

/**
 * 验证不同 OpenAI 兼容服务的响应结构。
 *
 * <p>只允许读取 choices[0].message.content；reasoning_content 和
 * reasoning/thought 类型片段必须忽略，避免把模型内部思考展示给用户。</p>
 */
public class ChatResponseTest {

    private final Gson gson = new Gson();

    @Test
    public void readsStandardStringContent() {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"标准正文\"},\"finish_reason\":\"stop\"}]}";

        ChatResponse response = gson.fromJson(json, ChatResponse.class);

        assertEquals("标准正文", response.getFirstContent());
        assertEquals("stop", response.getFinishReason());
    }

    @Test
    public void ignoresReasoningContent() {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"\",\"reasoning_content\":\"推理模型正文\"},"
                + "\"finish_reason\":\"stop\"}]}";

        ChatResponse response = gson.fromJson(json, ChatResponse.class);

        assertEquals("", response.getFirstContent());
    }

    @Test
    public void joinsArrayContentParts() {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"第一段\"},"
                + "{\"type\":\"text\",\"text\":\"第二段\"}]}}]}";

        ChatResponse response = gson.fromJson(json, ChatResponse.class);

        String content = response.getFirstContent();
        assertTrue(content.contains("第一段"));
        assertTrue(content.contains("第二段"));
    }

    @Test
    public void skipsReasoningContentParts() {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":[{\"type\":\"reasoning\",\"text\":\"内部思考\"},"
                + "{\"type\":\"text\",\"text\":\"最终正文\"}]}}]}";

        ChatResponse response = gson.fromJson(json, ChatResponse.class);

        assertEquals("最终正文", response.getFirstContent());
    }

    @Test
    public void exposesServerErrorMessage() {
        String json = "{\"error\":{\"message\":\"invalid model\"}}";

        ChatResponse response = gson.fromJson(json, ChatResponse.class);

        assertEquals("", response.getFirstContent());
        assertEquals("invalid model", response.getErrorMessage());
    }

    @Test
    public void exposesTruncatedReasoningDiagnostics() {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"\",\"reasoning_content\":\"很长的内部思考\"},"
                + "\"finish_reason\":\"length\"}],"
                + "\"usage\":{\"completion_tokens\":3072,"
                + "\"completion_tokens_details\":{\"reasoning_tokens\":3072}}}";

        ChatResponse response = gson.fromJson(json, ChatResponse.class);

        assertEquals("", response.getFirstContent());
        assertEquals("length", response.getFinishReason());
        assertEquals(7, response.getReasoningLength());
        assertEquals("completion=3072，reasoning=3072",
                response.getUsageSummary());
    }
}
