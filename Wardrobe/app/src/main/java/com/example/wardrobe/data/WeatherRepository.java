package com.example.wardrobe.data;

import android.content.Context;

import com.example.wardrobe.R;
import com.example.wardrobe.model.WeatherInfo;
import com.example.wardrobe.util.ApiConfig;
import com.example.wardrobe.util.LocationHelper;
import com.example.wardrobe.util.NetworkUtils;
import com.example.wardrobe.util.WeatherAdvisor;
import com.example.wardrobe.util.WeatherManager;

/**
 * 天气与定位仓库。
 *
 * <p>统一封装网络校验、LocationManager 定位、逆地理编码、每日天气请求
 * 和生活提示生成。ViewModel 不再直接接触 WeatherManager。</p>
 */
public final class WeatherRepository {

    public interface Callback {
        void onLocationName(String locationName);

        void onWeather(WeatherInfo weather, String alert);

        void onError(String message);

        void onFinished();
    }

    private final WeatherManager weatherManager =
            WeatherManager.getInstance();
    private final Context appContext;
    private int requestToken;

    public WeatherRepository(Context context) {
        appContext = context.getApplicationContext();
    }

    public void refresh(Context context,
                        boolean forceRefresh,
                        Callback callback) {
        Context appContext = context.getApplicationContext();
        final int token = ++requestToken;
        if (!NetworkUtils.isNetworkAvailable(appContext)) {
            callback.onError(appContext.getString(R.string.network_unavailable));
            callback.onFinished();
            return;
        }
        if (forceRefresh) {
            weatherManager.invalidateMemoryCache(appContext);
        }

        LocationHelper.requestCurrentLocation(appContext,
                (latitude, longitude, usedDefault) -> {
                    if (token != requestToken) {
                        return;
                    }
                    requestLocationName(appContext, latitude, longitude,
                            usedDefault, token, callback);
                    requestWeather(appContext, latitude, longitude,
                            forceRefresh, token, callback);
                });
    }

    private void requestLocationName(Context context,
                                     double latitude,
                                     double longitude,
                                     boolean usedDefault,
                                     int token,
                                     Callback callback) {
        weatherManager.fetchLocationName(context, latitude, longitude,
                new WeatherManager.LocationCallback() {
                    @Override
                    public void onSuccess(String locationName,
                                          boolean fromCache) {
                        if (token == requestToken) {
                            callback.onLocationName(locationName);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (token == requestToken) {
                            callback.onLocationName(context.getString(
                                    usedDefault
                                            ? R.string.home_weather_default_location
                                            : R.string.home_weather_location));
                        }
                    }
                });
    }

    private void requestWeather(Context context,
                                double latitude,
                                double longitude,
                                boolean forceRefresh,
                                int token,
                                Callback callback) {
        weatherManager.fetchWeather(context, latitude, longitude,
                forceRefresh,
                new WeatherManager.Callback() {
                    @Override
                    public void onSuccess(WeatherInfo weatherInfo,
                                          boolean fromCache) {
                        if (token != requestToken) {
                            return;
                        }
                        callback.onWeather(weatherInfo,
                                WeatherAdvisor.buildAlert(context, weatherInfo));
                        callback.onFinished();
                    }

                    @Override
                    public void onError(String message) {
                        if (token != requestToken) {
                            return;
                        }
                        callback.onError(message);
                        callback.onFinished();
                    }
                });
    }

    public WeatherInfo getCachedWeather() {
        return weatherManager.getCachedWeather(appContext);
    }

    public String getCachedLocationLabel() {
        return weatherManager.getCachedLocationLabel(appContext);
    }

    public void cancel() {
        requestToken++;
        weatherManager.cancelActiveCalls();
    }
}
