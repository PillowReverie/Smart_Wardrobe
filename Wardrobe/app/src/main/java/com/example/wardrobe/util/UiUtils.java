package com.example.wardrobe.util;

import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;
import android.view.Window;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 页面通用工具。
 *
 * <p>集中处理边到边布局的系统栏间距、Snackbar、日期和温度文本，
 * 避免六个 Activity 重复写相同逻辑。</p>
 */
public final class UiUtils {

    private UiUtils() {
        // 工具类禁止实例化。
    }

    /**
     * 全屏沉浸式入口：透明状态栏/导航栏，并按日间/夜间模式设置图标明暗。
     *
     * <p>页面根布局不再绘制白色 App Bar，Toolbar 背景透明，因此状态栏、
     * Toolbar 和内容区会共享同一层浅紫/深色背景，视觉上保持连续。</p>
     */
    public static void enableImmersiveMode(ComponentActivity activity) {
        EdgeToEdge.enable(activity);
        Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        int nightMode = activity.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        boolean useDarkIcons = nightMode != Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(useDarkIcons);
        controller.setAppearanceLightNavigationBars(useDarkIcons);
    }

    /**
     * 为根布局增加状态栏、导航栏和刘海区域的安全间距。
     *
     * <p>根布局可能本身已有 padding，因此先记录初始值，再在初始值基础上
     * 叠加系统栏尺寸，避免重复调用时越加越大。</p>
     */
    public static void applySystemBarInsets(View root) {
        final int initialLeft = root.getPaddingLeft();
        final int initialTop = root.getPaddingTop();
        final int initialRight = root.getPaddingRight();
        final int initialBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    initialLeft + insets.left,
                    initialTop + insets.top,
                    initialRight + insets.right,
                    initialBottom + insets.bottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    public static void showMessage(View anchor, CharSequence message) {
        Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT).show();
    }

    public static String formatDateTime(long timeMillis) {
        SimpleDateFormat formatter = new SimpleDateFormat(
                "yyyy年MM月dd日 HH:mm", Locale.CHINA);
        return formatter.format(new Date(timeMillis));
    }

    public static String formatMonthDay(long timeMillis) {
        SimpleDateFormat formatter = new SimpleDateFormat(
                "MM月dd日", Locale.CHINA);
        return formatter.format(new Date(timeMillis));
    }

}
