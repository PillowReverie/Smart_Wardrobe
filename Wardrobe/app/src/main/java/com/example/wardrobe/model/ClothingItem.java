package com.example.wardrobe.model;

/**
 * 衣物实体。
 *
 * <p>字段与 wardrobe_items 表一一对应。imagePath 保存的是私有图片文件名，
 * 不是完整绝对路径；显示图片时由 ImageStore 拼出真实文件。</p>
 */
public class ClothingItem {

    /**
     * 温度字段为兼容旧数据库保留。当前版本不再要求用户填写温度，
     * 新衣物统一写入一个宽区间，实际是否适合由 AI 根据天气判断。
     */
    public static final int DEFAULT_MIN_TEMP = 0;
    public static final int DEFAULT_MAX_TEMP = 40;

    private long id;
    private String name = "";
    private String category = "";
    private String imagePath = "";
    private String color = "";
    private int minTemp;
    private int maxTemp;
    private String note = "";
    private String aiDescription = "";
    private boolean travel;
    private long createdAt;
    private long updatedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

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

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getMinTemp() {
        return minTemp;
    }

    public void setMinTemp(int minTemp) {
        this.minTemp = minTemp;
    }

    public int getMaxTemp() {
        return maxTemp;
    }

    public void setMaxTemp(int maxTemp) {
        this.maxTemp = maxTemp;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /**
     * AI 生成的隐藏服装描述，只写入 Prompt，不作为正式版普通界面字段。
     */
    public String getAiDescription() {
        return aiDescription;
    }

    public void setAiDescription(String aiDescription) {
        this.aiDescription = aiDescription == null ? "" : aiDescription;
    }

    /**
     * true 表示衣物已经放入旅行衣橱。该状态持久化到 SQLite，
     * 首页、衣橱页和旅行打包结果共享同一份数据。
     */
    public boolean isTravel() {
        return travel;
    }

    public void setTravel(boolean travel) {
        this.travel = travel;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

}
