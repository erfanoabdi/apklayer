package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A neighboring cell.
 *
 * No cellular radio on a Mac, so TelephonyManager.getNeighboringCellInfo()
 * returns an empty list and none of these is ever produced by the framework.
 * The type is a faithful data holder: the three-arg constructor's hex-location
 * parsing is AOSP's, verbatim, because an app that builds one from its own
 * data must get the same field split.
 *
 * @deprecated AOSP deprecated this in API 23 in favour of CellInfo.
 *
 * Source: frameworks/base/telephony/java/android/telephony/NeighboringCellInfo.java
 */
@Deprecated
public class NeighboringCellInfo implements Parcelable {

    public static final int UNKNOWN_RSSI = 99;
    public static final int UNKNOWN_CID = -1;

    private int mRssi;
    private int mCid;
    private int mLac;
    private int mPsc;
    private int mNetworkType;

    public NeighboringCellInfo() {
        mRssi = UNKNOWN_RSSI;
        mLac = UNKNOWN_CID;
        mCid = UNKNOWN_CID;
        mPsc = UNKNOWN_CID;
        mNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
    }

    /** @deprecated AOSP's own note: initialises only rssi and cid. */
    @Deprecated
    public NeighboringCellInfo(int rssi, int cid) {
        mRssi = rssi;
        mCid = cid;
    }

    public NeighboringCellInfo(int rssi, String location, int radioType) {
        mRssi = rssi;
        mNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        mPsc = UNKNOWN_CID;
        mLac = UNKNOWN_CID;
        mCid = UNKNOWN_CID;

        int l = location.length();
        if (l > 8) return;
        if (l < 8) {
            StringBuilder pad = new StringBuilder();
            for (int i = 0; i < (8 - l); i++) pad.append('0');
            location = pad + location;
        }
        try {
            // Set LAC/CID or PSC based on radioType, per AOSP.
            switch (radioType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                    mNetworkType = radioType;
                    if (!location.equalsIgnoreCase("FFFFFFFF")) {
                        mCid = Integer.parseInt(location.substring(4), 16);
                        mLac = Integer.parseInt(location.substring(0, 4), 16);
                    }
                    break;
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                    mNetworkType = radioType;
                    mPsc = Integer.parseInt(location, 16);
                    break;
                default:
                    break;
            }
        } catch (NumberFormatException e) {
            mPsc = UNKNOWN_CID;
            mLac = UNKNOWN_CID;
            mCid = UNKNOWN_CID;
            mNetworkType = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        }
    }

    public NeighboringCellInfo(Parcel in) {
        mRssi = in.readInt();
        mLac = in.readInt();
        mCid = in.readInt();
        mPsc = in.readInt();
        mNetworkType = in.readInt();
    }

    public int getRssi() { return mRssi; }
    public int getLac() { return mLac; }
    public int getCid() { return mCid; }
    public int getPsc() { return mPsc; }
    public int getNetworkType() { return mNetworkType; }

    public void setCid(int cid) { mCid = cid; }
    public void setRssi(int rssi) { mRssi = rssi; }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        if (mPsc != UNKNOWN_CID) {
            sb.append(Integer.toHexString(mPsc))
                    .append("@").append(mRssi == UNKNOWN_RSSI ? "-" : mRssi);
        } else if (mLac != UNKNOWN_CID && mCid != UNKNOWN_CID) {
            sb.append(Integer.toHexString(mLac))
                    .append(Integer.toHexString(mCid))
                    .append("@").append(mRssi == UNKNOWN_RSSI ? "-" : mRssi);
        }
        sb.append("]");
        return sb.toString();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRssi);
        dest.writeInt(mLac);
        dest.writeInt(mCid);
        dest.writeInt(mPsc);
        dest.writeInt(mNetworkType);
    }

    public static final Creator<NeighboringCellInfo> CREATOR = new Creator<NeighboringCellInfo>() {
        @Override public NeighboringCellInfo createFromParcel(Parcel in) {
            return new NeighboringCellInfo(in);
        }
        @Override public NeighboringCellInfo[] newArray(int size) {
            return new NeighboringCellInfo[size];
        }
    };
}
