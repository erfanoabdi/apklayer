package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate signal strength across every radio technology.
 *
 * No radio on a Mac. A default-constructed SignalStrength holds one
 * default-constructed CellSignalStrength per technology -- each of which
 * reports UNAVAILABLE / NONE_OR_UNKNOWN -- which is AOSP's own shape and gives
 * getLevel() == SIGNAL_STRENGTH_NONE_OR_UNKNOWN honestly rather than by
 * hardcoding a 0.
 *
 * Source: frameworks/base/telephony/java/android/telephony/SignalStrength.java
 */
public class SignalStrength implements Parcelable {

    public static final int INVALID = Integer.MAX_VALUE;

    private final CellSignalStrengthCdma mCdma;
    private final CellSignalStrengthGsm mGsm;
    private final CellSignalStrengthWcdma mWcdma;
    private final CellSignalStrengthLte mLte;
    private final long mTimestampMillis;

    /** @hide AOSP marks the no-arg constructor hidden; kept for framework use. */
    public SignalStrength() {
        this(new CellSignalStrengthCdma(), new CellSignalStrengthGsm(),
                new CellSignalStrengthWcdma(), new CellSignalStrengthLte());
    }

    /** @hide */
    public SignalStrength(CellSignalStrengthCdma cdma, CellSignalStrengthGsm gsm,
            CellSignalStrengthWcdma wcdma, CellSignalStrengthLte lte) {
        mCdma = cdma;
        mGsm = gsm;
        mWcdma = wcdma;
        mLte = lte;
        mTimestampMillis = System.currentTimeMillis();
    }

    public SignalStrength(SignalStrength s) {
        mCdma = new CellSignalStrengthCdma();
        mGsm = new CellSignalStrengthGsm(s.mGsm);
        mWcdma = new CellSignalStrengthWcdma();
        mLte = new CellSignalStrengthLte();
        mTimestampMillis = s.mTimestampMillis;
    }

    private SignalStrength(Parcel in) {
        mCdma = CellSignalStrengthCdma.CREATOR.createFromParcel(in);
        mGsm = CellSignalStrengthGsm.CREATOR.createFromParcel(in);
        mWcdma = CellSignalStrengthWcdma.CREATOR.createFromParcel(in);
        mLte = CellSignalStrengthLte.CREATOR.createFromParcel(in);
        mTimestampMillis = in.readLong();
    }

    /** AOSP returns only the strengths that are VALID; ours are all invalid. */
    public List<CellSignalStrength> getCellSignalStrengths() {
        return getCellSignalStrengths(CellSignalStrength.class);
    }

    @SuppressWarnings("unchecked")
    public <T extends CellSignalStrength> List<T> getCellSignalStrengths(Class<T> clazz) {
        List<T> out = new ArrayList<>();
        for (CellSignalStrength css : new CellSignalStrength[] { mLte, mGsm, mWcdma, mCdma }) {
            // Nothing is valid here (no radio), so nothing is reported. AOSP
            // filters on isValid() for exactly this reason.
            if (css.getLevel() == CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN
                    && css.getDbm() == CellInfo.UNAVAILABLE) {
                continue;
            }
            if (clazz.isInstance(css)) out.add((T) css);
        }
        return Collections.unmodifiableList(out);
    }

    /** AOSP: the highest level reported by any technology. */
    public int getLevel() {
        int level = CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN;
        for (CellSignalStrength css : new CellSignalStrength[] { mLte, mGsm, mWcdma, mCdma }) {
            level = Math.max(level, css.getLevel());
        }
        return level;
    }

    public long getTimestampMillis() { return mTimestampMillis; }

    /** @deprecated AOSP replaced this by getCellSignalStrengths() in API 29. */
    @Deprecated public int getGsmSignalStrength() { return mGsm.getAsuLevel(); }
    /** @deprecated see {@link #getGsmSignalStrength()}. */
    @Deprecated public int getGsmBitErrorRate() { return mGsm.getBitErrorRate(); }
    /** @deprecated see {@link #getGsmSignalStrength()}. */
    @Deprecated public int getCdmaDbm() { return mCdma.getCdmaDbm(); }
    /** @deprecated see {@link #getGsmSignalStrength()}. */
    @Deprecated public int getCdmaEcio() { return mCdma.getCdmaEcio(); }
    /** @deprecated see {@link #getGsmSignalStrength()}. */
    @Deprecated public int getEvdoDbm() { return mCdma.getEvdoDbm(); }
    /** @deprecated see {@link #getGsmSignalStrength()}. */
    @Deprecated public int getEvdoEcio() { return mCdma.getEvdoEcio(); }
    /** @deprecated see {@link #getGsmSignalStrength()}. */
    @Deprecated public int getEvdoSnr() { return mCdma.getEvdoSnr(); }

    /**
     * @deprecated AOSP: "there is no longer a single technology". With no radio
     * at all, no technology is in use, so this is false rather than an invented
     * true.
     */
    @Deprecated public boolean isGsm() { return false; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        mCdma.writeToParcel(out, flags);
        mGsm.writeToParcel(out, flags);
        mWcdma.writeToParcel(out, flags);
        mLte.writeToParcel(out, flags);
        out.writeLong(mTimestampMillis);
    }

    public static final Creator<SignalStrength> CREATOR = new Creator<SignalStrength>() {
        @Override public SignalStrength createFromParcel(Parcel in) {
            return new SignalStrength(in);
        }
        @Override public SignalStrength[] newArray(int size) { return new SignalStrength[size]; }
    };

    @Override public int hashCode() { return Objects.hash(mCdma, mGsm, mWcdma, mLte); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignalStrength)) return false;
        SignalStrength s = (SignalStrength) o;
        return mCdma.equals(s.mCdma) && mGsm.equals(s.mGsm) && mWcdma.equals(s.mWcdma)
                && mLte.equals(s.mLte);
    }

    @Override public String toString() {
        return "SignalStrength:{level=" + getLevel() + "}";
    }
}
