// com.google.android.gms.location.LocationAvailability -- reports whether the
// fused provider currently has enough signals to deliver a usable location.
// Ref: developers.google.com/android/reference/com/google/android/gms/location/
// LocationAvailability. On the no-Play-backend device location is never
// available (no fused fixes), so isLocationAvailable() returns false -- the
// honest negative a real device with location off / no fix would report.
package com.google.android.gms.location;

public final class LocationAvailability {
    public static final String EXTRA_LOCATION_AVAILABILITY =
            "com.google.android.gms.location.EXTRA_LOCATION_AVAILABILITY";

    private final boolean available;

    LocationAvailability(boolean available) { this.available = available; }

    // Real ctor signature: LocationAvailability(int, int, int, long, NetworkLocationStatus[]).
    // We expose the locationStatus-driven public form used by app code via the
    // static-ish constants below; the int ctor maps a status < 1000 to available.
    public LocationAvailability(int locationStatus, int elapsedRealtimeNs,
                                int unused1, long timeNs) {
        this.available = locationStatus < 1000;
    }

    public boolean isLocationAvailable() { return available; }

    // The de-Googled device has no fused fix source, so location is unavailable.
    static LocationAvailability unavailable() { return new LocationAvailability(false); }

    @Override
    public String toString() {
        return "LocationAvailability[isLocationAvailable: " + available + "]";
    }
}
