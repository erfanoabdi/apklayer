package android.location;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A batch of raw GNSS measurements.
 *
 * No GNSS receiver on a Mac, so no event is ever delivered and the callback
 * reports STATUS_NOT_SUPPORTED -- the same value AOSP uses on a device whose
 * chipset cannot produce raw measurements (common even on real phones). The
 * value type itself is faithful, including the Builder and the parcel layout.
 *
 * Source: frameworks/base/location/java/android/location/GnssMeasurementsEvent.java
 */
public final class GnssMeasurementsEvent implements Parcelable {

    private static final int HAS_IS_FULL_TRACKING = 1;

    private final int mFlag;
    private final GnssClock mClock;
    private final List<GnssMeasurement> mMeasurements;
    private final List<GnssAutomaticGainControl> mGnssAgcs;
    private final boolean mIsFullTracking;

    private GnssMeasurementsEvent(int flag, GnssClock clock, List<GnssMeasurement> measurements,
            List<GnssAutomaticGainControl> agcs, boolean isFullTracking) {
        mFlag = flag;
        mClock = clock;
        mMeasurements = measurements == null
                ? Collections.<GnssMeasurement>emptyList() : measurements;
        mGnssAgcs = agcs == null ? Collections.<GnssAutomaticGainControl>emptyList() : agcs;
        mIsFullTracking = isFullTracking;
    }

    public GnssClock getClock() { return mClock; }

    public Collection<GnssMeasurement> getMeasurements() { return mMeasurements; }

    public Collection<GnssAutomaticGainControl> getGnssAutomaticGainControls() {
        return mGnssAgcs;
    }

    /** Only meaningful when {@link #hasIsFullTracking()} is true, per AOSP. */
    public boolean isFullTracking() { return mIsFullTracking; }

    public boolean hasIsFullTracking() {
        return (mFlag & HAS_IS_FULL_TRACKING) == HAS_IS_FULL_TRACKING;
    }

    public static final Creator<GnssMeasurementsEvent> CREATOR =
            new Creator<GnssMeasurementsEvent>() {
        @Override public GnssMeasurementsEvent createFromParcel(Parcel in) {
            int flag = in.readInt();
            GnssClock clock = in.readParcelable(getClass().getClassLoader(), GnssClock.class);
            List<GnssMeasurement> measurements = in.createTypedArrayList(GnssMeasurement.CREATOR);
            List<GnssAutomaticGainControl> agcs =
                    in.createTypedArrayList(GnssAutomaticGainControl.CREATOR);
            boolean isFullTracking = in.readBoolean();
            return new GnssMeasurementsEvent(flag, clock, measurements, agcs, isFullTracking);
        }
        @Override public GnssMeasurementsEvent[] newArray(int size) {
            return new GnssMeasurementsEvent[size];
        }
    };

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mFlag);
        parcel.writeParcelable(mClock, flags);
        parcel.writeTypedList(mMeasurements);
        parcel.writeTypedList(mGnssAgcs);
        parcel.writeBoolean(mIsFullTracking);
    }

    @Override public String toString() {
        StringBuilder builder = new StringBuilder("GnssMeasurementsEvent[");
        builder.append(mClock);
        builder.append(' ').append(mMeasurements);
        if (!mGnssAgcs.isEmpty()) builder.append(' ').append(mGnssAgcs);
        if (hasIsFullTracking()) builder.append(" isFullTracking=").append(mIsFullTracking);
        return builder.append(']').toString();
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GnssMeasurementsEvent)) return false;
        GnssMeasurementsEvent that = (GnssMeasurementsEvent) o;
        return mFlag == that.mFlag && mIsFullTracking == that.mIsFullTracking
                && Objects.equals(mClock, that.mClock)
                && mMeasurements.equals(that.mMeasurements)
                && mGnssAgcs.equals(that.mGnssAgcs);
    }

    @Override public int hashCode() {
        return Objects.hash(mFlag, mClock, mMeasurements, mGnssAgcs, mIsFullTracking);
    }

    /** AOSP declares Callback as an abstract class apps subclass. */
    public abstract static class Callback {
        public static final int STATUS_NOT_SUPPORTED = 0;
        public static final int STATUS_READY = 1;
        public static final int STATUS_LOCATION_DISABLED = 2;
        public static final int STATUS_NOT_ALLOWED = 3;

        public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) { }

        public void onStatusChanged(int status) { }
    }

    public static final class Builder {
        private int mFlag;
        private GnssClock mClock;
        private List<GnssMeasurement> mMeasurements;
        private List<GnssAutomaticGainControl> mGnssAgcs;
        private boolean mIsFullTracking;

        public Builder() {
            mClock = new GnssClock();
            mMeasurements = new ArrayList<>();
            mGnssAgcs = new ArrayList<>();
        }

        public Builder(GnssMeasurementsEvent event) {
            mFlag = event.mFlag;
            mClock = event.getClock();
            mMeasurements = new ArrayList<>(event.getMeasurements());
            mGnssAgcs = new ArrayList<>(event.getGnssAutomaticGainControls());
            mIsFullTracking = event.isFullTracking();
        }

        public Builder setClock(GnssClock clock) {
            mClock = Objects.requireNonNull(clock);
            return this;
        }

        public Builder setMeasurements(GnssMeasurement... measurements) {
            mMeasurements = measurements == null
                    ? Collections.<GnssMeasurement>emptyList() : Arrays.asList(measurements);
            return this;
        }

        public Builder setMeasurements(Collection<GnssMeasurement> measurements) {
            mMeasurements = new ArrayList<>(measurements);
            return this;
        }

        public Builder setGnssAutomaticGainControls(GnssAutomaticGainControl... agcs) {
            mGnssAgcs = agcs == null
                    ? Collections.<GnssAutomaticGainControl>emptyList() : Arrays.asList(agcs);
            return this;
        }

        public Builder setGnssAutomaticGainControls(Collection<GnssAutomaticGainControl> agcs) {
            mGnssAgcs = new ArrayList<>(agcs);
            return this;
        }

        public Builder setIsFullTracking(boolean isFullTracking) {
            mFlag |= HAS_IS_FULL_TRACKING;
            mIsFullTracking = isFullTracking;
            return this;
        }

        public Builder clearIsFullTracking() {
            mFlag &= ~HAS_IS_FULL_TRACKING;
            return this;
        }

        public GnssMeasurementsEvent build() {
            return new GnssMeasurementsEvent(mFlag, mClock, mMeasurements, mGnssAgcs,
                    mIsFullTracking);
        }
    }
}
