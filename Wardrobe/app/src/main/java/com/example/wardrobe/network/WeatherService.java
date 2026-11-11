package com.example.wardrobe.network;

import com.example.wardrobe.network.dto.WeatherResponse;
import com.example.wardrobe.network.dto.DailyWeatherResponse;
import com.example.wardrobe.network.dto.WeatherGeoResponse;
import com.example.wardrobe.network.dto.WeeklyWeatherResponse;
import com.example.wardrobe.util.ApiConfig;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Url;

/**
 * 和风天气接口定义。
 *
 * <p>使用 @Url 接收运行时拼接的完整地址，因为 Host 和 Key 都由用户在设置页
 * 配置，不能写死在 Retrofit 的 baseUrl 中。</p>
 */
public interface WeatherService {

    @GET
    Call<WeatherResponse> getCurrentWeather(
            @Url String fullUrl,
            @Header(ApiConfig.WEATHER_API_KEY_HEADER) String apiKey);

    /**
     * 获取每日预报，首页优先展示全天最低/最高温和天气现象。
     */
    @GET
    Call<DailyWeatherResponse> getDailyWeather(
            @Url String fullUrl,
            @Header(ApiConfig.WEATHER_API_KEY_HEADER) String apiKey);

    /**
     * 通过经纬度查询城市/区县名称。完整 URL 由 WeatherManager 动态拼接，
     * 其中 location 参数必须保持“经度在前、纬度在后”。
     */
    @GET
    Call<WeatherGeoResponse> lookupCity(
            @Url String fullUrl,
            @Header(ApiConfig.WEATHER_API_KEY_HEADER) String apiKey);

    /**
     * 目的地七天预报。location 使用 GeoAPI 返回的 Location ID。
     */
    @GET
    Call<WeeklyWeatherResponse> getWeeklyWeather(
            @Url String fullUrl,
            @Header(ApiConfig.WEATHER_API_KEY_HEADER) String apiKey);
}
