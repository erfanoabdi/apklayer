// com.google.android.gms.location.LocationSettingsStates -- the per-source
// enabled/usable flags returned inside a LocationSettingsResponse. Ref:
// developers.google.com/android/reference/com/google/android/gms/location/
// LocationSettingsStates. On this device GPS/network providers exist at the
// platform layer, so we report them present-and-usable; the BLE source is
// reported absent (no companion-device scanning backend).
package com.google.android.gms.location;

public final class LocationSettingsStates {
    private final boolean gpsUsable;
    private final boolean networkUsable;

    LocationSettingsStates(boolean gpsUsable, boolean networkUsable) {
        this.gpsUsable = gpsUsable;
        this.networkUsable = networkUsable;
    }

    public boolean isGpsPresent() { return true; }
    public boolean isGpsUsable() { return gpsUsable; }
    public boolean isNetworkLocationPresent() { return true; }
    public boolean isNetworkLocationUsable() { return networkUsable; }
    public boolean isLocationPresent() { return true; }
    public boolean isLocationUsable() { return gpsUsable || networkUsable; }
    public boolean isBlePresent() { return false; }
    public boolean isBleUsable() { return false; }
}
