package apklayer.host;

import android.os.ParcelFileDescriptor;
import java.io.FileDescriptor;
import java.io.IOException;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * VpnService.protect(): a VPN's own sockets must not go into the VPN. On Android netd's libnetd_client marks the socket (it needs CAP_NET_ADMIN, and the instance
 * has no netd). Here the hub's VPN guard (cli/vpn_guard.cpp) binds the socket to the host's own interface with SO_BINDTODEVICE, which needs no privilege
 * (docs/VPN.md). Not the VPN's owner, or no hub: false, as Android's for a socket it cannot protect.
 */
@Implements(className = "com.android.internal.net.NetworkUtilsInternal", isInAndroidSdk = false)
public class ShadowHostNetworkUtilsInternal {
    @Implementation protected static boolean protectFromVpn(int socketFd) {
        try (ParcelFileDescriptor dup = ParcelFileDescriptor.fromFd(socketFd)) { return protectFromVpn(dup.getFileDescriptor()); }
        catch (IOException failure) { System.out.println("[apklayer] protect: " + failure); return false; }
    }

    @Implementation protected static boolean protectFromVpn(FileDescriptor socketFd) {
        if (!HubClient.connected()) { System.out.println("[apklayer] protect: no hub"); return false; }
        try { return HubClient.current().vpnProtect(socketFd); }
        catch (RuntimeException noHub) { System.out.println("[apklayer] protect: " + noHub); return false; }
    }
}
