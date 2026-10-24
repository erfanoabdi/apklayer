// android.net.RouteInfo
//
// One IP routing-table entry (destination prefix + gateway + interface).
// Pure value type. Mac.apk does not expose the host routing table to the
// guest, but LinkProperties.getRoutes can hand these back, so the type +
// getters must resolve. Mirrors AOSP android.net.RouteInfo public API.
package android.net;

import java.net.InetAddress;

public final class RouteInfo {

    public static final int RTN_UNICAST = 1;
    public static final int RTN_UNREACHABLE = 7;
    public static final int RTN_THROW = 9;

    private final IpPrefix mDestination;
    private final InetAddress mGateway;
    private final String mInterface;
    private final int mType;

    public RouteInfo(IpPrefix destination, InetAddress gateway, String iface, int type) {
        mDestination = destination;
        mGateway = gateway;
        mInterface = iface;
        mType = type;
    }

    public RouteInfo(IpPrefix destination, InetAddress gateway, String iface) {
        this(destination, gateway, iface, RTN_UNICAST);
    }

    public RouteInfo(IpPrefix destination, InetAddress gateway) {
        this(destination, gateway, null, RTN_UNICAST);
    }

    public IpPrefix getDestination() { return mDestination; }
    public InetAddress getGateway() { return mGateway; }
    public String getInterface() { return mInterface; }
    public int getType() { return mType; }
    public boolean isDefaultRoute() {
        return mDestination != null && mDestination.getPrefixLength() == 0;
    }
    public boolean hasGateway() { return mGateway != null; }

    @Override public String toString() {
        return (mDestination == null ? "?" : mDestination.toString())
                + " -> " + (mGateway == null ? "*" : mGateway.getHostAddress())
                + (mInterface == null ? "" : " " + mInterface);
    }
}
