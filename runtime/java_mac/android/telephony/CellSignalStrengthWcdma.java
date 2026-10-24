package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * WCDMA signal strength. No radio -> every measurement UNAVAILABLE, level
 * NONE_OR_UNKNOWN.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellSignalStrengthWcdma.java
 */
public final class CellSignalStrengthWcdma extends CellSignalStrength implements Parcelable {

    private static final int WCDMA_RSSI_MAX = -51;
    private static final int WCDMA_RSSI_GREAT = -77;
    private static final int WCDMA_RSSI_GOOD = -87;
    private static final int WCDMA_RSSI_MODERATE = -97;
    private static final int WCDMA_RSSI_POOR = -107;
    private static final int WCDMA_RSSI_MIN = -113;

    private static final int[] sRssiThresholds =
            new int[] { WCDMA_RSSI_POOR, WCDMA_RSSI_MODERATE, WCDMA_RSSI_GOOD, WCDMA_RSSI_GREAT };

    private int mRssi;
    private int mBitErrorRate;
    private int mRscp;
    private int mEcNo;
    private int mLevel;

    public CellSignalStrengthWcdma() { setDefaultValues(); }

    /** @hide */
    public CellSignalStrengthWcdma(int rssi, int ber, int rscp, int ecno) {
        mRssi = inRangeOrUnavailable(rssi, WCDMA_RSSI_MIN, WCDMA_RSSI_MAX);
        mBitErrorRate = inRangeOrUnavailable(ber, 0, 7, 99);
        mRscp = inRangeOrUnavailable(rscp, -120, -24);
        mEcNo = inRangeOrUnavailable(ecno, -24, 1);
        updateLevel();
    }

    private CellSignalStrengthWcdma(Parcel in) {
        mRssi = in.readInt();
        mBitErrorRate = in.readInt();
        mRscp = in.readInt();
        mEcNo = in.readInt();
        mLevel = in.readInt();
    }

    /** @hide */
    public void setDefaultValues() {
        mRssi = CellInfo.UNAVAILABLE;
        mBitErrorRate = CellInfo.UNAVAILABLE;
        mRscp = CellInfo.UNAVAILABLE;
        mEcNo = CellInfo.UNAVAILABLE;
        mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
    }

    /** AOSP's default level-calculation method is RSSI, not RSCP. */
    private void updateLevel() {
        if (mRssi < WCDMA_RSSI_MIN || mRssi > WCDMA_RSSI_MAX) {
            mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
            return;
        }
        int level = NUM_SIGNAL_STRENGTH_THRESHOLDS;
        while (level > 0 && mRssi < sRssiThresholds[level - 1]) level--;
        mLevel = level;
    }

    @Override public int getLevel() { return mLevel; }

    /** AOSP prefers RSCP when available and falls back to RSSI. */
    @Override public int getDbm() {
        if (mRscp != CellInfo.UNAVAILABLE) return mRscp;
        return mRssi;
    }

    @Override public int getAsuLevel() {
        if (mRscp != CellInfo.UNAVAILABLE) return getAsuFromRscpDbm(mRscp);
        if (mRssi != CellInfo.UNAVAILABLE) return getAsuFromRssiDbm(mRssi);
        return getAsuFromRscpDbm(CellInfo.UNAVAILABLE);
    }

    /** @hide */ public int getRssi() { return mRssi; }
    /** @hide */ public int getRscp() { return mRscp; }
    /** @hide */ public int getBitErrorRate() { return mBitErrorRate; }

    public int getEcNo() { return mEcNo; }

    @Override public int hashCode() {
        return Objects.hash(mRssi, mBitErrorRate, mRscp, mEcNo, mLevel);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof CellSignalStrengthWcdma)) return false;
        CellSignalStrengthWcdma s = (CellSignalStrengthWcdma) o;
        return mRssi == s.mRssi && mBitErrorRate == s.mBitErrorRate && mRscp == s.mRscp
                && mEcNo == s.mEcNo && mLevel == s.mLevel;
    }

    @Override public String toString() {
        return "CellSignalStrengthWcdma: ss=" + mRssi + " ber=" + mBitErrorRate
                + " rscp=" + mRscp + " ecno=" + mEcNo + " level=" + mLevel;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRssi);
        dest.writeInt(mBitErrorRate);
        dest.writeInt(mRscp);
        dest.writeInt(mEcNo);
        dest.writeInt(mLevel);
    }

    public static final Creator<CellSignalStrengthWcdma> CREATOR =
            new Creator<CellSignalStrengthWcdma>() {
        @Override public CellSignalStrengthWcdma createFromParcel(Parcel in) {
            return new CellSignalStrengthWcdma(in);
        }
        @Override public CellSignalStrengthWcdma[] newArray(int size) {
            return new CellSignalStrengthWcdma[size];
        }
    };
}
