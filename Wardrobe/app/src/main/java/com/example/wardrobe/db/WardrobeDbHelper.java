package com.example.wardrobe.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * 原生 SQLite 数据库入口。
 *
 * <p>只负责数据库文件创建、建表、索引和版本升级，不在这里写业务 SQL。
 * 使用应用级 Context 保存单例，避免 Activity 泄漏，也避免多个页面重复
 * 打开不同的数据库连接。</p>
 */
public class WardrobeDbHelper extends SQLiteOpenHelper {

    private static volatile WardrobeDbHelper instance;

    private WardrobeDbHelper(Context context) {
        super(context.getApplicationContext(),
                WardrobeContract.DATABASE_NAME,
                null,
                WardrobeContract.DATABASE_VERSION);
    }

    /**
     * 获取全局唯一的数据库帮助器。
     */
    public static WardrobeDbHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (WardrobeDbHelper.class) {
                if (instance == null) {
                    instance = new WardrobeDbHelper(context);
                }
            }
        }
        return instance;
    }

    /**
     * 数据导出或导入前关闭全局连接。
     *
     * <p>SQLite 文件不能在有活动连接时直接替换。关闭后把单例置空，
     * 后续页面再次访问数据库时会重新创建连接。调用方应确保此方法
     * 与文件替换操作在后台线程按顺序执行。</p>
     */
    public static synchronized void closeForMaintenance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // SQLite 默认不强制外键，必须在每次连接建立后显式开启。
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(WardrobeContract.SQL_CREATE_ITEMS);
        db.execSQL(WardrobeContract.SQL_CREATE_OUTFITS);
        db.execSQL(WardrobeContract.SQL_CREATE_OUTFIT_ITEMS);
        db.execSQL(WardrobeContract.SQL_CREATE_INDEX_ITEM_CATEGORY);
        db.execSQL(WardrobeContract.SQL_CREATE_INDEX_ITEM_TEMPERATURE);
        db.execSQL(WardrobeContract.SQL_CREATE_INDEX_ITEM_TRAVEL);
        db.execSQL(WardrobeContract.SQL_CREATE_INDEX_OUTFIT_CREATED);
        db.execSQL(WardrobeContract.SQL_CREATE_INDEX_OUTFIT_ITEM);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 按版本逐级迁移，不能删除数据库，否则会丢失用户衣橱和搭配记录。
        if (oldVersion < 2) {
            db.execSQL(WardrobeContract.SQL_ADD_ITEM_AI_DESCRIPTION);
        }
        if (oldVersion < 3) {
            db.execSQL(WardrobeContract.SQL_ADD_ITEM_IS_TRAVEL);
            db.execSQL(WardrobeContract.SQL_CREATE_INDEX_ITEM_TRAVEL);
        }
    }
}
