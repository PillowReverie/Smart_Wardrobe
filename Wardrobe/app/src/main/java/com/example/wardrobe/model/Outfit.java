package com.example.wardrobe.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 已保存的穿搭实体。
 *
 * <p>temperature 使用 Integer 而不是 int，用于区分“没有天气信息”和“0°C”，
 * 避免在搭配库中把未知温度误显示成 0 度。</p>
 */
public class Outfit {

    public static final String SOURCE_TODAY = "TODAY";
    public static final String SOURCE_ITEM = "ITEM";

    private long id;
    private String title = "";
    private String content = "";
    private String source = SOURCE_TODAY;
    private String weatherText = "";
    private Integer temperature;
    private long createdAt;
    private List<ClothingItem> linkedItems = new ArrayList<>();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getWeatherText() {
        return weatherText;
    }

    public void setWeatherText(String weatherText) {
        this.weatherText = weatherText;
    }

    public Integer getTemperature() {
        return temperature;
    }

    public void setTemperature(Integer temperature) {
        this.temperature = temperature;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 搭配关联的衣物。由 WardrobeDao 查询 outfit_items 后回填，
     * 用于 Prompt 中的经典套装描述和搭配库横向图片展示。
     */
    public List<ClothingItem> getLinkedItems() {
        return linkedItems;
    }

    public void setLinkedItems(List<ClothingItem> linkedItems) {
        this.linkedItems = linkedItems == null
                ? new ArrayList<>() : new ArrayList<>(linkedItems);
    }
}
