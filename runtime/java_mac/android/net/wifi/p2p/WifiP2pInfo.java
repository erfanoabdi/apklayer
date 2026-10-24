// android.net.wifi.p2p.WifiP2pInfo -- the state of the current Wi-Fi Direct
// connection.
//
// AOSP exposes it as three public FIELDS, and apps read them directly (Signal
// reads all three). The defaults are the honest answer here and are also
// AOSP's own defaults for "not connected": groupFormed = false,
// isGroupOwner = false, groupOwnerAddress = null.
//
// groupFormed = false is the field that matters: an app checks it before using
// groupOwnerAddress, so a false answer routes it away from a socket connection
// that could never be made on a host with no Wi-Fi Direct radio.
//
// Mirrors wifi/java/android/net/wifi/p2p/WifiP2pInfo.java (android11-release).
package android.net.wifi.p2p;

import android.os.Parcel;
import android.os.Parcelable;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class WifiP2pInfo implements Parcelable {

    public boolean groupFormed;
    public boolean isGroupOwner;
    public InetAddress groupOwnerAddress;

    public WifiP2pInfo() { }

    public WifiP2pInfo(WifiP2pInfo source) {
        if (source != null) {
            groupFormed = source.groupFormed;
            isGroupOwner = source.isGroupOwner;
            groupOwnerAddress = source.groupOwnerAddress;
        }
    }

    @Override public String toString() {
        StringBuffer sbuf = new StringBuffer();
        sbuf.append("groupFormed: ").append(groupFormed)
            .append(" isGroupOwner: ").append(isGroupOwner)
            .append(" groupOwnerAddress: ").append(groupOwnerAddress);
        return sbuf.toString();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(groupFormed ? (byte) 1 : (byte) 0);
        dest.writeByte(isGroupOwner ? (byte) 1 : (byte) 0);
        if (groupOwnerAddress != null) {
            dest.writeByte((byte) 1);
            dest.writeByteArray(groupOwnerAddress.getAddress());
        } else {
            dest.writeByte((byte) 0);
        }
    }

    public static final Creator<WifiP2pInfo> CREATOR = new Creator<WifiP2pInfo>() {
        @Override public WifiP2pInfo createFromParcel(Parcel in) {
            WifiP2pInfo info = new WifiP2pInfo();
            info.groupFormed = in.readByte() == 1;
            info.isGroupOwner = in.readByte() == 1;
            if (in.readByte() == 1) {
                try {
                    byte[] addr = in.createByteArray();
                    info.groupOwnerAddress = InetAddress.getByAddress(addr);
                } catch (UnknownHostException e) {
                    info.groupOwnerAddress = null;
                }
            }
            return info;
        }

        @Override public WifiP2pInfo[] newArray(int size) { return new WifiP2pInfo[size]; }
    };
}
