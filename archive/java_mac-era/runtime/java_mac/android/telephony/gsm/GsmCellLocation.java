package android.telephony.gsm;

import android.os.Bundle;
import android.telephony.CellLocation;

/**
 * GSM cell location (deprecated on Android since API 26).
 *
 * No radio on a Mac, so a default-constructed one is AOSP's "state invalid"
 * (-1/-1/-1) and TelephonyManager.getCellLocation() returns null. The Bundle
 * constructor and fillInNotifierBundle use AOSP's exact key strings ("lac",
 * "cid", "psc"), because that Bundle is the documented wire format between a
 * PhoneStateListener callback and this class.
 *
 * Source: frameworks/base/telephony/java/android/telephony/gsm/GsmCellLocation.java
 */
public class GsmCellLocation extends CellLocation {

    private int mLac;
    private int mCid;
    private int mPsc;

    public GsmCellLocation() {
        mLac = -1;
        mCid = -1;
        mPsc = -1;
    }

    public GsmCellLocation(Bundle bundle) {
        mLac = bundle.getInt("lac", -1);
        mCid = bundle.getInt("cid", -1);
        mPsc = bundle.getInt("psc", -1);
    }

    public int getLac() { return mLac; }
    public int getCid() { return mCid; }
    public int getPsc() { return mPsc; }

    public void setStateInvalid() {
        mLac = -1;
        mCid = -1;
        mPsc = -1;
    }

    public void setLacAndCid(int lac, int cid) {
        mLac = lac;
        mCid = cid;
    }

    /** @hide */ public void setPsc(int psc) { mPsc = psc; }

    @Override public int hashCode() { return mLac ^ mCid; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof GsmCellLocation)) return false;
        GsmCellLocation s = (GsmCellLocation) o;
        return mLac == s.mLac && mCid == s.mCid && mPsc == s.mPsc;
    }

    @Override public String toString() { return "[" + mLac + "," + mCid + "," + mPsc + "]"; }

    public void fillInNotifierBundle(Bundle m) {
        m.putInt("lac", mLac);
        m.putInt("cid", mCid);
        m.putInt("psc", mPsc);
    }

    /** @hide */
    public boolean isEmpty() { return mLac == -1 && mCid == -1 && mPsc == -1; }
}
