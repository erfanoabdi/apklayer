// com.google.android.gms.location.LocationCallback -- the callback an app
// registers via FusedLocationProviderClient.requestLocationUpdates. Ref:
// developers.google.com/android/reference/com/google/android/gms/location/
// LocationCallback. Both methods are no-op overridables in the real SDK; the
// app overrides onLocationResult. On the no-Play-backend device the fused
// provider delivers no results, so neither fires (matches a device with
// location turned off / no fix available).
package com.google.android.gms.location;

public abstract class LocationCallback {
    public void onLocationResult(LocationResult result) {}

    public void onLocationAvailability(LocationAvailability availability) {}
}
