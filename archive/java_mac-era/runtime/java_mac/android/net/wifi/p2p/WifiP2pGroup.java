// android.net.wifi.p2p.WifiP2pGroup -- a formed Wi-Fi Direct group (its
// network name, owner, clients, passphrase and interface).
//
// A REAL container: the accessors and client-list operations all work. It is
// never PRODUCED here, though -- WifiP2pManager.requestGroupInfo reports null,
// because a Mac has no Wi-Fi Direct radio and therefore no group has formed.
// Null is AOSP's own answer when there is no current group, so the callers
// that matter (both corpus apps call getInterface() on the result) already
// null-check it.
//
// Mirrors wifi/java/android/net/wifi/p2p/WifiP2pGroup.java (android11-release).
package android.net.wifi.p2p;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class WifiP2pGroup implements Parcelable {

    public static final int NETWORK_ID_TEMPORARY = -1;

    /** @deprecated on AOSP in favour of NETWORK_ID_TEMPORARY. */
    @Deprecated
    public static final int TEMPORARY_NET_ID = NETWORK_ID_TEMPORARY;

    public static final int NETWORK_ID_PERSISTENT = -2;

    private String mNetworkName;
    private WifiP2pDevice mOwner;
    private boolean mIsGroupOwner;
    private List<WifiP2pDevice> mClients = new ArrayList<WifiP2pDevice>();
    private String mPassphrase;
    private String mInterface;
    private int mNetId;
    private int mFrequency;

    public WifiP2pGroup() { }

    public WifiP2pGroup(String supplicantEvent) throws IllegalArgumentException {
        // AOSP parses a wpa_supplicant P2P-GROUP event. Nothing on a Mac emits
        // one, so the only truthful response to an arbitrary string is to
        // reject it rather than fabricate a group.
        throw new IllegalArgumentException("Malformed supplicant event: " + supplicantEvent);
    }

    public WifiP2pGroup(WifiP2pGroup source) {
        if (source != null) {
            mNetworkName = source.getNetworkName();
            mOwner = source.getOwner() == null ? null : new WifiP2pDevice(source.getOwner());
            mIsGroupOwner = source.mIsGroupOwner;
            for (WifiP2pDevice d : source.getClientList()) mClients.add(d);
            mPassphrase = source.getPassphrase();
            mInterface = source.getInterface();
            mNetId = source.getNetworkId();
            mFrequency = source.getFrequency();
        }
    }

    /** @hide on AOSP. */
    public void setNetworkName(String networkName) { mNetworkName = networkName; }

    public String getNetworkName() { return mNetworkName; }

    /** @hide on AOSP. */
    public void setIsGroupOwner(boolean isGo) { mIsGroupOwner = isGo; }

    public boolean isGroupOwner() { return mIsGroupOwner; }

    /** @hide on AOSP. */
    public void setOwner(WifiP2pDevice device) { mOwner = device; }

    public WifiP2pDevice getOwner() { return mOwner; }

    /** @hide on AOSP. */
    public void addClient(String address) {
        WifiP2pDevice d = new WifiP2pDevice();
        d.deviceAddress = address;
        addClient(d);
    }

    /** @hide on AOSP. */
    public void addClient(WifiP2pDevice device) {
        for (WifiP2pDevice client : mClients) {
            if (client.equals(device)) return;
        }
        mClients.add(device);
    }

    /** @hide on AOSP. */
    public boolean removeClient(String address) {
        WifiP2pDevice d = new WifiP2pDevice();
        d.deviceAddress = address;
        return mClients.remove(d);
    }

    /** @hide on AOSP. */
    public boolean removeClient(WifiP2pDevice device) { return mClients.remove(device); }

    /** @hide on AOSP. */
    public boolean isClientListEmpty() { return mClients.size() == 0; }

    /** @hide on AOSP. */
    public boolean contains(WifiP2pDevice device) {
        return (mOwner != null && mOwner.equals(device)) || mClients.contains(device);
    }

    public Collection<WifiP2pDevice> getClientList() {
        return Collections.unmodifiableCollection(mClients);
    }

    /** @hide on AOSP. */
    public void setPassphrase(String passphrase) { mPassphrase = passphrase; }

    public String getPassphrase() { return mPassphrase; }

    /** @hide on AOSP. */
    public void setInterface(String intf) { mInterface = intf; }

    public String getInterface() { return mInterface; }

    public int getNetworkId() { return mNetId; }

    /** @hide on AOSP. */
    public void setNetworkId(int netId) { mNetId = netId; }

    public int getFrequency() { return mFrequency; }

    /** @hide on AOSP. */
    public void setFrequency(int freq) { mFrequency = freq; }

    @Override public String toString() {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append("network: ").append(mNetworkName);
        sbuf.append("\n isGO: ").append(mIsGroupOwner);
        sbuf.append("\n GO: ").append(mOwner);
        for (WifiP2pDevice client : mClients) {
            sbuf.append("\n Client: ").append(client);
        }
        sbuf.append("\n interface: ").append(mInterface);
        sbuf.append("\n networkId: ").append(mNetId);
        sbuf.append("\n frequency: ").append(mFrequency);
        return sbuf.toString();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mNetworkName);
        dest.writeParcelable(mOwner, flags);
        dest.writeByte(mIsGroupOwner ? (byte) 1 : (byte) 0);
        dest.writeInt(mClients.size());
        for (WifiP2pDevice client : mClients) {
            dest.writeParcelable(client, flags);
        }
        dest.writeString(mPassphrase);
        dest.writeString(mInterface);
        dest.writeInt(mNetId);
        dest.writeInt(mFrequency);
    }

    public static final Creator<WifiP2pGroup> CREATOR = new Creator<WifiP2pGroup>() {
        @Override public WifiP2pGroup createFromParcel(Parcel in) {
            WifiP2pGroup group = new WifiP2pGroup();
            group.setNetworkName(in.readString());
            group.setOwner((WifiP2pDevice) in.readParcelable(
                    WifiP2pDevice.class.getClassLoader()));
            group.setIsGroupOwner(in.readByte() == (byte) 1);
            int clientCount = in.readInt();
            for (int i = 0; i < clientCount; i++) {
                group.addClient((WifiP2pDevice) in.readParcelable(
                        WifiP2pDevice.class.getClassLoader()));
            }
            group.setPassphrase(in.readString());
            group.setInterface(in.readString());
            group.setNetworkId(in.readInt());
            group.setFrequency(in.readInt());
            return group;
        }

        @Override public WifiP2pGroup[] newArray(int size) { return new WifiP2pGroup[size]; }
    };
}
