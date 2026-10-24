package android.location;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Request parameters for LocationManager.registerGnssMeasurementsCallback.
 *
 * A Mac has no GNSS receiver, so the request is accepted and no measurement is
 * ever delivered (the callback reports STATUS_NOT_SUPPORTED, which is what AOSP
 * reports on any device whose chipset cannot produce raw measurements).
 * Immutable value type.
 *
 * Source: frameworks/base/location/java/android/location/GnssMeasurementRequest.java
 */
public final class GnssMeasurementRequest implements Parcelable {

    /** AOSP: represents a passive-only request; no active measurement is scheduled. */
    public static final int PASSIVE_INTERVAL = Integer.MAX_VALUE;

    private final boolean mFullTracking;
    private final int mIntervalMillis;

    private GnssMeasurementRequest(boolean fullTracking, int intervalMillis) {
        mFullTracking = fullTracking;
        mIntervalMillis = intervalMillis;
    }

    public boolean isFullTracking() { return mFullTracking; }

    public int getIntervalMillis() { return mIntervalMillis; }

    public static final Creator<GnssMeasurementRequest> CREATOR =
            new Creator<GnssMeasurementRequest>() {
        @Override public GnssMeasurementRequest createFromParcel(Parcel parcel) {
            return new GnssMeasurementRequest(parcel.readBoolean(), parcel.readInt());
        }
        @Override public GnssMeasurementRequest[] newArray(int i) {
            return new GnssMeasurementRequest[i];
        }
    };

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeBoolean(mFullTracking);
        parcel.writeInt(mIntervalMillis);
    }

    @Override public int describeContents() { return 0; }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GnssMeasurementRequest)) return false;
        GnssMeasurementRequest o = (GnssMeasurementRequest) obj;
        return mFullTracking == o.mFullTracking && mIntervalMillis == o.mIntervalMillis;
    }

    @Override public int hashCode() {
        return (mFullTracking ? 1 : 0) * 31 + mIntervalMillis;
    }

    @Override public String toString() {
        return "GnssMeasurementRequest[@" + mIntervalMillis
                + (mFullTracking ? ", FullTracking" : "") + ']';
    }

    public static final class Builder {
        private boolean mFullTracking;
        // AOSP's default is DEFAULT_INTERVAL_MILLIS == 1000.
        private int mIntervalMillis = 1000;

        public Builder() { }

        public Builder(GnssMeasurementRequest request) {
            mFullTracking = request.isFullTracking();
            mIntervalMillis = request.getIntervalMillis();
        }

        public Builder setFullTracking(boolean value) {
            mFullTracking = value;
            return this;
        }

        public Builder setIntervalMillis(int value) {
            // AOSP: Preconditions.checkArgumentInRange(value, 0, Integer.MAX_VALUE, ...).
            if (value < 0) {
                throw new IllegalArgumentException("intervalMillis must be non-negative");
            }
            mIntervalMillis = value;
            return this;
        }

        public GnssMeasurementRequest build() {
            return new GnssMeasurementRequest(mFullTracking, mIntervalMillis);
        }
    }
}
