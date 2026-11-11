package com.example.wardrobe.network;

import com.example.wardrobe.network.dto.ChatRequest;
import com.example.wardrobe.network.dto.ChatResponse;
import com.example.wardrobe.network.dto.VisionChatRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Url;

/**
 * Retrofit 的通用大模型 HTTP 接口。
 *
 * <p>@Url 允许每个用户配置不同的 Base URL，不需要在编译期确定厂商域名。</p>
 */
public interface LLMApi {

    @POST
    Call<ChatResponse> createChatCompletion(
            @Url String fullUrl,
            @Header("Authorization") String authorization,
            @Header("Content-Type") String contentType,
            @Body ChatRequest request);

    /**
     * OpenAI Vision 兼容请求。请求体使用 content 数组携带文本和 Base64 图片。
     */
    @POST
    Call<ChatResponse> createVisionChatCompletion(
            @Url String fullUrl,
            @Header("Authorization") String authorization,
            @Header("Content-Type") String contentType,
            @Body VisionChatRequest request);
}
