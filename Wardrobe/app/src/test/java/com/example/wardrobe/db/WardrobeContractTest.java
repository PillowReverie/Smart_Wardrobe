package com.example.wardrobe.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 锁定 v3 数据库契约，防止后续调整建表语句时漏掉旅行衣橱字段。
 */
public class WardrobeContractTest {

    @Test
    public void databaseVersionAndTravelColumnAreCurrent() {
        assertEquals(3, WardrobeContract.DATABASE_VERSION);
        assertTrue(WardrobeContract.SQL_CREATE_ITEMS.contains(
                WardrobeContract.COL_IS_TRAVEL + " INTEGER NOT NULL DEFAULT 0"));
        assertTrue(WardrobeContract.SQL_ADD_ITEM_IS_TRAVEL.contains(
                "ADD COLUMN " + WardrobeContract.COL_IS_TRAVEL));
    }
}
