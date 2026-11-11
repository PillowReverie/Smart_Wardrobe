package com.example.wardrobe.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Vision 消息。content 为文本和图片片段数组。
 */
public class VisionChatMessage {

    @SerializedName("role")
    private final String role;

    @SerializedName("content")
    private final List<VisionContentPart> content;

    public VisionChatMessage(String role, List<VisionContentPart> content) {
        this.role = role;
        this.content = content;
    }
}
