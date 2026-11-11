package com.example.wardrobe.network.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Vision content 数组中的单个片段：文本或图片 URL。
 */
public class VisionContentPart {

    @SerializedName("type")
    private final String type;

    @SerializedName("text")
    private final String text;

    @SerializedName("image_url")
    private final ImageUrl imageUrl;

    private VisionContentPart(String type, String text, ImageUrl imageUrl) {
        this.type = type;
        this.text = text;
        this.imageUrl = imageUrl;
    }

    public static VisionContentPart text(String text) {
        return new VisionContentPart("text", text, null);
    }

    public static VisionContentPart image(String dataUrl) {
        return new VisionContentPart(
                "image_url", null, new ImageUrl(dataUrl));
    }

    public static class ImageUrl {

        @SerializedName("url")
        private final String url;

        private ImageUrl(String url) {
            this.url = url;
        }
    }
}
