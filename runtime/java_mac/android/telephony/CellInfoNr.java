package android.telephony;

import android.os.Parcel;

/**
 * A NR cell observation. Never produced on Mac.apk (no modem); the type
 * must resolve for code that instanceof-checks getAllCellInfo() results.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellInfoNr.java
 */
public final class CellInfoNr extends CellInfo {

    private CellIdentityNr mCellIdentity;
    private CellSignalStrengthNr mCellSignalStrength;

    /** @hide */
    public CellInfoNr() {
        super();
        mCellIdentity = new CellIdentityNr();
        mCellSignalStrength = new CellSignalStrengthNr();
    }

    private CellInfoNr(Parcel in) {
        super(in);
        mCellIdentity = CellIdentityNr.CREATOR.createFromParcel(in);
        mCellSignalStrength = CellSignalStrengthNr.CREATOR.createFromParcel(in);
    }

    @Override public CellIdentity getCellIdentity() { return mCellIdentity; }

    @Override public CellSignalStrength getCellSignalStrength() {
        return mCellSignalStrength;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellInfoNr)) return false;
        CellInfoNr o = (CellInfoNr) other;
        return mCellIdentity.equals(o.mCellIdentity)
                && mCellSignalStrength.equals(o.mCellSignalStrength);
    }

    @Override public int hashCode() {
        return mCellIdentity.hashCode() * 31 + mCellSignalStrength.hashCode();
    }

    @Override public String toString() {
        return "CellInfoNr:{ " + mCellIdentity + " " + mCellSignalStrength + " }";
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags, TYPE_NR);
        mCellIdentity.writeToParcel(dest, flags);
        mCellSignalStrength.writeToParcel(dest, flags);
    }

    public static final Creator<CellInfoNr> CREATOR = new Creator<CellInfoNr>() {
        @Override public CellInfoNr createFromParcel(Parcel in) {
            in.readInt(); // consume the type token
            return createFromParcelBody(in);
        }
        @Override public CellInfoNr[] newArray(int size) { return new CellInfoNr[size]; }
    };

    /** @hide */
    protected static CellInfoNr createFromParcelBody(Parcel in) {
        return new CellInfoNr(in);
    }
}
