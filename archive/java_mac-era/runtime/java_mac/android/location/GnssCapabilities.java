package android.location;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What the GNSS chipset can do.
 *
 * A Mac has no GNSS chipset, so LocationManager.getGnssCapabilities() returns a
 * default-built instance: every capability flag clear (has* false) and no
 * signal types. That is the same object AOSP hands back before the HAL has
 * reported, and the same shape a device whose HAL supports nothing reports --
 * we are NOT claiming an absent radio can do things.
 *
 * hasAccumulatedDeltaRange() is the one three-valued answer: with no HAL the
 * ADR capability is genuinely UNKNOWN, which is what we return (AOSP gates it
 * on mIsAdrCapabilityKnown, which is false for a default-built instance).
 *
 * Source: frameworks/base/location/java/android/location/GnssCapabilities.java
 */
public final class GnssCapabilities implements Parcelable {

    public static final int CAPABILITY_UNKNOWN = 0;
    public static final int CAPABILITY_SUPPORTED = 1;
    public static final int CAPABILITY_UNSUPPORTED = 2;

    // Bit positions mirror the GNSS HAL; kept because writeToParcel round-trips them.
    private static final int TOP_HAL_CAPABILITY_SCHEDULING = 1;
    private static final int TOP_HAL_CAPABILITY_MSB = 1 << 1;
    private static final int TOP_HAL_CAPABILITY_MSA = 1 << 2;
    private static final int TOP_HAL_CAPABILITY_SINGLE_SHOT = 1 << 3;
    private static final int TOP_HAL_CAPABILITY_ON_DEMAND_TIME = 1 << 4;
    private static final int TOP_HAL_CAPABILITY_GEOFENCING = 1 << 5;
    private static final int TOP_HAL_CAPABILITY_MEASUREMENTS = 1 << 6;
    private static final int TOP_HAL_CAPABILITY_NAV_MESSAGES = 1 << 7;
    private static final int TOP_HAL_CAPABILITY_LOW_POWER_MODE = 1 << 8;
    private static final int TOP_HAL_CAPABILITY_SATELLITE_BLOCKLIST = 1 << 9;
    private static final int TOP_HAL_CAPABILITY_MEASUREMENT_CORRECTIONS = 1 << 10;
    private static final int TOP_HAL_CAPABILITY_ANTENNA_INFO = 1 << 11;
    private static final int TOP_HAL_CAPABILITY_CORRELATION_VECTOR = 1 << 12;
    private static final int TOP_HAL_CAPABILITY_SATELLITE_PVT = 1 << 13;
    private static final int TOP_HAL_CAPABILITY_MEASUREMENT_CORRECTIONS_FOR_DRIVING = 1 << 14;
    private static final int TOP_HAL_CAPABILITY_ACCUMULATED_DELTA_RANGE = 1 << 15;

    private static final int SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_LOS_SATS = 1;
    private static final int SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_EXCESS_PATH_LENGTH = 2;
    private static final int SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_REFLECTING_PLANE = 4;

    private static final int SUB_HAL_POWER_CAPABILITY_TOTAL = 1;
    private static final int SUB_HAL_POWER_CAPABILITY_SINGLEBAND_TRACKING = 2;
    private static final int SUB_HAL_POWER_CAPABILITY_MULTIBAND_TRACKING = 4;
    private static final int SUB_HAL_POWER_CAPABILITY_SINGLEBAND_ACQUISITION = 8;
    private static final int SUB_HAL_POWER_CAPABILITY_MULTIBAND_ACQUISITION = 16;
    private static final int SUB_HAL_POWER_CAPABILITY_OTHER_MODES = 32;

    private final int mTopFlags;
    private final boolean mIsAdrCapabilityKnown;
    private final int mMeasurementCorrectionsFlags;
    private final int mPowerFlags;
    private final List<GnssSignalType> mGnssSignalTypes;

    private GnssCapabilities(int topFlags, boolean isAdrCapabilityKnown,
            int measurementCorrectionsFlags, int powerFlags,
            List<GnssSignalType> gnssSignalTypes) {
        mTopFlags = topFlags;
        mIsAdrCapabilityKnown = isAdrCapabilityKnown;
        mMeasurementCorrectionsFlags = measurementCorrectionsFlags;
        mPowerFlags = powerFlags;
        mGnssSignalTypes = gnssSignalTypes == null
                ? Collections.<GnssSignalType>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(gnssSignalTypes));
    }

    public boolean hasScheduling() { return (mTopFlags & TOP_HAL_CAPABILITY_SCHEDULING) != 0; }
    public boolean hasMsb() { return (mTopFlags & TOP_HAL_CAPABILITY_MSB) != 0; }
    public boolean hasMsa() { return (mTopFlags & TOP_HAL_CAPABILITY_MSA) != 0; }
    public boolean hasSingleShotFix() { return (mTopFlags & TOP_HAL_CAPABILITY_SINGLE_SHOT) != 0; }
    public boolean hasOnDemandTime() {
        return (mTopFlags & TOP_HAL_CAPABILITY_ON_DEMAND_TIME) != 0;
    }
    public boolean hasGeofencing() { return (mTopFlags & TOP_HAL_CAPABILITY_GEOFENCING) != 0; }
    public boolean hasMeasurements() { return (mTopFlags & TOP_HAL_CAPABILITY_MEASUREMENTS) != 0; }
    public boolean hasNavigationMessages() {
        return (mTopFlags & TOP_HAL_CAPABILITY_NAV_MESSAGES) != 0;
    }
    public boolean hasLowPowerMode() {
        return (mTopFlags & TOP_HAL_CAPABILITY_LOW_POWER_MODE) != 0;
    }
    public boolean hasSatelliteBlocklist() {
        return (mTopFlags & TOP_HAL_CAPABILITY_SATELLITE_BLOCKLIST) != 0;
    }
    public boolean hasSatellitePvt() { return (mTopFlags & TOP_HAL_CAPABILITY_SATELLITE_PVT) != 0; }
    public boolean hasMeasurementCorrections() {
        return (mTopFlags & TOP_HAL_CAPABILITY_MEASUREMENT_CORRECTIONS) != 0;
    }
    public boolean hasAntennaInfo() { return (mTopFlags & TOP_HAL_CAPABILITY_ANTENNA_INFO) != 0; }

    /** @deprecated AOSP renamed this to {@link #hasAntennaInfo()} in API 33. */
    @Deprecated public boolean hasGnssAntennaInfo() { return hasAntennaInfo(); }

    public boolean hasMeasurementCorrelationVectors() {
        return (mTopFlags & TOP_HAL_CAPABILITY_CORRELATION_VECTOR) != 0;
    }
    public boolean hasMeasurementCorrectionsForDriving() {
        return (mTopFlags & TOP_HAL_CAPABILITY_MEASUREMENT_CORRECTIONS_FOR_DRIVING) != 0;
    }

    /**
     * Three-valued: AOSP returns CAPABILITY_UNKNOWN unless the HAL has reported.
     * With no HAL at all that is exactly our situation.
     */
    public int hasAccumulatedDeltaRange() {
        if (!mIsAdrCapabilityKnown) {
            return CAPABILITY_UNKNOWN;
        }
        return (mTopFlags & TOP_HAL_CAPABILITY_ACCUMULATED_DELTA_RANGE) != 0
                ? CAPABILITY_SUPPORTED : CAPABILITY_UNSUPPORTED;
    }

    public boolean hasMeasurementCorrectionsLosSats() {
        return (mMeasurementCorrectionsFlags
                & SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_LOS_SATS) != 0;
    }
    public boolean hasMeasurementCorrectionsExcessPathLength() {
        return (mMeasurementCorrectionsFlags
                & SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_EXCESS_PATH_LENGTH) != 0;
    }
    public boolean hasMeasurementCorrectionsReflectingPlane() {
        return (mMeasurementCorrectionsFlags
                & SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_REFLECTING_PLANE) != 0;
    }

    public boolean hasPowerTotal() { return (mPowerFlags & SUB_HAL_POWER_CAPABILITY_TOTAL) != 0; }
    public boolean hasPowerSinglebandTracking() {
        return (mPowerFlags & SUB_HAL_POWER_CAPABILITY_SINGLEBAND_TRACKING) != 0;
    }
    public boolean hasPowerMultibandTracking() {
        return (mPowerFlags & SUB_HAL_POWER_CAPABILITY_MULTIBAND_TRACKING) != 0;
    }
    public boolean hasPowerSinglebandAcquisition() {
        return (mPowerFlags & SUB_HAL_POWER_CAPABILITY_SINGLEBAND_ACQUISITION) != 0;
    }
    public boolean hasPowerMultibandAcquisition() {
        return (mPowerFlags & SUB_HAL_POWER_CAPABILITY_MULTIBAND_ACQUISITION) != 0;
    }
    public boolean hasPowerOtherModes() {
        return (mPowerFlags & SUB_HAL_POWER_CAPABILITY_OTHER_MODES) != 0;
    }

    public List<GnssSignalType> getGnssSignalTypes() { return mGnssSignalTypes; }

    public static final Creator<GnssCapabilities> CREATOR = new Creator<GnssCapabilities>() {
        @Override public GnssCapabilities createFromParcel(Parcel in) {
            return new GnssCapabilities(in.readInt(), in.readBoolean(), in.readInt(),
                    in.readInt(), in.createTypedArrayList(GnssSignalType.CREATOR));
        }
        @Override public GnssCapabilities[] newArray(int size) { return new GnssCapabilities[size]; }
    };

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mTopFlags);
        parcel.writeBoolean(mIsAdrCapabilityKnown);
        parcel.writeInt(mMeasurementCorrectionsFlags);
        parcel.writeInt(mPowerFlags);
        parcel.writeTypedList(mGnssSignalTypes);
    }

    @Override public int describeContents() { return 0; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GnssCapabilities)) return false;
        GnssCapabilities that = (GnssCapabilities) o;
        return mTopFlags == that.mTopFlags
                && mIsAdrCapabilityKnown == that.mIsAdrCapabilityKnown
                && mMeasurementCorrectionsFlags == that.mMeasurementCorrectionsFlags
                && mPowerFlags == that.mPowerFlags
                && mGnssSignalTypes.equals(that.mGnssSignalTypes);
    }

    @Override public int hashCode() {
        return Objects.hash(mTopFlags, mIsAdrCapabilityKnown, mMeasurementCorrectionsFlags,
                mPowerFlags, mGnssSignalTypes);
    }

    @Override public String toString() {
        StringBuilder b = new StringBuilder("[");
        if (hasScheduling()) b.append("SCHEDULING ");
        if (hasMeasurements()) b.append("MEASUREMENTS ");
        if (hasNavigationMessages()) b.append("NAVIGATION_MESSAGES ");
        if (hasAntennaInfo()) b.append("ANTENNA_INFO ");
        if (!mGnssSignalTypes.isEmpty()) b.append("signalTypes=").append(mGnssSignalTypes);
        if (b.length() > 1) b.setLength(b.length() - 1);
        return b.append(']').toString();
    }

    public static final class Builder {
        private int mTopFlags;
        private boolean mIsAdrCapabilityKnown;
        private int mMeasurementCorrectionsFlags;
        private int mPowerFlags;
        private List<GnssSignalType> mGnssSignalTypes = Collections.emptyList();

        public Builder() { }

        public Builder(GnssCapabilities capabilities) {
            mTopFlags = capabilities.mTopFlags;
            mIsAdrCapabilityKnown = capabilities.mIsAdrCapabilityKnown;
            mMeasurementCorrectionsFlags = capabilities.mMeasurementCorrectionsFlags;
            mPowerFlags = capabilities.mPowerFlags;
            mGnssSignalTypes = capabilities.mGnssSignalTypes;
        }

        private Builder setTop(int flag, boolean set) {
            if (set) mTopFlags |= flag; else mTopFlags &= ~flag;
            return this;
        }
        private Builder setMc(int flag, boolean set) {
            if (set) mMeasurementCorrectionsFlags |= flag;
            else mMeasurementCorrectionsFlags &= ~flag;
            return this;
        }
        private Builder setPower(int flag, boolean set) {
            if (set) mPowerFlags |= flag; else mPowerFlags &= ~flag;
            return this;
        }

        public Builder setHasScheduling(boolean b) {
            return setTop(TOP_HAL_CAPABILITY_SCHEDULING, b);
        }
        public Builder setHasMsb(boolean b) { return setTop(TOP_HAL_CAPABILITY_MSB, b); }
        public Builder setHasMsa(boolean b) { return setTop(TOP_HAL_CAPABILITY_MSA, b); }
        public Builder setHasSingleShotFix(boolean b) {
            return setTop(TOP_HAL_CAPABILITY_SINGLE_SHOT, b);
        }
        public Builder setHasOnDemandTime(boolean b) {
            return setTop(TOP_HAL_CAPABILITY_ON_DEMAND_TIME, b);
        }
        public Builder setHasGeofencing(boolean b) {
            return setTop(TOP_HAL_CAPABILITY_GEOFENCING, b);
        }
        public Builder setHasMeasurements(boolean b) {
            return setTop(TOP_HAL_CAPABILITY_MEASUREMENTS, b);
        }
        public Builder setHasNavigationMessages(boolean b) {
            return setTop(TOP_HAL_CAPABILITY_NAV_MESSAGES, b);
        }
        public Builder setHasLowPowerMode(boolean b) {
            return setTop(TOP_HAL_CAPABILITY_LOW_POWER_MODE, b);
        }
        public Builder setHasSatelliteBlocklist(boolean b) {
            return setTop(TOP_HAL_CAPABILITY_SATELLITE_BLOCKLIST, b);
        }
        public Builder setHasSatellitePvt(boolean b) {
            return setTop(TOP_HAL_CAPABILITY_SATELLITE_PVT, b);
        }
        public Builder setHasMeasurementCorrections(boolean b) {
            return setTop(TOP_HAL_CAPABILITY_MEASUREMENT_CORRECTIONS, b);
        }
        public Builder setHasAntennaInfo(boolean b) {
            return setTop(TOP_HAL_CAPABILITY_ANTENNA_INFO, b);
        }
        public Builder setHasMeasurementCorrelationVectors(boolean b) {
            return setTop(TOP_HAL_CAPABILITY_CORRELATION_VECTOR, b);
        }
        public Builder setHasMeasurementCorrectionsForDriving(boolean b) {
            return setTop(TOP_HAL_CAPABILITY_MEASUREMENT_CORRECTIONS_FOR_DRIVING, b);
        }

        /** Takes a CAPABILITY_* value; UNKNOWN clears the "known" bit, per AOSP. */
        public Builder setHasAccumulatedDeltaRange(int capability) {
            if (capability == CAPABILITY_UNKNOWN) {
                mIsAdrCapabilityKnown = false;
                mTopFlags &= ~TOP_HAL_CAPABILITY_ACCUMULATED_DELTA_RANGE;
            } else {
                mIsAdrCapabilityKnown = true;
                setTop(TOP_HAL_CAPABILITY_ACCUMULATED_DELTA_RANGE,
                        capability == CAPABILITY_SUPPORTED);
            }
            return this;
        }

        public Builder setHasMeasurementCorrectionsLosSats(boolean b) {
            return setMc(SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_LOS_SATS, b);
        }
        public Builder setHasMeasurementCorrectionsExcessPathLength(boolean b) {
            return setMc(SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_EXCESS_PATH_LENGTH, b);
        }
        public Builder setHasMeasurementCorrectionsReflectingPlane(boolean b) {
            return setMc(SUB_HAL_MEASUREMENT_CORRECTIONS_CAPABILITY_REFLECTING_PLANE, b);
        }

        public Builder setHasPowerTotal(boolean b) {
            return setPower(SUB_HAL_POWER_CAPABILITY_TOTAL, b);
        }
        public Builder setHasPowerSinglebandTracking(boolean b) {
            return setPower(SUB_HAL_POWER_CAPABILITY_SINGLEBAND_TRACKING, b);
        }
        public Builder setHasPowerMultibandTracking(boolean b) {
            return setPower(SUB_HAL_POWER_CAPABILITY_MULTIBAND_TRACKING, b);
        }
        public Builder setHasPowerSinglebandAcquisition(boolean b) {
            return setPower(SUB_HAL_POWER_CAPABILITY_SINGLEBAND_ACQUISITION, b);
        }
        public Builder setHasPowerMultibandAcquisition(boolean b) {
            return setPower(SUB_HAL_POWER_CAPABILITY_MULTIBAND_ACQUISITION, b);
        }
        public Builder setHasPowerOtherModes(boolean b) {
            return setPower(SUB_HAL_POWER_CAPABILITY_OTHER_MODES, b);
        }

        public Builder setGnssSignalTypes(List<GnssSignalType> gnssSignalTypes) {
            mGnssSignalTypes = gnssSignalTypes;
            return this;
        }

        public GnssCapabilities build() {
            return new GnssCapabilities(mTopFlags, mIsAdrCapabilityKnown,
                    mMeasurementCorrectionsFlags, mPowerFlags, mGnssSignalTypes);
        }
    }
}
