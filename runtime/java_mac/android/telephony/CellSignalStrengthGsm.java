package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * GSM signal strength. No radio -> RSSI/BER/timing-advance all UNAVAILABLE and
 * the level NONE_OR_UNKNOWN, which is AOSP's own default state.
 *
 * NOTE getDbm() returns the RSSI, i.e. CellInfo.UNAVAILABLE here. It does NOT
 * return -113: -113 is a real reading at the bottom of the GSM range, and
 * returning it would claim an extremely weak signal rather than no signal.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellSignalStrengthGsm.java
 */
public final class CellSignalStrengthGsm extends CellSignalStrength implements Parcelable {

    private static final int GSM_RSSI_MAX = -51;
    private static final int GSM_RSSI_GREAT = -89;
    private static final int GSM_RSSI_GOOD = -97;
    private static final int GSM_RSSI_MODERATE = -103;
    private static final int GSM_RSSI_POOR = -107;
    private static final int GSM_RSSI_MIN = -113;

    private static final int[] sRssiThresholds =
            new int[] { GSM_RSSI_POOR, GSM_RSSI_MODERATE, GSM_RSSI_GOOD, GSM_RSSI_GREAT };

    private int mRssi;
    private int mBitErrorRate;
    private int mTimingAdvance;
    private int mLevel;

    public CellSignalStrengthGsm() { setDefaultValues(); }

    /** @hide */
    public CellSignalStrengthGsm(int rssi, int ber, int ta) {
        mRssi = inRangeOrUnavailable(rssi, GSM_RSSI_MIN, GSM_RSSI_MAX);
        mBitErrorRate = inRangeOrUnavailable(ber, 0, 7, 99);
        mTimingAdvance = inRangeOrUnavailable(ta, 0, 219);
        updateLevel();
    }

    /** @hide */
    public CellSignalStrengthGsm(CellSignalStrengthGsm s) {
        mRssi = s.mRssi;
        mBitErrorRate = s.mBitErrorRate;
        mTimingAdvance = s.mTimingAdvance;
        mLevel = s.mLevel;
    }

    private CellSignalStrengthGsm(Parcel in) {
        mRssi = in.readInt();
        mBitErrorRate = in.readInt();
        mTimingAdvance = in.readInt();
        mLevel = in.readInt();
    }

    /** @hide */
    public void setDefaultValues() {
        mRssi = CellInfo.UNAVAILABLE;
        mBitErrorRate = CellInfo.UNAVAILABLE;
        mTimingAdvance = CellInfo.UNAVAILABLE;
        mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
    }

    private void updateLevel() {
        if (mRssi < GSM_RSSI_MIN || mRssi > GSM_RSSI_MAX) {
            mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
            return;
        }
        int level = NUM_SIGNAL_STRENGTH_THRESHOLDS;
        while (level > 0 && mRssi < sRssiThresholds[level - 1]) level--;
        mLevel = level;
    }

    @Override public int getLevel() { return mLevel; }

    @Override public int getDbm() { return mRssi; }

    @Override public int getAsuLevel() { return getAsuFromRssiDbm(mRssi); }

    public int getRssi() { return mRssi; }

    public int getBitErrorRate() { return mBitErrorRate; }

    public int getTimingAdvance() { return mTimingAdvance; }

    @Override public int hashCode() { return Objects.hash(mRssi, mBitErrorRate, mTimingAdvance); }

    @Override public boolean equals(Object o) {
        if (!(o instanceof CellSignalStrengthGsm)) return false;
        CellSignalStrengthGsm s = (CellSignalStrengthGsm) o;
        return mRssi == s.mRssi && mBitErrorRate == s.mBitErrorRate
                && mTimingAdvance == s.mTimingAdvance && mLevel == s.mLevel;
    }

    @Override public String toString() {
        return "CellSignalStrengthGsm: rssi=" + mRssi + " ber=" + mBitErrorRate
                + " mTa=" + mTimingAdvance + " mLevel=" + mLevel;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRssi);
        dest.writeInt(mBitErrorRate);
        dest.writeInt(mTimingAdvance);
        dest.writeInt(mLevel);
    }

    public static final Creator<CellSignalStrengthGsm> CREATOR =
            new Creator<CellSignalStrengthGsm>() {
        @Override public CellSignalStrengthGsm createFromParcel(Parcel in) {
            return new CellSignalStrengthGsm(in);
        }
        @Override public CellSignalStrengthGsm[] newArray(int size) {
            return new CellSignalStrengthGsm[size];
        }
    };
}
