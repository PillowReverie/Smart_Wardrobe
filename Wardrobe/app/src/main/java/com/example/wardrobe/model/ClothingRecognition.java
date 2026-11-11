package com.example.wardrobe.model;

/**
 * 大模型视觉识别出的衣物信息。
 */
public class ClothingRecognition {

    private String name;
    private String category;
    private String color;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
