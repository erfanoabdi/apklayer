package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Objects;

/**
 * One signal-measurement threshold set, used by SignalStrengthUpdateRequest.
 *
 * Immutable value type; no radio required to build or read one. Mac.apk never
 * reports a threshold crossing because there is no radio to cross one.
 *
 * Source: frameworks/base/telephony/java/android/telephony/SignalThresholdInfo.java
 */
public final class SignalThresholdInfo implements Parcelable {

    public static final int SIGNAL_MEASUREMENT_TYPE_UNKNOWN = 0;
    public static final int SIGNAL_MEASUREMENT_TYPE_RSSI = 1;
    public static final int SIGNAL_MEASUREMENT_TYPE_RSCP = 2;
    public static final int SIGNAL_MEASUREMENT_TYPE_RSRP = 3;
    public static final int SIGNAL_MEASUREMENT_TYPE_RSRQ = 4;
    public static final int SIGNAL_MEASUREMENT_TYPE_RSSNR = 5;
    public static final int SIGNAL_MEASUREMENT_TYPE_SSRSRP = 6;
    public static final int SIGNAL_MEASUREMENT_TYPE_SSRSRQ = 7;
    public static final int SIGNAL_MEASUREMENT_TYPE_SSSINR = 8;
    public static final int SIGNAL_MEASUREMENT_TYPE_ECNO = 9;

    /** AOSP's MAXIMUM_NUMBER_OF_THRESHOLDS_ALLOWED. */
    private static final int MAXIMUM_NUMBER_OF_THRESHOLDS_ALLOWED = 4;
    /** AOSP's MINIMUM_NUMBER_OF_THRESHOLDS_ALLOWED. */
    private static final int MINIMUM_NUMBER_OF_THRESHOLDS_ALLOWED = 1;

    private final int mRan;
    private final int mSignalMeasurementType;
    private final int mHysteresisDb;
    private final int[] mThresholds;

    private SignalThresholdInfo(int ran, int signalMeasurementType, int hysteresisDb,
            int[] thresholds) {
        mRan = ran;
        mSignalMeasurementType = signalMeasurementType;
        mHysteresisDb = hysteresisDb;
        mThresholds = thresholds == null ? new int[0] : thresholds.clone();
    }

    public int getRadioAccessNetworkType() { return mRan; }

    public int getSignalMeasurementType() { return mSignalMeasurementType; }

    public int getHysteresisDb() { return mHysteresisDb; }

    public int[] getThresholds() { return mThresholds.clone(); }

    public static int getMaximumNumberOfThresholdsAllowed() {
        return MAXIMUM_NUMBER_OF_THRESHOLDS_ALLOWED;
    }

    public static int getMinimumNumberOfThresholdsAllowed() {
        return MINIMUM_NUMBER_OF_THRESHOLDS_ALLOWED;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mRan);
        out.writeInt(mSignalMeasurementType);
        out.writeInt(mHysteresisDb);
        out.writeIntArray(mThresholds);
    }

    private SignalThresholdInfo(Parcel in) {
        mRan = in.readInt();
        mSignalMeasurementType = in.readInt();
        mHysteresisDb = in.readInt();
        int[] t = in.createIntArray();
        mThresholds = t == null ? new int[0] : t;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignalThresholdInfo)) return false;
        SignalThresholdInfo other = (SignalThresholdInfo) o;
        return mRan == other.mRan && mSignalMeasurementType == other.mSignalMeasurementType
                && mHysteresisDb == other.mHysteresisDb
                && Arrays.equals(mThresholds, other.mThresholds);
    }

    @Override public int hashCode() {
        return Objects.hash(mRan, mSignalMeasurementType, mHysteresisDb,
                Arrays.hashCode(mThresholds));
    }

    @Override public String toString() {
        return "SignalThresholdInfo{mRan=" + mRan + " mSignalMeasurementType="
                + mSignalMeasurementType + " mHysteresisDb=" + mHysteresisDb
                + " mThresholds=" + Arrays.toString(mThresholds) + "}";
    }

    public static final Creator<SignalThresholdInfo> CREATOR = new Creator<SignalThresholdInfo>() {
        @Override public SignalThresholdInfo createFromParcel(Parcel in) {
            return new SignalThresholdInfo(in);
        }
        @Override public SignalThresholdInfo[] newArray(int size) {
            return new SignalThresholdInfo[size];
        }
    };

    public static final class Builder {
        private int mRan = AccessNetworkConstants.AccessNetworkType.UNKNOWN;
        private int mSignalMeasurementType = SIGNAL_MEASUREMENT_TYPE_UNKNOWN;
        private int mHysteresisDb = 2;
        private int[] mThresholds = null;

        public Builder() { }

        public Builder setRadioAccessNetworkType(int ran) { mRan = ran; return this; }

        public Builder setSignalMeasurementType(int signalMeasurementType) {
            mSignalMeasurementType = signalMeasurementType;
            return this;
        }

        public Builder setHysteresisDb(int hysteresisDb) {
            if (hysteresisDb < 0) {
                throw new IllegalArgumentException("hysteresis db value should not be less than 0");
            }
            mHysteresisDb = hysteresisDb;
            return this;
        }

        public Builder setThresholds(int[] thresholds) {
            Objects.requireNonNull(thresholds, "thresholds must not be null");
            if (thresholds.length < MINIMUM_NUMBER_OF_THRESHOLDS_ALLOWED
                    || thresholds.length > MAXIMUM_NUMBER_OF_THRESHOLDS_ALLOWED) {
                throw new IllegalArgumentException("thresholds length must between "
                        + MINIMUM_NUMBER_OF_THRESHOLDS_ALLOWED + " and "
                        + MAXIMUM_NUMBER_OF_THRESHOLDS_ALLOWED);
            }
            mThresholds = thresholds.clone();
            Arrays.sort(mThresholds);
            return this;
        }

        public SignalThresholdInfo build() {
            return new SignalThresholdInfo(mRan, mSignalMeasurementType, mHysteresisDb,
                    mThresholds);
        }
    }
}
