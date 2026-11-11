package com.example.wardrobe.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.CancellationSignal;

import androidx.core.content.ContextCompat;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LocationManager 定位工具。
 *
 * <p>优先使用 2 分钟内的最后已知位置，否则请求一次当前定位。定位总时长
 * 限制为 10 秒，超时后回退到旧的最后位置或北京默认坐标。所有回调都在
 * 主线程执行。</p>
 */
public final class LocationHelper {

    public static final double DEFAULT_LATITUDE = 39.9042;
    public static final double DEFAULT_LONGITUDE = 116.4074;

    private static final long LOCATION_TIMEOUT_MS = 10_000L;
    private static final long FRESH_LOCATION_MS = 2 * 60 * 1000L;

    private LocationHelper() {
        // 工具类禁止实例化。
    }

    public interface Callback {
        void onLocation(double latitude, double longitude, boolean usedDefault);
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestCurrentLocation(Context context, Callback callback) {
        if (!hasLocationPermission(context)) {
            callback.onLocation(DEFAULT_LATITUDE, DEFAULT_LONGITUDE, true);
            return;
        }

        LocationManager locationManager = (LocationManager)
                context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            callback.onLocation(DEFAULT_LATITUDE, DEFAULT_LONGITUDE, true);
            return;
        }

        Location lastKnown = getBestLastKnownLocation(locationManager);
        if (lastKnown != null
                && System.currentTimeMillis() - lastKnown.getTime()
                <= FRESH_LOCATION_MS) {
            callback.onLocation(lastKnown.getLatitude(),
                    lastKnown.getLongitude(), false);
            return;
        }

        String provider = chooseProvider(context, locationManager);
        if (provider == null) {
            deliverFallback(callback, lastKnown);
            return;
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());
        AtomicBoolean delivered = new AtomicBoolean(false);
        CancellationSignal[] cancellationSignal = new CancellationSignal[1];
        LocationListener[] locationListener = new LocationListener[1];

        Runnable timeout = () -> {
            if (delivered.compareAndSet(false, true)) {
                if (cancellationSignal[0] != null) {
                    cancellationSignal[0].cancel();
                }
                if (locationListener[0] != null) {
                    try {
                        locationManager.removeUpdates(locationListener[0]);
                    } catch (SecurityException ignored) {
                        // 权限在请求过程中被撤销，直接走回退位置。
                    }
                }
                deliverFallback(callback, lastKnown);
            }
        };
        mainHandler.postDelayed(timeout, LOCATION_TIMEOUT_MS);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cancellationSignal[0] = new CancellationSignal();
                locationManager.getCurrentLocation(
                        provider,
                        cancellationSignal[0],
                        ContextCompat.getMainExecutor(context),
                        location -> {
                            if (location != null
                                    && delivered.compareAndSet(false, true)) {
                                mainHandler.removeCallbacks(timeout);
                                callback.onLocation(location.getLatitude(),
                                        location.getLongitude(), false);
                            }
                        });
            } else {
                locationListener[0] = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        if (location != null
                                && delivered.compareAndSet(false, true)) {
                            mainHandler.removeCallbacks(timeout);
                            callback.onLocation(location.getLatitude(),
                                    location.getLongitude(), false);
                        }
                    }

                    @Override
                    @Deprecated
                    public void onStatusChanged(String provider, int status,
                                                Bundle extras) {
                    }

                    @Override
                    public void onProviderEnabled(String provider) {
                    }

                    @Override
                    public void onProviderDisabled(String provider) {
                    }
                };
                locationManager.requestSingleUpdate(
                        provider, locationListener[0], Looper.getMainLooper());
            }
        } catch (SecurityException exception) {
            if (delivered.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeout);
                deliverFallback(callback, lastKnown);
            }
        } catch (Exception exception) {
            if (delivered.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeout);
                deliverFallback(callback, lastKnown);
            }
        }
    }

    private static String chooseProvider(Context context,
                                         LocationManager locationManager) {
        boolean fineGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (fineGranted && locationManager.isProviderEnabled(
                LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }
        if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            return LocationManager.PASSIVE_PROVIDER;
        }
        return null;
    }

    private static Location getBestLastKnownLocation(
            LocationManager locationManager) {
        String[] providers = new String[]{
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
        };
        Location best = null;
        for (String provider : providers) {
            try {
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate != null
                        && (best == null || candidate.getTime() > best.getTime())) {
                    best = candidate;
                }
            } catch (SecurityException ignored) {
                // 没有该 Provider 的权限，继续检查下一个。
            }
        }
        return best;
    }

    private static void deliverFallback(Callback callback, Location lastKnown) {
        if (lastKnown != null) {
            callback.onLocation(lastKnown.getLatitude(),
                    lastKnown.getLongitude(), false);
        } else {
            callback.onLocation(DEFAULT_LATITUDE, DEFAULT_LONGITUDE, true);
        }
    }
}
