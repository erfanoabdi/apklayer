// android.net.IpPrefix
//
// An IP address + prefix length (CIDR block). Pure value type built on the
// JDK's InetAddress, so we implement it faithfully. Mirrors
// frameworks/base packages Connectivity android.net.IpPrefix public API.
package android.net;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public final class IpPrefix {

    private final byte[] mAddress;
    private final int mPrefixLength;

    public IpPrefix(InetAddress address, int prefixLength) {
        mAddress = address.getAddress();
        mPrefixLength = prefixLength;
    }

    public IpPrefix(String prefix) {
        int slash = prefix.indexOf('/');
        if (slash < 0) throw new IllegalArgumentException("Invalid IpPrefix: " + prefix);
        try {
            mAddress = InetAddress.getByName(prefix.substring(0, slash)).getAddress();
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IpPrefix: " + prefix, e);
        }
        mPrefixLength = Integer.parseInt(prefix.substring(slash + 1));
    }

    public InetAddress getAddress() {
        try { return InetAddress.getByAddress(mAddress); }
        catch (UnknownHostException e) { return null; }
    }

    public byte[] getRawAddress() { return mAddress.clone(); }
    public int getPrefixLength() { return mPrefixLength; }

    public boolean contains(InetAddress address) {
        byte[] addr = address.getAddress();
        if (addr.length != mAddress.length) return false;
        int fullBytes = mPrefixLength / 8;
        int remainingBits = mPrefixLength % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (addr[i] != mAddress[i]) return false;
        }
        if (remainingBits > 0) {
            int mask = 0xff << (8 - remainingBits);
            return (addr[fullBytes] & mask) == (mAddress[fullBytes] & mask);
        }
        return true;
    }

    @Override public String toString() {
        InetAddress a = getAddress();
        return (a == null ? "?" : a.getHostAddress()) + "/" + mPrefixLength;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof IpPrefix)) return false;
        IpPrefix that = (IpPrefix) o;
        return mPrefixLength == that.mPrefixLength && Arrays.equals(mAddress, that.mAddress);
    }
    @Override public int hashCode() { return Arrays.hashCode(mAddress) * 31 + mPrefixLength; }
}
