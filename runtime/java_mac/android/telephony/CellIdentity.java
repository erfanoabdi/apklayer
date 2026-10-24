package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Base cell identity.
 *
 * No cellular radio on a Mac, so none of these is ever produced by the
 * framework and every subclass field reports AOSP's "unavailable" sentinel
 * (Integer.MAX_VALUE for ints, null for strings). The type-tagged Parcelable
 * dispatch is AOSP's, verbatim in shape.
 *
 * Source: frameworks/base/telephony/java/android/telephony/CellIdentity.java
 */
public abstract class CellIdentity implements Parcelable {

    public static final int UNAVAILABLE = Integer.MAX_VALUE;

    /** @hide */ protected final int mType;
    /** @hide */ protected final String mMccStr;
    /** @hide */ protected final String mMncStr;
    /** @hide */ protected String mAlphaLong;
    /** @hide */ protected String mAlphaShort;

    /** @hide */
    protected CellIdentity(String tag, int type, String mcc, String mnc, String alphal,
            String alphas) {
        mType = type;
        // AOSP requires MCC/MNC to be 3 and 2-3 digits respectively, and drops
        // both if only one is present -- an identity with half a PLMN is invalid.
        String mccStr = isNumeric(mcc, 3, 3) ? mcc : null;
        String mncStr = isNumeric(mnc, 2, 3) ? mnc : null;
        if ((mccStr != null && mncStr == null) || (mccStr == null && mncStr != null)) {
            mccStr = null;
            mncStr = null;
        }
        mMccStr = mccStr;
        mMncStr = mncStr;
        mAlphaLong = alphal;
        mAlphaShort = alphas;
    }

    /** @hide */
    protected CellIdentity(String tag, int type, Parcel source) {
        mType = type;
        mMccStr = source.readString();
        mMncStr = source.readString();
        mAlphaLong = source.readString();
        mAlphaShort = source.readString();
    }

    private static boolean isNumeric(String s, int min, int max) {
        if (s == null || s.length() < min || s.length() > max) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    /** @hide */ public int getType() { return mType; }

    /** @hide */ public String getMccString() { return mMccStr; }

    /** @hide */ public String getMncString() { return mMncStr; }

    public CharSequence getOperatorAlphaLong() { return mAlphaLong; }

    public CharSequence getOperatorAlphaShort() { return mAlphaShort; }

    /** @hide */ public void setOperatorAlphaLong(String alphaLong) { mAlphaLong = alphaLong; }

    /** @hide */ public void setOperatorAlphaShort(String alphaShort) { mAlphaShort = alphaShort; }

    /** AOSP returns null unless BOTH halves of the PLMN are present. */
    /** @hide */
    public String getMobileNetworkOperator() {
        if (mMccStr == null || mMncStr == null) return null;
        return mMccStr + mMncStr;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof CellIdentity)) return false;
        CellIdentity o = (CellIdentity) other;
        return mType == o.mType
                && Objects.equals(mMccStr, o.mMccStr)
                && Objects.equals(mMncStr, o.mMncStr)
                && Objects.equals(mAlphaLong, o.mAlphaLong)
                && Objects.equals(mAlphaShort, o.mAlphaShort);
    }

    @Override public int hashCode() {
        return Objects.hash(mAlphaLong, mAlphaShort, mMccStr, mMncStr, mType);
    }

    @Override public int describeContents() { return 0; }

    /**
     * NOTE the AOSP signature quirk: the second parameter is the TYPE token, not
     * the Parcelable flags. Subclasses call super.writeToParcel(dest, TYPE_*).
     */
    @Override public void writeToParcel(Parcel dest, int type) {
        dest.writeInt(type);
        dest.writeString(mMccStr);
        dest.writeString(mMncStr);
        dest.writeString(mAlphaLong);
        dest.writeString(mAlphaShort);
    }

    public static final Creator<CellIdentity> CREATOR = new Creator<CellIdentity>() {
        @Override public CellIdentity createFromParcel(Parcel in) {
            int type = in.readInt();
            switch (type) {
                case CellInfo.TYPE_GSM: return CellIdentityGsm.createFromParcelBody(in);
                case CellInfo.TYPE_WCDMA: return CellIdentityWcdma.createFromParcelBody(in);
                case CellInfo.TYPE_CDMA: return CellIdentityCdma.createFromParcelBody(in);
                case CellInfo.TYPE_LTE: return CellIdentityLte.createFromParcelBody(in);
                case CellInfo.TYPE_TDSCDMA:
                    return CellIdentityTdscdma.createFromParcelBody(in);
                case CellInfo.TYPE_NR: return CellIdentityNr.createFromParcelBody(in);
                default: throw new IllegalArgumentException("Bad Cell identity Parcel");
            }
        }
        @Override public CellIdentity[] newArray(int size) { return new CellIdentity[size]; }
    };
}
