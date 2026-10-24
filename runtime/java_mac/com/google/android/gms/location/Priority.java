// com.google.android.gms.location.Priority -- the modern location-request
// priority constants (replaced LocationRequest.PRIORITY_* in play-services-
// location 21.x). Ref: developers.google.com/android/reference/com/google/
// android/gms/location/Priority. Values match the published artifact so apps
// that pass a Priority int into LocationRequest.Builder compare correctly.
package com.google.android.gms.location;

public final class Priority {
    private Priority() {}

    public static final int PRIORITY_HIGH_ACCURACY = 100;
    public static final int PRIORITY_BALANCED_POWER_ACCURACY = 102;
    public static final int PRIORITY_LOW_POWER = 104;
    public static final int PRIORITY_PASSIVE = 105;
}
