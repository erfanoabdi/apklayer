package android.telephony.cdma;

import android.os.Bundle;
import android.telephony.CellLocation;

/**
 * CDMA cell location (deprecated on Android since API 26).
 *
 * No radio on a Mac, so a default-constructed one is AOSP's "state invalid"
 * and TelephonyManager.getCellLocation() returns null. The Bundle key strings
 * are AOSP's exact ones, because that Bundle is the documented wire format.
 *
 * Source: frameworks/base/telephony/java/android/telephony/cdma/CdmaCellLocation.java
 */
public class CdmaCellLocation extends CellLocation {

    public static final int INVALID_LAT_LONG = Integer.MAX_VALUE;

    private int mBaseStationId = -1;
    private int mBaseStationLatitude = INVALID_LAT_LONG;
    private int mBaseStationLongitude = INVALID_LAT_LONG;
    private int mSystemId = -1;
    private int mNetworkId = -1;

    public CdmaCellLocation() {
        mBaseStationId = -1;
        mBaseStationLatitude = INVALID_LAT_LONG;
        mBaseStationLongitude = INVALID_LAT_LONG;
        mSystemId = -1;
        mNetworkId = -1;
    }

    public CdmaCellLocation(Bundle bundle) {
        mBaseStationId = bundle.getInt("baseStationId", mBaseStationId);
        mBaseStationLatitude = bundle.getInt("baseStationLatitude", mBaseStationLatitude);
        mBaseStationLongitude = bundle.getInt("baseStationLongitude", mBaseStationLongitude);
        mSystemId = bundle.getInt("systemId", mSystemId);
        mNetworkId = bundle.getInt("networkId", mNetworkId);
    }

    public int getBaseStationId() { return mBaseStationId; }
    public int getBaseStationLatitude() { return mBaseStationLatitude; }
    public int getBaseStationLongitude() { return mBaseStationLongitude; }
    public int getSystemId() { return mSystemId; }
    public int getNetworkId() { return mNetworkId; }

    public void setStateInvalid() {
        mBaseStationId = -1;
        mBaseStationLatitude = INVALID_LAT_LONG;
        mBaseStationLongitude = INVALID_LAT_LONG;
        mSystemId = -1;
        mNetworkId = -1;
    }

    public void setCellLocationData(int baseStationId, int baseStationLatitude,
            int baseStationLongitude) {
        mBaseStationId = baseStationId;
        mBaseStationLatitude = baseStationLatitude;
        mBaseStationLongitude = baseStationLongitude;
    }

    public void setCellLocationData(int baseStationId, int baseStationLatitude,
            int baseStationLongitude, int systemId, int networkId) {
        mBaseStationId = baseStationId;
        mBaseStationLatitude = baseStationLatitude;
        mBaseStationLongitude = baseStationLongitude;
        mSystemId = systemId;
        mNetworkId = networkId;
    }

    @Override public int hashCode() {
        return mBaseStationId ^ mBaseStationLatitude ^ mBaseStationLongitude
                ^ mSystemId ^ mNetworkId;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof CdmaCellLocation)) return false;
        CdmaCellLocation s = (CdmaCellLocation) o;
        return mBaseStationId == s.mBaseStationId
                && mBaseStationLatitude == s.mBaseStationLatitude
                && mBaseStationLongitude == s.mBaseStationLongitude
                && mSystemId == s.mSystemId
                && mNetworkId == s.mNetworkId;
    }

    @Override public String toString() {
        return "[" + mBaseStationId + "," + mBaseStationLatitude + ","
                + mBaseStationLongitude + "," + mSystemId + "," + mNetworkId + "]";
    }

    public void fillInNotifierBundle(Bundle bundleToFill) {
        bundleToFill.putInt("baseStationId", mBaseStationId);
        bundleToFill.putInt("baseStationLatitude", mBaseStationLatitude);
        bundleToFill.putInt("baseStationLongitude", mBaseStationLongitude);
        bundleToFill.putInt("systemId", mSystemId);
        bundleToFill.putInt("networkId", mNetworkId);
    }

    /** @hide */
    public boolean isEmpty() {
        return mBaseStationId == -1
                && mBaseStationLatitude == INVALID_LAT_LONG
                && mBaseStationLongitude == INVALID_LAT_LONG
                && mSystemId == -1
                && mNetworkId == -1;
    }

    /** AOSP rejects out-of-range quarter-seconds rather than silently converting. */
    public static double convertQuartSecToDecDegrees(int quartSec) {
        if (quartSec < -2592000 || quartSec > 2592000) {
            throw new IllegalArgumentException("Invalid coordiante value:" + quartSec);
        }
        return ((double) quartSec) / (3600 * 4);
    }
}
