// android.net.LinkAddress
//
// An interface address (InetAddress + prefix length + flags + scope). Pure
// value type on the JDK's InetAddress. Mirrors AOSP android.net.LinkAddress
// public API.
package android.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class LinkAddress {

    public static final int SCOPE_HOST = 0x10;     // RT_SCOPE_HOST
    public static final int SCOPE_LINK = 0xfd;     // RT_SCOPE_LINK
    public static final int SCOPE_SITE = 0xc8;     // RT_SCOPE_SITE
    public static final int SCOPE_UNIVERSE = 0x00; // RT_SCOPE_UNIVERSE

    private final InetAddress mAddress;
    private final int mPrefixLength;
    private final int mFlags;
    private final int mScope;

    public LinkAddress(InetAddress address, int prefixLength) {
        this(address, prefixLength, 0, SCOPE_UNIVERSE);
    }

    public LinkAddress(InetAddress address, int prefixLength, int flags, int scope) {
        mAddress = address;
        mPrefixLength = prefixLength;
        mFlags = flags;
        mScope = scope;
    }

    public LinkAddress(String address) {
        int slash = address.indexOf('/');
        if (slash < 0) throw new IllegalArgumentException("Invalid LinkAddress: " + address);
        try {
            mAddress = InetAddress.getByName(address.substring(0, slash));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid LinkAddress: " + address, e);
        }
        mPrefixLength = Integer.parseInt(address.substring(slash + 1));
        mFlags = 0;
        mScope = SCOPE_UNIVERSE;
    }

    public InetAddress getAddress() { return mAddress; }
    public int getPrefixLength() { return mPrefixLength; }
    public int getFlags() { return mFlags; }
    public int getScope() { return mScope; }

    @Override public String toString() {
        return (mAddress == null ? "" : mAddress.getHostAddress()) + "/" + mPrefixLength;
    }
}
