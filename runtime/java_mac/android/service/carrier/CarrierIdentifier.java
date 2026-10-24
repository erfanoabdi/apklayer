package android.service.carrier;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Identifies which carrier a SIM belongs to: MCC/MNC, plus the optional SPN,
 * IMSI prefix and group identifiers that distinguish MVNOs sharing an MCC/MNC.
 *
 * PACKAGE NOTE -- applies to android.service.carrier. A CarrierService /
 * CarrierMessagingService is bound by the telephony stack of the app that is
 * the SIM's carrier-privileged app. Mac.apk has no radio, no SIM and no
 * telephony stack, so nothing binds either service and no SMS/MMS ever arrives.
 * The identifier and the result objects are ordinary value types, and those are
 * faithful.
 *
 * Source: frameworks/base/telephony/java/android/service/carrier/CarrierIdentifier.java
 */
public class CarrierIdentifier implements Parcelable {

    /** AOSP's sentinel for "no carrier id assigned"; TelephonyManager
     *  .UNKNOWN_CARRIER_ID is -1. */
    private static final int UNKNOWN_CARRIER_ID = -1;

    private final String mMcc;
    private final String mMnc;
    private final String mSpn;
    private final String mImsi;
    private final String mGid1;
    private final String mGid2;
    private final int mCarrierId;
    private final int mSpecificCarrierId;

    public CarrierIdentifier(String mcc, String mnc, String spn, String imsi,
            String gid1, String gid2) {
        this(mcc, mnc, spn, imsi, gid1, gid2, UNKNOWN_CARRIER_ID, UNKNOWN_CARRIER_ID);
    }

    /** API 28. */
    public CarrierIdentifier(String mcc, String mnc, String spn, String imsi,
            String gid1, String gid2, int carrierid, int specificCarrierId) {
        mMcc = mcc;
        mMnc = mnc;
        mSpn = spn;
        mImsi = imsi;
        mGid1 = gid1;
        mGid2 = gid2;
        mCarrierId = carrierid;
        mSpecificCarrierId = specificCarrierId;
    }

    /**
     * Builds the identifier out of the raw MCC/MNC bytes of a SIM's IMSI.
     * AOSP decodes the first three BCD digits as MCC and the next two or three
     * as MNC; the split is carrier-dependent, and AOSP's own constructor
     * assumes the 3+2 form for a mccMnc byte array of length >= 3.
     */
    public CarrierIdentifier(byte[] mccMnc, String gid1, String gid2) {
        if (mccMnc == null || mccMnc.length < 3) {
            throw new IllegalArgumentException("MCC & MNC must be set by 'mccMnc'");
        }
        // BCD, low nibble first: digit order is [0]lo [0]hi [1]lo | [1]hi [2]lo [2]hi
        final char[] mcc = new char[3];
        mcc[0] = bcd(mccMnc[0] & 0xF);
        mcc[1] = bcd((mccMnc[0] >> 4) & 0xF);
        mcc[2] = bcd(mccMnc[1] & 0xF);
        final StringBuilder mnc = new StringBuilder();
        mnc.append(bcd(mccMnc[2] & 0xF));
        mnc.append(bcd((mccMnc[2] >> 4) & 0xF));
        // 0xF in the high nibble of byte 1 means a two-digit MNC.
        if (((mccMnc[1] >> 4) & 0xF) != 0xF) {
            mnc.append(bcd((mccMnc[1] >> 4) & 0xF));
        }
        mMcc = new String(mcc);
        mMnc = mnc.toString();
        mSpn = null;
        mImsi = null;
        mGid1 = gid1;
        mGid2 = gid2;
        mCarrierId = UNKNOWN_CARRIER_ID;
        mSpecificCarrierId = UNKNOWN_CARRIER_ID;
    }

    private static char bcd(int nibble) { return (char) ('0' + nibble); }

    public String getMcc() { return mMcc; }

    public String getMnc() { return mMnc; }

    public String getSpn() { return mSpn; }

    public String getImsi() { return mImsi; }

    public String getGid1() { return mGid1; }

    public String getGid2() { return mGid2; }

    /** API 28. */
    public int getCarrierId() { return mCarrierId; }

    public int getSpecificCarrierId() { return mSpecificCarrierId; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(mMcc);
        out.writeString(mMnc);
        out.writeString(mSpn);
        out.writeString(mImsi);
        out.writeString(mGid1);
        out.writeString(mGid2);
        out.writeInt(mCarrierId);
        out.writeInt(mSpecificCarrierId);
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CarrierIdentifier)) return false;
        CarrierIdentifier other = (CarrierIdentifier) obj;
        return java.util.Objects.equals(mMcc, other.mMcc)
                && java.util.Objects.equals(mMnc, other.mMnc)
                && java.util.Objects.equals(mSpn, other.mSpn)
                && java.util.Objects.equals(mImsi, other.mImsi)
                && java.util.Objects.equals(mGid1, other.mGid1)
                && java.util.Objects.equals(mGid2, other.mGid2)
                && mCarrierId == other.mCarrierId
                && mSpecificCarrierId == other.mSpecificCarrierId;
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(mMcc, mMnc, mSpn, mImsi, mGid1, mGid2,
                Integer.valueOf(mCarrierId), Integer.valueOf(mSpecificCarrierId));
    }

    @Override public String toString() {
        return "CarrierIdentifier{mcc=" + mMcc + ",mnc=" + mMnc + "}";
    }

    public static final Creator<CarrierIdentifier> CREATOR =
            new Creator<CarrierIdentifier>() {
        @Override public CarrierIdentifier createFromParcel(Parcel in) {
            return new CarrierIdentifier(in.readString(), in.readString(),
                    in.readString(), in.readString(), in.readString(), in.readString(),
                    in.readInt(), in.readInt());
        }
        @Override public CarrierIdentifier[] newArray(int size) {
            return new CarrierIdentifier[size];
        }
    };
}
