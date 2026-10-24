package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * TD-SCDMA signal strength. No radio -> every measurement UNAVAILABLE.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellSignalStrengthTdscdma.java
 */
public final class CellSignalStrengthTdscdma extends CellSignalStrength implements Parcelable {

    private static final int[] sRscpThresholds = new int[] { -114, -104, -94, -84 };

    private int mRssi;
    private int mBitErrorRate;
    private int mRscp;
    private int mLevel;

    public CellSignalStrengthTdscdma() { setDefaultValues(); }

    private CellSignalStrengthTdscdma(Parcel in) {
        mRssi = in.readInt();
        mBitErrorRate = in.readInt();
        mRscp = in.readInt();
        mLevel = in.readInt();
    }

    /** @hide */
    public void setDefaultValues() {
        mRssi = CellInfo.UNAVAILABLE;
        mBitErrorRate = CellInfo.UNAVAILABLE;
        mRscp = CellInfo.UNAVAILABLE;
        mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
    }

    @Override public int getLevel() { return mLevel; }

    @Override public int getDbm() { return mRscp; }

    @Override public int getAsuLevel() {
        if (mRscp != CellInfo.UNAVAILABLE) return getAsuFromRscpDbm(mRscp);
        return getAsuFromRscpDbm(CellInfo.UNAVAILABLE);
    }

    public int getRscp() { return mRscp; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRssi);
        dest.writeInt(mBitErrorRate);
        dest.writeInt(mRscp);
        dest.writeInt(mLevel);
    }

    public static final Creator<CellSignalStrengthTdscdma> CREATOR =
            new Creator<CellSignalStrengthTdscdma>() {
        @Override public CellSignalStrengthTdscdma createFromParcel(Parcel in) {
            return new CellSignalStrengthTdscdma(in);
        }
        @Override public CellSignalStrengthTdscdma[] newArray(int size) {
            return new CellSignalStrengthTdscdma[size];
        }
    };

    @Override public int hashCode() {
        return Objects.hash(mRssi, mBitErrorRate, mRscp, mLevel);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof CellSignalStrengthTdscdma)) return false;
        CellSignalStrengthTdscdma s = (CellSignalStrengthTdscdma) o;
        return mRssi == s.mRssi && mBitErrorRate == s.mBitErrorRate && mRscp == s.mRscp
                && mLevel == s.mLevel;
    }

    @Override public String toString() {
        return "CellSignalStrengthTdscdma: rscp=" + mRscp + " level=" + mLevel;
    }
}
