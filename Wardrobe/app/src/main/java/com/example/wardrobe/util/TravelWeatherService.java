package com.example.wardrobe.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.example.wardrobe.R;
import com.example.wardrobe.network.ApiClient;
import com.example.wardrobe.network.dto.DailyWeatherResponse;
import com.example.wardrobe.network.dto.WeatherGeoResponse;
import com.example.wardrobe.network.dto.WeeklyWeatherResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

/**
 * 异地游玩天气服务。
 *
 * <p>先通过 GeoAPI 把城市名解析成 Location ID，再请求七天预报。
 * 如果当前 Host 不支持 v7 七天接口，则回退到项目已有的按经纬度
 * 每日预报接口，保证旅行模式仍能拿到目的地天气。</p>
 */
public final class TravelWeatherService {

    private static final String TAG = "TravelWeatherService";
    private static Call<WeatherGeoResponse> geoCall;
    private static Call<WeeklyWeatherResponse> weeklyCall;
    private static Call<DailyWeatherResponse> dailyCall;

    public interface Callback {
        void onSuccess(String cityName, String weatherSummary);

        void onError(String message);
    }

    private TravelWeatherService() {
    }

    public static void fetch(Context context,
                             String city,
                             Callback callback) {
        Context appContext = context.getApplicationContext();
        if (!ApiConfig.isWeatherConfigured(appContext)) {
            callback.onError(appContext.getString(R.string.weather_not_configured));
            return;
        }
        String host = ApiConfig.getWeatherBaseUrl(appContext);
        String apiKey = ApiConfig.getWeatherApiKey(appContext);
        String url = host + ApiConfig.WEATHER_GEO_CITY_PATH
                + "?location=" + Uri.encode(city);

        cancel();
        try {
            geoCall = ApiClient.getWeatherService().lookupCity(url, apiKey);
        } catch (Exception exception) {
            Log.e(TAG, "城市搜索请求创建失败", exception);
            callback.onError(appContext.getString(R.string.travel_geo_failed));
            return;
        }
        geoCall.enqueue(new retrofit2.Callback<WeatherGeoResponse>() {
            @Override
            public void onResponse(Call<WeatherGeoResponse> call,
                                   Response<WeatherGeoResponse> response) {
                if (call.isCanceled()) {
                    return;
                }
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getLocations() == null
                        || response.body().getLocations().isEmpty()) {
                    NetworkLogUtils.logHttpError(TAG, "城市搜索", response);
                    callback.onError(appContext.getString(
                            R.string.travel_geo_failed));
                    return;
                }
                WeatherGeoResponse.Location location =
                        response.body().getLocations().get(0);
                String cityName = formatCityName(
                        location.getAdm1(), location.getName());
                fetchWeekly(appContext, host, apiKey, location,
                        cityName, callback);
            }

            @Override
            public void onFailure(Call<WeatherGeoResponse> call,
                                  Throwable throwable) {
                if (!call.isCanceled()) {
                    callback.onError(appContext.getString(
                            R.string.travel_geo_failed));
                }
            }
        });
    }

    private static void fetchWeekly(Context context,
                                    String host,
                                    String apiKey,
                                    WeatherGeoResponse.Location location,
                                    String cityName,
                                    Callback callback) {
        if (location.getId() == null || location.getId().trim().isEmpty()) {
            fetchDailyFallback(context, host, apiKey, location,
                    cityName, callback);
            return;
        }
        String url = host + "v7/weather/7d?location="
                + Uri.encode(location.getId().trim());
        try {
            weeklyCall = ApiClient.getWeatherService()
                    .getWeeklyWeather(url, apiKey);
        } catch (Exception exception) {
            Log.e(TAG, "七天预报请求创建失败", exception);
            fetchDailyFallback(context, host, apiKey, location,
                    cityName, callback);
            return;
        }
        weeklyCall.enqueue(new retrofit2.Callback<WeeklyWeatherResponse>() {
            @Override
            public void onResponse(Call<WeeklyWeatherResponse> call,
                                   Response<WeeklyWeatherResponse> response) {
                if (call.isCanceled()) {
                    return;
                }
                WeeklyWeatherResponse body = response.body();
                if (!response.isSuccessful() || body == null
                        || body.getDaily() == null
                        || body.getDaily().isEmpty()) {
                    NetworkLogUtils.logHttpError(TAG, "七天预报", response);
                    fetchDailyFallback(context, host, apiKey, location,
                            cityName, callback);
                    return;
                }
                String summary = formatWeekly(body.getDaily());
                if (summary.isEmpty()) {
                    fetchDailyFallback(context, host, apiKey, location,
                            cityName, callback);
                    return;
                }
                callback.onSuccess(cityName, summary);
            }

            @Override
            public void onFailure(Call<WeeklyWeatherResponse> call,
                                  Throwable throwable) {
                if (!call.isCanceled()) {
                    fetchDailyFallback(context, host, apiKey, location,
                            cityName, callback);
                }
            }
        });
    }

    /**
     * 回退到已有的经纬度每日预报接口。
     */
    private static void fetchDailyFallback(Context context,
                                           String host,
                                           String apiKey,
                                           WeatherGeoResponse.Location location,
                                           String cityName,
                                           Callback callback) {
        if (location.getLat() == null || location.getLon() == null) {
            callback.onError(context.getString(R.string.travel_weather_failed));
            return;
        }
        String path = ApiConfig.WEATHER_DAILY_PATH
                .replace("{lat}", location.getLat())
                .replace("{lon}", location.getLon());
        try {
            dailyCall = ApiClient.getWeatherService()
                    .getDailyWeather(host + path, apiKey);
        } catch (Exception exception) {
            Log.e(TAG, "目的地每日预报请求创建失败", exception);
            callback.onError(context.getString(R.string.travel_weather_failed));
            return;
        }
        dailyCall.enqueue(new retrofit2.Callback<DailyWeatherResponse>() {
            @Override
            public void onResponse(Call<DailyWeatherResponse> call,
                                   Response<DailyWeatherResponse> response) {
                if (call.isCanceled()) {
                    return;
                }
                DailyWeatherResponse body = response.body();
                if (!response.isSuccessful() || body == null
                        || body.getDays() == null || body.getDays().isEmpty()) {
                    NetworkLogUtils.logHttpError(TAG, "目的地每日预报", response);
                    callback.onError(context.getString(
                            R.string.travel_weather_failed));
                    return;
                }
                String summary = formatDaily(body.getDays());
                callback.onSuccess(cityName, summary);
            }

            @Override
            public void onFailure(Call<DailyWeatherResponse> call,
                                  Throwable throwable) {
                if (!call.isCanceled()) {
                    callback.onError(context.getString(
                            R.string.travel_weather_failed));
                }
            }
        });
    }

    private static String formatWeekly(List<WeeklyWeatherResponse.Day> days) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(7, days.size());
        for (int i = 0; i < count; i++) {
            WeeklyWeatherResponse.Day day = days.get(i);
            builder.append(formatDate(day.getFxDate(), i))
                    .append(" ")
                    .append(joinCondition(day.getTextDay(), day.getTextNight()))
                    .append(" ")
                    .append(safe(day.getTempMin()))
                    .append("~")
                    .append(safe(day.getTempMax()))
                    .append("℃");
            if (!safe(day.getWindScaleDay()).isEmpty()) {
                builder.append(" 风力").append(day.getWindScaleDay()).append("级");
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    private static String formatDaily(List<DailyWeatherResponse.Daily> days) {
        StringBuilder builder = new StringBuilder();
        int count = Math.min(7, days.size());
        for (int i = 0; i < count; i++) {
            DailyWeatherResponse.Daily day = days.get(i);
            builder.append("第").append(i + 1).append("天 ")
                    .append(joinCondition(day.getLegacyTextDay(),
                            day.getLegacyTextNight()))
                    .append(" ")
                    .append(safe(day.getLegacyTempMin()))
                    .append("~")
                    .append(safe(day.getLegacyTempMax()))
                    .append("℃\n");
        }
        return builder.toString().trim();
    }

    private static String joinCondition(String day, String night) {
        String dayText = safe(day);
        String nightText = safe(night);
        if (dayText.isEmpty()) {
            return nightText.isEmpty() ? "天气未知" : nightText;
        }
        if (nightText.isEmpty() || dayText.equals(nightText)) {
            return dayText;
        }
        return dayText + "转" + nightText;
    }

    private static String formatDate(String fxDate, int index) {
        String value = safe(fxDate);
        if (value.length() >= 10) {
            return value.substring(5, 7) + "月" + value.substring(8, 10) + "日";
        }
        return "第" + (index + 1) + "天";
    }

    private static String formatCityName(String province, String city) {
        String adm1 = safe(province);
        String name = safe(city);
        if (adm1.isEmpty()) {
            return name;
        }
        if (name.isEmpty() || adm1.equals(name)) {
            return adm1;
        }
        return adm1 + " " + name;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static void cancel() {
        if (geoCall != null) {
            geoCall.cancel();
            geoCall = null;
        }
        if (weeklyCall != null) {
            weeklyCall.cancel();
            weeklyCall = null;
        }
        if (dailyCall != null) {
            dailyCall.cancel();
            dailyCall = null;
        }
    }
}
