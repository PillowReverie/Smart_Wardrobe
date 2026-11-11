package com.example.wardrobe.model;

/**
 * 大模型解析出的旅行意图。
 */
public final class TravelIntent {

    private final String city;
    private final String startDate;
    private final int days;

    public TravelIntent(String city, String startDate, int days) {
        this.city = city;
        this.startDate = startDate;
        this.days = days;
    }

    public String getCity() {
        return city;
    }

    public String getStartDate() {
        return startDate;
    }

    public int getDays() {
        return days;
    }
}
