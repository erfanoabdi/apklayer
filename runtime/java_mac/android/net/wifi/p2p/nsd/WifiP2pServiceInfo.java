// android.net.wifi.p2p.nsd.WifiP2pServiceInfo -- a local service advertised
// over Wi-Fi Direct service discovery.
//
// A REAL value object: it holds the wpa_supplicant query strings its subclass
// built, with no device dependency. Advertising it is what needs the radio,
// and WifiP2pManager.addLocalService reports failure honestly.
//
// Mirrors wifi/java/android/net/wifi/p2p/nsd/WifiP2pServiceInfo.java
// (android11-release).
package android.net.wifi.p2p.nsd;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

public class WifiP2pServiceInfo implements Parcelable {

    public static final int SERVICE_TYPE_ALL = 0;
    public static final int SERVICE_TYPE_BONJOUR = 1;
    public static final int SERVICE_TYPE_UPNP = 2;
    public static final int SERVICE_TYPE_WS_DISCOVERY = 3;
    public static final int SERVICE_TYPE_VENDOR_SPECIFIC = 255;

    private List<String> mQueryList;

    protected WifiP2pServiceInfo(List<String> queryList) {
        if (queryList == null) {
            throw new IllegalArgumentException("query list cannot be null");
        }
        mQueryList = queryList;
    }

    /** @hide on AOSP. */
    public List<String> getSupplicantQueryList() {
        return mQueryList;
    }

    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof WifiP2pServiceInfo)) return false;
        WifiP2pServiceInfo servInfo = (WifiP2pServiceInfo) o;
        return mQueryList.equals(servInfo.mQueryList);
    }

    @Override public int hashCode() {
        return 17 + 31 * (mQueryList == null ? 0 : mQueryList.hashCode());
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringList(mQueryList);
    }

    public static final Creator<WifiP2pServiceInfo> CREATOR =
            new Creator<WifiP2pServiceInfo>() {
        @Override public WifiP2pServiceInfo createFromParcel(Parcel in) {
            List<String> data = new ArrayList<String>();
            in.readStringList(data);
            return new WifiP2pServiceInfo(data);
        }

        @Override public WifiP2pServiceInfo[] newArray(int size) {
            return new WifiP2pServiceInfo[size];
        }
    };
}
