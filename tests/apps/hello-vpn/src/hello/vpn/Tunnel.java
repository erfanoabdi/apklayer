package hello.vpn;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A VPN as an app writes one (WireGuard's, OpenVPN's shape): establish() gives the TUN, a thread reads IP packets from it. Here the "tunnel" is an echo:
 * an ICMP echo request is answered, and so is an IPv4 UDP packet to port 7777 (same payload, from the address it was sent to). Every UDP payload that is
 * not port 7777's DNS-less noise is recorded for Main: "probe-*" payloads (any port) and port 7777's.
 */
public final class Tunnel extends VpnService {
    static final List<String> seen = new CopyOnWriteArrayList<>();
    static volatile CompletableFuture<Boolean> ready = new CompletableFuture<>();
    static volatile boolean revoked;
    private ParcelFileDescriptor tun;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        boolean everything = intent != null && intent.getBooleanExtra("default", false);
        new Thread(() -> establishAs(everything), "establish").start();
        return START_NOT_STICKY;
    }

    private void establishAs(boolean everything) {
        try {
            Builder builder = new Builder().setSession("hello-vpn").setMtu(1400).addAddress("10.88.0.1", 24).setBlocking(true);
            if (everything) builder.addRoute("0.0.0.0", 0).addDnsServer("10.88.0.53"); else builder.addRoute("10.88.0.0", 24).addDnsServer("10.88.0.53").addSearchDomain("vpn.test");
            ParcelFileDescriptor fresh = builder.establish();
            if (fresh == null) { System.out.println("[vpn] establish returned null"); ready.complete(false); return; }
            tun = fresh;
            new Thread(() -> loop(fresh), "tun").start();
            ready.complete(true);
        } catch (Throwable failure) { failure.printStackTrace(System.out); ready.complete(false); }
    }

    private static int checksum(byte[] p, int from, int to) {
        int sum = 0;
        for (int i = from; i + 1 < to; i += 2) sum += ((p[i] & 255) << 8) | (p[i + 1] & 255);
        if (((to - from) & 1) != 0) sum += (p[to - 1] & 255) << 8;
        while (sum > 0xffff) sum = (sum & 0xffff) + (sum >> 16);
        return ~sum & 0xffff;
    }

    private static void loop(ParcelFileDescriptor tun) {
        byte[] packet = new byte[2048];
        try (FileInputStream in = new FileInputStream(tun.getFileDescriptor()); FileOutputStream out = new FileOutputStream(tun.getFileDescriptor())) {
            for (int n; (n = in.read(packet)) > 0;) {
                if ((packet[0] >> 4) != 4) continue;  // IPv4 only (IPv6 neighbour discovery from the interface itself is ignored)
                int ihl = (packet[0] & 15) * 4, proto = packet[9];
                if (proto == 1 && packet[ihl] == 8) {  // ICMP echo request: the reply
                    packet[ihl] = 0;
                    packet[ihl + 2] = packet[ihl + 3] = 0;
                    int c = checksum(packet, ihl, n);
                    packet[ihl + 2] = (byte) (c >> 8);
                    packet[ihl + 3] = (byte) c;
                } else if (proto == 17) {  // UDP
                    int port = ((packet[ihl + 2] & 255) << 8) | (packet[ihl + 3] & 255);
                    String payload = new String(packet, ihl + 8, n - ihl - 8);
                    if (payload.startsWith("probe-") || port == 7777) seen.add(payload); else continue;
                    if (port != 7777) continue;  // a probe is only recorded
                    for (int i = 0; i < 2; i++) { byte t = packet[ihl + i]; packet[ihl + i] = packet[ihl + 2 + i]; packet[ihl + 2 + i] = t; }
                    packet[ihl + 6] = packet[ihl + 7] = 0;  // no UDP checksum
                } else continue;
                for (int i = 0; i < 4; i++) { byte t = packet[12 + i]; packet[12 + i] = packet[16 + i]; packet[16 + i] = t; }  // swap the addresses
                packet[10] = packet[11] = 0;
                int c = checksum(packet, 0, ihl);
                packet[10] = (byte) (c >> 8);
                packet[11] = (byte) c;
                out.write(packet, 0, n);
            }
        } catch (java.io.IOException closed) { /* the TUN was closed: replaced, revoked or disconnected */ }
    }

    @Override public void onRevoke() {
        System.out.println("[vpn] onRevoke");
        revoked = true;
        try { if (tun != null) tun.close(); } catch (java.io.IOException ignored) { /* closed */ }
        stopSelf();
    }
}
