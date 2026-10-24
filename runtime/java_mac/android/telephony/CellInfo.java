// android.telephony.CellInfo
//
// Abstract base for cell-tower observations (CellInfoGsm/Lte/etc.).
//
// Mac.apk has no cellular radio, so TelephonyManager.getAllCellInfo() returns an
// empty list and the framework never produces one of these. The type is still a
// faithful data holder -- including the type-tagged Parcelable dispatch, which is
// AOSP's own mechanism (the subclass writes a TYPE_* token first and
// CellInfo.CREATOR switches on it) and which an app can exercise directly by
// putting a CellInfo into a Bundle.
//
// Source: frameworks/base/telephony/java/android/telephony/CellInfo.java
package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

public abstract class CellInfo implements Parcelable {

    public static final int UNAVAILABLE = Integer.MAX_VALUE;
    public static final long UNAVAILABLE_LONG = Long.MAX_VALUE;

    public static final int CONNECTION_NONE = 0;
    public static final int CONNECTION_PRIMARY_SERVING = 1;
    public static final int CONNECTION_SECONDARY_SERVING = 2;
    public static final int CONNECTION_UNKNOWN = Integer.MAX_VALUE;

    /** @hide in AOSP; the parcel type tokens. */
    public static final int TYPE_UNKNOWN = 0;
    /** @hide */ public static final int TYPE_GSM = 1;
    /** @hide */ public static final int TYPE_CDMA = 2;
    /** @hide */ public static final int TYPE_LTE = 3;
    /** @hide */ public static final int TYPE_WCDMA = 4;
    /** @hide */ public static final int TYPE_TDSCDMA = 5;
    /** @hide */ public static final int TYPE_NR = 6;

    private boolean mRegistered;
    private long mTimeStamp;
    private int mCellConnectionStatus = CONNECTION_NONE;

    protected CellInfo() {
        // AOSP's no-arg form seeds UNAVAILABLE_LONG, not 0: a zero timestamp
        // would read as "observed at boot".
        mRegistered = false;
        mTimeStamp = Long.MAX_VALUE;
        mCellConnectionStatus = CONNECTION_NONE;
    }

    protected CellInfo(int cellConnectionStatus, boolean registered, long timestamp) {
        mCellConnectionStatus = cellConnectionStatus;
        mRegistered = registered;
        mTimeStamp = timestamp;
    }

    protected CellInfo(CellInfo ci) {
        mRegistered = ci.mRegistered;
        mTimeStamp = ci.mTimeStamp;
        mCellConnectionStatus = ci.mCellConnectionStatus;
    }

    protected CellInfo(Parcel in) {
        mRegistered = in.readInt() == 1;
        mTimeStamp = in.readLong();
        mCellConnectionStatus = in.readInt();
    }

    public boolean isRegistered() { return mRegistered; }
    /** @hide */ public void setRegistered(boolean registered) { mRegistered = registered; }

    /** @deprecated AOSP replaced this by {@link #getTimestampMillis} in API 30. */
    @Deprecated public long getTimeStamp() { return mTimeStamp; }
    /** @hide */ public void setTimeStamp(long timeStamp) { mTimeStamp = timeStamp; }

    /** AOSP stores NANOSECONDS internally and divides by 1e6 here. */
    public long getTimestampMillis() { return mTimeStamp / 1000000; }

    public int getCellConnectionStatus() { return mCellConnectionStatus; }
    /** @hide */ public void setCellConnectionStatus(int status) { mCellConnectionStatus = status; }

    public abstract CellIdentity getCellIdentity();

    public abstract CellSignalStrength getCellSignalStrength();

    @Override public int describeContents() { return 0; }

    /** Subclasses call this first, passing their TYPE_* token. */
    protected void writeToParcel(Parcel dest, int flags, int type) {
        dest.writeInt(type);
        dest.writeInt(mRegistered ? 1 : 0);
        dest.writeLong(mTimeStamp);
        dest.writeInt(mCellConnectionStatus);
    }

    public static final Creator<CellInfo> CREATOR = new Creator<CellInfo>() {
        @Override public CellInfo createFromParcel(Parcel in) {
            int type = in.readInt();
            switch (type) {
                case TYPE_GSM: return CellInfoGsm.createFromParcelBody(in);
                case TYPE_CDMA: return CellInfoCdma.createFromParcelBody(in);
                case TYPE_LTE: return CellInfoLte.createFromParcelBody(in);
                case TYPE_WCDMA: return CellInfoWcdma.createFromParcelBody(in);
                case TYPE_TDSCDMA: return CellInfoTdscdma.createFromParcelBody(in);
                case TYPE_NR: return CellInfoNr.createFromParcelBody(in);
                default: throw new RuntimeException("Bad CellInfo Parcel");
            }
        }
        @Override public CellInfo[] newArray(int size) { return new CellInfo[size]; }
    };
}
