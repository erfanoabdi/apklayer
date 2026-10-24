package android.location.provider;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Static capabilities of a location provider.
 *
 * A Mac registers no location providers, so LocationManager.getProviderProperties()
 * throws IllegalArgumentException for every name -- AOSP's documented behaviour
 * for an unknown provider. This value type exists so an app that builds one, or
 * that implements a provider itself, links and behaves identically.
 *
 * Source: frameworks/base/location/java/android/location/provider/ProviderProperties.java
 */
public final class ProviderProperties implements Parcelable {

    public static final int POWER_USAGE_LOW = 1;
    public static final int POWER_USAGE_MEDIUM = 2;
    public static final int POWER_USAGE_HIGH = 3;

    public static final int ACCURACY_FINE = 1;
    public static final int ACCURACY_COARSE = 2;

    private final boolean mHasNetworkRequirement;
    private final boolean mHasSatelliteRequirement;
    private final boolean mHasCellRequirement;
    private final boolean mHasMonetaryCost;
    private final boolean mHasAltitudeSupport;
    private final boolean mHasSpeedSupport;
    private final boolean mHasBearingSupport;
    private final int mPowerUsage;
    private final int mAccuracy;

    private ProviderProperties(boolean hasNetworkRequirement, boolean hasSatelliteRequirement,
            boolean hasCellRequirement, boolean hasMonetaryCost, boolean hasAltitudeSupport,
            boolean hasSpeedSupport, boolean hasBearingSupport, int powerUsage, int accuracy) {
        mHasNetworkRequirement = hasNetworkRequirement;
        mHasSatelliteRequirement = hasSatelliteRequirement;
        mHasCellRequirement = hasCellRequirement;
        mHasMonetaryCost = hasMonetaryCost;
        mHasAltitudeSupport = hasAltitudeSupport;
        mHasSpeedSupport = hasSpeedSupport;
        mHasBearingSupport = hasBearingSupport;
        mPowerUsage = powerUsage;
        mAccuracy = accuracy;
    }

    public boolean hasNetworkRequirement() { return mHasNetworkRequirement; }
    public boolean hasSatelliteRequirement() { return mHasSatelliteRequirement; }
    public boolean hasCellRequirement() { return mHasCellRequirement; }
    public boolean hasMonetaryCost() { return mHasMonetaryCost; }
    public boolean hasAltitudeSupport() { return mHasAltitudeSupport; }
    public boolean hasSpeedSupport() { return mHasSpeedSupport; }
    public boolean hasBearingSupport() { return mHasBearingSupport; }
    public int getPowerUsage() { return mPowerUsage; }
    public int getAccuracy() { return mAccuracy; }

    public static final Creator<ProviderProperties> CREATOR = new Creator<ProviderProperties>() {
        @Override public ProviderProperties createFromParcel(Parcel in) {
            return new ProviderProperties(in.readBoolean(), in.readBoolean(), in.readBoolean(),
                    in.readBoolean(), in.readBoolean(), in.readBoolean(), in.readBoolean(),
                    in.readInt(), in.readInt());
        }
        @Override public ProviderProperties[] newArray(int size) {
            return new ProviderProperties[size];
        }
    };

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeBoolean(mHasNetworkRequirement);
        parcel.writeBoolean(mHasSatelliteRequirement);
        parcel.writeBoolean(mHasCellRequirement);
        parcel.writeBoolean(mHasMonetaryCost);
        parcel.writeBoolean(mHasAltitudeSupport);
        parcel.writeBoolean(mHasSpeedSupport);
        parcel.writeBoolean(mHasBearingSupport);
        parcel.writeInt(mPowerUsage);
        parcel.writeInt(mAccuracy);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProviderProperties)) return false;
        ProviderProperties that = (ProviderProperties) o;
        return mHasNetworkRequirement == that.mHasNetworkRequirement
                && mHasSatelliteRequirement == that.mHasSatelliteRequirement
                && mHasCellRequirement == that.mHasCellRequirement
                && mHasMonetaryCost == that.mHasMonetaryCost
                && mHasAltitudeSupport == that.mHasAltitudeSupport
                && mHasSpeedSupport == that.mHasSpeedSupport
                && mHasBearingSupport == that.mHasBearingSupport
                && mPowerUsage == that.mPowerUsage
                && mAccuracy == that.mAccuracy;
    }

    @Override public int hashCode() {
        return Objects.hash(mHasNetworkRequirement, mHasSatelliteRequirement, mHasCellRequirement,
                mHasMonetaryCost, mHasAltitudeSupport, mHasSpeedSupport, mHasBearingSupport,
                mPowerUsage, mAccuracy);
    }

    @Override public String toString() {
        StringBuilder b = new StringBuilder("ProviderProperties[");
        b.append("powerUsage=").append(mPowerUsage);
        b.append(", accuracy=").append(mAccuracy);
        return b.append(']').toString();
    }

    public static final class Builder {
        private boolean mHasNetworkRequirement;
        private boolean mHasSatelliteRequirement;
        private boolean mHasCellRequirement;
        private boolean mHasMonetaryCost;
        private boolean mHasAltitudeSupport;
        private boolean mHasSpeedSupport;
        private boolean mHasBearingSupport;
        // AOSP's no-arg default is the most pessimistic pair, not zero.
        private int mPowerUsage = POWER_USAGE_HIGH;
        private int mAccuracy = ACCURACY_COARSE;

        public Builder() { }

        public Builder(ProviderProperties providerProperties) {
            mHasNetworkRequirement = providerProperties.mHasNetworkRequirement;
            mHasSatelliteRequirement = providerProperties.mHasSatelliteRequirement;
            mHasCellRequirement = providerProperties.mHasCellRequirement;
            mHasMonetaryCost = providerProperties.mHasMonetaryCost;
            mHasAltitudeSupport = providerProperties.mHasAltitudeSupport;
            mHasSpeedSupport = providerProperties.mHasSpeedSupport;
            mHasBearingSupport = providerProperties.mHasBearingSupport;
            mPowerUsage = providerProperties.mPowerUsage;
            mAccuracy = providerProperties.mAccuracy;
        }

        public Builder setHasNetworkRequirement(boolean v) { mHasNetworkRequirement = v; return this; }
        public Builder setHasSatelliteRequirement(boolean v) { mHasSatelliteRequirement = v; return this; }
        public Builder setHasCellRequirement(boolean v) { mHasCellRequirement = v; return this; }
        public Builder setHasMonetaryCost(boolean v) { mHasMonetaryCost = v; return this; }
        public Builder setHasAltitudeSupport(boolean v) { mHasAltitudeSupport = v; return this; }
        public Builder setHasSpeedSupport(boolean v) { mHasSpeedSupport = v; return this; }
        public Builder setHasBearingSupport(boolean v) { mHasBearingSupport = v; return this; }

        public Builder setPowerUsage(int powerUsage) {
            // AOSP: Preconditions.checkArgumentInRange(powerUsage, POWER_USAGE_LOW,
            // POWER_USAGE_HIGH, "powerUsage").
            if (powerUsage < POWER_USAGE_LOW || powerUsage > POWER_USAGE_HIGH) {
                throw new IllegalArgumentException("powerUsage out of range of ["
                        + POWER_USAGE_LOW + ", " + POWER_USAGE_HIGH + "]: is " + powerUsage);
            }
            mPowerUsage = powerUsage;
            return this;
        }

        public Builder setAccuracy(int accuracy) {
            // AOSP: Preconditions.checkArgumentInRange(accuracy, ACCURACY_FINE,
            // ACCURACY_COARSE, "accuracy").
            if (accuracy < ACCURACY_FINE || accuracy > ACCURACY_COARSE) {
                throw new IllegalArgumentException("accuracy out of range of ["
                        + ACCURACY_FINE + ", " + ACCURACY_COARSE + "]: is " + accuracy);
            }
            mAccuracy = accuracy;
            return this;
        }

        public ProviderProperties build() {
            return new ProviderProperties(mHasNetworkRequirement, mHasSatelliteRequirement,
                    mHasCellRequirement, mHasMonetaryCost, mHasAltitudeSupport, mHasSpeedSupport,
                    mHasBearingSupport, mPowerUsage, mAccuracy);
        }
    }
}
