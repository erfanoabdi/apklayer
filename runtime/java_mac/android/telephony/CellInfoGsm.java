package android.telephony;

import android.os.Parcel;

/**
 * A GSM cell observation.
 *
 * Never produced on Mac.apk (no modem), so the identity and signal strength are
 * both AOSP's all-UNAVAILABLE defaults. The type must still resolve for code
 * that instanceof-checks getAllCellInfo() results, and the Parcelable half is
 * real so an app can round-trip one it built itself.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellInfoGsm.java
 */
public final class CellInfoGsm extends CellInfo {

    private CellIdentityGsm mCellIdentity;
    private CellSignalStrengthGsm mCellSignalStrength;

    /** @hide */
    public CellInfoGsm() {
        super();
        mCellIdentity = new CellIdentityGsm();
        mCellSignalStrength = new CellSignalStrengthGsm();
    }

    /** @hide */
    public CellInfoGsm(int connectionStatus, boolean registered, long timeStamp,
            CellIdentityGsm cellIdentity, CellSignalStrengthGsm cellSignalStrength) {
        super(connectionStatus, registered, timeStamp);
        mCellIdentity = cellIdentity;
        mCellSignalStrength = cellSignalStrength;
    }

    private CellInfoGsm(Parcel in) {
        super(in);
        mCellIdentity = CellIdentityGsm.CREATOR.createFromParcel(in);
        mCellSignalStrength = CellSignalStrengthGsm.CREATOR.createFromParcel(in);
    }

    @Override public CellIdentityGsm getCellIdentity() { return mCellIdentity; }

    /** @hide */
    public void setCellIdentity(CellIdentityGsm cid) { mCellIdentity = cid; }

    @Override public CellSignalStrengthGsm getCellSignalStrength() { return mCellSignalStrength; }

    /** @hide */
    public void setCellSignalStrength(CellSignalStrengthGsm css) { mCellSignalStrength = css; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellInfoGsm)) return false;
        CellInfoGsm o = (CellInfoGsm) other;
        return mCellIdentity.equals(o.mCellIdentity)
                && mCellSignalStrength.equals(o.mCellSignalStrength);
    }

    @Override public int hashCode() {
        return mCellIdentity.hashCode() * 31 + mCellSignalStrength.hashCode();
    }

    @Override public String toString() {
        return "CellInfoGsm:{ " + mCellIdentity + " " + mCellSignalStrength + " }";
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags, TYPE_GSM);
        mCellIdentity.writeToParcel(dest, flags);
        mCellSignalStrength.writeToParcel(dest, flags);
    }

    public static final Creator<CellInfoGsm> CREATOR = new Creator<CellInfoGsm>() {
        @Override public CellInfoGsm createFromParcel(Parcel in) {
            in.readInt(); // consume the type token
            return createFromParcelBody(in);
        }
        @Override public CellInfoGsm[] newArray(int size) { return new CellInfoGsm[size]; }
    };

    /** @hide */
    protected static CellInfoGsm createFromParcelBody(Parcel in) {
        return new CellInfoGsm(in);
    }
}
