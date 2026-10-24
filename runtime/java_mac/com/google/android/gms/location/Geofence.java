// com.google.android.gms.location.Geofence -- a single circular geofence
// definition. Ref: developers.google.com/android/reference/com/google/android/
// gms/location/Geofence. We retain the geometry/transition fields for fidelity;
// the GeofencingClient registers them as a no-op (no fix source => no
// transitions, matching a device with location unavailable).
package com.google.android.gms.location;

public final class Geofence {
    public static final int GEOFENCE_TRANSITION_ENTER = 1;
    public static final int GEOFENCE_TRANSITION_EXIT = 2;
    public static final int GEOFENCE_TRANSITION_DWELL = 4;

    public static final long NEVER_EXPIRE = -1L;

    public static final int LOITERING_DELAY_NONE = -1;

    private final String requestId;
    private final double latitude;
    private final double longitude;
    private final float radius;
    private final long expirationDuration;
    private final int transitionTypes;
    private final int loiteringDelay;
    private final int notificationResponsiveness;

    private Geofence(String requestId, double latitude, double longitude, float radius,
                     long expirationDuration, int transitionTypes, int loiteringDelay,
                     int notificationResponsiveness) {
        this.requestId = requestId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
        this.expirationDuration = expirationDuration;
        this.transitionTypes = transitionTypes;
        this.loiteringDelay = loiteringDelay;
        this.notificationResponsiveness = notificationResponsiveness;
    }

    public String getRequestId() { return requestId; }

    public static final class Builder {
        private String requestId;
        private double latitude;
        private double longitude;
        private float radius;
        private long expirationDuration = NEVER_EXPIRE;
        private int transitionTypes;
        private int loiteringDelay = LOITERING_DELAY_NONE;
        private int notificationResponsiveness = 0;

        public Builder setRequestId(String requestId) { this.requestId = requestId; return this; }

        public Builder setCircularRegion(double latitude, double longitude, float radius) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.radius = radius;
            return this;
        }

        public Builder setExpirationDuration(long durationMillis) {
            this.expirationDuration = durationMillis;
            return this;
        }

        public Builder setTransitionTypes(int transitionTypes) {
            this.transitionTypes = transitionTypes;
            return this;
        }

        public Builder setLoiteringDelay(int loiteringDelayMs) {
            this.loiteringDelay = loiteringDelayMs;
            return this;
        }

        public Builder setNotificationResponsiveness(int notificationResponsivenessMs) {
            this.notificationResponsiveness = notificationResponsivenessMs;
            return this;
        }

        public Geofence build() {
            return new Geofence(requestId, latitude, longitude, radius, expirationDuration,
                    transitionTypes, loiteringDelay, notificationResponsiveness);
        }
    }
}
