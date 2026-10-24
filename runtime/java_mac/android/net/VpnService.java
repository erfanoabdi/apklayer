// android.net.VpnService
//
// Base class an app extends to provide a VPN. Mac.apk does not implement a
// guest VPN transport (no TUN device exposed), so prepare() returns a consent
// Intent of null (already-prepared on a device with no VPN) and
// Builder.establish() returns null (the documented "could not establish"
// result) so VPN apps take their failure path instead of NPEing on a missing
// type. The full Builder surface must resolve. Mirrors
// frameworks/base/core/java/android/net/VpnService.java.
package android.net;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;

import java.net.InetAddress;

public class VpnService extends Service {

    public static final String SERVICE_INTERFACE = "android.net.VpnService";
    public static final String SERVICE_META_DATA_SUPPORTS_ALWAYS_ON =
            "android.net.VpnService.SUPPORTS_ALWAYS_ON";

    public VpnService() {}

    @Override public android.os.IBinder onBind(Intent intent) {
        // AOSP returns its internal Callback binder only for SERVICE_INTERFACE.
        return super.onBind(intent);
    }

    /** AOSP prepare(): returns a consent Intent if the user has not authorized
     *  a VPN, else null. With no VPN subsystem there is nothing to consent to,
     *  so we return null ("already prepared"). */
    public static Intent prepare(Context context) { return null; }

    /** AOSP protect(): excludes a socket from the VPN. No VPN, so nothing to
     *  protect -- report success (the socket is unaffected, which is correct). */
    public boolean protect(int socket) { return true; }
    public boolean protect(java.net.Socket socket) { return true; }
    public boolean protect(java.net.DatagramSocket socket) { return true; }

    public boolean setUnderlyingNetworks(Network[] networks) { return false; }

    /**
     * Always-on VPN is a SETTINGS state the user turns on for a VPN app. There
     * is no VPN subsystem and no such setting on this host, so false -- and
     * false is what AOSP reports for any VPN service the user has not made
     * always-on, which is the overwhelmingly common case there too.
     */
    public final boolean isAlwaysOn() { return false; }

    /** @see #isAlwaysOn -- lockdown is the always-on "block connections
     *  without VPN" sub-setting, so it cannot be on when always-on is off. */
    public final boolean isLockdownEnabled() { return false; }

    public void onRevoke() {}

    public class Builder {
        public Builder() {}

        public Builder setSession(String session) { return this; }
        public Builder setConfigureIntent(android.app.PendingIntent intent) { return this; }
        public Builder setMtu(int mtu) { return this; }
        public Builder addAddress(InetAddress address, int prefixLength) { return this; }
        public Builder addAddress(String address, int prefixLength) { return this; }
        public Builder addRoute(InetAddress address, int prefixLength) { return this; }
        public Builder addRoute(String address, int prefixLength) { return this; }
        public Builder addRoute(IpPrefix prefix) { return this; }
        public Builder excludeRoute(IpPrefix prefix) { return this; }
        public Builder addDnsServer(InetAddress address) { return this; }
        public Builder addDnsServer(String address) { return this; }
        public Builder addSearchDomain(String domain) { return this; }
        public Builder allowFamily(int family) { return this; }
        // AOSP declares NameNotFoundException on both: it resolves the package
        // through PackageManager first. Mirrored so an app's existing
        // try/catch is not made unreachable-catch by our narrower signature.
        public Builder addAllowedApplication(String packageName)
                throws android.content.pm.PackageManager.NameNotFoundException {
            return this;
        }
        public Builder addDisallowedApplication(String packageName)
                throws android.content.pm.PackageManager.NameNotFoundException {
            return this;
        }
        public Builder allowBypass() { return this; }
        public Builder setBlocking(boolean blocking) { return this; }
        public Builder setUnderlyingNetworks(Network[] networks) { return this; }
        public Builder setMetered(boolean isMetered) { return this; }
        public Builder setHttpProxy(ProxyInfo proxyInfo) { return this; }

        /** AOSP establish(): null if the VPN could not be created. We have no
         *  TUN device, so null is the honest result. */
        public ParcelFileDescriptor establish() { return null; }
    }
}
