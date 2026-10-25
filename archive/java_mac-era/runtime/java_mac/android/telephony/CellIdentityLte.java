package android.telephony;

import android.os.Parcel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * LTE cell identity. Never populated on Mac.apk (no modem), so every field is
 * AOSP's UNAVAILABLE / null / empty.
 * Same additional-PLMN parcel deviation as CellIdentityGsm (see that file).
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellIdentityLte.java
 */
public final class CellIdentityLte extends CellIdentity {

    private static final String TAG = "CellIdentityLte";
    private static final int[] NO_BANDS = new int[0];

    private final int mCi;
    private final int mPci;
    private final int mTac;
    private final int mEarfcn;
    private final int[] mBands;
    private final int mBandwidth;
    private final Set<String> mAdditionalPlmns;
    private final ClosedSubscriberGroupInfo mCsgInfo;

    public CellIdentityLte() {
        super(TAG, CellInfo.TYPE_LTE, null, null, null, null);
        mCi = CellInfo.UNAVAILABLE;
        mPci = CellInfo.UNAVAILABLE;
        mTac = CellInfo.UNAVAILABLE;
        mEarfcn = CellInfo.UNAVAILABLE;
        mBands = NO_BANDS;
        mBandwidth = CellInfo.UNAVAILABLE;
        mAdditionalPlmns = Collections.emptySet();
        mCsgInfo = null;
    }

    /** @hide */
    public CellIdentityLte(int ci, int pci, int tac, int earfcn, int[] bands, int bandwidth,
            String mccStr, String mncStr, String alphal, String alphas,
            Set<String> additionalPlmns, ClosedSubscriberGroupInfo csgInfo) {
        super(TAG, CellInfo.TYPE_LTE, mccStr, mncStr, alphal, alphas);
        mCi = ci;
        mPci = pci;
        mTac = tac;
        mEarfcn = earfcn;
        mBands = bands == null ? NO_BANDS : bands.clone();
        mBandwidth = bandwidth;
        mAdditionalPlmns = additionalPlmns == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(additionalPlmns));
        mCsgInfo = csgInfo;
    }

    private CellIdentityLte(Parcel in) {
        super(TAG, CellInfo.TYPE_LTE, in);
        mCi = in.readInt();
        mPci = in.readInt();
        mTac = in.readInt();
        mEarfcn = in.readInt();
        int[] bands = in.createIntArray();
        mBands = bands == null ? NO_BANDS : bands;
        mBandwidth = in.readInt();
        List<String> plmns = in.createStringArrayList();
        mAdditionalPlmns = plmns == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(plmns));
        mCsgInfo = in.readInt() == 1 ? ClosedSubscriberGroupInfo.createFromParcel(in) : null;
    }

    public int getCi() { return mCi; }
    public int getPci() { return mPci; }
    public int getTac() { return mTac; }
    public int getEarfcn() { return mEarfcn; }
    public int getBandwidth() { return mBandwidth; }

    /** AOSP returns a copy; callers may mutate it. */
    public int[] getBands() { return mBands.clone(); }

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
        if (!(other instanceof CellIdentityLte)) return false;
        CellIdentityLte o = (CellIdentityLte) other;
        return mCi == o.mCi && mPci == o.mPci && mTac == o.mTac && mEarfcn == o.mEarfcn
                && Arrays.equals(mBands, o.mBands) && mBandwidth == o.mBandwidth
                && mAdditionalPlmns.equals(o.mAdditionalPlmns)
                && Objects.equals(mCsgInfo, o.mCsgInfo) && super.equals(other);
    }

    @Override public int hashCode() {
        return Objects.hash(mCi, mPci, mTac, mEarfcn, Arrays.hashCode(mBands), mBandwidth,
                mAdditionalPlmns, mCsgInfo, super.hashCode());
    }

    @Override public String toString() {
        return "CellIdentityLte:{ mCi=" + mCi + " mPci=" + mPci + " mTac=" + mTac
                + " mEarfcn=" + mEarfcn + " mBandwidth=" + mBandwidth
                + " mMcc=" + mMccStr + " mMnc=" + mMncStr + "}";
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, CellInfo.TYPE_LTE);
        dest.writeInt(mCi);
        dest.writeInt(mPci);
        dest.writeInt(mTac);
        dest.writeInt(mEarfcn);
        dest.writeIntArray(mBands);
        dest.writeInt(mBandwidth);
        dest.writeStringList(new ArrayList<>(mAdditionalPlmns));
        if (mCsgInfo != null) {
            dest.writeInt(1);
            mCsgInfo.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
    }

    public static final Creator<CellIdentityLte> CREATOR = new Creator<CellIdentityLte>() {
        @Override public CellIdentityLte createFromParcel(Parcel in) {
            in.readInt(); // consume the type token
            return createFromParcelBody(in);
        }
        @Override public CellIdentityLte[] newArray(int size) { return new CellIdentityLte[size]; }
    };

    /** @hide */
    protected static CellIdentityLte createFromParcelBody(Parcel in) {
        return new CellIdentityLte(in);
    }
}
