// android.net.MacAddress
//
// A 48-bit hardware (MAC) address value type. Pure parsing/formatting -- no
// hardware needed -- so we implement it faithfully against the AOSP contract.
// Mirrors frameworks/base/packages/Connectivity/framework/src/android/net/
// MacAddress.java (public API).
package android.net;

import java.util.Arrays;

public final class MacAddress {

    public static final int TYPE_UNICAST = 1;
    public static final int TYPE_MULTICAST = 2;
    public static final int TYPE_BROADCAST = 3;

    public static final MacAddress BROADCAST_ADDRESS =
            fromBytes(new byte[] { -1, -1, -1, -1, -1, -1 });

    private final byte[] mAddr;

    private MacAddress(byte[] addr) { mAddr = addr; }

    public static MacAddress fromBytes(byte[] addr) {
        if (addr == null || addr.length != 6) {
            throw new IllegalArgumentException("MacAddress must be 6 bytes");
        }
        return new MacAddress(addr.clone());
    }

    public static MacAddress fromString(String addr) {
        if (addr == null) throw new IllegalArgumentException("addr cannot be null");
        String[] parts = addr.split(":");
        if (parts.length != 6) {
            throw new IllegalArgumentException("Invalid MAC address: " + addr);
        }
        byte[] bytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            int v = Integer.parseInt(parts[i], 16);
            if (v < 0 || v > 0xff) {
                throw new IllegalArgumentException("Invalid MAC address: " + addr);
            }
            bytes[i] = (byte) v;
        }
        return new MacAddress(bytes);
    }

    public byte[] toByteArray() { return mAddr.clone(); }

    public int getAddressType() {
        if (Arrays.equals(mAddr, BROADCAST_ADDRESS.mAddr)) return TYPE_BROADCAST;
        return ((mAddr[0] & 0x01) != 0) ? TYPE_MULTICAST : TYPE_UNICAST;
    }

    public boolean isLocallyAssigned() { return (mAddr[0] & 0x02) != 0; }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mAddr.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02x", mAddr[i] & 0xff));
        }
        return sb.toString();
    }

    @Override public boolean equals(Object o) {
        return (o instanceof MacAddress) && Arrays.equals(mAddr, ((MacAddress) o).mAddr);
    }
    @Override public int hashCode() { return Arrays.hashCode(mAddr); }
}
