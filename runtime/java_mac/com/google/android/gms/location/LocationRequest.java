// com.google.android.gms.location.LocationRequest -- the GMS fused-location
// request descriptor. DISTINCT from android.location.LocationRequest (the AOSP
// platform type); apps that integrate play-services-location construct THIS
// class. Ref: developers.google.com/android/reference/com/google/android/gms/
// location/LocationRequest.
//
// We mirror both the legacy mutable setter surface (create()/setInterval/
// setPriority/...) and the modern Builder (play-services-location 18+). The
// object is purely a value holder we hand back to FusedLocationProviderClient;
// since the no-Play-backend device produces no fused fixes, only the getters
// matter for apps that read their own request back.
package com.google.android.gms.location;

public final class LocationRequest {
    // Legacy priority constants kept for source/binary compat with apps that
    // still reference LocationRequest.PRIORITY_* (these mirror Priority.*).
    public static final int PRIORITY_HIGH_ACCURACY = Priority.PRIORITY_HIGH_ACCURACY;
    public static final int PRIORITY_BALANCED_POWER_ACCURACY = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
    public static final int PRIORITY_LOW_POWER = Priority.PRIORITY_LOW_POWER;
    public static final int PRIORITY_NO_POWER = Priority.PRIORITY_PASSIVE;

    private long intervalMillis = 3600000L;
    private long minUpdateIntervalMillis = -1L; // -1 => derive from interval
    private long maxUpdateDelayMillis = 0L;
    private int priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
    private long durationMillis = Long.MAX_VALUE;
    private int maxUpdates = Integer.MAX_VALUE;
    private float minUpdateDistanceMeters = 0f;
    private boolean waitForAccurateLocation = false;
    private long maxUpdateAgeMillis = Long.MAX_VALUE;

    // Deprecated public no-arg create() factory (pre-Builder API).
    public LocationRequest() {}

    @Deprecated
    public static LocationRequest create() { return new LocationRequest(); }

    // ---- legacy mutable setters (deprecated in modern SDK, still shipped) ----
    @Deprecated
    public LocationRequest setInterval(long millis) {
        this.intervalMillis = millis;
        if (minUpdateIntervalMillis == -1L || minUpdateIntervalMillis > millis) {
            // real SDK clamps fastest to interval when fastest is unset
        }
        return this;
    }

    @Deprecated
    public LocationRequest setFastestInterval(long millis) {
        this.minUpdateIntervalMillis = millis;
        return this;
    }

    @Deprecated
    public LocationRequest setPriority(int priority) { this.priority = priority; return this; }

    @Deprecated
    public LocationRequest setSmallestDisplacement(float meters) { this.minUpdateDistanceMeters = meters; return this; }

    @Deprecated
    public LocationRequest setNumUpdates(int numUpdates) { this.maxUpdates = numUpdates; return this; }

    @Deprecated
    public LocationRequest setExpirationDuration(long durationMillis) { this.durationMillis = durationMillis; return this; }

    @Deprecated
    public LocationRequest setMaxWaitTime(long millis) { this.maxUpdateDelayMillis = millis; return this; }

    @Deprecated
    public LocationRequest setWaitForAccurateLocation(boolean wait) { this.waitForAccurateLocation = wait; return this; }

    // ---- getters (modern names) ----
    public long getIntervalMillis() { return intervalMillis; }
    public long getMinUpdateIntervalMillis() {
        return minUpdateIntervalMillis == -1L ? intervalMillis : minUpdateIntervalMillis;
    }
    public long getMaxUpdateDelayMillis() { return maxUpdateDelayMillis; }
    public int getPriority() { return priority; }
    public long getDurationMillis() { return durationMillis; }
    public int getMaxUpdates() { return maxUpdates; }
    public float getMinUpdateDistanceMeters() { return minUpdateDistanceMeters; }
    public boolean isWaitForAccurateLocation() { return waitForAccurateLocation; }
    public long getMaxUpdateAgeMillis() { return maxUpdateAgeMillis; }

    // ---- legacy getters (deprecated names some apps still read) ----
    @Deprecated public long getInterval() { return intervalMillis; }
    @Deprecated public long getFastestInterval() { return getMinUpdateIntervalMillis(); }
    @Deprecated public float getSmallestDisplacement() { return minUpdateDistanceMeters; }
    @Deprecated public int getNumUpdates() { return maxUpdates; }

    @Override
    public String toString() {
        return "Request[priority=" + priority + ", interval=" + intervalMillis + "ms]";
    }

    // ---- modern Builder (play-services-location 18+) ----
    public static final class Builder {
        private final LocationRequest req = new LocationRequest();

        // Builder(long intervalMillis)
        public Builder(long intervalMillis) {
            req.intervalMillis = intervalMillis;
            req.priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        }

        // Builder(int priority, long intervalMillis)
        public Builder(int priority, long intervalMillis) {
            req.priority = priority;
            req.intervalMillis = intervalMillis;
        }

        // Copy ctor from an existing request.
        public Builder(LocationRequest source) {
            if (source != null) {
                req.intervalMillis = source.intervalMillis;
                req.minUpdateIntervalMillis = source.minUpdateIntervalMillis;
                req.maxUpdateDelayMillis = source.maxUpdateDelayMillis;
                req.priority = source.priority;
                req.durationMillis = source.durationMillis;
                req.maxUpdates = source.maxUpdates;
                req.minUpdateDistanceMeters = source.minUpdateDistanceMeters;
                req.waitForAccurateLocation = source.waitForAccurateLocation;
                req.maxUpdateAgeMillis = source.maxUpdateAgeMillis;
            }
        }

        public Builder setIntervalMillis(long intervalMillis) { req.intervalMillis = intervalMillis; return this; }
        public Builder setMinUpdateIntervalMillis(long millis) { req.minUpdateIntervalMillis = millis; return this; }
        public Builder setMaxUpdateDelayMillis(long millis) { req.maxUpdateDelayMillis = millis; return this; }
        public Builder setPriority(int priority) { req.priority = priority; return this; }
        public Builder setDurationMillis(long millis) { req.durationMillis = millis; return this; }
        public Builder setMaxUpdates(int maxUpdates) { req.maxUpdates = maxUpdates; return this; }
        public Builder setMinUpdateDistanceMeters(float meters) { req.minUpdateDistanceMeters = meters; return this; }
        public Builder setWaitForAccurateLocation(boolean wait) { req.waitForAccurateLocation = wait; return this; }
        public Builder setMaxUpdateAgeMillis(long millis) { req.maxUpdateAgeMillis = millis; return this; }

        public LocationRequest build() { return req; }
    }
}
