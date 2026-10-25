package android.telephony;

import android.os.Parcel;

/**
 * A WCDMA cell observation.
 *
 * Never produced on Mac.apk (no modem), so the identity and signal strength are
 * both AOSP's all-UNAVAILABLE defaults. The type must still resolve for code
 * that instanceof-checks getAllCellInfo() results, and the Parcelable half is
 * real so an app can round-trip one it built itself.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellInfoWcdma.java
 */
public final class CellInfoWcdma extends CellInfo {

    private CellIdentityWcdma mCellIdentity;
    private CellSignalStrengthWcdma mCellSignalStrength;

    /** @hide */
    public CellInfoWcdma() {
        super();
        mCellIdentity = new CellIdentityWcdma();
        mCellSignalStrength = new CellSignalStrengthWcdma();
    }

    /** @hide */
    public CellInfoWcdma(int connectionStatus, boolean registered, long timeStamp,
            CellIdentityWcdma cellIdentity, CellSignalStrengthWcdma cellSignalStrength) {
        super(connectionStatus, registered, timeStamp);
        mCellIdentity = cellIdentity;
        mCellSignalStrength = cellSignalStrength;
    }

    private CellInfoWcdma(Parcel in) {
        super(in);
        mCellIdentity = CellIdentityWcdma.CREATOR.createFromParcel(in);
        mCellSignalStrength = CellSignalStrengthWcdma.CREATOR.createFromParcel(in);
    }

    @Override public CellIdentityWcdma getCellIdentity() { return mCellIdentity; }

    /** @hide */
    public void setCellIdentity(CellIdentityWcdma cid) { mCellIdentity = cid; }

    @Override public CellSignalStrengthWcdma getCellSignalStrength() { return mCellSignalStrength; }

    /** @hide */
    public void setCellSignalStrength(CellSignalStrengthWcdma css) { mCellSignalStrength = css; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellInfoWcdma)) return false;
        CellInfoWcdma o = (CellInfoWcdma) other;
        return mCellIdentity.equals(o.mCellIdentity)
                && mCellSignalStrength.equals(o.mCellSignalStrength);
    }

    @Override public int hashCode() {
        return mCellIdentity.hashCode() * 31 + mCellSignalStrength.hashCode();
    }

    @Override public String toString() {
        return "CellInfoWcdma:{ " + mCellIdentity + " " + mCellSignalStrength + " }";
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags, TYPE_WCDMA);
        mCellIdentity.writeToParcel(dest, flags);
        mCellSignalStrength.writeToParcel(dest, flags);
    }

    public static final Creator<CellInfoWcdma> CREATOR = new Creator<CellInfoWcdma>() {
        @Override public CellInfoWcdma createFromParcel(Parcel in) {
            in.readInt(); // consume the type token
            return createFromParcelBody(in);
        }
        @Override public CellInfoWcdma[] newArray(int size) { return new CellInfoWcdma[size]; }
    };

    /** @hide */
    protected static CellInfoWcdma createFromParcelBody(Parcel in) {
        return new CellInfoWcdma(in);
    }
}
