package com.example.wardrobe.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * OpenAI Chat Completions 消息。
 *
 * <p>请求和响应中的 message 结构相同，因此复用同一个 DTO：
 * {"role":"system|user|assistant","content":"..."}。</p>
 */
public class ChatMessage {

    @SerializedName("role")
    private String role;

    @SerializedName("content")
    private String content;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
