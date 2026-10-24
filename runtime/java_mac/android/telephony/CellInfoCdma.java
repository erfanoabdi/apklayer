package android.telephony;

import android.os.Parcel;

/**
 * A CDMA cell observation.
 *
 * Never produced on Mac.apk (no modem), so the identity and signal strength are
 * both AOSP's all-UNAVAILABLE defaults. The type must still resolve for code
 * that instanceof-checks getAllCellInfo() results, and the Parcelable half is
 * real so an app can round-trip one it built itself.
 *
 * @deprecated AOSP is retiring CDMA support.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellInfoCdma.java
 */
@Deprecated
public final class CellInfoCdma extends CellInfo {

    private CellIdentityCdma mCellIdentity;
    private CellSignalStrengthCdma mCellSignalStrength;

    /** @hide */
    public CellInfoCdma() {
        super();
        mCellIdentity = new CellIdentityCdma();
        mCellSignalStrength = new CellSignalStrengthCdma();
    }

    /** @hide */
    public CellInfoCdma(int connectionStatus, boolean registered, long timeStamp,
            CellIdentityCdma cellIdentity, CellSignalStrengthCdma cellSignalStrength) {
        super(connectionStatus, registered, timeStamp);
        mCellIdentity = cellIdentity;
        mCellSignalStrength = cellSignalStrength;
    }

    private CellInfoCdma(Parcel in) {
        super(in);
        mCellIdentity = CellIdentityCdma.CREATOR.createFromParcel(in);
        mCellSignalStrength = CellSignalStrengthCdma.CREATOR.createFromParcel(in);
    }

    @Override public CellIdentityCdma getCellIdentity() { return mCellIdentity; }

    /** @hide */
    public void setCellIdentity(CellIdentityCdma cid) { mCellIdentity = cid; }

    @Override public CellSignalStrengthCdma getCellSignalStrength() { return mCellSignalStrength; }

    /** @hide */
    public void setCellSignalStrength(CellSignalStrengthCdma css) { mCellSignalStrength = css; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellInfoCdma)) return false;
        CellInfoCdma o = (CellInfoCdma) other;
        return mCellIdentity.equals(o.mCellIdentity)
                && mCellSignalStrength.equals(o.mCellSignalStrength);
    }

    @Override public int hashCode() {
        return mCellIdentity.hashCode() * 31 + mCellSignalStrength.hashCode();
    }

    @Override public String toString() {
        return "CellInfoCdma:{ " + mCellIdentity + " " + mCellSignalStrength + " }";
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags, TYPE_CDMA);
        mCellIdentity.writeToParcel(dest, flags);
        mCellSignalStrength.writeToParcel(dest, flags);
    }

    public static final Creator<CellInfoCdma> CREATOR = new Creator<CellInfoCdma>() {
        @Override public CellInfoCdma createFromParcel(Parcel in) {
            in.readInt(); // consume the type token
            return createFromParcelBody(in);
        }
        @Override public CellInfoCdma[] newArray(int size) { return new CellInfoCdma[size]; }
    };

    /** @hide */
    protected static CellInfoCdma createFromParcelBody(Parcel in) {
        return new CellInfoCdma(in);
    }
}
