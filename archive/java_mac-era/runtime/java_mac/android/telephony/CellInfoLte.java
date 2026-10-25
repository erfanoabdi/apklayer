package android.telephony;

import android.os.Parcel;

/**
 * A LTE cell observation.
 *
 * Never produced on Mac.apk (no modem), so the identity and signal strength are
 * both AOSP's all-UNAVAILABLE defaults. The type must still resolve for code
 * that instanceof-checks getAllCellInfo() results, and the Parcelable half is
 * real so an app can round-trip one it built itself.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellInfoLte.java
 */
public final class CellInfoLte extends CellInfo {

    private CellIdentityLte mCellIdentity;
    private CellSignalStrengthLte mCellSignalStrength;

    /** @hide */
    public CellInfoLte() {
        super();
        mCellIdentity = new CellIdentityLte();
        mCellSignalStrength = new CellSignalStrengthLte();
    }

    /** @hide */
    public CellInfoLte(int connectionStatus, boolean registered, long timeStamp,
            CellIdentityLte cellIdentity, CellSignalStrengthLte cellSignalStrength) {
        super(connectionStatus, registered, timeStamp);
        mCellIdentity = cellIdentity;
        mCellSignalStrength = cellSignalStrength;
    }

    private CellInfoLte(Parcel in) {
        super(in);
        mCellIdentity = CellIdentityLte.CREATOR.createFromParcel(in);
        mCellSignalStrength = CellSignalStrengthLte.CREATOR.createFromParcel(in);
    }

    @Override public CellIdentityLte getCellIdentity() { return mCellIdentity; }

    /** @hide */
    public void setCellIdentity(CellIdentityLte cid) { mCellIdentity = cid; }

    @Override public CellSignalStrengthLte getCellSignalStrength() { return mCellSignalStrength; }

    /** @hide */
    public void setCellSignalStrength(CellSignalStrengthLte css) { mCellSignalStrength = css; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellInfoLte)) return false;
        CellInfoLte o = (CellInfoLte) other;
        return mCellIdentity.equals(o.mCellIdentity)
                && mCellSignalStrength.equals(o.mCellSignalStrength);
    }

    @Override public int hashCode() {
        return mCellIdentity.hashCode() * 31 + mCellSignalStrength.hashCode();
    }

    @Override public String toString() {
        return "CellInfoLte:{ " + mCellIdentity + " " + mCellSignalStrength + " }";
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags, TYPE_LTE);
        mCellIdentity.writeToParcel(dest, flags);
        mCellSignalStrength.writeToParcel(dest, flags);
    }

    public static final Creator<CellInfoLte> CREATOR = new Creator<CellInfoLte>() {
        @Override public CellInfoLte createFromParcel(Parcel in) {
            in.readInt(); // consume the type token
            return createFromParcelBody(in);
        }
        @Override public CellInfoLte[] newArray(int size) { return new CellInfoLte[size]; }
    };

    /** @hide */
    protected static CellInfoLte createFromParcelBody(Parcel in) {
        return new CellInfoLte(in);
    }
}
