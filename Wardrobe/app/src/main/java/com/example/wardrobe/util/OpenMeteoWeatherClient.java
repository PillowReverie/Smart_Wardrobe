package com.example.wardrobe.util;

import com.example.wardrobe.model.WeatherInfo;
import com.example.wardrobe.network.ApiClient;
import com.example.wardrobe.network.dto.OpenMeteoForecastResponse;
import com.example.wardrobe.network.dto.OpenMeteoWeatherResponse;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Open-Meteo 天气客户端。
 *
 * <p>把无需 Key 的每日天气、实况兜底和 WMO 解析从 WeatherManager 中隔离出来，
 * 由 WeatherManager 负责路由、缓存和取消。Open-Meteo 返回的数据统一转换为
 * 项目的 WeatherInfo，首页、详情页和 AI 不需要区分天气供应商。</p>
 */
public final class OpenMeteoWeatherClient {

    private static final String TAG = "OpenMeteoWeatherClient";

    public interface Callback {
        void onSuccess(WeatherInfo weatherInfo);

        void onError(boolean timeout);
    }

    private Call<OpenMeteoForecastResponse> dailyCall;
    private Call<OpenMeteoWeatherResponse> currentCall;

    /**
     * 请求每日天气。每日预报失败时回退到 current 接口。
     */
    public void fetchDaily(double latitude,
                           double longitude,
                           Callback callback) {
        cancel();
        String latitudeText = String.format(Locale.US, "%.5f", latitude);
        String longitudeText = String.format(Locale.US, "%.5f", longitude);
        String url = ApiConfig.OPEN_METEO_FORECAST_URL
                + "?latitude=" + latitudeText
                + "&longitude=" + longitudeText
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                + "&timezone=auto";
        try {
            dailyCall = ApiClient.getOpenMeteoService().getDailyForecast(url);
        } catch (Exception exception) {
            fetchCurrent(latitudeText, longitudeText, callback);
            return;
        }
        dailyCall.enqueue(new retrofit2.Callback<OpenMeteoForecastResponse>() {
            @Override
            public void onResponse(Call<OpenMeteoForecastResponse> call,
                                   Response<OpenMeteoForecastResponse> response) {
                if (call.isCanceled()) {
                    return;
                }
                WeatherInfo parsed = parseForecast(response.body());
                if (!response.isSuccessful() || parsed == null) {
                    NetworkLogUtils.logHttpError(TAG, "Open-Meteo 每日预报", response);
                    fetchCurrent(latitudeText, longitudeText, callback);
                    return;
                }
                callback.onSuccess(parsed);
            }

            @Override
            public void onFailure(Call<OpenMeteoForecastResponse> call,
                                  Throwable throwable) {
                if (!call.isCanceled()) {
                    fetchCurrent(latitudeText, longitudeText, callback);
                }
            }
        });
    }

    /**
     * Open-Meteo 实况兜底，保证每日数组异常时首页仍然有温度。
     */
    private void fetchCurrent(String latitudeText,
                              String longitudeText,
                              Callback callback) {
        if (currentCall != null) {
            currentCall.cancel();
        }
        String url = ApiConfig.OPEN_METEO_FORECAST_URL
                + "?latitude=" + latitudeText
                + "&longitude=" + longitudeText
                + "&current=temperature_2m,weather_code"
                + "&timezone=auto";
        try {
            currentCall = ApiClient.getOpenMeteoService().getCurrentWeather(url);
        } catch (Exception exception) {
            callback.onError(false);
            return;
        }
        currentCall.enqueue(new retrofit2.Callback<OpenMeteoWeatherResponse>() {
            @Override
            public void onResponse(Call<OpenMeteoWeatherResponse> call,
                                   Response<OpenMeteoWeatherResponse> response) {
                if (call.isCanceled()) {
                    return;
                }
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getCurrent() == null
                        || response.body().getCurrent().getTemperature() == null) {
                    NetworkLogUtils.logHttpError(TAG, "Open-Meteo 当前天气", response);
                    callback.onError(false);
                    return;
                }
                int temperature = (int) Math.round(
                        response.body().getCurrent().getTemperature());
                String condition = OpenMeteoWeatherCodes.toChinese(
                        response.body().getCurrent().getWeatherCode());
                callback.onSuccess(new WeatherInfo(
                        temperature, condition, System.currentTimeMillis()));
            }

            @Override
            public void onFailure(Call<OpenMeteoWeatherResponse> call,
                                  Throwable throwable) {
                if (call.isCanceled()) {
                    return;
                }
                boolean timeout = throwable instanceof SocketTimeoutException
                        || throwable instanceof InterruptedIOException;
                callback.onError(timeout);
            }
        });
    }

    private WeatherInfo parseForecast(OpenMeteoForecastResponse response) {
        if (response == null || response.getDaily() == null) {
            return null;
        }
        OpenMeteoForecastResponse.Daily daily = response.getDaily();
        List<Integer> codes = daily.getWeatherCodes();
        List<Double> maximums = daily.getTemperatureMax();
        List<Double> minimums = daily.getTemperatureMin();
        if (codes == null || maximums == null || minimums == null
                || codes.isEmpty() || maximums.isEmpty() || minimums.isEmpty()
                || maximums.get(0) == null || minimums.get(0) == null) {
            return null;
        }
        return new WeatherInfo(
                (int) Math.round(minimums.get(0)),
                (int) Math.round(maximums.get(0)),
                OpenMeteoWeatherCodes.toChinese(codes.get(0)),
                0,
                "",
                System.currentTimeMillis());
    }

    public void cancel() {
        if (dailyCall != null) {
            dailyCall.cancel();
            dailyCall = null;
        }
        if (currentCall != null) {
            currentCall.cancel();
            currentCall = null;
        }
    }
}
