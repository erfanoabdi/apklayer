package android.location;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Antenna characteristics of a GNSS receiver.
 *
 * A Mac has no GNSS antenna, so LocationManager.getGnssAntennaInfos() returns
 * null (AOSP's documented answer for "the antenna info is not available") and
 * a registered listener is never called. The value type itself is faithful,
 * including validation and parcel field order.
 *
 * Source: frameworks/base/location/java/android/location/GnssAntennaInfo.java
 */
public final class GnssAntennaInfo implements Parcelable {

    private final double mCarrierFrequencyMHz;
    private final PhaseCenterOffset mPhaseCenterOffset;
    private final SphericalCorrections mPhaseCenterVariationCorrections;
    private final SphericalCorrections mSignalGainCorrections;

    private GnssAntennaInfo(double carrierFrequencyMHz, PhaseCenterOffset phaseCenterOffset,
            SphericalCorrections phaseCenterVariationCorrections,
            SphericalCorrections signalGainCorrections) {
        mCarrierFrequencyMHz = carrierFrequencyMHz;
        mPhaseCenterOffset = Objects.requireNonNull(phaseCenterOffset);
        mPhaseCenterVariationCorrections = phaseCenterVariationCorrections;
        mSignalGainCorrections = signalGainCorrections;
    }

    public double getCarrierFrequencyMHz() { return mCarrierFrequencyMHz; }

    public PhaseCenterOffset getPhaseCenterOffset() { return mPhaseCenterOffset; }

    public SphericalCorrections getPhaseCenterVariationCorrections() {
        return mPhaseCenterVariationCorrections;
    }

    public SphericalCorrections getSignalGainCorrections() { return mSignalGainCorrections; }

    public static final Creator<GnssAntennaInfo> CREATOR = new Creator<GnssAntennaInfo>() {
        @Override public GnssAntennaInfo createFromParcel(Parcel in) {
            double carrierFrequencyMHz = in.readDouble();
            PhaseCenterOffset phaseCenterOffset = in.readTypedObject(PhaseCenterOffset.CREATOR);
            SphericalCorrections phaseCenterVariationCorrections =
                    in.readTypedObject(SphericalCorrections.CREATOR);
            SphericalCorrections signalGainCorrections =
                    in.readTypedObject(SphericalCorrections.CREATOR);
            return new GnssAntennaInfo(carrierFrequencyMHz, phaseCenterOffset,
                    phaseCenterVariationCorrections, signalGainCorrections);
        }
        @Override public GnssAntennaInfo[] newArray(int size) { return new GnssAntennaInfo[size]; }
    };

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeDouble(mCarrierFrequencyMHz);
        parcel.writeTypedObject(mPhaseCenterOffset, flags);
        parcel.writeTypedObject(mPhaseCenterVariationCorrections, flags);
        parcel.writeTypedObject(mSignalGainCorrections, flags);
    }

    @Override public int describeContents() { return 0; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GnssAntennaInfo)) return false;
        GnssAntennaInfo that = (GnssAntennaInfo) o;
        return Double.compare(mCarrierFrequencyMHz, that.mCarrierFrequencyMHz) == 0
                && mPhaseCenterOffset.equals(that.mPhaseCenterOffset)
                && Objects.equals(mPhaseCenterVariationCorrections,
                        that.mPhaseCenterVariationCorrections)
                && Objects.equals(mSignalGainCorrections, that.mSignalGainCorrections);
    }

    @Override public int hashCode() {
        return Objects.hash(mCarrierFrequencyMHz, mPhaseCenterOffset,
                mPhaseCenterVariationCorrections, mSignalGainCorrections);
    }

    @Override public String toString() {
        return "GnssAntennaInfo{carrierFrequencyMHz=" + mCarrierFrequencyMHz
                + ", phaseCenterOffset=" + mPhaseCenterOffset + '}';
    }

    /** AOSP declares Listener as an interface. */
    public interface Listener {
        void onGnssAntennaInfoReceived(java.util.List<GnssAntennaInfo> gnssAntennaInfos);
    }

    public static final class PhaseCenterOffset implements Parcelable {
        private final double mOffsetXMm;
        private final double mOffsetXUncertaintyMm;
        private final double mOffsetYMm;
        private final double mOffsetYUncertaintyMm;
        private final double mOffsetZMm;
        private final double mOffsetZUncertaintyMm;

        public PhaseCenterOffset(double offsetXMm, double offsetXUncertaintyMm,
                double offsetYMm, double offsetYUncertaintyMm,
                double offsetZMm, double offsetZUncertaintyMm) {
            mOffsetXMm = offsetXMm;
            mOffsetYMm = offsetYMm;
            mOffsetZMm = offsetZMm;
            mOffsetXUncertaintyMm = offsetXUncertaintyMm;
            mOffsetYUncertaintyMm = offsetYUncertaintyMm;
            mOffsetZUncertaintyMm = offsetZUncertaintyMm;
        }

        public static final Creator<PhaseCenterOffset> CREATOR = new Creator<PhaseCenterOffset>() {
            @Override public PhaseCenterOffset createFromParcel(Parcel in) {
                return new PhaseCenterOffset(in.readDouble(), in.readDouble(), in.readDouble(),
                        in.readDouble(), in.readDouble(), in.readDouble());
            }
            @Override public PhaseCenterOffset[] newArray(int size) {
                return new PhaseCenterOffset[size];
            }
        };

        public double getXOffsetMm() { return mOffsetXMm; }
        public double getXOffsetUncertaintyMm() { return mOffsetXUncertaintyMm; }
        public double getYOffsetMm() { return mOffsetYMm; }
        public double getYOffsetUncertaintyMm() { return mOffsetYUncertaintyMm; }
        public double getZOffsetMm() { return mOffsetZMm; }
        public double getZOffsetUncertaintyMm() { return mOffsetZUncertaintyMm; }

        @Override public int describeContents() { return 0; }

        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeDouble(mOffsetXMm);
            dest.writeDouble(mOffsetXUncertaintyMm);
            dest.writeDouble(mOffsetYMm);
            dest.writeDouble(mOffsetYUncertaintyMm);
            dest.writeDouble(mOffsetZMm);
            dest.writeDouble(mOffsetZUncertaintyMm);
        }

        @Override public String toString() {
            return "PhaseCenterOffset{"
                    + "OffsetXMm=" + mOffsetXMm + " +/-" + mOffsetXUncertaintyMm
                    + ", OffsetYMm=" + mOffsetYMm + " +/-" + mOffsetYUncertaintyMm
                    + ", OffsetZMm=" + mOffsetZMm + " +/-" + mOffsetZUncertaintyMm
                    + '}';
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PhaseCenterOffset)) return false;
            PhaseCenterOffset that = (PhaseCenterOffset) o;
            return Double.compare(that.mOffsetXMm, mOffsetXMm) == 0
                    && Double.compare(that.mOffsetXUncertaintyMm, mOffsetXUncertaintyMm) == 0
                    && Double.compare(that.mOffsetYMm, mOffsetYMm) == 0
                    && Double.compare(that.mOffsetYUncertaintyMm, mOffsetYUncertaintyMm) == 0
                    && Double.compare(that.mOffsetZMm, mOffsetZMm) == 0
                    && Double.compare(that.mOffsetZUncertaintyMm, mOffsetZUncertaintyMm) == 0;
        }

        @Override public int hashCode() {
            return Objects.hash(mOffsetXMm, mOffsetXUncertaintyMm, mOffsetYMm,
                    mOffsetYUncertaintyMm, mOffsetZMm, mOffsetZUncertaintyMm);
        }
    }

    public static final class SphericalCorrections implements Parcelable {
        private final int mNumRows;
        private final int mNumColumns;
        private final double[][] mCorrections;
        private final double[][] mCorrectionUncertainties;

        public SphericalCorrections(double[][] corrections, double[][] correctionUncertainties) {
            if (corrections.length != correctionUncertainties.length || corrections.length < 1) {
                throw new IllegalArgumentException("correction and uncertainty arrays must have "
                        + "the same (non-zero) dimensions");
            }
            mNumRows = corrections.length;
            mNumColumns = corrections[0].length;
            for (int i = 0; i < corrections.length; i++) {
                if (corrections[i].length != mNumColumns
                        || correctionUncertainties[i].length != mNumColumns || mNumColumns < 2) {
                    throw new IllegalArgumentException("correction and uncertainty arrays must all "
                            + " have the same (greater than 2) number of columns");
                }
            }
            mCorrections = corrections;
            mCorrectionUncertainties = correctionUncertainties;
        }

        private SphericalCorrections(Parcel in) {
            int numRows = in.readInt();
            int numColumns = in.readInt();
            double[][] corrections = new double[numRows][numColumns];
            double[][] correctionUncertainties = new double[numRows][numColumns];
            for (int row = 0; row < numRows; row++) {
                for (int col = 0; col < numColumns; col++) {
                    corrections[row][col] = in.readDouble();
                    correctionUncertainties[row][col] = in.readDouble();
                }
            }
            mNumRows = numRows;
            mNumColumns = numColumns;
            mCorrections = corrections;
            mCorrectionUncertainties = correctionUncertainties;
        }

        public double[][] getCorrectionsArray() { return mCorrections; }

        public double[][] getCorrectionUncertaintiesArray() { return mCorrectionUncertainties; }

        public double getDeltaTheta() { return 360.0D / mNumRows; }

        public double getDeltaPhi() { return 180.0D / (mNumColumns - 1); }

        public static final Creator<SphericalCorrections> CREATOR =
                new Creator<SphericalCorrections>() {
            @Override public SphericalCorrections createFromParcel(Parcel in) {
                return new SphericalCorrections(in);
            }
            @Override public SphericalCorrections[] newArray(int size) {
                return new SphericalCorrections[size];
            }
        };

        @Override public int describeContents() { return 0; }

        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mNumRows);
            dest.writeInt(mNumColumns);
            for (int row = 0; row < mNumRows; row++) {
                for (int col = 0; col < mNumColumns; col++) {
                    dest.writeDouble(mCorrections[row][col]);
                    dest.writeDouble(mCorrectionUncertainties[row][col]);
                }
            }
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SphericalCorrections)) return false;
            SphericalCorrections that = (SphericalCorrections) o;
            return mNumRows == that.mNumRows && mNumColumns == that.mNumColumns
                    && Arrays.deepEquals(mCorrections, that.mCorrections)
                    && Arrays.deepEquals(mCorrectionUncertainties, that.mCorrectionUncertainties);
        }

        @Override public int hashCode() {
            return Objects.hash(mNumRows, mNumColumns, Arrays.deepHashCode(mCorrections),
                    Arrays.deepHashCode(mCorrectionUncertainties));
        }

        @Override public String toString() {
            return "SphericalCorrections{rows=" + mNumRows + ", columns=" + mNumColumns + '}';
        }
    }

    /** AOSP declares Builder as a plain (non-final) static class. */
    public static class Builder {
        private double mCarrierFrequencyMHz;
        private PhaseCenterOffset mPhaseCenterOffset;
        private SphericalCorrections mPhaseCenterVariationCorrections;
        private SphericalCorrections mSignalGainCorrections;

        /**
         * @deprecated AOSP deprecated the no-arg constructor in API 33 because it
         * leaves the mandatory phase-center offset unset; use the two-arg form.
         */
        @Deprecated public Builder() {
            // AOSP's deprecated ctor seeds an all-zero offset so build() cannot NPE.
            mPhaseCenterOffset = new PhaseCenterOffset(0, 0, 0, 0, 0, 0);
        }

        public Builder(double carrierFrequencyMHz, PhaseCenterOffset phaseCenterOffset) {
            mCarrierFrequencyMHz = carrierFrequencyMHz;
            mPhaseCenterOffset = Objects.requireNonNull(phaseCenterOffset);
        }

        public Builder(GnssAntennaInfo antennaInfo) {
            mCarrierFrequencyMHz = antennaInfo.mCarrierFrequencyMHz;
            mPhaseCenterOffset = antennaInfo.mPhaseCenterOffset;
            mPhaseCenterVariationCorrections = antennaInfo.mPhaseCenterVariationCorrections;
            mSignalGainCorrections = antennaInfo.mSignalGainCorrections;
        }

        public Builder setCarrierFrequencyMHz(double carrierFrequencyMHz) {
            mCarrierFrequencyMHz = carrierFrequencyMHz;
            return this;
        }

        public Builder setPhaseCenterOffset(PhaseCenterOffset phaseCenterOffset) {
            mPhaseCenterOffset = Objects.requireNonNull(phaseCenterOffset);
            return this;
        }

        public Builder setPhaseCenterVariationCorrections(SphericalCorrections corrections) {
            mPhaseCenterVariationCorrections = corrections;
            return this;
        }

        public Builder setSignalGainCorrections(SphericalCorrections corrections) {
            mSignalGainCorrections = corrections;
            return this;
        }

        public GnssAntennaInfo build() {
            return new GnssAntennaInfo(mCarrierFrequencyMHz, mPhaseCenterOffset,
                    mPhaseCenterVariationCorrections, mSignalGainCorrections);
        }
    }
}
