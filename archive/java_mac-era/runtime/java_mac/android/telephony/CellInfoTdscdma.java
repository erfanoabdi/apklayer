package android.telephony;

import android.os.Parcel;

/**
 * A TDSCDMA cell observation. Never produced on Mac.apk (no modem); the type
 * must resolve for code that instanceof-checks getAllCellInfo() results.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellInfoTdscdma.java
 */
public final class CellInfoTdscdma extends CellInfo {

    private CellIdentityTdscdma mCellIdentity;
    private CellSignalStrengthTdscdma mCellSignalStrength;

    /** @hide */
    public CellInfoTdscdma() {
        super();
        mCellIdentity = new CellIdentityTdscdma();
        mCellSignalStrength = new CellSignalStrengthTdscdma();
    }

    private CellInfoTdscdma(Parcel in) {
        super(in);
        mCellIdentity = CellIdentityTdscdma.CREATOR.createFromParcel(in);
        mCellSignalStrength = CellSignalStrengthTdscdma.CREATOR.createFromParcel(in);
    }

    @Override public CellIdentityTdscdma getCellIdentity() { return mCellIdentity; }

    @Override public CellSignalStrengthTdscdma getCellSignalStrength() {
        return mCellSignalStrength;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellInfoTdscdma)) return false;
        CellInfoTdscdma o = (CellInfoTdscdma) other;
        return mCellIdentity.equals(o.mCellIdentity)
                && mCellSignalStrength.equals(o.mCellSignalStrength);
    }

    @Override public int hashCode() {
        return mCellIdentity.hashCode() * 31 + mCellSignalStrength.hashCode();
    }

    @Override public String toString() {
        return "CellInfoTdscdma:{ " + mCellIdentity + " " + mCellSignalStrength + " }";
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags, TYPE_TDSCDMA);
        mCellIdentity.writeToParcel(dest, flags);
        mCellSignalStrength.writeToParcel(dest, flags);
    }

    public static final Creator<CellInfoTdscdma> CREATOR = new Creator<CellInfoTdscdma>() {
        @Override public CellInfoTdscdma createFromParcel(Parcel in) {
            in.readInt(); // consume the type token
            return createFromParcelBody(in);
        }
        @Override public CellInfoTdscdma[] newArray(int size) { return new CellInfoTdscdma[size]; }
    };

    /** @hide */
    protected static CellInfoTdscdma createFromParcelBody(Parcel in) {
        return new CellInfoTdscdma(in);
    }
}
