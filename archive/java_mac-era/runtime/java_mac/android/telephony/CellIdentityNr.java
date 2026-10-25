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
 * 5G NR cell identity. No radio -> every field UNAVAILABLE / null / empty.
 * Same additional-PLMN parcel deviation as CellIdentityGsm (see that file).
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellIdentityNr.java
 */
public final class CellIdentityNr extends CellIdentity {

    private static final String TAG = "CellIdentityNr";
    private static final int[] NO_BANDS = new int[0];

    private final int mNrArfcn;
    private final int mPci;
    private final int mTac;
    private final long mNci;
    private final int[] mBands;
    private final Set<String> mAdditionalPlmns;

    /** @hide */
    public CellIdentityNr() {
        super(TAG, CellInfo.TYPE_NR, null, null, null, null);
        mNrArfcn = CellInfo.UNAVAILABLE;
        mPci = CellInfo.UNAVAILABLE;
        mTac = CellInfo.UNAVAILABLE;
        mNci = CellInfo.UNAVAILABLE_LONG;
        mBands = NO_BANDS;
        mAdditionalPlmns = Collections.emptySet();
    }

    /** @hide */
    public CellIdentityNr(int pci, int tac, int nrArfcn, int[] bands, String mccStr,
            String mncStr, long nci, String alphal, String alphas, Set<String> additionalPlmns) {
        super(TAG, CellInfo.TYPE_NR, mccStr, mncStr, alphal, alphas);
        mPci = pci;
        mTac = tac;
        mNrArfcn = nrArfcn;
        mBands = bands == null ? NO_BANDS : bands.clone();
        mNci = nci;
        mAdditionalPlmns = additionalPlmns == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(additionalPlmns));
    }

    private CellIdentityNr(Parcel in) {
        super(TAG, CellInfo.TYPE_NR, in);
        mPci = in.readInt();
        mTac = in.readInt();
        mNrArfcn = in.readInt();
        int[] bands = in.createIntArray();
        mBands = bands == null ? NO_BANDS : bands;
        mNci = in.readLong();
        List<String> plmns = in.createStringArrayList();
        mAdditionalPlmns = plmns == null
                ? Collections.<String>emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(plmns));
    }

    public int getNrarfcn() { return mNrArfcn; }
    public int[] getBands() { return mBands.clone(); }
    public int getPci() { return mPci; }
    public int getTac() { return mTac; }
    public long getNci() { return mNci; }
    @Override public String getMccString() { return mMccStr; }
    @Override public String getMncString() { return mMncStr; }
    public Set<String> getAdditionalPlmns() { return mAdditionalPlmns; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellIdentityNr)) return false;
        CellIdentityNr o = (CellIdentityNr) other;
        return mPci == o.mPci && mTac == o.mTac && mNrArfcn == o.mNrArfcn
                && Arrays.equals(mBands, o.mBands) && mNci == o.mNci
                && mAdditionalPlmns.equals(o.mAdditionalPlmns) && super.equals(other);
    }

    @Override public int hashCode() {
        return Objects.hash(mPci, mTac, mNrArfcn, Arrays.hashCode(mBands), mNci,
                mAdditionalPlmns, super.hashCode());
    }

    @Override public String toString() {
        return "CellIdentityNr:{ mPci=" + mPci + " mTac=" + mTac + " mNrArfcn=" + mNrArfcn
                + " mNci=" + mNci + "}";
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, CellInfo.TYPE_NR);
        dest.writeInt(mPci);
        dest.writeInt(mTac);
        dest.writeInt(mNrArfcn);
        dest.writeIntArray(mBands);
        dest.writeLong(mNci);
        dest.writeStringList(new ArrayList<>(mAdditionalPlmns));
    }

    public static final Creator<CellIdentityNr> CREATOR = new Creator<CellIdentityNr>() {
        @Override public CellIdentityNr createFromParcel(Parcel in) {
            in.readInt(); // consume the type token
            return createFromParcelBody(in);
        }
        @Override public CellIdentityNr[] newArray(int size) { return new CellIdentityNr[size]; }
    };

    /** @hide */
    protected static CellIdentityNr createFromParcelBody(Parcel in) {
        return new CellIdentityNr(in);
    }
}
