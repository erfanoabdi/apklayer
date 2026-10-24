package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 5G NR signal strength. No radio on a Mac, so every measurement is
 * CellInfo.UNAVAILABLE and the level NONE_OR_UNKNOWN -- AOSP's own default.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellSignalStrengthNr.java
 */
public final class CellSignalStrengthNr extends CellSignalStrength implements Parcelable {

    /** AOSP's default SS-RSRP thresholds. */
    private static final int[] sRsrpThresholds = new int[] { -110, -90, -80, -65 };

    private int mCsiRsrp;
    private int mCsiRsrq;
    private int mCsiSinr;
    private int mCsiCqiTableIndex;
    private List<Integer> mCsiCqiReport;
    private int mSsRsrp;
    private int mSsRsrq;
    private int mSsSinr;
    private int mTimingAdvance;
    private int mLevel;

    public CellSignalStrengthNr() { setDefaultValues(); }

    private CellSignalStrengthNr(Parcel in) {
        mCsiRsrp = in.readInt();
        mCsiRsrq = in.readInt();
        mCsiSinr = in.readInt();
        mCsiCqiTableIndex = in.readInt();
        int[] cqi = in.createIntArray();
        if (cqi == null) {
            mCsiCqiReport = Collections.emptyList();
        } else {
            java.util.List<Integer> list = new java.util.ArrayList<>(cqi.length);
            for (int v : cqi) list.add(v);
            mCsiCqiReport = Collections.unmodifiableList(list);
        }
        mSsRsrp = in.readInt();
        mSsRsrq = in.readInt();
        mSsSinr = in.readInt();
        mTimingAdvance = in.readInt();
        mLevel = in.readInt();
    }

    /** @hide */
    public void setDefaultValues() {
        mCsiRsrp = CellInfo.UNAVAILABLE;
        mCsiRsrq = CellInfo.UNAVAILABLE;
        mCsiSinr = CellInfo.UNAVAILABLE;
        mCsiCqiTableIndex = CellInfo.UNAVAILABLE;
        mCsiCqiReport = Collections.emptyList();
        mSsRsrp = CellInfo.UNAVAILABLE;
        mSsRsrq = CellInfo.UNAVAILABLE;
        mSsSinr = CellInfo.UNAVAILABLE;
        mTimingAdvance = CellInfo.UNAVAILABLE;
        mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
    }

    public int getCsiRsrp() { return mCsiRsrp; }
    public int getCsiRsrq() { return mCsiRsrq; }
    public int getCsiSinr() { return mCsiSinr; }
    public int getCsiCqiTableIndex() { return mCsiCqiTableIndex; }
    public List<Integer> getCsiCqiReport() { return mCsiCqiReport; }
    public int getSsRsrp() { return mSsRsrp; }
    public int getSsRsrq() { return mSsRsrq; }
    public int getSsSinr() { return mSsSinr; }
    public int getTimingAdvanceMicros() { return mTimingAdvance; }

    /** AOSP reports the SS-RSRP as the representative dBm. */
    @Override public int getDbm() { return mSsRsrp; }

    @Override public int getAsuLevel() {
        int asuLevel;
        int nrDbm = getDbm();
        if (nrDbm == CellInfo.UNAVAILABLE) asuLevel = 99;
        else if (nrDbm <= -140) asuLevel = 0;
        else if (nrDbm >= -43) asuLevel = 97;
        else asuLevel = nrDbm + 140;
        return asuLevel;
    }

    @Override public int getLevel() { return mLevel; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCsiRsrp);
        dest.writeInt(mCsiRsrq);
        dest.writeInt(mCsiSinr);
        dest.writeInt(mCsiCqiTableIndex);
        int[] cqi = new int[mCsiCqiReport.size()];
        for (int i = 0; i < cqi.length; i++) cqi[i] = mCsiCqiReport.get(i);
        dest.writeIntArray(cqi);
        dest.writeInt(mSsRsrp);
        dest.writeInt(mSsRsrq);
        dest.writeInt(mSsSinr);
        dest.writeInt(mTimingAdvance);
        dest.writeInt(mLevel);
    }

    public static final Creator<CellSignalStrengthNr> CREATOR = new Creator<CellSignalStrengthNr>() {
        @Override public CellSignalStrengthNr createFromParcel(Parcel in) {
            return new CellSignalStrengthNr(in);
        }
        @Override public CellSignalStrengthNr[] newArray(int size) {
            return new CellSignalStrengthNr[size];
        }
    };

    @Override public int hashCode() {
        return Objects.hash(mCsiRsrp, mCsiRsrq, mCsiSinr, mCsiCqiTableIndex, mCsiCqiReport,
                mSsRsrp, mSsRsrq, mSsSinr, mTimingAdvance, mLevel);
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CellSignalStrengthNr)) return false;
        CellSignalStrengthNr o = (CellSignalStrengthNr) obj;
        return mCsiRsrp == o.mCsiRsrp && mCsiRsrq == o.mCsiRsrq && mCsiSinr == o.mCsiSinr
                && mCsiCqiTableIndex == o.mCsiCqiTableIndex
                && mCsiCqiReport.equals(o.mCsiCqiReport)
                && mSsRsrp == o.mSsRsrp && mSsRsrq == o.mSsRsrq && mSsSinr == o.mSsSinr
                && mTimingAdvance == o.mTimingAdvance && mLevel == o.mLevel;
    }

    @Override public String toString() {
        return "CellSignalStrengthNr:{ ssRsrp=" + mSsRsrp + " level=" + mLevel + " }";
    }
}
