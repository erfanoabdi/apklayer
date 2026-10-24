package android.telephony;

import android.os.Parcel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * WCDMA cell identity. No radio -> every field UNAVAILABLE / null.
 * Same additional-PLMN parcel deviation as CellIdentityGsm (see that file).
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellIdentityWcdma.java
 */
public final class CellIdentityWcdma extends CellIdentity {

    private static final String TAG = "CellIdentityWcdma";

    private final int mLac;
    private final int mCid;
    private final int mPsc;
    private final int mUarfcn;
    private final Set<String> mAdditionalPlmns;
    private final ClosedSubscriberGroupInfo mCsgInfo;

    /** @hide */
    public CellIdentityWcdma() {
        super(TAG, CellInfo.TYPE_WCDMA, null, null, null, null);
        mLac = CellInfo.UNAVAILABLE;
        mCid = CellInfo.UNAVAILABLE;
        mPsc = CellInfo.UNAVAILABLE;
        mUarfcn = CellInfo.UNAVAILABLE;
        mAdditionalPlmns = Collections.emptySet();
        mCsgInfo = null;
    }

    /** @hide */
    public CellIdentityWcdma(int lac, int cid, int psc, int uarfcn, String mccStr, String mncStr,
            String alphal, String alphas, Set<String> additionalPlmns,
            ClosedSubscriberGroupInfo csgInfo) {
        super(TAG, CellInfo.TYPE_WCDMA, mccStr, mncStr, alphal, alphas);
        mLac = lac;
        mCid = cid;
        mPsc = psc;
        mUarfcn = uarfcn;
        mAdditionalPlmns = additionalPlmns == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(additionalPlmns));
        mCsgInfo = csgInfo;
    }

    private CellIdentityWcdma(Parcel in) {
        super(TAG, CellInfo.TYPE_WCDMA, in);
        mLac = in.readInt();
        mCid = in.readInt();
        mPsc = in.readInt();
        mUarfcn = in.readInt();
        List<String> plmns = in.createStringArrayList();
        mAdditionalPlmns = plmns == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(plmns));
        mCsgInfo = in.readInt() == 1 ? ClosedSubscriberGroupInfo.createFromParcel(in) : null;
    }

    public int getLac() { return mLac; }
    public int getCid() { return mCid; }
    public int getPsc() { return mPsc; }
    public int getUarfcn() { return mUarfcn; }

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

    @Override public String getMccString() { return mMccStr; }
    @Override public String getMncString() { return mMncStr; }
    @Override public String getMobileNetworkOperator() { return super.getMobileNetworkOperator(); }

    public Set<String> getAdditionalPlmns() { return mAdditionalPlmns; }

    public ClosedSubscriberGroupInfo getClosedSubscriberGroupInfo() { return mCsgInfo; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellIdentityWcdma)) return false;
        CellIdentityWcdma o = (CellIdentityWcdma) other;
        return mLac == o.mLac && mCid == o.mCid && mPsc == o.mPsc && mUarfcn == o.mUarfcn
                && mAdditionalPlmns.equals(o.mAdditionalPlmns)
                && Objects.equals(mCsgInfo, o.mCsgInfo) && super.equals(other);
    }

    @Override public int hashCode() {
        return Objects.hash(mLac, mCid, mPsc, mUarfcn, mAdditionalPlmns, mCsgInfo,
                super.hashCode());
    }

    @Override public String toString() {
        return "CellIdentityWcdma:{ mLac=" + mLac + " mCid=" + mCid + " mPsc=" + mPsc
                + " mUarfcn=" + mUarfcn + " mMcc=" + mMccStr + " mMnc=" + mMncStr + "}";
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, CellInfo.TYPE_WCDMA);
        dest.writeInt(mLac);
        dest.writeInt(mCid);
        dest.writeInt(mPsc);
        dest.writeInt(mUarfcn);
        dest.writeStringList(new ArrayList<>(mAdditionalPlmns));
        if (mCsgInfo != null) {
            dest.writeInt(1);
            mCsgInfo.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
    }

    public static final Creator<CellIdentityWcdma> CREATOR = new Creator<CellIdentityWcdma>() {
        @Override public CellIdentityWcdma createFromParcel(Parcel in) {
            in.readInt(); // consume the type token
            return createFromParcelBody(in);
        }
        @Override public CellIdentityWcdma[] newArray(int size) {
            return new CellIdentityWcdma[size];
        }
    };

    /** @hide */
    protected static CellIdentityWcdma createFromParcelBody(Parcel in) {
        return new CellIdentityWcdma(in);
    }
}
