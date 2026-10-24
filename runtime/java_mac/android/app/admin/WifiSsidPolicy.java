// android.app.admin.WifiSsidPolicy -- "Used to indicate the Wi-Fi SSID
// restriction policy the network must satisfy in order to be eligible for a
// connection." Set by a device owner. API 33.
//
// AOSP ref: frameworks/base/core/java/android/app/admin/WifiSsidPolicy.java
// (android16-release).
package android.app.admin;

import android.net.wifi.WifiSsid;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class WifiSsidPolicy implements Parcelable {

    /** AOSP WIFI_SSID_POLICY_TYPE_ALLOWLIST: only the listed SSIDs may be
     *  joined. */
    public static final int WIFI_SSID_POLICY_TYPE_ALLOWLIST = 0;
    /** AOSP WIFI_SSID_POLICY_TYPE_DENYLIST: every SSID except the listed ones. */
    public static final int WIFI_SSID_POLICY_TYPE_DENYLIST = 1;

    private final int mPolicyType;
    private final Set<WifiSsid> mSsids;

    /** AOSP WifiSsidPolicy(int policyType, Set<WifiSsid> ssids): AOSP rejects an
     *  EMPTY ssid set with IllegalArgumentException -- an allowlist of nothing
     *  would brick Wi-Fi and a denylist of nothing is a no-op -- and validates
     *  the policy type. */
    public WifiSsidPolicy(int policyType, Set<WifiSsid> ssids) {
        if (policyType != WIFI_SSID_POLICY_TYPE_ALLOWLIST
                && policyType != WIFI_SSID_POLICY_TYPE_DENYLIST) {
            throw new IllegalArgumentException("Invalid policy type: " + policyType);
        }
        if (ssids == null || ssids.isEmpty()) {
            throw new IllegalArgumentException("SSID list cannot be empty");
        }
        mPolicyType = policyType;
        mSsids = new HashSet<WifiSsid>(ssids);
    }

    // NOTE for the android.net.wifi lane: AOSP's WifiSsid IS Parcelable, and
    // AOSP's WifiSsidPolicy parcels the set through writeTypedObject.
    // android.net.wifi.WifiSsid in this tree is not Parcelable, so the SSIDs
    // travel as their raw bytes here -- which is the same information
    // (WifiSsid.fromBytes/getBytes round-trips exactly) but a different wire
    // format from a device's. Re-point at writeTypedObject once WifiSsid gains
    // the interface.
    private WifiSsidPolicy(Parcel in) {
        mPolicyType = in.readInt();
        mSsids = new HashSet<WifiSsid>();
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            byte[] raw = in.createByteArray();
            if (raw != null) mSsids.add(WifiSsid.fromBytes(raw));
        }
    }

    public int getPolicyType() { return mPolicyType; }
    /** AOSP getSsids(): never null. */
    public Set<WifiSsid> getSsids() { return Collections.unmodifiableSet(mSsids); }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPolicyType);
        dest.writeInt(mSsids.size());
        for (WifiSsid s : mSsids) dest.writeByteArray(s.getBytes());
    }
    @Override public boolean equals(Object o) {
        if (!(o instanceof WifiSsidPolicy)) return false;
        WifiSsidPolicy other = (WifiSsidPolicy) o;
        return mPolicyType == other.mPolicyType && mSsids.equals(other.mSsids);
    }
    @Override public int hashCode() { return java.util.Objects.hash(mPolicyType, mSsids); }

    public static final Parcelable.Creator<WifiSsidPolicy> CREATOR =
            new Parcelable.Creator<WifiSsidPolicy>() {
        @Override public WifiSsidPolicy createFromParcel(Parcel in) {
            return new WifiSsidPolicy(in);
        }
        @Override public WifiSsidPolicy[] newArray(int size) { return new WifiSsidPolicy[size]; }
    };
}
