package com.example.wardrobe.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import com.example.wardrobe.model.ClothingItem;
import com.example.wardrobe.model.Outfit;

import java.util.ArrayList;
import java.util.List;

/**
 * 衣橱数据访问对象。
 *
 * <p>所有 SQL 都集中在这里，Activity 不直接拼 SQL。网络请求必须异步，
 * 本地 SQLite 数据量很小，因此在调用线程直接执行查询；后续如果数据量
 * 变大，可以在这里统一切换到单线程执行器，而不影响页面代码。</p>
 */
public class WardrobeDao {

    private final WardrobeDbHelper dbHelper;

    public WardrobeDao(Context context) {
        this.dbHelper = WardrobeDbHelper.getInstance(context);
    }

    /* ------------------------------ 衣物 CRUD ------------------------------ */

    /**
     * 新增衣物，并回填数据库生成的自增 ID。
     */
    public long insertItem(ClothingItem item) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long now = System.currentTimeMillis();
        if (item.getCreatedAt() <= 0) {
            item.setCreatedAt(now);
        }
        item.setUpdatedAt(now);

        ContentValues values = itemToValues(item);
        long id = db.insertOrThrow(WardrobeContract.TABLE_ITEMS, null, values);
        item.setId(id);
        return id;
    }

    /**
     * 更新衣物。created_at 不会被覆盖。
     */
    public int updateItem(ClothingItem item) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        item.setUpdatedAt(System.currentTimeMillis());
        ContentValues values = itemToValues(item);
        values.remove(WardrobeContract.COL_CREATED_AT);
        return db.update(
                WardrobeContract.TABLE_ITEMS,
                values,
                WardrobeContract.COL_ID + " = ?",
                new String[]{String.valueOf(item.getId())}
        );
    }

    /**
     * 删除衣物。outfit_items 中的关联记录会由外键自动级联删除。
     */
    public int deleteItem(long itemId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(
                WardrobeContract.TABLE_ITEMS,
                WardrobeContract.COL_ID + " = ?",
                new String[]{String.valueOf(itemId)}
        );
    }

    public ClothingItem getItemById(long itemId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(
                WardrobeContract.TABLE_ITEMS,
                null,
                WardrobeContract.COL_ID + " = ?",
                new String[]{String.valueOf(itemId)},
                null,
                null,
                null
        )) {
            if (cursor.moveToFirst()) {
                return readItem(cursor);
            }
        }
        return null;
    }

    public List<ClothingItem> getAllItems() {
        return queryItems(null, null);
    }

    /**
     * 查询所有已加入旅行衣橱的衣物。
     */
    public List<ClothingItem> getTravelItems() {
        return queryItems(
                WardrobeContract.COL_IS_TRAVEL + " = 1",
                null
        );
    }

    /**
     * 按类别查询；category 为空或“全部”时返回所有衣物。
     */
    public List<ClothingItem> getItemsByCategory(String category) {
        if (category == null || category.trim().isEmpty() || "全部".equals(category)) {
            return getAllItems();
        }
        return queryItems(
                WardrobeContract.COL_CATEGORY + " = ?",
                new String[]{category}
        );
    }

    /**
     * 按类别和关键词组合搜索衣物。
     *
     * <p>关键词同时匹配名称与颜色/风格。LIKE 中的 %、_ 和反斜杠会被转义，
     * 这样用户输入的符号不会被误认为 SQL 通配符。category 为 null 时表示
     * “全部”类别；keyword 为空时等价于只按类别筛选。</p>
     */
    public List<ClothingItem> searchItems(String category, String keyword) {
        return searchItems(category, keyword, false);
    }

    /**
     * 按类别、关键词和旅行状态组合查询。
     *
     * @param travelOnly true 时只查询 is_travel = 1
     */
    public List<ClothingItem> searchItems(String category,
                                          String keyword,
                                          boolean travelOnly) {
        String normalizedCategory = category == null ? "" : category.trim();
        if ("全部".equals(normalizedCategory)) {
            normalizedCategory = "";
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim();

        String selection = null;
        List<String> args = new ArrayList<>();
        if (!normalizedKeyword.isEmpty()) {
            String escaped = escapeLikeKeyword(normalizedKeyword);
            String likeValue = "%" + escaped + "%";
            selection = "(" + WardrobeContract.COL_NAME
                    + " LIKE ? ESCAPE '\\' OR "
                    + WardrobeContract.COL_COLOR
                    + " LIKE ? ESCAPE '\\')";
            args.add(likeValue);
            args.add(likeValue);
        }
        if (!normalizedCategory.isEmpty()) {
            if (selection == null) {
                selection = WardrobeContract.COL_CATEGORY + " = ?";
            } else {
                selection += " AND " + WardrobeContract.COL_CATEGORY + " = ?";
            }
            args.add(normalizedCategory);
        }
        if (travelOnly) {
            String travelSelection = WardrobeContract.COL_IS_TRAVEL + " = 1";
            selection = selection == null
                    ? travelSelection
                    : selection + " AND " + travelSelection;
        }

        return queryItems(
                selection,
                args.isEmpty() ? null : args.toArray(new String[0])
        );
    }

    public int getItemCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return (int) DatabaseUtils.queryNumEntries(db, WardrobeContract.TABLE_ITEMS);
    }

    /**
     * 原子更新单件衣物的旅行状态，并同步 updated_at 供 DiffUtil 刷新。
     *
     * @return 受影响行数；0 表示衣物已不存在
     */
    public int setItemTravel(long itemId, boolean travel) {
        if (itemId <= 0) {
            return 0;
        }
        ContentValues values = new ContentValues();
        values.put(WardrobeContract.COL_IS_TRAVEL, travel ? 1 : 0);
        values.put(WardrobeContract.COL_UPDATED_AT,
                System.currentTimeMillis());
        return dbHelper.getWritableDatabase().update(
                WardrobeContract.TABLE_ITEMS,
                values,
                WardrobeContract.COL_ID + " = ?",
                new String[]{String.valueOf(itemId)}
        );
    }

    /**
     * 清空旅行衣橱只重置标记，不删除任何主衣橱衣物。
     */
    public int clearTravelItems() {
        ContentValues values = new ContentValues();
        values.put(WardrobeContract.COL_IS_TRAVEL, 0);
        values.put(WardrobeContract.COL_UPDATED_AT,
                System.currentTimeMillis());
        return dbHelper.getWritableDatabase().update(
                WardrobeContract.TABLE_ITEMS,
                values,
                WardrobeContract.COL_IS_TRAVEL + " = 1",
                null
        );
    }

    /* ------------------------------ 搭配 CRUD ------------------------------ */

    /**
     * 保存穿搭及其关联衣物。
     *
     * <p>搭配主表和关联表必须一次性成功，所以使用事务；任何一步失败都会
     * 回滚，避免出现“有搭配记录但没有衣物关联”的半成品数据。</p>
     */
    public long insertOutfit(Outfit outfit, List<Long> itemIds) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            if (outfit.getCreatedAt() <= 0) {
                outfit.setCreatedAt(now);
            }

            ContentValues values = new ContentValues();
            values.put(WardrobeContract.COL_TITLE, safeText(outfit.getTitle()));
            values.put(WardrobeContract.COL_CONTENT, safeText(outfit.getContent()));
            values.put(WardrobeContract.COL_SOURCE, safeText(outfit.getSource()));
            values.put(WardrobeContract.COL_WEATHER_TEXT, outfit.getWeatherText());
            if (outfit.getTemperature() == null) {
                values.putNull(WardrobeContract.COL_TEMPERATURE);
            } else {
                values.put(WardrobeContract.COL_TEMPERATURE, outfit.getTemperature());
            }
            values.put(WardrobeContract.COL_CREATED_AT, outfit.getCreatedAt());

            long outfitId = db.insertOrThrow(
                    WardrobeContract.TABLE_OUTFITS,
                    null,
                    values
            );
            outfit.setId(outfitId);

            if (itemIds != null) {
                int sortOrder = 0;
                for (Long itemId : itemIds) {
                    if (itemId == null || itemId <= 0) {
                        continue;
                    }
                    ContentValues relation = new ContentValues();
                    relation.put(WardrobeContract.COL_OUTFIT_ID, outfitId);
                    relation.put(WardrobeContract.COL_ITEM_ID, itemId);
                    relation.put(WardrobeContract.COL_SORT_ORDER, sortOrder++);
                    // 同一件衣物被 Prompt 重复带入时只保留一条关联记录。
                    db.insertWithOnConflict(
                            WardrobeContract.TABLE_OUTFIT_ITEMS,
                            null,
                            relation,
                            SQLiteDatabase.CONFLICT_IGNORE
                    );
                }
            }

            db.setTransactionSuccessful();
            return outfitId;
        } finally {
            db.endTransaction();
        }
    }

    public List<Outfit> getAllOutfits() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Outfit> outfits = new ArrayList<>();
        try (Cursor cursor = db.query(
                WardrobeContract.TABLE_OUTFITS,
                null,
                null,
                null,
                null,
                null,
                WardrobeContract.COL_CREATED_AT + " DESC"
        )) {
            while (cursor.moveToNext()) {
                Outfit outfit = new Outfit();
                outfit.setId(cursor.getLong(
                        cursor.getColumnIndexOrThrow(WardrobeContract.COL_ID)));
                outfit.setTitle(cursor.getString(
                        cursor.getColumnIndexOrThrow(WardrobeContract.COL_TITLE)));
                outfit.setContent(cursor.getString(
                        cursor.getColumnIndexOrThrow(WardrobeContract.COL_CONTENT)));
                outfit.setSource(cursor.getString(
                        cursor.getColumnIndexOrThrow(WardrobeContract.COL_SOURCE)));
                outfit.setWeatherText(cursor.getString(
                        cursor.getColumnIndexOrThrow(WardrobeContract.COL_WEATHER_TEXT)));

                int temperatureIndex = cursor.getColumnIndexOrThrow(
                        WardrobeContract.COL_TEMPERATURE);
                outfit.setTemperature(cursor.isNull(temperatureIndex)
                        ? null : cursor.getInt(temperatureIndex));
                outfit.setCreatedAt(cursor.getLong(
                        cursor.getColumnIndexOrThrow(WardrobeContract.COL_CREATED_AT)));
                outfits.add(outfit);
            }
        }
        return outfits;
    }

    /**
     * 查询搭配并回填关联衣物，供首页经典套装 Prompt 和搭配库图片列表使用。
     *
     * <p>搭配数量通常不大，这里采用“主表一次查询 + 每套关联查询”的方式，
     * 保持实现直观；如果未来搭配数量明显增长，可以再改成一次 JOIN 查询。</p>
     */
    public List<Outfit> getAllOutfitsWithItems() {
        List<Outfit> outfits = getAllOutfits();
        for (Outfit outfit : outfits) {
            outfit.setLinkedItems(getItemsForOutfit(outfit.getId()));
        }
        return outfits;
    }

    /**
     * 查询一套搭配关联的衣物，按保存时的顺序返回。
     */
    public List<ClothingItem> getItemsForOutfit(long outfitId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT i.* FROM " + WardrobeContract.TABLE_ITEMS + " i " +
                "INNER JOIN " + WardrobeContract.TABLE_OUTFIT_ITEMS + " oi " +
                "ON i." + WardrobeContract.COL_ID + " = oi." + WardrobeContract.COL_ITEM_ID + " " +
                "WHERE oi." + WardrobeContract.COL_OUTFIT_ID + " = ? " +
                "ORDER BY oi." + WardrobeContract.COL_SORT_ORDER + " ASC";

        List<ClothingItem> items = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, new String[]{String.valueOf(outfitId)})) {
            while (cursor.moveToNext()) {
                items.add(readItem(cursor));
            }
        }
        return items;
    }

    public int deleteOutfit(long outfitId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(
                WardrobeContract.TABLE_OUTFITS,
                WardrobeContract.COL_ID + " = ?",
                new String[]{String.valueOf(outfitId)}
        );
    }

    public int getOutfitCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return (int) DatabaseUtils.queryNumEntries(db, WardrobeContract.TABLE_OUTFITS);
    }

    /* ------------------------------ 数据库自检 ------------------------------ */

    /**
     * 检查三张业务表是否都已创建。About 页面用它做第一阶段验收。
     */
    public boolean isSchemaReady() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT COUNT(*) FROM sqlite_master " +
                "WHERE type = 'table' AND name IN (?, ?, ?)";
        try (Cursor cursor = db.rawQuery(sql, new String[]{
                WardrobeContract.TABLE_ITEMS,
                WardrobeContract.TABLE_OUTFITS,
                WardrobeContract.TABLE_OUTFIT_ITEMS
        })) {
            return cursor.moveToFirst() && cursor.getInt(0) == 3;
        }
    }

    public int getDatabaseVersion() {
        return dbHelper.getReadableDatabase().getVersion();
    }

    /* ------------------------------ 内部工具 ------------------------------ */

    private List<ClothingItem> queryItems(String selection, String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<ClothingItem> items = new ArrayList<>();
        try (Cursor cursor = db.query(
                WardrobeContract.TABLE_ITEMS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                WardrobeContract.COL_CREATED_AT + " DESC"
        )) {
            while (cursor.moveToNext()) {
                items.add(readItem(cursor));
            }
        }
        return items;
    }

    /**
     * 转义 SQLite LIKE 中具有特殊含义的字符。
     *
     * <p>先替换反斜杠本身，再替换 % 和 _，避免出现二次转义问题。</p>
     */
    private String escapeLikeKeyword(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private ClothingItem readItem(Cursor cursor) {
        ClothingItem item = new ClothingItem();
        item.setId(cursor.getLong(
                cursor.getColumnIndexOrThrow(WardrobeContract.COL_ID)));
        item.setName(cursor.getString(
                cursor.getColumnIndexOrThrow(WardrobeContract.COL_NAME)));
        item.setCategory(cursor.getString(
                cursor.getColumnIndexOrThrow(WardrobeContract.COL_CATEGORY)));
        item.setImagePath(cursor.getString(
                cursor.getColumnIndexOrThrow(WardrobeContract.COL_IMAGE_PATH)));
        item.setColor(cursor.getString(
                cursor.getColumnIndexOrThrow(WardrobeContract.COL_COLOR)));
        item.setMinTemp(cursor.getInt(
                cursor.getColumnIndexOrThrow(WardrobeContract.COL_MIN_TEMP)));
        item.setMaxTemp(cursor.getInt(
                cursor.getColumnIndexOrThrow(WardrobeContract.COL_MAX_TEMP)));
        item.setNote(cursor.getString(
                cursor.getColumnIndexOrThrow(WardrobeContract.COL_NOTE)));
        item.setAiDescription(cursor.getString(
                cursor.getColumnIndexOrThrow(
                        WardrobeContract.COL_AI_DESCRIPTION)));
        item.setTravel(cursor.getInt(
                cursor.getColumnIndexOrThrow(
                        WardrobeContract.COL_IS_TRAVEL)) == 1);
        item.setCreatedAt(cursor.getLong(
                cursor.getColumnIndexOrThrow(WardrobeContract.COL_CREATED_AT)));
        item.setUpdatedAt(cursor.getLong(
                cursor.getColumnIndexOrThrow(WardrobeContract.COL_UPDATED_AT)));
        return item;
    }

    private ContentValues itemToValues(ClothingItem item) {
        ContentValues values = new ContentValues();
        values.put(WardrobeContract.COL_NAME, safeText(item.getName()));
        values.put(WardrobeContract.COL_CATEGORY, safeText(item.getCategory()));
        values.put(WardrobeContract.COL_IMAGE_PATH, safeText(item.getImagePath()));
        values.put(WardrobeContract.COL_COLOR, item.getColor());
        values.put(WardrobeContract.COL_MIN_TEMP, item.getMinTemp());
        values.put(WardrobeContract.COL_MAX_TEMP, item.getMaxTemp());
        values.put(WardrobeContract.COL_NOTE, item.getNote());
        values.put(WardrobeContract.COL_AI_DESCRIPTION,
                safeText(item.getAiDescription()));
        values.put(WardrobeContract.COL_IS_TRAVEL,
                item.isTravel() ? 1 : 0);
        values.put(WardrobeContract.COL_CREATED_AT, item.getCreatedAt());
        values.put(WardrobeContract.COL_UPDATED_AT, item.getUpdatedAt());
        return values;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
