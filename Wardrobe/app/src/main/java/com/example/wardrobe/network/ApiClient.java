package com.example.wardrobe.network;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 天气 Retrofit 客户端。
 *
 * <p>请求地址在接口方法中通过 @Url 完整传入，这里的 baseUrl 只是 Retrofit
 * 要求的占位地址。OkHttp 同时配置连接、读取和整体调用超时，避免页面一直
 * 停留在加载状态。</p>
 */
public final class ApiClient {

    private static volatile WeatherService weatherService;
    private static volatile OpenMeteoService openMeteoService;
    private static volatile LLMApi llmApi;

    private ApiClient() {
        // 工具类禁止实例化。
    }

    public static WeatherService getWeatherService() {
        if (weatherService == null) {
            synchronized (ApiClient.class) {
                if (weatherService == null) {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .callTimeout(45, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build();

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl("https://weather-placeholder.invalid/")
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();

                    weatherService = retrofit.create(WeatherService.class);
                }
            }
        }
        return weatherService;
    }

    /**
     * Open-Meteo 使用独立的免费天气客户端，不发送任何密钥请求头。
     */
    public static OpenMeteoService getOpenMeteoService() {
        if (openMeteoService == null) {
            synchronized (ApiClient.class) {
                if (openMeteoService == null) {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(20, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .callTimeout(30, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build();

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl("https://open-meteo-placeholder.invalid/")
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();

                    openMeteoService = retrofit.create(OpenMeteoService.class);
                }
            }
        }
        return openMeteoService;
    }

    /**
     * 大模型使用独立连接池。生成三套长文本时读取超时设为 60 秒，
     * 避免服务端仍在生成时客户端过早断开。
     */
    public static LLMApi getLlmApi() {
        if (llmApi == null) {
            synchronized (ApiClient.class) {
                if (llmApi == null) {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .callTimeout(75, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build();

                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl("https://llm-placeholder.invalid/")
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build();

                    llmApi = retrofit.create(LLMApi.class);
                }
            }
        }
        return llmApi;
    }
}
