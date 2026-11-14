package com.example.wardrobe.network;

import com.example.wardrobe.network.dto.OpenMeteoForecastResponse;
import com.example.wardrobe.network.dto.OpenMeteoGeocodingResponse;
import com.example.wardrobe.network.dto.OpenMeteoWeatherResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Url;

/**
 * Open-Meteo 免费天气与城市搜索接口。
 *
 * <p>三个接口均使用完整 URL，避免把服务域名写死在 Retrofit 实例中。
 * Open-Meteo 不要求 API Key，也不使用常见的 Authorization 请求头。</p>
 */
public interface OpenMeteoService {

    @GET
    Call<OpenMeteoForecastResponse> getDailyForecast(@Url String fullUrl);

    @GET
    Call<OpenMeteoWeatherResponse> getCurrentWeather(@Url String fullUrl);

    @GET
    Call<OpenMeteoGeocodingResponse> searchCity(@Url String fullUrl);
}
