package com.example.wardrobe.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import com.example.wardrobe.R;
import com.example.wardrobe.model.WeatherInfo;
import com.example.wardrobe.network.ApiClient;
import com.example.wardrobe.network.dto.DailyWeatherResponse;
import com.example.wardrobe.network.dto.WeatherGeoResponse;
import com.example.wardrobe.network.dto.WeatherResponse;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Response;

/**
 * 天气数据管理器。
 *
 * <p>负责读取 SharedPreferences 中的天气配置、拼接完整 URL、异步调用 Retrofit，
 * 并在内存中缓存成功的天气结果 30 分钟。首页和详情页共享同一份缓存，
 * 避免重复消耗 API 额度。</p>
 */
public class WeatherManager {

    private static final String TAG = "WeatherManager";
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;
    private static final String PREFS_NAME = "weather_persistent_cache";
    private static final String KEY_WEATHER_FETCHED_AT = "weather_fetched_at";
    private static final String KEY_WEATHER_MIN = "weather_min";
    private static final String KEY_WEATHER_MAX = "weather_max";
    private static final String KEY_WEATHER_CONDITION = "weather_condition";
    private static final String KEY_WEATHER_UV = "weather_uv";
    private static final String KEY_WEATHER_WIND = "weather_wind";
    private static final String KEY_LOCATION_NAME = "location_name";
    private static final String KEY_LOCATION_FETCHED_AT = "location_fetched_at";
    private static final String KEY_LOCATION_LAT = "location_lat";
    private static final String KEY_LOCATION_LON = "location_lon";
    private static volatile WeatherManager instance;

    private WeatherInfo cachedWeather;
    private long cacheTime;
    private double lastLatitude = LocationHelper.DEFAULT_LATITUDE;
    private double lastLongitude = LocationHelper.DEFAULT_LONGITUDE;
    private Call<WeatherResponse> currentCall;
    private Call<DailyWeatherResponse> currentDailyCall;
    private Call<WeatherGeoResponse> currentGeoCall;

    private String cachedLocationName;
    private long locationCacheTime;
    private double locationCacheLatitude;
    private double locationCacheLongitude;

    private WeatherManager() {
        // 单例，禁止外部实例化。
    }

    public static WeatherManager getInstance() {
        if (instance == null) {
            synchronized (WeatherManager.class) {
                if (instance == null) {
                    instance = new WeatherManager();
                }
            }
        }
        return instance;
    }

    public interface Callback {
        void onSuccess(WeatherInfo weatherInfo, boolean fromCache);

        void onError(String message);
    }

    public interface LocationCallback {
        void onSuccess(String locationName, boolean fromCache);

        void onError(String message);
    }

    /**
     * 返回 30 分钟内缓存的数据，没有有效缓存时返回 null。
     */
    public WeatherInfo getCachedWeather(Context context) {
        if (cachedWeather == null) {
            cachedWeather = loadPersistentWeather(context);
        }
        if (cachedWeather == null) {
            return null;
        }
        long fetchedAt = cachedWeather.getFetchedAt();
        if (fetchedAt <= 0
                || System.currentTimeMillis() - fetchedAt > CACHE_TTL_MS) {
            cachedWeather = null;
            clearPersistentWeather(context);
            return null;
        }
        cacheTime = SystemClock.elapsedRealtime();
        return cachedWeather;
    }

    /**
     * 用户主动下拉刷新时清除内存缓存，确保下一次请求真正访问网络。
     */
    public void invalidateMemoryCache(Context context) {
        cachedWeather = null;
        cacheTime = 0L;
        cachedLocationName = null;
        locationCacheTime = 0L;
        preferences(context).edit().clear().apply();
    }

    /**
     * 获取 30 分钟内的逆地理编码缓存。经纬度变化超过约 1 公里时重新请求，
     * 避免用户移动后仍显示上一个城市。
     */
    public String getCachedLocationName(Context context,
                                        double latitude,
                                        double longitude) {
        if (cachedLocationName == null) {
            loadPersistentLocation(context, latitude, longitude);
        }
        if (cachedLocationName == null) {
            return null;
        }
        if (System.currentTimeMillis() - locationCacheTime > CACHE_TTL_MS) {
            cachedLocationName = null;
            return null;
        }
        if (Math.abs(locationCacheLatitude - latitude) > 0.01
                || Math.abs(locationCacheLongitude - longitude) > 0.01) {
            return null;
        }
        return cachedLocationName;
    }

    /**
     * 返回 30 分钟内的持久化地名，用于复用缓存天气时直接恢复首页文案。
     */
    public String getCachedLocationLabel(Context context) {
        SharedPreferences preferences = preferences(context);
        long fetchedAt = preferences.getLong(KEY_LOCATION_FETCHED_AT, 0L);
        String label = preferences.getString(KEY_LOCATION_NAME, "");
        if (fetchedAt <= 0
                || System.currentTimeMillis() - fetchedAt > CACHE_TTL_MS
                || label == null || label.trim().isEmpty()) {
            return "";
        }
        return label;
    }

    /**
     * 根据经纬度获取行政区名称。所有请求使用 enqueue 异步执行，并独立于
     * 天气请求，因此逆地理编码失败时仍然可以正常展示天气温度。
     */
    public void fetchLocationName(Context context,
                                  double latitude,
                                  double longitude,
                                  LocationCallback callback) {
        Context appContext = context.getApplicationContext();
        if (!ApiConfig.isWeatherConfigured(appContext)) {
            callback.onError(appContext.getString(R.string.weather_not_configured));
            return;
        }

        String cached = getCachedLocationName(
                appContext, latitude, longitude);
        if (cached != null) {
            callback.onSuccess(cached, true);
            return;
        }

        String host = ApiConfig.getWeatherBaseUrl(appContext);
        String apiKey = ApiConfig.getWeatherApiKey(appContext);
        String latitudeText = String.format(Locale.US, "%.5f", latitude);
        String longitudeText = String.format(Locale.US, "%.5f", longitude);
        // 和风天气逆地理编码参数顺序固定为“经度,纬度”。
        String fullUrl = host + ApiConfig.WEATHER_GEO_CITY_PATH
                + "?location=" + longitudeText + "," + latitudeText;

        if (currentGeoCall != null) {
            currentGeoCall.cancel();
        }

        final Call<WeatherGeoResponse> call;
        try {
            call = ApiClient.getWeatherService().lookupCity(fullUrl, apiKey);
        } catch (Exception exception) {
            Log.e(TAG, "逆地理编码请求创建失败", exception);
            callback.onError(appContext.getString(
                    R.string.weather_location_failed));
            return;
        }
        currentGeoCall = call;

        call.enqueue(new retrofit2.Callback<WeatherGeoResponse>() {
            @Override
            public void onResponse(Call<WeatherGeoResponse> requestCall,
                                   Response<WeatherGeoResponse> response) {
                if (requestCall.isCanceled()) {
                    return;
                }
                if (response.code() == 401 || response.code() == 403) {
                    callback.onError(appContext.getString(
                            R.string.weather_auth_failed));
                    return;
                }
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getLocations() == null
                        || response.body().getLocations().isEmpty()) {
                    callback.onError(appContext.getString(
                            R.string.weather_location_failed));
                    return;
                }

                WeatherGeoResponse.Location location =
                        response.body().getLocations().get(0);
                String displayName = formatLocationName(
                        location.getAdm1(), location.getName());
                if (displayName.isEmpty()) {
                    callback.onError(appContext.getString(
                            R.string.weather_location_failed));
                    return;
                }

                cachedLocationName = displayName;
                locationCacheTime = System.currentTimeMillis();
                locationCacheLatitude = latitude;
                locationCacheLongitude = longitude;
                savePersistentLocation(
                        appContext, displayName, latitude, longitude);

                callback.onSuccess(displayName, false);
            }

            @Override
            public void onFailure(Call<WeatherGeoResponse> requestCall,
                                  Throwable throwable) {
                if (requestCall.isCanceled()) {
                    return;
                }
                boolean timeout = throwable instanceof SocketTimeoutException
                        || throwable instanceof InterruptedIOException;
                callback.onError(appContext.getString(timeout
                        ? R.string.weather_timeout
                        : R.string.weather_location_failed));
            }
        });
    }

    /**
     * 组合省份和城市/区县，并避免出现“北京市 北京市”这类重复展示。
     */
    static String formatLocationName(String province, String city) {
        String safeProvince = province == null ? "" : province.trim();
        String safeCity = city == null ? "" : city.trim();
        if (safeProvince.isEmpty()) {
            return safeCity;
        }
        if (safeCity.isEmpty() || safeProvince.equals(safeCity)) {
            return safeProvince;
        }
        return safeProvince + " " + safeCity;
    }

    /**
     * 页面销毁时取消尚未完成的天气和地名请求。
     */
    public void cancelActiveCalls() {
        if (currentCall != null) {
            currentCall.cancel();
            currentCall = null;
        }
        if (currentDailyCall != null) {
            currentDailyCall.cancel();
            currentDailyCall = null;
        }
        if (currentGeoCall != null) {
            currentGeoCall.cancel();
            currentGeoCall = null;
        }
    }

    /**
     * 优先使用缓存；没有缓存时使用最近一次定位坐标，初始为北京。
     */
    public void getWeather(Context context, Callback callback) {
        WeatherInfo weather = getCachedWeather(context);
        if (weather != null) {
            callback.onSuccess(weather, true);
            return;
        }
        fetchWeather(context, lastLatitude, lastLongitude, callback);
    }

    /**
     * 请求指定经纬度的每日预报。所有网络调用都通过 Retrofit 的 enqueue
     * 异步执行；日预报不可用时回退到实况温度，保证天气卡片仍有兜底数据。
     */
    public void fetchWeather(Context context,
                             double latitude,
                             double longitude,
                             Callback callback) {
        fetchWeather(context, latitude, longitude, false, callback);
    }

    /**
     * @param forceRefresh true 时跳过天气内存缓存
     */
    public void fetchWeather(Context context,
                             double latitude,
                             double longitude,
                             boolean forceRefresh,
                             Callback callback) {
        Context appContext = context.getApplicationContext();
        if (!ApiConfig.isWeatherConfigured(appContext)) {
            callback.onError(appContext.getString(R.string.weather_not_configured));
            return;
        }

        WeatherInfo cached = forceRefresh ? null : getCachedWeather(appContext);
        if (cached != null) {
            callback.onSuccess(cached, true);
            return;
        }

        String host = ApiConfig.getWeatherBaseUrl(appContext);
        String apiKey = ApiConfig.getWeatherApiKey(appContext);
        String latitudeText = String.format(Locale.US, "%.2f", latitude);
        String longitudeText = String.format(Locale.US, "%.2f", longitude);
        String path = ApiConfig.WEATHER_DAILY_PATH
                .replace("{lat}", latitudeText)
                .replace("{lon}", longitudeText);
        String fullUrl = host + path;

        lastLatitude = latitude;
        lastLongitude = longitude;

        if (currentDailyCall != null) {
            currentDailyCall.cancel();
        }

        final Call<DailyWeatherResponse> call;
        try {
            call = ApiClient.getWeatherService()
                    .getDailyWeather(fullUrl, apiKey);
        } catch (Exception exception) {
            Log.e(TAG, "每日预报 URL 或请求创建失败", exception);
            fetchCurrentWeatherFallback(
                    appContext, latitudeText, longitudeText, callback);
            return;
        }
        currentDailyCall = call;

        call.enqueue(new retrofit2.Callback<DailyWeatherResponse>() {
            @Override
            public void onResponse(Call<DailyWeatherResponse> call,
                                   Response<DailyWeatherResponse> response) {
                if (call.isCanceled()) {
                    return;
                }
                if (response.code() == 401 || response.code() == 403) {
                    NetworkLogUtils.logHttpError(TAG, "每日预报", response);
                    callback.onError(appContext.getString(
                            R.string.weather_auth_failed));
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    NetworkLogUtils.logHttpError(TAG, "每日预报", response);
                    fetchCurrentWeatherFallback(
                            appContext, latitudeText, longitudeText, callback);
                    return;
                }

                List<DailyWeatherResponse.Daily> dailyList =
                        response.body().getDays();
                if (dailyList == null || dailyList.isEmpty()) {
                    fetchCurrentWeatherFallback(
                            appContext, latitudeText, longitudeText, callback);
                    return;
                }

                DailyWeatherResponse.Daily daily = dailyList.get(0);
                int tempMin = readTempMin(daily);
                int tempMax = readTempMax(daily);
                if (tempMin == Integer.MIN_VALUE || tempMax == Integer.MIN_VALUE) {
                    fetchCurrentWeatherFallback(
                            appContext, latitudeText, longitudeText, callback);
                    return;
                }

                String condition = readDailyCondition(daily);
                String windScale = readWindScale(daily);
                WeatherInfo weatherInfo = new WeatherInfo(
                        tempMin,
                        tempMax,
                        condition,
                        readUvIndex(daily),
                        windScale,
                        System.currentTimeMillis());
                cachedWeather = weatherInfo;
                cacheTime = SystemClock.elapsedRealtime();
                savePersistentWeather(appContext, weatherInfo);

                callback.onSuccess(weatherInfo, false);
            }

            @Override
            public void onFailure(Call<DailyWeatherResponse> call,
                                  Throwable throwable) {
                if (call.isCanceled()) {
                    return;
                }
                fetchCurrentWeatherFallback(
                        appContext, latitudeText, longitudeText, callback);
            }
        });
    }

    /**
     * 日预报失败时回退到实况接口。此时最低温和最高温相同，UI 仍可正常工作。
     */
    private void fetchCurrentWeatherFallback(Context appContext,
                                             String latitudeText,
                                             String longitudeText,
                                             Callback callback) {
        if (currentCall != null) {
            currentCall.cancel();
        }
        String host = ApiConfig.getWeatherBaseUrl(appContext);
        String apiKey = ApiConfig.getWeatherApiKey(appContext);
        String path = ApiConfig.WEATHER_CURRENT_PATH
                .replace("{lat}", latitudeText)
                .replace("{lon}", longitudeText);
        String fullUrl = host + path;

        final Call<WeatherResponse> call;
        try {
            call = ApiClient.getWeatherService()
                    .getCurrentWeather(fullUrl, apiKey);
        } catch (Exception exception) {
            Log.e(TAG, "实况天气 URL 或请求创建失败", exception);
            callback.onError(appContext.getString(R.string.weather_load_failed));
            return;
        }
        currentCall = call;

        call.enqueue(new retrofit2.Callback<WeatherResponse>() {
            @Override
            public void onResponse(Call<WeatherResponse> requestCall,
                                   Response<WeatherResponse> response) {
                if (requestCall.isCanceled()) {
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    NetworkLogUtils.logHttpError(TAG, "实况天气", response);
                    callback.onError(appContext.getString(
                            R.string.weather_load_failed));
                    return;
                }
                WeatherResponse body = response.body();
                if (body.getCondition() == null
                        || body.getTemperature() == null) {
                    callback.onError(appContext.getString(
                            R.string.weather_load_failed));
                    return;
                }

                int temperature = (int) Math.round(
                        body.getTemperature().getValue());
                String condition = WeatherTextMapper.toChinese(
                        body.getCondition().getText(),
                        body.getCondition().getCode());
                WeatherInfo weatherInfo = new WeatherInfo(
                        temperature, condition, System.currentTimeMillis());
                cachedWeather = weatherInfo;
                cacheTime = SystemClock.elapsedRealtime();
                savePersistentWeather(appContext, weatherInfo);
                callback.onSuccess(weatherInfo, false);
            }

            @Override
            public void onFailure(Call<WeatherResponse> requestCall,
                                  Throwable throwable) {
                if (requestCall.isCanceled()) {
                    return;
                }
                boolean timeout = throwable instanceof SocketTimeoutException
                        || throwable instanceof InterruptedIOException;
                callback.onError(appContext.getString(timeout
                        ? R.string.weather_timeout
                        : R.string.weather_load_failed));
            }
        });
    }

    private int safeParseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int readTempMin(DailyWeatherResponse.Daily daily) {
        Double value = daily.getTempMinValue();
        if (value != null) {
            return (int) Math.round(value);
        }
        return safeParseInt(daily.getLegacyTempMin(), Integer.MIN_VALUE);
    }

    private int readTempMax(DailyWeatherResponse.Daily daily) {
        Double value = daily.getTempMaxValue();
        if (value != null) {
            return (int) Math.round(value);
        }
        return safeParseInt(daily.getLegacyTempMax(), Integer.MIN_VALUE);
    }

    private int readUvIndex(DailyWeatherResponse.Daily daily) {
        if (daily.getUvIndexMax() != null) {
            return daily.getUvIndexMax();
        }
        return safeParseInt(daily.getLegacyUvIndex(), 0);
    }

    /**
     * 同时读取日间和夜间天气，晚间有雨时也能触发“带伞”预警。
     */
    private String readDailyCondition(DailyWeatherResponse.Daily daily) {
        String dayText = "";
        String nightText = "";

        if (daily.getDaytime() != null
                && daily.getDaytime().getCondition() != null) {
            dayText = mapConditionOrEmpty(
                    daily.getDaytime().getCondition().getText(),
                    daily.getDaytime().getCondition().getCode());
        } else {
            dayText = mapConditionOrEmpty(
                    daily.getLegacyTextDay(), daily.getLegacyIconDay());
        }

        if (daily.getNighttime() != null
                && daily.getNighttime().getCondition() != null) {
            nightText = mapConditionOrEmpty(
                    daily.getNighttime().getCondition().getText(),
                    daily.getNighttime().getCondition().getCode());
        } else {
            nightText = mapConditionOrEmpty(
                    daily.getLegacyTextNight(), daily.getLegacyIconNight());
        }

        if (dayText.isEmpty()) {
            return nightText;
        }
        if (nightText.isEmpty() || dayText.equals(nightText)) {
            return dayText;
        }
        return dayText + "转" + nightText;
    }

    private String mapConditionOrEmpty(String text, String code) {
        if ((text == null || text.trim().isEmpty())
                && (code == null || code.trim().isEmpty())) {
            return "";
        }
        return WeatherTextMapper.toChinese(text, code);
    }

    private String readWindScale(DailyWeatherResponse.Daily daily) {
        int maximum = 0;
        if (daily.getDaytime() != null && daily.getDaytime().getWind() != null
                && daily.getDaytime().getWind().getScale() != null) {
            maximum = daily.getDaytime().getWind().getScale();
        }
        if (daily.getNighttime() != null && daily.getNighttime().getWind() != null
                && daily.getNighttime().getWind().getScale() != null) {
            maximum = Math.max(maximum,
                    daily.getNighttime().getWind().getScale());
        }
        if (maximum == 0 && daily.getLegacyWindScaleDay() != null) {
            return daily.getLegacyWindScaleDay();
        }
        return String.valueOf(maximum);
    }

    private WeatherInfo loadPersistentWeather(Context context) {
        SharedPreferences preferences = preferences(context);
        long fetchedAt = preferences.getLong(KEY_WEATHER_FETCHED_AT, 0L);
        if (fetchedAt <= 0
                || System.currentTimeMillis() - fetchedAt > CACHE_TTL_MS) {
            clearPersistentWeather(context);
            return null;
        }
        String condition = preferences.getString(KEY_WEATHER_CONDITION, "");
        if (condition == null || condition.trim().isEmpty()) {
            return null;
        }
        return new WeatherInfo(
                preferences.getInt(KEY_WEATHER_MIN, 0),
                preferences.getInt(KEY_WEATHER_MAX, 0),
                condition,
                preferences.getInt(KEY_WEATHER_UV, 0),
                preferences.getString(KEY_WEATHER_WIND, ""),
                fetchedAt
        );
    }

    private void savePersistentWeather(Context context,
                                       WeatherInfo weatherInfo) {
        if (weatherInfo == null) {
            return;
        }
        preferences(context).edit()
                .putLong(KEY_WEATHER_FETCHED_AT, weatherInfo.getFetchedAt())
                .putInt(KEY_WEATHER_MIN, weatherInfo.getTempMin())
                .putInt(KEY_WEATHER_MAX, weatherInfo.getTempMax())
                .putString(KEY_WEATHER_CONDITION,
                        weatherInfo.getConditionText())
                .putInt(KEY_WEATHER_UV, weatherInfo.getUvIndex())
                .putString(KEY_WEATHER_WIND, weatherInfo.getWindScale())
                .apply();
    }

    private void clearPersistentWeather(Context context) {
        preferences(context).edit()
                .remove(KEY_WEATHER_FETCHED_AT)
                .remove(KEY_WEATHER_MIN)
                .remove(KEY_WEATHER_MAX)
                .remove(KEY_WEATHER_CONDITION)
                .remove(KEY_WEATHER_UV)
                .remove(KEY_WEATHER_WIND)
                .apply();
    }

    private void savePersistentLocation(Context context,
                                        String name,
                                        double latitude,
                                        double longitude) {
        preferences(context).edit()
                .putString(KEY_LOCATION_NAME, name)
                .putLong(KEY_LOCATION_FETCHED_AT, System.currentTimeMillis())
                .putString(KEY_LOCATION_LAT, String.valueOf(latitude))
                .putString(KEY_LOCATION_LON, String.valueOf(longitude))
                .apply();
    }

    private void loadPersistentLocation(Context context,
                                        double latitude,
                                        double longitude) {
        SharedPreferences preferences = preferences(context);
        long fetchedAt = preferences.getLong(KEY_LOCATION_FETCHED_AT, 0L);
        if (fetchedAt <= 0
                || System.currentTimeMillis() - fetchedAt > CACHE_TTL_MS) {
            return;
        }
        try {
            double savedLat = Double.parseDouble(
                    preferences.getString(KEY_LOCATION_LAT, "0"));
            double savedLon = Double.parseDouble(
                    preferences.getString(KEY_LOCATION_LON, "0"));
            if (Math.abs(savedLat - latitude) > 0.01
                    || Math.abs(savedLon - longitude) > 0.01) {
                return;
            }
            cachedLocationName =
                    preferences.getString(KEY_LOCATION_NAME, "");
            locationCacheTime = fetchedAt;
            locationCacheLatitude = savedLat;
            locationCacheLongitude = savedLon;
        } catch (NumberFormatException ignored) {
            cachedLocationName = null;
        }
    }

    private SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
