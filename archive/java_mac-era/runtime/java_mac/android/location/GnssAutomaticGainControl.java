package android.location;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Automatic-gain-control level reported by a GNSS receiver for one signal type.
 *
 * A Mac has no GNSS receiver, so GnssMeasurementsEvent always reports an empty
 * AGC collection. Immutable value type; faithful as-is.
 *
 * Source: frameworks/base/location/java/android/location/GnssAutomaticGainControl.java
 */
public final class GnssAutomaticGainControl implements Parcelable {

    private final int mConstellationType;
    private final long mCarrierFrequencyHz;
    private final double mLevelDb;

    private GnssAutomaticGainControl(int constellationType, long carrierFrequencyHz,
            double levelDb) {
        mConstellationType = constellationType;
        mCarrierFrequencyHz = carrierFrequencyHz;
        mLevelDb = levelDb;
    }

    public int getConstellationType() { return mConstellationType; }

    public long getCarrierFrequencyHz() { return mCarrierFrequencyHz; }

    public double getLevelDb() { return mLevelDb; }

    public static final Creator<GnssAutomaticGainControl> CREATOR =
            new Creator<GnssAutomaticGainControl>() {
        @Override public GnssAutomaticGainControl createFromParcel(Parcel parcel) {
            return new GnssAutomaticGainControl(parcel.readInt(), parcel.readLong(),
                    parcel.readDouble());
        }
        @Override public GnssAutomaticGainControl[] newArray(int i) {
            return new GnssAutomaticGainControl[i];
        }
    };

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mConstellationType);
        parcel.writeLong(mCarrierFrequencyHz);
        parcel.writeDouble(mLevelDb);
    }

    @Override public int describeContents() { return 0; }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GnssAutomaticGainControl)) return false;
        GnssAutomaticGainControl o = (GnssAutomaticGainControl) obj;
        return mConstellationType == o.mConstellationType
                && mCarrierFrequencyHz == o.mCarrierFrequencyHz
                && Double.compare(mLevelDb, o.mLevelDb) == 0;
    }

    @Override public int hashCode() {
        return Objects.hash(mConstellationType, mCarrierFrequencyHz, mLevelDb);
    }

    @Override public String toString() {
        return "GnssAutomaticGainControl[Constellation=" + mConstellationType
                + ", CarrierFrequencyHz=" + mCarrierFrequencyHz
                + ", LevelDb=" + mLevelDb + ']';
    }

    public static final class Builder {
        private int mConstellationType = GnssStatus.CONSTELLATION_UNKNOWN;
        private long mCarrierFrequencyHz;
        private double mLevelDb;

        public Builder() { }

        public Builder(GnssAutomaticGainControl agc) {
            mConstellationType = agc.getConstellationType();
            mCarrierFrequencyHz = agc.getCarrierFrequencyHz();
            mLevelDb = agc.getLevelDb();
        }

        public Builder setConstellationType(int constellationType) {
            mConstellationType = constellationType;
            return this;
        }

        public Builder setCarrierFrequencyHz(long carrierFrequencyHz) {
            // AOSP: Preconditions.checkArgumentNonnegative(carrierFrequencyHz).
            if (carrierFrequencyHz < 0) {
                throw new IllegalArgumentException("carrierFrequencyHz must be non-negative");
            }
            mCarrierFrequencyHz = carrierFrequencyHz;
            return this;
        }

        public Builder setLevelDb(double levelDb) {
            // AOSP clamps the documented range [-10000, 10000] with an argument check.
            if (!(levelDb >= -10000 && levelDb <= 10000)) {
                throw new IllegalArgumentException("levelDb must be in the range [-10000, 10000]");
            }
            mLevelDb = levelDb;
            return this;
        }

        public GnssAutomaticGainControl build() {
            return new GnssAutomaticGainControl(mConstellationType, mCarrierFrequencyHz, mLevelDb);
        }
    }
}
