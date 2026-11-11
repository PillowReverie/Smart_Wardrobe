package com.example.wardrobe.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * 网络状态检查工具。
 *
 * <p>点击刷新天气或生成 AI 穿搭前先做一次轻量级检查，
 * 断网时直接提示用户，避免发起注定失败的网络请求。</p>
 */
public final class NetworkUtils {

    private NetworkUtils() {
        // 工具类禁止实例化。
    }

    public static boolean isNetworkAvailable(Context context) {
        if (context == null) {
            return false;
        }
        ConnectivityManager manager = (ConnectivityManager)
                context.getApplicationContext()
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities =
                manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
