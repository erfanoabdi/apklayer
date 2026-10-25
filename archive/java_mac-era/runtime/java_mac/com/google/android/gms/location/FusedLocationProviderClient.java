// com.google.android.gms.location.FusedLocationProviderClient -- the modern
// fused-location client. Ref: developers.google.com/android/reference/com/
// google/android/gms/location/FusedLocationProviderClient.
//
// Policy: this shadow only runs for an app that did NOT bundle
// play-services-location (the GMS family is child-first, see AppClassLoader),
// so there is no Google service on the other end of it. Since the platform's
// own fused provider is backed by macOS Location Services, the shadow is a thin
// client of it: android.location.LocationManager.FUSED_PROVIDER, with the same
// permission, enabled-state and getCurrentLocation rules. An app that bundles
// its own copy talks to Google Play services itself, which this device does
// not run, and gets that library's own "API unavailable" answer.
package com.google.android.gms.location;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import android.app.PendingIntent;

import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.GoogleApi;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class FusedLocationProviderClient
        extends GoogleApi<Api.ApiOptions.NoOptions> {

    // Shared Api token (real SDK: LocationServices.API). A single instance is
    // fine: it is an opaque marker.
    static final Api<Api.ApiOptions.NoOptions> API =
            new Api<>("LocationServices.API");

    FusedLocationProviderClient(Context context) {
        super(context, API, Api.ApiOptions.NO_OPTIONS);
    }

    FusedLocationProviderClient(Activity activity) {
        super(activity, API, Api.ApiOptions.NO_OPTIONS, Settings.DEFAULT_SETTINGS);
    }

    // getLastLocation(): Task<Location> resolving with the newest cached fix or
    // null -- the platform fused provider's last known location. A caller
    // without a location permission gets null, not a fabricated point.
    public Task<Location> getLastLocation() {
        TaskCompletionSource<Location> tcs = new TaskCompletionSource<>();
        Location l = null;
        LocationManager lm = locationManager();
        if (lm != null) {
            try { l = lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER); }
            catch (SecurityException e) { l = null; }
        }
        tcs.setResult(l);
        return tcs.getTask();
    }

    // getCurrentLocation(...) overloads -- the modern one-shot fix request,
    // answered by the platform's getCurrentLocation (a fix younger than 30 s at
    // once, else the next one within 30 s, else null).
    public Task<Location> getCurrentLocation(int priority, Object cancellationToken) {
        return currentLocation(Long.MAX_VALUE);
    }

    public Task<Location> getCurrentLocation(LocationRequest request, Object cancellationToken) {
        return currentLocation(request != null ? request.getDurationMillis() : Long.MAX_VALUE);
    }

    private Task<Location> currentLocation(long durationMillis) {
        TaskCompletionSource<Location> tcs = new TaskCompletionSource<>();
        LocationManager lm = locationManager();
        if (lm == null) {
            tcs.setResult(null);
            return tcs.getTask();
        }
        try {
            android.location.LocationRequest.Builder b = new android.location.LocationRequest.Builder(0);
            if (durationMillis > 0 && durationMillis < Long.MAX_VALUE) b.setDurationMillis(durationMillis);
            lm.getCurrentLocation(LocationManager.FUSED_PROVIDER, b.build(), null,
                    Runnable::run, tcs::setResult);
        } catch (SecurityException e) {
            tcs.setResult(null);
        }
        return tcs.getTask();
    }

    private LocationManager locationManager() {
        try {
            Context ctx = getApplicationContext();
            if (ctx == null) return null;
            Object svc = ctx.getSystemService(Context.LOCATION_SERVICE);
            return svc instanceof LocationManager ? (LocationManager) svc : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // requestLocationUpdates: a platform registration on the fused provider (or
    // passive, for PRIORITY_PASSIVE) whose fixes arrive as LocationResults. The
    // registration Task completes once the request is in place, as the real
    // client's does; a SecurityException fails it, as the real client's does.
    private final Map<Object, android.location.LocationListener> listeners =
            new ConcurrentHashMap<>();

    public Task<Void> requestLocationUpdates(LocationRequest request,
                                             LocationCallback callback,
                                             Looper looper) {
        Looper l = looper != null ? looper : Looper.myLooper();
        if (l == null) l = Looper.getMainLooper();
        android.os.Handler h = new android.os.Handler(l);
        return requestLocationUpdates(request, h::post, callback);
    }

    public Task<Void> requestLocationUpdates(LocationRequest request,
                                             Executor executor,
                                             LocationCallback callback) {
        LocationManager lm = locationManager();
        if (lm == null || callback == null) return Tasks.forResult(null);
        android.location.LocationListener listener = new android.location.LocationListener() {
            @Override public void onLocationChanged(Location location) {
                callback.onLocationResult(
                        LocationResult.create(Collections.singletonList(location)));
            }
            @Override public void onProviderEnabled(String provider) {
                callback.onLocationAvailability(new LocationAvailability(true));
            }
            @Override public void onProviderDisabled(String provider) {
                callback.onLocationAvailability(LocationAvailability.unavailable());
            }
        };
        android.location.LocationListener old = listeners.put(callback, listener);
        if (old != null) lm.removeUpdates(old);
        try {
            lm.requestLocationUpdates(provider(request), platformRequest(request), executor,
                    listener);
        } catch (SecurityException e) {
            listeners.remove(callback);
            return Tasks.forException(e);
        }
        return Tasks.forResult(null);
    }

    public Task<Void> requestLocationUpdates(LocationRequest request,
                                             PendingIntent pendingIntent) {
        LocationManager lm = locationManager();
        if (lm == null || pendingIntent == null) return Tasks.forResult(null);
        try {
            lm.requestLocationUpdates(provider(request), platformRequest(request), pendingIntent);
        } catch (SecurityException e) {
            return Tasks.forException(e);
        }
        return Tasks.forResult(null);
    }

    public Task<Void> removeLocationUpdates(LocationCallback callback) {
        android.location.LocationListener l = callback != null ? listeners.remove(callback) : null;
        LocationManager lm = locationManager();
        if (l != null && lm != null) lm.removeUpdates(l);
        return Tasks.forResult(null);
    }

    public Task<Void> removeLocationUpdates(PendingIntent pendingIntent) {
        LocationManager lm = locationManager();
        if (lm != null && pendingIntent != null) lm.removeUpdates(pendingIntent);
        return Tasks.forResult(null);
    }

    private static String provider(LocationRequest r) {
        return r != null && r.getPriority() == Priority.PRIORITY_PASSIVE
                ? LocationManager.PASSIVE_PROVIDER : LocationManager.FUSED_PROVIDER;
    }

    private static android.location.LocationRequest platformRequest(LocationRequest r) {
        if (r == null) return new android.location.LocationRequest.Builder(0).build();
        android.location.LocationRequest.Builder b =
                new android.location.LocationRequest.Builder(Math.max(0L, r.getIntervalMillis()))
                        .setMinUpdateIntervalMillis(Math.max(0L, r.getMinUpdateIntervalMillis()))
                        .setMinUpdateDistanceMeters(Math.max(0f, r.getMinUpdateDistanceMeters()))
                        .setQuality(r.getPriority());
        if (r.getMaxUpdates() > 0 && r.getMaxUpdates() < Integer.MAX_VALUE) b.setMaxUpdates(r.getMaxUpdates());
        if (r.getDurationMillis() > 0 && r.getDurationMillis() < Long.MAX_VALUE) b.setDurationMillis(r.getDurationMillis());
        return b.build();
    }

    // flushLocations: nothing buffered to flush -> complete cleanly.
    public Task<Void> flushLocations() {
        return Tasks.forResult(null);
    }

    // getLocationAvailability: available when the platform's fused provider is
    // enabled and the app may receive its fixes.
    public Task<LocationAvailability> getLocationAvailability() {
        LocationManager lm = locationManager();
        boolean ok = false;
        try {
            ok = lm != null && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)
                    && !lm.getProviders(true).isEmpty();
        } catch (Throwable ignored) { }
        return Tasks.forResult(ok ? new LocationAvailability(true) : LocationAvailability.unavailable());
    }

    // Mock-location setters succeed as no-ops (apps in test mode call these).
    public Task<Void> setMockMode(boolean isMockMode) { return Tasks.forResult(null); }
    public Task<Void> setMockLocation(Location mockLocation) { return Tasks.forResult(null); }
}
