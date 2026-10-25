package android.telephony;

import android.os.Parcel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * TD-SCDMA cell identity. No radio -> every field UNAVAILABLE / null.
 * Same additional-PLMN parcel deviation as CellIdentityGsm (see that file).
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellIdentityTdscdma.java
 */
public final class CellIdentityTdscdma extends CellIdentity {

    private static final String TAG = "CellIdentityTdscdma";

    private final int mLac;
    private final int mCid;
    private final int mCpid;
    private final int mUarfcn;
    private final Set<String> mAdditionalPlmns;
    private final ClosedSubscriberGroupInfo mCsgInfo;

    /** @hide */
    public CellIdentityTdscdma() {
        super(TAG, CellInfo.TYPE_TDSCDMA, null, null, null, null);
        mLac = CellInfo.UNAVAILABLE;
        mCid = CellInfo.UNAVAILABLE;
        mCpid = CellInfo.UNAVAILABLE;
        mUarfcn = CellInfo.UNAVAILABLE;
        mAdditionalPlmns = Collections.emptySet();
        mCsgInfo = null;
    }

    /** @hide */
    public CellIdentityTdscdma(String mcc, String mnc, int lac, int cid, int cpid, int uarfcn,
            String alphal, String alphas, Set<String> additionalPlmns,
            ClosedSubscriberGroupInfo csgInfo) {
        super(TAG, CellInfo.TYPE_TDSCDMA, mcc, mnc, alphal, alphas);
        mLac = lac;
        mCid = cid;
        mCpid = cpid;
        mUarfcn = uarfcn;
        mAdditionalPlmns = additionalPlmns == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(additionalPlmns));
        mCsgInfo = csgInfo;
    }

    private CellIdentityTdscdma(Parcel in) {
        super(TAG, CellInfo.TYPE_TDSCDMA, in);
        mLac = in.readInt();
        mCid = in.readInt();
        mCpid = in.readInt();
        mUarfcn = in.readInt();
        List<String> plmns = in.createStringArrayList();
        mAdditionalPlmns = plmns == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(plmns));
        mCsgInfo = in.readInt() == 1 ? ClosedSubscriberGroupInfo.createFromParcel(in) : null;
    }

    public int getLac() { return mLac; }
    public int getCid() { return mCid; }
    public int getCpid() { return mCpid; }
    public int getUarfcn() { return mUarfcn; }
    @Override public String getMccString() { return mMccStr; }
    @Override public String getMncString() { return mMncStr; }
    @Override public String getMobileNetworkOperator() { return super.getMobileNetworkOperator(); }
    public Set<String> getAdditionalPlmns() { return mAdditionalPlmns; }
    public ClosedSubscriberGroupInfo getClosedSubscriberGroupInfo() { return mCsgInfo; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellIdentityTdscdma)) return false;
        CellIdentityTdscdma o = (CellIdentityTdscdma) other;
        return mLac == o.mLac && mCid == o.mCid && mCpid == o.mCpid && mUarfcn == o.mUarfcn
                && mAdditionalPlmns.equals(o.mAdditionalPlmns)
                && Objects.equals(mCsgInfo, o.mCsgInfo) && super.equals(other);
    }

    @Override public int hashCode() {
        return Objects.hash(mLac, mCid, mCpid, mUarfcn, mAdditionalPlmns, mCsgInfo,
                super.hashCode());
    }

    @Override public String toString() {
        return "CellIdentityTdscdma:{ mLac=" + mLac + " mCid=" + mCid + " mCpid=" + mCpid
                + " mUarfcn=" + mUarfcn + "}";
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, CellInfo.TYPE_TDSCDMA);
        dest.writeInt(mLac);
        dest.writeInt(mCid);
        dest.writeInt(mCpid);
        dest.writeInt(mUarfcn);
        dest.writeStringList(new ArrayList<>(mAdditionalPlmns));
        if (mCsgInfo != null) {
            dest.writeInt(1);
            mCsgInfo.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
    }

    public static final Creator<CellIdentityTdscdma> CREATOR = new Creator<CellIdentityTdscdma>() {
        @Override public CellIdentityTdscdma createFromParcel(Parcel in) {
            in.readInt(); // consume the type token
            return createFromParcelBody(in);
        }
        @Override public CellIdentityTdscdma[] newArray(int size) {
            return new CellIdentityTdscdma[size];
        }
    };

    /** @hide */
    protected static CellIdentityTdscdma createFromParcelBody(Parcel in) {
        return new CellIdentityTdscdma(in);
    }
}
