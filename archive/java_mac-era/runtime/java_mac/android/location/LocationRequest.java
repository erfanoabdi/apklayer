// android.location.LocationRequest -- parameters for a location-update
// request (interval, quality, etc.). Builder-shaped per AOSP. Mac.apk
// never delivers fixes, but the type + Builder must exist so apps that
// construct a request and pass it to LocationManager.requestLocationUpdates
// compile and run. Data holder only.
//
// Source: frameworks/base/location/java/android/location/LocationRequest.java
package android.location;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

public final class LocationRequest implements Parcelable {
    public static final int QUALITY_LOW_POWER = 104;
    public static final int QUALITY_BALANCED_POWER_ACCURACY = 102;
    public static final int QUALITY_HIGH_ACCURACY = 100;

    public static final long PASSIVE_INTERVAL = Long.MAX_VALUE;

    /**
     * AOSP's sentinel for "the caller never set a minimum update interval", which
     * is NOT the same as setting it to 0 -- an unset minimum is derived from the
     * interval instead. Stored in the parcel verbatim.
     */
    private static final long IMPLICIT_MIN_UPDATE_INTERVAL = -1;
    private static final double LEGACY_IMPLICIT_MIN_UPDATE_INTERVAL_FACTOR = 1D / 6D;

    private final long intervalMillis;
    private final long minUpdateIntervalMillis;
    private final float minUpdateDistanceMeters;
    private final int quality;
    private final long durationMillis;
    private final int maxUpdates;
    private final long maxUpdateDelayMillis;

    private LocationRequest(Builder b) {
        this.intervalMillis = b.intervalMillis;
        this.minUpdateIntervalMillis = b.minUpdateIntervalMillis;
        this.minUpdateDistanceMeters = b.minUpdateDistanceMeters;
        this.quality = b.quality;
        this.durationMillis = b.durationMillis;
        this.maxUpdates = b.maxUpdates;
        this.maxUpdateDelayMillis = b.maxUpdateDelayMillis;
    }

    public long getIntervalMillis() { return intervalMillis; }

    /**
     * AOSP does NOT return the interval when the minimum is unset -- it returns a
     * fraction of it (historically 1/6th), and otherwise min(minimum, interval).
     * Mirror that, because an app can read this back.
     */
    public long getMinUpdateIntervalMillis() {
        if (minUpdateIntervalMillis == IMPLICIT_MIN_UPDATE_INTERVAL) {
            return (long) (intervalMillis * LEGACY_IMPLICIT_MIN_UPDATE_INTERVAL_FACTOR);
        }
        // AOSP clamps because a deprecated setter can leave the two inconsistent.
        return Math.min(minUpdateIntervalMillis, intervalMillis);
    }

    public float getMinUpdateDistanceMeters() { return minUpdateDistanceMeters; }
    public int getQuality() { return quality; }
    public long getDurationMillis() { return durationMillis; }
    public int getMaxUpdates() { return maxUpdates; }
    public long getMaxUpdateDelayMillis() { return maxUpdateDelayMillis; }

    // === Parcelable =========================================================
    // AOSP's own parcel carries several @hide fields (provider, expireAt,
    // hideFromAppOps, adasGnssBypass, bypass, lowPower, WorkSource). Those are
    // system-server plumbing that no app can set or read, and Mac.apk has no
    // system server, so they are not modelled. The public fields are written in
    // AOSP's order so a reader of this parcel sees a coherent, self-consistent
    // request.
    public static final Creator<LocationRequest> CREATOR = new Creator<LocationRequest>() {
        @Override public LocationRequest createFromParcel(Parcel in) {
            Builder b = new Builder(in.readLong());
            b.quality = in.readInt();
            b.durationMillis = in.readLong();
            b.maxUpdates = in.readInt();
            b.minUpdateIntervalMillis = in.readLong();
            b.minUpdateDistanceMeters = in.readFloat();
            b.maxUpdateDelayMillis = in.readLong();
            return b.build();
        }
        @Override public LocationRequest[] newArray(int size) { return new LocationRequest[size]; }
    };

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeLong(intervalMillis);
        parcel.writeInt(quality);
        parcel.writeLong(durationMillis);
        parcel.writeInt(maxUpdates);
        parcel.writeLong(minUpdateIntervalMillis);
        parcel.writeFloat(minUpdateDistanceMeters);
        parcel.writeLong(maxUpdateDelayMillis);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocationRequest that = (LocationRequest) o;
        return intervalMillis == that.intervalMillis
                && quality == that.quality
                && durationMillis == that.durationMillis
                && maxUpdates == that.maxUpdates
                && minUpdateIntervalMillis == that.minUpdateIntervalMillis
                && Float.compare(that.minUpdateDistanceMeters, minUpdateDistanceMeters) == 0
                && maxUpdateDelayMillis == that.maxUpdateDelayMillis;
    }

    @Override public int hashCode() {
        return Objects.hash(intervalMillis, quality, minUpdateIntervalMillis,
                minUpdateDistanceMeters);
    }

    @Override public String toString() {
        StringBuilder s = new StringBuilder("Request[");
        if (intervalMillis == PASSIVE_INTERVAL) {
            s.append("PASSIVE");
        } else {
            s.append("@").append(intervalMillis).append("ms");
        }
        s.append(", quality=").append(quality);
        return s.append(']').toString();
    }

    public static final class Builder {
        private long intervalMillis;
        private long minUpdateIntervalMillis = IMPLICIT_MIN_UPDATE_INTERVAL;
        private float minUpdateDistanceMeters = 0f;
        private int quality = QUALITY_BALANCED_POWER_ACCURACY;
        private long durationMillis = Long.MAX_VALUE;
        private int maxUpdates = Integer.MAX_VALUE;
        private long maxUpdateDelayMillis = 0;

        public Builder(long intervalMillis) { this.intervalMillis = intervalMillis; }

        public Builder(LocationRequest request) {
            this.intervalMillis = request.intervalMillis;
            this.minUpdateIntervalMillis = request.minUpdateIntervalMillis;
            this.minUpdateDistanceMeters = request.minUpdateDistanceMeters;
            this.quality = request.quality;
            this.durationMillis = request.durationMillis;
            this.maxUpdates = request.maxUpdates;
            this.maxUpdateDelayMillis = request.maxUpdateDelayMillis;
        }

        public Builder setIntervalMillis(long v) {
            // AOSP: Preconditions.checkArgumentInRange(v, 0, Long.MAX_VALUE, "intervalMillis").
            if (v < 0) throw new IllegalArgumentException("intervalMillis must be non-negative");
            this.intervalMillis = v;
            return this;
        }

        public Builder setMinUpdateIntervalMillis(long v) {
            if (v < 0) {
                throw new IllegalArgumentException("minUpdateIntervalMillis must be non-negative");
            }
            this.minUpdateIntervalMillis = v;
            return this;
        }

        /** Restores the "never explicitly set" state, per AOSP. */
        public Builder clearMinUpdateIntervalMillis() {
            this.minUpdateIntervalMillis = IMPLICIT_MIN_UPDATE_INTERVAL;
            return this;
        }

        public Builder setMinUpdateDistanceMeters(float v) {
            if (!(v >= 0)) {
                throw new IllegalArgumentException("minUpdateDistanceMeters must be non-negative");
            }
            this.minUpdateDistanceMeters = v;
            return this;
        }

        public Builder setQuality(int v) { this.quality = v; return this; }

        public Builder setDurationMillis(long v) {
            if (v < 1) throw new IllegalArgumentException("durationMillis must be at least 1");
            this.durationMillis = v;
            return this;
        }

        public Builder setMaxUpdates(int v) {
            if (v < 1) throw new IllegalArgumentException("maxUpdates must be at least 1");
            this.maxUpdates = v;
            return this;
        }

        public Builder setMaxUpdateDelayMillis(long v) {
            if (v < 0) {
                throw new IllegalArgumentException("maxUpdateDelayMillis must be non-negative");
            }
            this.maxUpdateDelayMillis = v;
            return this;
        }

        public Builder setWaitForAccurateLocation(boolean v) { return this; }

        public LocationRequest build() { return new LocationRequest(this); }
    }
}
