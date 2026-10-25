package android.telephony;

import android.os.Parcel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * GSM cell identity. No radio on a Mac, so a default-constructed one reports
 * UNAVAILABLE for every numeric field and null for every string -- AOSP's own
 * "not reported by the modem" values.
 *
 * ⚠ Parcel-format deviation, stated rather than hidden: AOSP writes the
 * additional-PLMN set with Parcel.writeArraySet, which android.os.Parcel here
 * does not implement. We write it as a string list instead. The round trip is
 * exact; only the byte layout differs from a device's, and nothing in Mac.apk
 * exchanges parcels with a device.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellIdentityGsm.java
 */
public final class CellIdentityGsm extends CellIdentity {

    private static final String TAG = "CellIdentityGsm";

    private final int mLac;
    private final int mCid;
    private final int mArfcn;
    private final int mBsic;
    private final Set<String> mAdditionalPlmns;

    /** @hide */
    public CellIdentityGsm() {
        super(TAG, CellInfo.TYPE_GSM, null, null, null, null);
        mLac = CellInfo.UNAVAILABLE;
        mCid = CellInfo.UNAVAILABLE;
        mArfcn = CellInfo.UNAVAILABLE;
        mBsic = CellInfo.UNAVAILABLE;
        mAdditionalPlmns = Collections.emptySet();
    }

    /** @hide */
    public CellIdentityGsm(int lac, int cid, int arfcn, int bsic, String mccStr, String mncStr,
            String alphal, String alphas, Set<String> additionalPlmns) {
        super(TAG, CellInfo.TYPE_GSM, mccStr, mncStr, alphal, alphas);
        mLac = lac;
        mCid = cid;
        mArfcn = arfcn;
        mBsic = bsic;
        mAdditionalPlmns = additionalPlmns == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(additionalPlmns));
    }

    private CellIdentityGsm(Parcel in) {
        super(TAG, CellInfo.TYPE_GSM, in);
        mLac = in.readInt();
        mCid = in.readInt();
        mArfcn = in.readInt();
        mBsic = in.readInt();
        List<String> plmns = in.createStringArrayList();
        mAdditionalPlmns = plmns == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(plmns));
    }

    public int getLac() { return mLac; }
    public int getCid() { return mCid; }
    public int getArfcn() { return mArfcn; }
    public int getBsic() { return mBsic; }

    /** @deprecated AOSP replaced the int MCC by {@link #getMccString()} in API 28. */
    @Deprecated public int getMcc() {
        try { return mMccStr == null ? CellInfo.UNAVAILABLE : Integer.parseInt(mMccStr); }
        catch (NumberFormatException e) { return CellInfo.UNAVAILABLE; }
    }

    /** @deprecated AOSP replaced the int MNC by {@link #getMncString()} in API 28. */
    @Deprecated public int getMnc() {
        try { return mMncStr == null ? CellInfo.UNAVAILABLE : Integer.parseInt(mMncStr); }
        catch (NumberFormatException e) { return CellInfo.UNAVAILABLE; }
    }

    /** @deprecated GSM cells do not report a PSC; AOSP always returns UNAVAILABLE. */
    @Deprecated public int getPsc() { return CellInfo.UNAVAILABLE; }

    @Override public String getMccString() { return mMccStr; }
    @Override public String getMncString() { return mMncStr; }
    @Override public String getMobileNetworkOperator() { return super.getMobileNetworkOperator(); }

    public Set<String> getAdditionalPlmns() { return mAdditionalPlmns; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellIdentityGsm)) return false;
        CellIdentityGsm o = (CellIdentityGsm) other;
        return mLac == o.mLac && mCid == o.mCid && mArfcn == o.mArfcn && mBsic == o.mBsic
                && mAdditionalPlmns.equals(o.mAdditionalPlmns) && super.equals(other);
    }

    @Override public int hashCode() {
        return Objects.hash(mLac, mCid, mArfcn, mBsic, mAdditionalPlmns, super.hashCode());
    }

    @Override public String toString() {
        return "CellIdentityGsm:{ mLac=" + mLac + " mCid=" + mCid + " mArfcn=" + mArfcn
                + " mBsic=" + mBsic + " mMcc=" + mMccStr + " mMnc=" + mMncStr + "}";
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, CellInfo.TYPE_GSM);
        dest.writeInt(mLac);
        dest.writeInt(mCid);
        dest.writeInt(mArfcn);
        dest.writeInt(mBsic);
        dest.writeStringList(new ArrayList<>(mAdditionalPlmns));
    }

    public static final Creator<CellIdentityGsm> CREATOR = new Creator<CellIdentityGsm>() {
        @Override public CellIdentityGsm createFromParcel(Parcel in) {
            in.readInt(); // consume the type token
            return createFromParcelBody(in);
        }
        @Override public CellIdentityGsm[] newArray(int size) { return new CellIdentityGsm[size]; }
    };

    /** @hide */
    protected static CellIdentityGsm createFromParcelBody(Parcel in) {
        return new CellIdentityGsm(in);
    }
}
