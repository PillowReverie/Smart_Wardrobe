package com.example.wardrobe;

import android.app.Application;

import com.example.wardrobe.util.ThemeManager;

/**
 * 应用入口：在 Activity 创建前恢复深色模式选择。
 */
public class WardrobeApplication extends Application {

    /**
     * 进程退出时释放天气模块的后台地名线程。
     */
    @Override
    public void onTerminate() {
        com.example.wardrobe.util.WeatherManager
                .getInstance()
                .shutdown();
        super.onTerminate();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applySavedMode(this);
    }
}
