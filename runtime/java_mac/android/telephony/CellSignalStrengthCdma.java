package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * CDMA signal strength. No radio -> every measurement UNAVAILABLE, so the
 * derived levels are all NONE_OR_UNKNOWN and getAsuLevel() is 99.
 *
 * The level/ASU tables below are AOSP's, verbatim, including the "take the
 * lesser of the dBm-derived and Ec/Io-derived level" rule that makes an
 * UNAVAILABLE Ec/Io pin the whole level to unknown.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellSignalStrengthCdma.java
 */
public final class CellSignalStrengthCdma extends CellSignalStrength implements Parcelable {

    private int mCdmaDbm;
    private int mCdmaEcio;
    private int mEvdoDbm;
    private int mEvdoEcio;
    private int mEvdoSnr;
    private int mLevel;

    public CellSignalStrengthCdma() { setDefaultValues(); }

    /** @hide */
    public CellSignalStrengthCdma(int cdmaDbm, int cdmaEcio, int evdoDbm, int evdoEcio,
            int evdoSnr) {
        mCdmaDbm = inRangeOrUnavailable(cdmaDbm, -120, 0);
        mCdmaEcio = inRangeOrUnavailable(cdmaEcio, -160, 0);
        mEvdoDbm = inRangeOrUnavailable(evdoDbm, -120, 0);
        mEvdoEcio = inRangeOrUnavailable(evdoEcio, -160, 0);
        mEvdoSnr = inRangeOrUnavailable(evdoSnr, 0, 8);
        updateLevel();
    }

    private CellSignalStrengthCdma(Parcel in) {
        mCdmaDbm = in.readInt();
        mCdmaEcio = in.readInt();
        mEvdoDbm = in.readInt();
        mEvdoEcio = in.readInt();
        mEvdoSnr = in.readInt();
        mLevel = in.readInt();
    }

    /** @hide */
    public void setDefaultValues() {
        mCdmaDbm = CellInfo.UNAVAILABLE;
        mCdmaEcio = CellInfo.UNAVAILABLE;
        mEvdoDbm = CellInfo.UNAVAILABLE;
        mEvdoEcio = CellInfo.UNAVAILABLE;
        mEvdoSnr = CellInfo.UNAVAILABLE;
        mLevel = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
    }

    private void updateLevel() {
        int cdmaLevel = getCdmaLevel();
        int evdoLevel = getEvdoLevel();
        if (evdoLevel == SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
            mLevel = cdmaLevel;
        } else if (cdmaLevel == SIGNAL_STRENGTH_NONE_OR_UNKNOWN) {
            mLevel = evdoLevel;
        } else {
            // AOSP: "We know both, use the lowest level".
            mLevel = Math.min(cdmaLevel, evdoLevel);
        }
    }

    @Override public int getLevel() { return mLevel; }

    /** AOSP uses the LOWER of the CDMA and EVDO dBm, to be conservative. */
    @Override public int getDbm() {
        return Math.min(getCdmaDbm(), getEvdoDbm());
    }

    @Override public int getAsuLevel() {
        final int cdmaDbm = getCdmaDbm();
        final int cdmaEcio = getCdmaEcio();
        int cdmaAsuLevel;
        int ecioAsuLevel;

        if (cdmaDbm == CellInfo.UNAVAILABLE) cdmaAsuLevel = 99;
        else if (cdmaDbm >= -75) cdmaAsuLevel = 16;
        else if (cdmaDbm >= -82) cdmaAsuLevel = 8;
        else if (cdmaDbm >= -90) cdmaAsuLevel = 4;
        else if (cdmaDbm >= -95) cdmaAsuLevel = 2;
        else if (cdmaDbm >= -100) cdmaAsuLevel = 1;
        else cdmaAsuLevel = 99;

        // Ec/Io are in dB*10.
        if (cdmaEcio == CellInfo.UNAVAILABLE) ecioAsuLevel = 99;
        else if (cdmaEcio >= -90) ecioAsuLevel = 16;
        else if (cdmaEcio >= -100) ecioAsuLevel = 8;
        else if (cdmaEcio >= -115) ecioAsuLevel = 4;
        else if (cdmaEcio >= -130) ecioAsuLevel = 2;
        else if (cdmaEcio >= -150) ecioAsuLevel = 1;
        else ecioAsuLevel = 99;

        return Math.min(cdmaAsuLevel, ecioAsuLevel);
    }

    public int getCdmaLevel() {
        final int cdmaDbm = getCdmaDbm();
        final int cdmaEcio = getCdmaEcio();
        int levelDbm;
        int levelEcio;

        if (cdmaDbm == CellInfo.UNAVAILABLE) levelDbm = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        else if (cdmaDbm >= -75) levelDbm = SIGNAL_STRENGTH_GREAT;
        else if (cdmaDbm >= -85) levelDbm = SIGNAL_STRENGTH_GOOD;
        else if (cdmaDbm >= -95) levelDbm = SIGNAL_STRENGTH_MODERATE;
        else if (cdmaDbm >= -100) levelDbm = SIGNAL_STRENGTH_POOR;
        else levelDbm = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;

        if (cdmaEcio == CellInfo.UNAVAILABLE) levelEcio = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        else if (cdmaEcio >= -90) levelEcio = SIGNAL_STRENGTH_GREAT;
        else if (cdmaEcio >= -110) levelEcio = SIGNAL_STRENGTH_GOOD;
        else if (cdmaEcio >= -130) levelEcio = SIGNAL_STRENGTH_MODERATE;
        else if (cdmaEcio >= -150) levelEcio = SIGNAL_STRENGTH_POOR;
        else levelEcio = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;

        return Math.min(levelDbm, levelEcio);
    }

    public int getEvdoLevel() {
        int evdoDbm = getEvdoDbm();
        int evdoSnr = getEvdoSnr();
        int levelEvdoDbm;
        int levelEvdoSnr;

        if (evdoDbm == CellInfo.UNAVAILABLE) levelEvdoDbm = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        else if (evdoDbm >= -65) levelEvdoDbm = SIGNAL_STRENGTH_GREAT;
        else if (evdoDbm >= -75) levelEvdoDbm = SIGNAL_STRENGTH_GOOD;
        else if (evdoDbm >= -90) levelEvdoDbm = SIGNAL_STRENGTH_MODERATE;
        else if (evdoDbm >= -105) levelEvdoDbm = SIGNAL_STRENGTH_POOR;
        else levelEvdoDbm = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;

        if (evdoSnr == CellInfo.UNAVAILABLE) levelEvdoSnr = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        else if (evdoSnr >= 7) levelEvdoSnr = SIGNAL_STRENGTH_GREAT;
        else if (evdoSnr >= 5) levelEvdoSnr = SIGNAL_STRENGTH_GOOD;
        else if (evdoSnr >= 3) levelEvdoSnr = SIGNAL_STRENGTH_MODERATE;
        else if (evdoSnr >= 1) levelEvdoSnr = SIGNAL_STRENGTH_POOR;
        else levelEvdoSnr = SIGNAL_STRENGTH_NONE_OR_UNKNOWN;

        return Math.min(levelEvdoDbm, levelEvdoSnr);
    }

    public int getCdmaDbm() { return mCdmaDbm; }
    public int getCdmaEcio() { return mCdmaEcio; }
    public int getEvdoDbm() { return mEvdoDbm; }
    public int getEvdoEcio() { return mEvdoEcio; }
    public int getEvdoSnr() { return mEvdoSnr; }

    @Override public int hashCode() {
        return Objects.hash(mCdmaDbm, mCdmaEcio, mEvdoDbm, mEvdoEcio, mEvdoSnr, mLevel);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof CellSignalStrengthCdma)) return false;
        CellSignalStrengthCdma s = (CellSignalStrengthCdma) o;
        return mCdmaDbm == s.mCdmaDbm && mCdmaEcio == s.mCdmaEcio && mEvdoDbm == s.mEvdoDbm
                && mEvdoEcio == s.mEvdoEcio && mEvdoSnr == s.mEvdoSnr && mLevel == s.mLevel;
    }

    @Override public String toString() {
        return "CellSignalStrengthCdma: cdmaDbm=" + mCdmaDbm + " cdmaEcio=" + mCdmaEcio
                + " evdoDbm=" + mEvdoDbm + " evdoEcio=" + mEvdoEcio + " evdoSnr=" + mEvdoSnr
                + " level=" + mLevel;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCdmaDbm);
        dest.writeInt(mCdmaEcio);
        dest.writeInt(mEvdoDbm);
        dest.writeInt(mEvdoEcio);
        dest.writeInt(mEvdoSnr);
        dest.writeInt(mLevel);
    }

    public static final Creator<CellSignalStrengthCdma> CREATOR =
            new Creator<CellSignalStrengthCdma>() {
        @Override public CellSignalStrengthCdma createFromParcel(Parcel in) {
            return new CellSignalStrengthCdma(in);
        }
        @Override public CellSignalStrengthCdma[] newArray(int size) {
            return new CellSignalStrengthCdma[size];
        }
    };
}
