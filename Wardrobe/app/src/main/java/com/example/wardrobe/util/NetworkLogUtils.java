package com.example.wardrobe.util;

import android.util.Log;

import java.io.IOException;

import retrofit2.Response;

/**
 * 网络层统一日志工具，避免各个 Service 重复实现 ErrorBody 读取。
 */
public final class NetworkLogUtils {

    private NetworkLogUtils() {
    }

    public static void logHttpError(String tag,
                                    String apiName,
                                    Response<?> response) {
        String errorBody = "";
        if (response.errorBody() != null) {
            try {
                errorBody = response.errorBody().string();
            } catch (IOException exception) {
                errorBody = "<读取 ErrorBody 失败>";
            }
        }
        Log.e(tag, apiName + " HTTP 错误：code=" + response.code()
                + "，message=" + response.message()
                + "，errorBody=" + errorBody);
    }
}
