package com.example.wardrobe.util;

import com.example.wardrobe.network.ApiClient;
import com.example.wardrobe.network.dto.OpenMeteoForecastResponse;
import com.example.wardrobe.network.dto.OpenMeteoGeocodingResponse;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Open-Meteo 异地城市搜索与七日天气客户端。
 *
 * <p>城市搜索先使用用户原始输入；中文查询失败时尝试常见城市拼音。
 * 找到经纬度后请求完整七天天气，并格式化为与和风天气一致的摘要。</p>
 */
public final class OpenMeteoTravelClient {

    private static final String TAG = "OpenMeteoTravelClient";

    public interface Callback {
        void onSuccess(String cityName, String weatherSummary);

        void onCityNotFound();

        void onError(String message);
    }

    private Call<OpenMeteoGeocodingResponse> geoCall;
    private Call<OpenMeteoForecastResponse> weatherCall;
    private int requestToken;

    public void fetch(String city, Callback callback) {
        cancel();
        final int token = ++requestToken;
        // 中文城市先转成连续拼音，避免 Open-Meteo 把“北京”误判为小地名。
        search(CityNameHelper.convertCityNameToQuery(city),
                token, callback);
    }

    /**
     * 使用转换后的关键词查询城市。
     */
    private void search(String query,
                        int token,
                        Callback callback) {
        if (token != requestToken) {
            return;
        }
        String url = ApiConfig.OPEN_METEO_GEOCODING_URL
                + "?name=" + encode(query)
                + "&count=1&language=zh&format=json";
        try {
            geoCall = ApiClient.getOpenMeteoService().searchCity(url);
        } catch (Exception exception) {
            callback.onError("城市搜索请求创建失败");
            return;
        }
        geoCall.enqueue(new retrofit2.Callback<OpenMeteoGeocodingResponse>() {
            @Override
            public void onResponse(Call<OpenMeteoGeocodingResponse> call,
                                   Response<OpenMeteoGeocodingResponse> response) {
                if (token != requestToken || call.isCanceled()) {
                    return;
                }
                OpenMeteoGeocodingResponse body = response.body();
                if (!response.isSuccessful() || body == null
                        || body.getResults() == null
                        || body.getResults().isEmpty()) {
                    callback.onCityNotFound();
                    return;
                }
                OpenMeteoGeocodingResponse.Result result =
                        body.getResults().get(0);
                if (result.getLatitude() == null
                        || result.getLongitude() == null) {
                    callback.onCityNotFound();
                    return;
                }
                fetchWeather(result, token, callback);
            }

            @Override
            public void onFailure(Call<OpenMeteoGeocodingResponse> call,
                                  Throwable throwable) {
                if (token != requestToken || call.isCanceled()) {
                    return;
                }
                boolean timeout = throwable instanceof SocketTimeoutException
                        || throwable instanceof InterruptedIOException;
                callback.onError(timeout
                        ? "搜索超时，请稍后重试"
                        : "城市搜索失败，请检查网络");
            }
        });
    }

    private void fetchWeather(OpenMeteoGeocodingResponse.Result result,
                              int token,
                              Callback callback) {
        if (token != requestToken) {
            return;
        }
        String latitude = String.format(Locale.US, "%.5f", result.getLatitude());
        String longitude = String.format(
                Locale.US, "%.5f", result.getLongitude());
        String url = ApiConfig.OPEN_METEO_FORECAST_URL
                + "?latitude=" + latitude
                + "&longitude=" + longitude
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                + "&timezone=auto&forecast_days=7";
        try {
            weatherCall = ApiClient.getOpenMeteoService()
                    .getDailyForecast(url);
        } catch (Exception exception) {
            callback.onError("目的地天气请求创建失败");
            return;
        }
        weatherCall.enqueue(new retrofit2.Callback<OpenMeteoForecastResponse>() {
            @Override
            public void onResponse(Call<OpenMeteoForecastResponse> call,
                                   Response<OpenMeteoForecastResponse> response) {
                if (token != requestToken || call.isCanceled()) {
                    return;
                }
                String summary = formatForecast(response.body());
                if (!response.isSuccessful() || summary.isEmpty()) {
                    NetworkLogUtils.logHttpError(TAG, "目的地天气", response);
                    callback.onError("获取目的地天气失败，请稍后重试");
                    return;
                }
                String cityName = formatCityName(
                        result.getAdmin1(), result.getName());
                callback.onSuccess(cityName, summary);
            }

            @Override
            public void onFailure(Call<OpenMeteoForecastResponse> call,
                                  Throwable throwable) {
                if (token != requestToken || call.isCanceled()) {
                    return;
                }
                boolean timeout = throwable instanceof SocketTimeoutException
                        || throwable instanceof InterruptedIOException;
                callback.onError(timeout
                        ? "目的地天气请求超时，请稍后重试"
                        : "获取目的地天气失败，请稍后重试");
            }
        });
    }

    /**
     * 将 Open-Meteo 七天数组格式化成 Prompt 使用的“日期 天气 温度”列表。
     */
    static String formatForecast(OpenMeteoForecastResponse response) {
        if (response == null || response.getDaily() == null) {
            return "";
        }
        OpenMeteoForecastResponse.Daily daily = response.getDaily();
        List<String> dates = daily.getDates();
        List<Integer> codes = daily.getWeatherCodes();
        List<Double> maximums = daily.getTemperatureMax();
        List<Double> minimums = daily.getTemperatureMin();
        if (dates == null || codes == null || maximums == null
                || minimums == null) {
            return "";
        }
        int count = Math.min(7,
                Math.min(dates.size(),
                        Math.min(codes.size(),
                                Math.min(maximums.size(), minimums.size()))));
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (maximums.get(index) == null || minimums.get(index) == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(formatDate(dates.get(index)))
                    .append(" ")
                    .append(OpenMeteoWeatherCodes.toChinese(codes.get(index)))
                    .append(" ")
                    .append((int) Math.round(minimums.get(index)))
                    .append("~")
                    .append((int) Math.round(maximums.get(index)))
                    .append("℃");
        }
        return builder.toString().trim();
    }

    private static String formatDate(String isoDate) {
        if (isoDate == null) {
            return "日期未知";
        }
        String value = isoDate.trim();
        if (value.length() >= 10) {
            return value.substring(5, 7) + "月" + value.substring(8, 10) + "日";
        }
        return value;
    }

    private static String formatCityName(String province, String city) {
        String safeProvince = province == null ? "" : province.trim();
        String safeCity = city == null ? "" : city.trim();
        if (safeProvince.isEmpty()) {
            return safeCity.isEmpty() ? "目的地" : safeCity;
        }
        if (safeCity.isEmpty() || safeProvince.equals(safeCity)) {
            return safeProvince;
        }
        return safeProvince + " " + safeCity;
    }

    private static String encode(String value) {
        return android.net.Uri.encode(value == null ? "" : value.trim());
    }

    public void cancel() {
        requestToken++;
        if (geoCall != null) {
            geoCall.cancel();
            geoCall = null;
        }
        if (weatherCall != null) {
            weatherCall.cancel();
            weatherCall = null;
        }
    }
}
