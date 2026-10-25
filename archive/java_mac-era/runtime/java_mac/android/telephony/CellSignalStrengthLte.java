package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * LTE signal strength. Mac.apk has no cellular modem, so getAllCellInfo() never
 * yields one and every measurement reports CellInfo.UNAVAILABLE with the level
 * NONE_OR_UNKNOWN -- AOSP's own setDefaultValues() state.
 *
 * getDbm() returns the RSRP (AOSP's choice), which is UNAVAILABLE here rather
 * than a plausible-looking dBm.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellSignalStrengthLte.java
 */
public final class CellSignalStrengthLte extends CellSignalStrength implements Parcelable {

    // AOSP's default RSRP thresholds (carrier config can override on a device).
    private static final int[] sRsrpThresholds = new int[] { -115, -105, -95, -85 };

    private int mSignalStrength;
    private int mRssi;
    private int mRsrp;
    private int mRsrq;
    private int mRssnr;
    private int mCqiTableIndex;
    private int mCqi;
    private int mTimingAdvance;
    private int mLevel;

    public CellSignalStrengthLte() { setDefaultValues(); }

    /** @hide */
    public CellSignalStrengthLte(int rssi, int rsrp, int rsrq, int rssnr, int cqiTableIndex,
            int cqi, int timingAdvance) {
        mRssi = inRangeOrUnavailable(rssi, -113, -51);
        mSignalStrength = mRssi;
        mRsrp = inRangeOrUnavailable(rsrp, -140, -43);
        mRsrq = inRangeOrUnavailable(rsrq, -34, 3);
        mRssnr = inRangeOrUnavailable(rssnr, -20, 30);
        mCqiTableIndex = inRangeOrUnavailable(cqiTableIndex, 1, 6);
        mCqi = inRangeOrUnavailable(cqi, 0, 15);
        mTimingAdvance = inRangeOrUnavailable(timingAdvance, 0, 1282);
        updateLevel();
    }

    private CellSignalStrengthLte(Parcel in) {
        mRssi = in.readInt();
        mSignalStrength = mRssi;
        mRsrp = in.readInt();
        mRsrq = in.readInt();
        mRssnr = in.readInt();
        mCqiTableIndex = in.readInt();
        mCqi = in.readInt();
        mTimingAdvance = in.readInt();
        mLevel = in.readInt();
    }

    /** @hide */
    public void setDefaultValues() {
        mSignalStrength = CellInfo.UNAVAILABLE;
        mRssi = CellInfo.UNAVAILABLE;
        mRsrp = CellInfo.UNAVAILABLE;
        mRsrq = CellInfo.UNAVAILABLE;
        mRssnr = CellInfo.UNAVAILABLE;
        mCqiTableIndex = CellInfo.UNAVAILABLE;
        mCqi = CellInfo.UNAVAILABLE;
        mTimingAdvance = CellInfo.UNAVAILABLE;
        mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
    }

    /** AOSP's default parameter for level is RSRP. */
    private void updateLevel() {
        if (mRsrp == CellInfo.UNAVAILABLE) {
            mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
            return;
        }
        int level = NUM_SIGNAL_STRENGTH_THRESHOLDS;
        while (level > 0 && mRsrp < sRsrpThresholds[level - 1]) level--;
        mLevel = level;
    }

    @Override public int getLevel() { return mLevel; }

    @Override public int getDbm() { return mRsrp; }

    @Override public int getAsuLevel() {
        int lteAsuLevel;
        int lteDbm = mRsrp;
        if (lteDbm == CellInfo.UNAVAILABLE) lteAsuLevel = 99;
        else if (lteDbm <= -140) lteAsuLevel = 0;
        else if (lteDbm >= -43) lteAsuLevel = 97;
        else lteAsuLevel = lteDbm + 140;
        return lteAsuLevel;
    }

    public int getRsrp() { return mRsrp; }
    public int getRsrq() { return mRsrq; }
    public int getRssi() { return mRssi; }
    public int getRssnr() { return mRssnr; }
    public int getCqi() { return mCqi; }
    public int getCqiTableIndex() { return mCqiTableIndex; }
    public int getTimingAdvance() { return mTimingAdvance; }

    @Override public int hashCode() {
        return Objects.hash(mRssi, mRsrp, mRsrq, mRssnr, mCqiTableIndex, mCqi, mTimingAdvance,
                mLevel);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof CellSignalStrengthLte)) return false;
        CellSignalStrengthLte s = (CellSignalStrengthLte) o;
        return mRssi == s.mRssi && mRsrp == s.mRsrp && mRsrq == s.mRsrq && mRssnr == s.mRssnr
                && mCqiTableIndex == s.mCqiTableIndex && mCqi == s.mCqi
                && mTimingAdvance == s.mTimingAdvance && mLevel == s.mLevel;
    }

    @Override public String toString() {
        return "CellSignalStrengthLte: rssi=" + mRssi + " rsrp=" + mRsrp + " rsrq=" + mRsrq
                + " rssnr=" + mRssnr + " cqi=" + mCqi + " ta=" + mTimingAdvance
                + " level=" + mLevel;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRssi);
        dest.writeInt(mRsrp);
        dest.writeInt(mRsrq);
        dest.writeInt(mRssnr);
        dest.writeInt(mCqiTableIndex);
        dest.writeInt(mCqi);
        dest.writeInt(mTimingAdvance);
        dest.writeInt(mLevel);
    }

    public static final Creator<CellSignalStrengthLte> CREATOR =
            new Creator<CellSignalStrengthLte>() {
        @Override public CellSignalStrengthLte createFromParcel(Parcel in) {
            return new CellSignalStrengthLte(in);
        }
        @Override public CellSignalStrengthLte[] newArray(int size) {
            return new CellSignalStrengthLte[size];
        }
    };
}
