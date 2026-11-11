package com.example.wardrobe.db;

/**
 * SQLite 数据库契约类。
 *
 * <p>集中存放数据库名、版本、表名、字段名和建表 SQL，避免 DbHelper 与 Dao
 * 各自硬编码字符串导致字段名不一致。当前数据库版本为 3。</p>
 */
public final class WardrobeContract {

    private WardrobeContract() {
        // 纯常量类，禁止实例化。
    }

    public static final String DATABASE_NAME = "wardrobe.db";
    public static final int DATABASE_VERSION = 3;

    public static final String TABLE_ITEMS = "wardrobe_items";
    public static final String TABLE_OUTFITS = "outfits";
    public static final String TABLE_OUTFIT_ITEMS = "outfit_items";

    public static final String COL_ID = "id";
    public static final String COL_NAME = "name";
    public static final String COL_CATEGORY = "category";
    public static final String COL_IMAGE_PATH = "image_path";
    public static final String COL_COLOR = "color";
    public static final String COL_MIN_TEMP = "min_temp";
    public static final String COL_MAX_TEMP = "max_temp";
    public static final String COL_NOTE = "note";
    public static final String COL_AI_DESCRIPTION = "ai_description";
    public static final String COL_IS_TRAVEL = "is_travel";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_UPDATED_AT = "updated_at";

    public static final String COL_TITLE = "title";
    public static final String COL_CONTENT = "content";
    public static final String COL_SOURCE = "source";
    public static final String COL_WEATHER_TEXT = "weather_text";
    public static final String COL_TEMPERATURE = "temperature";

    public static final String COL_OUTFIT_ID = "outfit_id";
    public static final String COL_ITEM_ID = "item_id";
    public static final String COL_SORT_ORDER = "sort_order";

    /**
     * 衣物表。
     *
     * <p>image_path 只保存应用私有目录下的文件名，不保存绝对路径，
     * 避免应用被恢复或迁移后路径失效。color 和 note 可为空。
     * ai_description 是 AI 根据用户资料生成的隐藏描述，只用于 Prompt，
     * 正式版界面不展示。</p>
     */
    public static final String SQL_CREATE_ITEMS =
            "CREATE TABLE " + TABLE_ITEMS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_NAME + " TEXT NOT NULL, " +
                    COL_CATEGORY + " TEXT NOT NULL, " +
                    COL_IMAGE_PATH + " TEXT NOT NULL DEFAULT '', " +
                    COL_COLOR + " TEXT, " +
                    COL_MIN_TEMP + " INTEGER NOT NULL, " +
                    COL_MAX_TEMP + " INTEGER NOT NULL, " +
                    COL_NOTE + " TEXT, " +
                    COL_AI_DESCRIPTION + " TEXT NOT NULL DEFAULT '', " +
                    COL_IS_TRAVEL + " INTEGER NOT NULL DEFAULT 0, " +
                    COL_CREATED_AT + " INTEGER NOT NULL, " +
                    COL_UPDATED_AT + " INTEGER NOT NULL, " +
                    "CHECK (" + COL_MIN_TEMP + " <= " + COL_MAX_TEMP + ")" +
                    ")";

    /**
     * v1 -> v2：新增 AI 隐藏描述字段，旧数据默认空字符串。
     */
    public static final String SQL_ADD_ITEM_AI_DESCRIPTION =
            "ALTER TABLE " + TABLE_ITEMS + " ADD COLUMN " +
                    COL_AI_DESCRIPTION + " TEXT NOT NULL DEFAULT ''";

    /**
     * v2 -> v3：新增旅行衣橱标记，历史衣物全部默认不在旅行箱中。
     */
    public static final String SQL_ADD_ITEM_IS_TRAVEL =
            "ALTER TABLE " + TABLE_ITEMS + " ADD COLUMN " +
                    COL_IS_TRAVEL + " INTEGER NOT NULL DEFAULT 0";

    /**
     * 搭配表。
     *
     * <p>source 用于区分“今日推荐”和“单品拓展”，temperature 允许为空，
     * 因为用户在天气不可用时仍可能保存一条通用搭配。</p>
     */
    public static final String SQL_CREATE_OUTFITS =
            "CREATE TABLE " + TABLE_OUTFITS + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_TITLE + " TEXT NOT NULL, " +
                    COL_CONTENT + " TEXT NOT NULL, " +
                    COL_SOURCE + " TEXT NOT NULL, " +
                    COL_WEATHER_TEXT + " TEXT, " +
                    COL_TEMPERATURE + " INTEGER, " +
                    COL_CREATED_AT + " INTEGER NOT NULL" +
                    ")";

    /**
     * 穿搭与衣物的多对多关联表。
     *
     * <p>不使用逗号拼接 ID，保留排序字段以便稳定展示；删除衣物或穿搭时
     * 通过外键级联清理关联记录。</p>
     */
    public static final String SQL_CREATE_OUTFIT_ITEMS =
            "CREATE TABLE " + TABLE_OUTFIT_ITEMS + " (" +
                    COL_OUTFIT_ID + " INTEGER NOT NULL, " +
                    COL_ITEM_ID + " INTEGER NOT NULL, " +
                    COL_SORT_ORDER + " INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (" + COL_OUTFIT_ID + ", " + COL_ITEM_ID + "), " +
                    "FOREIGN KEY (" + COL_OUTFIT_ID + ") REFERENCES " +
                    TABLE_OUTFITS + "(" + COL_ID + ") ON DELETE CASCADE, " +
                    "FOREIGN KEY (" + COL_ITEM_ID + ") REFERENCES " +
                    TABLE_ITEMS + "(" + COL_ID + ") ON DELETE CASCADE" +
                    ")";

    public static final String SQL_CREATE_INDEX_ITEM_CATEGORY =
            "CREATE INDEX idx_items_category ON " + TABLE_ITEMS +
                    "(" + COL_CATEGORY + ")";

    public static final String SQL_CREATE_INDEX_ITEM_TEMPERATURE =
            "CREATE INDEX idx_items_temperature ON " + TABLE_ITEMS +
                    "(" + COL_MIN_TEMP + ", " + COL_MAX_TEMP + ")";

    public static final String SQL_CREATE_INDEX_ITEM_TRAVEL =
            "CREATE INDEX idx_items_travel ON " + TABLE_ITEMS +
                    "(" + COL_IS_TRAVEL + ", " + COL_CREATED_AT + " DESC)";

    public static final String SQL_CREATE_INDEX_OUTFIT_CREATED =
            "CREATE INDEX idx_outfits_created ON " + TABLE_OUTFITS +
                    "(" + COL_CREATED_AT + " DESC)";

    public static final String SQL_CREATE_INDEX_OUTFIT_ITEM =
            "CREATE INDEX idx_outfit_items_item ON " + TABLE_OUTFIT_ITEMS +
                    "(" + COL_ITEM_ID + ")";
}
