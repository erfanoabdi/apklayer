package hello.vpn;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Bundle;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * VpnService from an app's point of view (tests/vm/vpn.sh). -Dvpn.phase=unit (default): prepare() and the consent dialog, establish() with 10.88.0.1/24 and a route
 * to that prefix only, protect() succeeds; then it waits to be revoked while the test pings 10.88.0.2 and sends UDP to it from the host (Tunnel echoes both),
 * disconnects (`apklayer vpn disconnect`: onRevoke). -Dvpn.phase=crash: the same, and the test kills this app. -Dvpn.phase=default: the default route through the
 * TUN: an unprotected UDP probe to 1.1.1.1 must arrive on the TUN, a protect()ed one must not, and the active network must be a VPN.
 */
public final class Main extends Activity {
    private static final CompletableFuture<Integer> consent = new CompletableFuture<>();

    private static void require(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            try { run(System.getProperty("vpn.phase", "unit")); System.out.println("[vpn] PASS"); }
            catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[vpn] FAIL " + failure); }
        }).start();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) { consent.complete(result); }

    private boolean seen(String text) { for (String s : Tunnel.seen) if (s.contains(text)) return true; return false; }

    private boolean vpnNetwork() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
    }

    private void probe(String text, boolean protect) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            if (protect) require(new Tunnel().protect(socket), "protect(DatagramSocket)");
            byte[] data = ("probe-" + text).getBytes();
            try { socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("1.1.1.1"), 53)); }
            catch (java.io.IOException unreachable) { System.out.println("[vpn] probe " + text + " send: " + unreachable.getMessage()); }
        }
    }

    private void run(String phase) throws Exception {
        Intent ask = VpnService.prepare(this);
        System.out.println("[vpn] prepare " + (ask == null ? "null (agreed before)" : "needs consent " + ask.getComponent()));
        if (ask != null) {
            runOnUiThread(() -> startActivityForResult(ask, 1));
            require(consent.get(60, TimeUnit.SECONDS) == RESULT_OK, "consent refused");
            require(VpnService.prepare(this) == null, "prepare still asks after consent");
        }
        Tunnel.ready = new java.util.concurrent.CompletableFuture<>();
        startService(new Intent(this, Tunnel.class).putExtra("default", phase.equals("default")));
        require(Tunnel.ready.get(60, TimeUnit.SECONDS), "establish");
        System.out.println("[vpn] established");
        if (phase.equals("default")) {
            for (int i = 0; i < 40 && !vpnNetwork(); i++) Thread.sleep(250);
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            require(vpnNetwork(), "the active network is not the VPN: " + cm.getActiveNetwork() + " " + cm.getNetworkCapabilities(cm.getActiveNetwork()) + " " + cm.getActiveNetworkInfo());
            System.out.println("[vpn] active network is the VPN");
            probe("unprotected", false);
            probe("protected", true);
            for (int i = 0; i < 40 && !seen("probe-unprotected"); i++) Thread.sleep(250);
            Thread.sleep(1500);  // the protected one would have been here by now
            require(seen("probe-unprotected"), "the unprotected socket's datagram did not go into the VPN: " + Tunnel.seen);
            require(!seen("probe-protected"), "the protected socket's datagram went into the VPN: " + Tunnel.seen);
            System.out.println("[vpn] protect ok");
        } else {
            try (DatagramSocket mine = new DatagramSocket()) { require(new Tunnel().protect(mine), "protect(DatagramSocket)"); }
            System.out.println("[vpn] protect returned true");
        }
        System.out.println("[vpn] waiting for revoke");
        for (int i = 0; i < 240 && !Tunnel.revoked; i++) Thread.sleep(250);
        require(Tunnel.revoked, "onRevoke never came");
        System.out.println("[vpn] revoked");
    }
}
