// com.google.android.gms.location.LocationServices -- the static factory entry
// point for the location clients. Ref: developers.google.com/android/reference/
// com/google/android/gms/location/LocationServices. Each getXxxClient returns
// a fresh client bound to the supplied Context/Activity, mirroring the real
// SDK (clients are cheap value objects, not singletons).
package com.google.android.gms.location;

import android.app.Activity;
import android.content.Context;

import com.google.android.gms.common.api.Api;

public final class LocationServices {
    private LocationServices() {}

    // The Api token apps reference when building a GoogleApiClient. Reuse the
    // FusedLocationProviderClient's token so the marker identity is stable.
    public static final Api<Api.ApiOptions.NoOptions> API = FusedLocationProviderClient.API;

    public static FusedLocationProviderClient getFusedLocationProviderClient(Context context) {
        return new FusedLocationProviderClient(context);
    }

    public static FusedLocationProviderClient getFusedLocationProviderClient(Activity activity) {
        return new FusedLocationProviderClient(activity);
    }

    public static SettingsClient getSettingsClient(Context context) {
        return new SettingsClient(context);
    }

    public static SettingsClient getSettingsClient(Activity activity) {
        return new SettingsClient(activity);
    }

    public static GeofencingClient getGeofencingClient(Context context) {
        return new GeofencingClient(context);
    }

    public static GeofencingClient getGeofencingClient(Activity activity) {
        return new GeofencingClient(activity);
    }
}
