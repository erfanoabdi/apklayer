// android.net.wifi.p2p.nsd.WifiP2pServiceRequest -- a service-discovery query
// to broadcast over Wi-Fi Direct.
//
// A REAL value object holding a protocol type and a hex query string, with no
// device dependency. Issuing it needs the radio, and
// WifiP2pManager.addServiceRequest/discoverServices report failure honestly.
//
// Mirrors wifi/java/android/net/wifi/p2p/nsd/WifiP2pServiceRequest.java
// (android11-release).
package android.net.wifi.p2p.nsd;

import android.os.Parcel;
import android.os.Parcelable;

public class WifiP2pServiceRequest implements Parcelable {

    protected int mProtocolType;
    protected int mLength;
    protected int mTransId;
    protected String mQuery;

    protected WifiP2pServiceRequest(int protocolType, String query) {
        validateQuery(query);
        mProtocolType = protocolType;
        mQuery = query;
        mLength = query == null ? 2 : (query.length() / 2) + 2;
    }

    private WifiP2pServiceRequest(int serviceType, int length, int transId, String query) {
        mProtocolType = serviceType;
        mLength = length;
        mTransId = transId;
        mQuery = query;
    }

    /** @hide on AOSP. */
    public int getTransactionId() { return mTransId; }

    /** @hide on AOSP. */
    public void setTransactionId(int id) { mTransId = id; }

    /** @hide on AOSP. */
    public String getSupplicantQuery() {
        StringBuffer sb = new StringBuffer();
        sb.append(String.format(java.util.Locale.US, "%02x", mLength & 0xff));
        sb.append(String.format(java.util.Locale.US, "%02x", (mLength >> 8) & 0xff));
        sb.append(String.format(java.util.Locale.US, "%02x", mProtocolType));
        sb.append(String.format(java.util.Locale.US, "%02x", mTransId));
        if (mQuery != null) sb.append(mQuery);
        return sb.toString();
    }

    /** AOSP rejects a non-hex or odd-length query rather than truncating it. */
    private void validateQuery(String query) {
        if (query == null) return;

        int UNSIGNED_SHORT_MAX = 65535;
        if (query.length() % 2 == 1) {
            throw new IllegalArgumentException(
                    "query size is invalid. query=" + query);
        }
        if (query.length() / 2 > UNSIGNED_SHORT_MAX) {
            throw new IllegalArgumentException(
                    "query size is too large. len=" + query.length());
        }
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                throw new IllegalArgumentException(
                        "query should be hex string. query=" + query);
            }
        }
    }

    public static WifiP2pServiceRequest newInstance(int protocolType, String queryData) {
        return new WifiP2pServiceRequest(protocolType, queryData);
    }

    public static WifiP2pServiceRequest newInstance(int protocolType) {
        return new WifiP2pServiceRequest(protocolType, null);
    }

    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof WifiP2pServiceRequest)) return false;
        WifiP2pServiceRequest req = (WifiP2pServiceRequest) o;
        // AOSP deliberately ignores the transaction id here.
        return req.mProtocolType == mProtocolType
                && req.mLength == mLength
                && equals(req.mQuery, mQuery);
    }

    private static boolean equals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + mProtocolType;
        result = 31 * result + mLength;
        result = 31 * result + (mQuery == null ? 0 : mQuery.hashCode());
        return result;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mProtocolType);
        dest.writeInt(mLength);
        dest.writeInt(mTransId);
        dest.writeString(mQuery);
    }

    public static final Creator<WifiP2pServiceRequest> CREATOR =
            new Creator<WifiP2pServiceRequest>() {
        @Override public WifiP2pServiceRequest createFromParcel(Parcel in) {
            return new WifiP2pServiceRequest(in.readInt(), in.readInt(), in.readInt(),
                    in.readString());
        }

        @Override public WifiP2pServiceRequest[] newArray(int size) {
            return new WifiP2pServiceRequest[size];
        }
    };
}
