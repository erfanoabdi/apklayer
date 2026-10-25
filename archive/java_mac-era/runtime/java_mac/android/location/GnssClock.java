package android.location;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * GNSS receiver clock state for a measurements batch.
 *
 * A Mac has no GNSS receiver, so no GnssClock is ever handed to an app by the
 * framework. The class is still a faithful data holder: AOSP's own contract is
 * a flags word plus per-field "has"/"get"/"set"/"reset" triples, and a
 * default-constructed clock reports every optional field ABSENT (has* == false)
 * with the documented sentinel value. That is exactly what a real device
 * reports before the chipset has produced a measurement, so an app that
 * correctly checks has* before get* behaves identically here.
 *
 * Field order in writeToParcel/CREATOR matches AOSP byte-for-byte.
 *
 * Source: frameworks/base/location/java/android/location/GnssClock.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/location/java/android/location/GnssClock.java
 */
public final class GnssClock implements Parcelable {

    // AOSP: "The following enumerations must be in sync with the values
    // declared in gps.h".
    private static final int HAS_NO_FLAGS = 0;
    private static final int HAS_LEAP_SECOND = (1 << 0);
    private static final int HAS_TIME_UNCERTAINTY = (1 << 1);
    private static final int HAS_FULL_BIAS = (1 << 2);
    private static final int HAS_BIAS = (1 << 3);
    private static final int HAS_BIAS_UNCERTAINTY = (1 << 4);
    private static final int HAS_DRIFT = (1 << 5);
    private static final int HAS_DRIFT_UNCERTAINTY = (1 << 6);
    private static final int HAS_ELAPSED_REALTIME_NANOS = (1 << 7);
    private static final int HAS_ELAPSED_REALTIME_UNCERTAINTY_NANOS = (1 << 8);
    private static final int HAS_REFERENCE_CONSTELLATION_TYPE_FOR_ISB = (1 << 9);
    private static final int HAS_REFERENCE_CARRIER_FREQUENCY_FOR_ISB = (1 << 10);
    private static final int HAS_REFERENCE_CODE_TYPE_FOR_ISB = (1 << 11);

    private int mFlags;
    private int mLeapSecond;
    private long mTimeNanos;
    private double mTimeUncertaintyNanos;
    private long mFullBiasNanos;
    private double mBiasNanos;
    private double mBiasUncertaintyNanos;
    private double mDriftNanosPerSecond;
    private double mDriftUncertaintyNanosPerSecond;
    private int mHardwareClockDiscontinuityCount;
    private long mElapsedRealtimeNanos;
    private double mElapsedRealtimeUncertaintyNanos;
    private int mReferenceConstellationTypeForIsb;
    private double mReferenceCarrierFrequencyHzForIsb;
    private String mReferenceCodeTypeForIsb;

    /** AOSP marks this @hide @TestApi; it exists so the framework can build one. */
    public GnssClock() { initialize(); }

    public GnssClock(GnssClock clock) { set(clock); }

    public void set(GnssClock clock) {
        mFlags = clock.mFlags;
        mLeapSecond = clock.mLeapSecond;
        mTimeNanos = clock.mTimeNanos;
        mTimeUncertaintyNanos = clock.mTimeUncertaintyNanos;
        mFullBiasNanos = clock.mFullBiasNanos;
        mBiasNanos = clock.mBiasNanos;
        mBiasUncertaintyNanos = clock.mBiasUncertaintyNanos;
        mDriftNanosPerSecond = clock.mDriftNanosPerSecond;
        mDriftUncertaintyNanosPerSecond = clock.mDriftUncertaintyNanosPerSecond;
        mHardwareClockDiscontinuityCount = clock.mHardwareClockDiscontinuityCount;
        mElapsedRealtimeNanos = clock.mElapsedRealtimeNanos;
        mElapsedRealtimeUncertaintyNanos = clock.mElapsedRealtimeUncertaintyNanos;
        mReferenceConstellationTypeForIsb = clock.mReferenceConstellationTypeForIsb;
        mReferenceCarrierFrequencyHzForIsb = clock.mReferenceCarrierFrequencyHzForIsb;
        mReferenceCodeTypeForIsb = clock.mReferenceCodeTypeForIsb;
    }

    public void reset() { initialize(); }

    public boolean hasLeapSecond() { return isFlagSet(HAS_LEAP_SECOND); }
    public int getLeapSecond() { return mLeapSecond; }
    public void setLeapSecond(int leapSecond) {
        setFlag(HAS_LEAP_SECOND);
        mLeapSecond = leapSecond;
    }
    public void resetLeapSecond() {
        resetFlag(HAS_LEAP_SECOND);
        mLeapSecond = Integer.MIN_VALUE;
    }

    public long getTimeNanos() { return mTimeNanos; }
    public void setTimeNanos(long timeNanos) { mTimeNanos = timeNanos; }

    public boolean hasTimeUncertaintyNanos() { return isFlagSet(HAS_TIME_UNCERTAINTY); }
    public double getTimeUncertaintyNanos() { return mTimeUncertaintyNanos; }
    public void setTimeUncertaintyNanos(double timeUncertaintyNanos) {
        setFlag(HAS_TIME_UNCERTAINTY);
        mTimeUncertaintyNanos = timeUncertaintyNanos;
    }
    public void resetTimeUncertaintyNanos() { resetFlag(HAS_TIME_UNCERTAINTY); }

    public boolean hasFullBiasNanos() { return isFlagSet(HAS_FULL_BIAS); }
    public long getFullBiasNanos() { return mFullBiasNanos; }
    public void setFullBiasNanos(long value) {
        setFlag(HAS_FULL_BIAS);
        mFullBiasNanos = value;
    }
    public void resetFullBiasNanos() {
        resetFlag(HAS_FULL_BIAS);
        mFullBiasNanos = Long.MIN_VALUE;
    }

    public boolean hasBiasNanos() { return isFlagSet(HAS_BIAS); }
    public double getBiasNanos() { return mBiasNanos; }
    public void setBiasNanos(double biasNanos) {
        setFlag(HAS_BIAS);
        mBiasNanos = biasNanos;
    }
    public void resetBiasNanos() { resetFlag(HAS_BIAS); }

    public boolean hasBiasUncertaintyNanos() { return isFlagSet(HAS_BIAS_UNCERTAINTY); }
    public double getBiasUncertaintyNanos() { return mBiasUncertaintyNanos; }
    public void setBiasUncertaintyNanos(double biasUncertaintyNanos) {
        setFlag(HAS_BIAS_UNCERTAINTY);
        mBiasUncertaintyNanos = biasUncertaintyNanos;
    }
    public void resetBiasUncertaintyNanos() { resetFlag(HAS_BIAS_UNCERTAINTY); }

    public boolean hasDriftNanosPerSecond() { return isFlagSet(HAS_DRIFT); }
    public double getDriftNanosPerSecond() { return mDriftNanosPerSecond; }
    public void setDriftNanosPerSecond(double driftNanosPerSecond) {
        setFlag(HAS_DRIFT);
        mDriftNanosPerSecond = driftNanosPerSecond;
    }
    public void resetDriftNanosPerSecond() { resetFlag(HAS_DRIFT); }

    public boolean hasDriftUncertaintyNanosPerSecond() { return isFlagSet(HAS_DRIFT_UNCERTAINTY); }
    public double getDriftUncertaintyNanosPerSecond() { return mDriftUncertaintyNanosPerSecond; }
    public void setDriftUncertaintyNanosPerSecond(double v) {
        setFlag(HAS_DRIFT_UNCERTAINTY);
        mDriftUncertaintyNanosPerSecond = v;
    }
    public void resetDriftUncertaintyNanosPerSecond() { resetFlag(HAS_DRIFT_UNCERTAINTY); }

    public boolean hasElapsedRealtimeNanos() { return isFlagSet(HAS_ELAPSED_REALTIME_NANOS); }
    public long getElapsedRealtimeNanos() { return mElapsedRealtimeNanos; }
    public void setElapsedRealtimeNanos(long elapsedRealtimeNanos) {
        setFlag(HAS_ELAPSED_REALTIME_NANOS);
        mElapsedRealtimeNanos = elapsedRealtimeNanos;
    }
    public void resetElapsedRealtimeNanos() {
        resetFlag(HAS_ELAPSED_REALTIME_NANOS);
        mElapsedRealtimeNanos = 0;
    }

    public boolean hasElapsedRealtimeUncertaintyNanos() {
        return isFlagSet(HAS_ELAPSED_REALTIME_UNCERTAINTY_NANOS);
    }
    public double getElapsedRealtimeUncertaintyNanos() { return mElapsedRealtimeUncertaintyNanos; }
    public void setElapsedRealtimeUncertaintyNanos(double v) {
        setFlag(HAS_ELAPSED_REALTIME_UNCERTAINTY_NANOS);
        mElapsedRealtimeUncertaintyNanos = v;
    }
    public void resetElapsedRealtimeUncertaintyNanos() {
        resetFlag(HAS_ELAPSED_REALTIME_UNCERTAINTY_NANOS);
    }

    public boolean hasReferenceConstellationTypeForIsb() {
        return isFlagSet(HAS_REFERENCE_CONSTELLATION_TYPE_FOR_ISB);
    }
    public int getReferenceConstellationTypeForIsb() { return mReferenceConstellationTypeForIsb; }
    public void setReferenceConstellationTypeForIsb(int value) {
        setFlag(HAS_REFERENCE_CONSTELLATION_TYPE_FOR_ISB);
        mReferenceConstellationTypeForIsb = value;
    }
    public void resetReferenceConstellationTypeForIsb() {
        resetFlag(HAS_REFERENCE_CONSTELLATION_TYPE_FOR_ISB);
        mReferenceConstellationTypeForIsb = GnssStatus.CONSTELLATION_UNKNOWN;
    }

    public boolean hasReferenceCarrierFrequencyHzForIsb() {
        return isFlagSet(HAS_REFERENCE_CARRIER_FREQUENCY_FOR_ISB);
    }
    public double getReferenceCarrierFrequencyHzForIsb() {
        return mReferenceCarrierFrequencyHzForIsb;
    }
    public void setReferenceCarrierFrequencyHzForIsb(double value) {
        setFlag(HAS_REFERENCE_CARRIER_FREQUENCY_FOR_ISB);
        mReferenceCarrierFrequencyHzForIsb = value;
    }
    public void resetReferenceCarrierFrequencyHzForIsb() {
        resetFlag(HAS_REFERENCE_CARRIER_FREQUENCY_FOR_ISB);
    }

    public boolean hasReferenceCodeTypeForIsb() {
        return isFlagSet(HAS_REFERENCE_CODE_TYPE_FOR_ISB);
    }
    public String getReferenceCodeTypeForIsb() { return mReferenceCodeTypeForIsb; }
    public void setReferenceCodeTypeForIsb(String codeType) {
        setFlag(HAS_REFERENCE_CODE_TYPE_FOR_ISB);
        mReferenceCodeTypeForIsb = codeType;
    }
    public void resetReferenceCodeTypeForIsb() {
        resetFlag(HAS_REFERENCE_CODE_TYPE_FOR_ISB);
        // AOSP resets to the literal "UNKNOWN", not null.
        mReferenceCodeTypeForIsb = "UNKNOWN";
    }

    public int getHardwareClockDiscontinuityCount() { return mHardwareClockDiscontinuityCount; }
    public void setHardwareClockDiscontinuityCount(int value) {
        mHardwareClockDiscontinuityCount = value;
    }

    public static final Creator<GnssClock> CREATOR = new Creator<GnssClock>() {
        @Override public GnssClock createFromParcel(Parcel parcel) {
            GnssClock gpsClock = new GnssClock();
            gpsClock.mFlags = parcel.readInt();
            gpsClock.mLeapSecond = parcel.readInt();
            gpsClock.mTimeNanos = parcel.readLong();
            gpsClock.mTimeUncertaintyNanos = parcel.readDouble();
            gpsClock.mFullBiasNanos = parcel.readLong();
            gpsClock.mBiasNanos = parcel.readDouble();
            gpsClock.mBiasUncertaintyNanos = parcel.readDouble();
            gpsClock.mDriftNanosPerSecond = parcel.readDouble();
            gpsClock.mDriftUncertaintyNanosPerSecond = parcel.readDouble();
            gpsClock.mHardwareClockDiscontinuityCount = parcel.readInt();
            gpsClock.mElapsedRealtimeNanos = parcel.readLong();
            gpsClock.mElapsedRealtimeUncertaintyNanos = parcel.readDouble();
            gpsClock.mReferenceConstellationTypeForIsb = parcel.readInt();
            gpsClock.mReferenceCarrierFrequencyHzForIsb = parcel.readDouble();
            gpsClock.mReferenceCodeTypeForIsb = parcel.readString();
            return gpsClock;
        }

        @Override public GnssClock[] newArray(int size) { return new GnssClock[size]; }
    };

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mFlags);
        parcel.writeInt(mLeapSecond);
        parcel.writeLong(mTimeNanos);
        parcel.writeDouble(mTimeUncertaintyNanos);
        parcel.writeLong(mFullBiasNanos);
        parcel.writeDouble(mBiasNanos);
        parcel.writeDouble(mBiasUncertaintyNanos);
        parcel.writeDouble(mDriftNanosPerSecond);
        parcel.writeDouble(mDriftUncertaintyNanosPerSecond);
        parcel.writeInt(mHardwareClockDiscontinuityCount);
        parcel.writeLong(mElapsedRealtimeNanos);
        parcel.writeDouble(mElapsedRealtimeUncertaintyNanos);
        parcel.writeInt(mReferenceConstellationTypeForIsb);
        parcel.writeDouble(mReferenceCarrierFrequencyHzForIsb);
        parcel.writeString(mReferenceCodeTypeForIsb);
    }

    @Override public int describeContents() { return 0; }

    @Override public String toString() {
        final String format = "   %-15s = %s\n";
        StringBuilder builder = new StringBuilder("GnssClock:\n");
        if (hasLeapSecond()) {
            builder.append(String.format(format, "LeapSecond", mLeapSecond));
        }
        builder.append(String.format(format, "TimeNanos", mTimeNanos));
        if (hasFullBiasNanos()) {
            builder.append(String.format(format, "FullBiasNanos", mFullBiasNanos));
        }
        builder.append(String.format(format, "HardwareClockDiscontinuityCount",
                mHardwareClockDiscontinuityCount));
        return builder.toString();
    }

    private void initialize() {
        mFlags = HAS_NO_FLAGS;
        resetLeapSecond();
        setTimeNanos(Long.MIN_VALUE);
        resetTimeUncertaintyNanos();
        resetFullBiasNanos();
        resetBiasNanos();
        resetBiasUncertaintyNanos();
        resetDriftNanosPerSecond();
        resetDriftUncertaintyNanosPerSecond();
        setHardwareClockDiscontinuityCount(Integer.MIN_VALUE);
        resetElapsedRealtimeNanos();
        resetElapsedRealtimeUncertaintyNanos();
        resetReferenceConstellationTypeForIsb();
        resetReferenceCarrierFrequencyHzForIsb();
        resetReferenceCodeTypeForIsb();
    }

    private void setFlag(int flag) { mFlags |= flag; }
    private void resetFlag(int flag) { mFlags &= ~flag; }
    private boolean isFlagSet(int flag) { return (mFlags & flag) == flag; }
}
