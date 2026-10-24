package android.telephony;

import android.os.Parcel;

import java.util.Objects;

/**
 * CDMA cell identity. No radio -> every field UNAVAILABLE.
 *
 * @deprecated AOSP is retiring CDMA support (com.android.internal.telephony.flags.deprecate_cdma).
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellIdentityCdma.java
 */
@Deprecated
public final class CellIdentityCdma extends CellIdentity {

    private static final String TAG = "CellIdentityCdma";

    private final int mNetworkId;
    private final int mSystemId;
    private final int mBasestationId;
    private final int mLongitude;
    private final int mLatitude;

    /** @hide */
    public CellIdentityCdma() {
        super(TAG, CellInfo.TYPE_CDMA, null, null, null, null);
        mNetworkId = CellInfo.UNAVAILABLE;
        mSystemId = CellInfo.UNAVAILABLE;
        mBasestationId = CellInfo.UNAVAILABLE;
        mLongitude = CellInfo.UNAVAILABLE;
        mLatitude = CellInfo.UNAVAILABLE;
    }

    /** @hide */
    public CellIdentityCdma(int nid, int sid, int bid, int lon, int lat, String alphal,
            String alphas) {
        super(TAG, CellInfo.TYPE_CDMA, null, null, alphal, alphas);
        mNetworkId = nid;
        mSystemId = sid;
        mBasestationId = bid;
        mLongitude = lon;
        mLatitude = lat;
    }

    private CellIdentityCdma(Parcel in) {
        super(TAG, CellInfo.TYPE_CDMA, in);
        mNetworkId = in.readInt();
        mSystemId = in.readInt();
        mBasestationId = in.readInt();
        mLongitude = in.readInt();
        mLatitude = in.readInt();
    }

    public int getNetworkId() { return mNetworkId; }
    public int getSystemId() { return mSystemId; }
    public int getBasestationId() { return mBasestationId; }
    public int getLongitude() { return mLongitude; }
    public int getLatitude() { return mLatitude; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellIdentityCdma)) return false;
        CellIdentityCdma o = (CellIdentityCdma) other;
        return mNetworkId == o.mNetworkId && mSystemId == o.mSystemId
                && mBasestationId == o.mBasestationId && mLatitude == o.mLatitude
                && mLongitude == o.mLongitude && super.equals(other);
    }

    @Override public int hashCode() {
        return Objects.hash(mNetworkId, mSystemId, mBasestationId, mLatitude, mLongitude,
                super.hashCode());
    }

    @Override public String toString() {
        return "CellIdentityCdma:{ mNetworkId=" + mNetworkId + " mSystemId=" + mSystemId
                + " mBasestationId=" + mBasestationId + " mLongitude=" + mLongitude
                + " mLatitude=" + mLatitude + "}";
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, CellInfo.TYPE_CDMA);
        dest.writeInt(mNetworkId);
        dest.writeInt(mSystemId);
        dest.writeInt(mBasestationId);
        dest.writeInt(mLongitude);
        dest.writeInt(mLatitude);
    }

    public static final Creator<CellIdentityCdma> CREATOR = new Creator<CellIdentityCdma>() {
        @Override public CellIdentityCdma createFromParcel(Parcel in) {
            in.readInt(); // consume the type token
            return createFromParcelBody(in);
        }
        @Override public CellIdentityCdma[] newArray(int size) {
            return new CellIdentityCdma[size];
        }
    };

    /** @hide */
    protected static CellIdentityCdma createFromParcelBody(Parcel in) {
        return new CellIdentityCdma(in);
    }
}
