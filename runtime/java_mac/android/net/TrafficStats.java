package android.net;

import java.net.Socket;
import java.net.DatagramSocket;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * android.net.TrafficStats. Per-uid/thread-tag accounting has no macOS
 * analogue (there is no kernel per-uid byte accounting on a single-process
 * desktop host the way Android's xt_qtaguid provides) so those stay
 * UNSUPPORTED (-1) -- the SAME value a real device returns when a statistic
 * is unavailable, so callers degrade correctly (this is the documented
 * contract, not a fake success). The thread-tagging calls remain no-ops for
 * the same reason. JetpackJoyride and others reference this class at init;
 * a missing class threw NoClassDefFoundError.
 *
 * The Total counters and getRxBytes(iface)/getTxBytes(iface) ARE answerable
 * on macOS: BSD's ifnet keeps a real cumulative per-interface counter
 * (struct if_data64, confirmed via apple-oss-distributions/xnu
 * bsd/net/if_var.h -- ifi_ibytes/ifi_obytes/ifi_ipackets/ifi_opackets) and
 * "netstat -ib" prints exactly that counter per interface with no extra
 * privilege needed. We shell out to it (cached briefly) rather than
 * fabricate a number.
 */
public class TrafficStats {
    public static final int UNSUPPORTED = -1;
    public static final int UID_TETHERING = -5;

    // netstat -ib emits one summary row per interface (Network column ==
    // "<Link#N>") carrying the real BSD ifnet cumulative counters, PLUS one
    // repeat row per configured address family on that interface (IPv4,
    // IPv6 link-local, ...) echoing the SAME counters -- summing every row
    // would multiply-count. We keep only the "<Link#N>" row per interface.
    // Column layout (confirmed against this host's own `netstat -ib`):
    //   Name Mtu Network [Address] Ipkts Ierrs Ibytes Opkts Oerrs Obytes Coll
    // Address is blank (and so absent from the whitespace-split tokens) for
    // interfaces with no link-layer address, so we index the 7 trailing
    // numeric columns from the END of the line rather than a fixed offset.
    private static final long CACHE_TTL_NANOS = 1_000_000_000L; // 1s
    private static long sCacheTime = Long.MIN_VALUE;
    private static Map<String, long[]> sCache; // iface -> {rxBytes, txBytes, rxPackets, txPackets}

    private static synchronized Map<String, long[]> ifaceCounters() {
        long now = System.nanoTime();
        if (sCache != null && (now - sCacheTime) < CACHE_TTL_NANOS) return sCache;
        Map<String, long[]> counters = new HashMap<>();
        try {
            Process p = new ProcessBuilder("/usr/sbin/netstat", "-ib").start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                boolean header = true;
                while ((line = r.readLine()) != null) {
                    if (header) { header = false; continue; }
                    String[] t = line.trim().split("\\s+");
                    if (t.length < 10 || !t[2].startsWith("<Link#")) continue;
                    String name = t[0];
                    if (name.endsWith("*")) name = name.substring(0, name.length() - 1);
                    try {
                        long ipkts  = Long.parseLong(t[t.length - 7]);
                        long ibytes = Long.parseLong(t[t.length - 5]);
                        long opkts  = Long.parseLong(t[t.length - 4]);
                        long obytes = Long.parseLong(t[t.length - 2]);
                        counters.put(name, new long[]{ ibytes, obytes, ipkts, opkts });
                    } catch (NumberFormatException ignored) {
                        // A row we didn't expect (unit change, extra column
                        // on a future macOS) -- skip it rather than guess.
                    }
                }
            }
            p.waitFor();
        } catch (Exception e) {
            // netstat missing/blocked: fall through with whatever we
            // parsed (possibly nothing); callers see the honest UNSUPPORTED
            // fallback below rather than a fabricated count.
        }
        sCache = counters;
        sCacheTime = now;
        return counters;
    }

    private static long totalOf(int field) {
        long sum = -1;
        for (Map.Entry<String, long[]> e : ifaceCounters().entrySet()) {
            // AOSP's Total* means real network traffic (radios), not the
            // loopback device; loopback would otherwise swamp the total
            // with purely-local inter-process traffic that never touched a
            // network.
            if (e.getKey().startsWith("lo")) continue;
            if (sum < 0) sum = 0;
            sum += e.getValue()[field];
        }
        return sum < 0 ? UNSUPPORTED : sum;
    }

    public static long getTotalRxBytes()    { return totalOf(0); }
    public static long getTotalTxBytes()    { return totalOf(1); }
    public static long getTotalRxPackets()  { return totalOf(2); }
    public static long getTotalTxPackets()  { return totalOf(3); }
    // No cellular radio on a Mac -- same honest UNSUPPORTED a real
    // Wi-Fi-only/no-modem Android device (e.g. a tablet, a TV box) returns.
    public static long getMobileRxBytes()   { return UNSUPPORTED; }
    public static long getMobileTxBytes()   { return UNSUPPORTED; }
    public static long getMobileRxPackets() { return UNSUPPORTED; }
    public static long getMobileTxPackets() { return UNSUPPORTED; }

    // AOSP per-interface counters (e.g. getRxBytes("wlan0")). Real macOS
    // interface names (en0, en1, ...) resolve to real counters; an
    // Android-style name (e.g. "wlan0") that doesn't exist on this host
    // legitimately finds nothing, same as querying a real device for an
    // iface it doesn't have -> UNSUPPORTED.
    private static long ifaceField(String iface, int field) {
        if (iface == null) return UNSUPPORTED;
        long[] v = ifaceCounters().get(iface);
        return v == null ? UNSUPPORTED : v[field];
    }
    public static long getRxBytes(String iface)   { return ifaceField(iface, 0); }
    public static long getTxBytes(String iface)   { return ifaceField(iface, 1); }
    public static long getRxPackets(String iface) { return ifaceField(iface, 2); }
    public static long getTxPackets(String iface) { return ifaceField(iface, 3); }

    public static long getUidRxBytes(int uid)    { return UNSUPPORTED; }
    public static long getUidTxBytes(int uid)    { return UNSUPPORTED; }
    public static long getUidRxPackets(int uid)  { return UNSUPPORTED; }
    public static long getUidTxPackets(int uid)  { return UNSUPPORTED; }
    // AOSP per-uid TCP/UDP byte+segment+packet counters (all deprecated since
    // API 18 but still in the public surface and referenced by older SDKs).
    // No per-uid accounting here -> UNSUPPORTED.
    @Deprecated public static long getUidTcpTxBytes(int uid)    { return UNSUPPORTED; }
    @Deprecated public static long getUidTcpRxBytes(int uid)    { return UNSUPPORTED; }
    @Deprecated public static long getUidUdpTxBytes(int uid)    { return UNSUPPORTED; }
    @Deprecated public static long getUidUdpRxBytes(int uid)    { return UNSUPPORTED; }
    @Deprecated public static long getUidTcpTxSegments(int uid) { return UNSUPPORTED; }
    @Deprecated public static long getUidTcpRxSegments(int uid) { return UNSUPPORTED; }
    @Deprecated public static long getUidUdpTxPackets(int uid)  { return UNSUPPORTED; }
    @Deprecated public static long getUidUdpRxPackets(int uid)  { return UNSUPPORTED; }

    public static void setThreadStatsTag(int tag) {}
    // AOSP getAndSetThreadStatsTag(tag): set the calling thread's stats tag and
    // return whatever it was before. We don't track a tag, so report the
    // "no tag" value (0) as the previous tag while accepting the new one.
    public static int  getAndSetThreadStatsTag(int tag) { return 0; }
    public static int  getThreadStatsTag()        { return 0; }
    public static void clearThreadStatsTag()      {}
    public static void setThreadStatsUid(int uid) {}
    // AOSP getThreadStatsUid(): the uid the calling thread's traffic is
    // attributed to, or -1 when unset. We never set one -> -1.
    public static int  getThreadStatsUid()        { return -1; }
    public static void clearThreadStatsUid()      {}

    // AOSP declares these throwing SocketException / IOException. The throws
    // clause does not change the JVM method descriptor (so binding is
    // unaffected either way), but mirroring it lets call sites that try/catch
    // the checked exception compile exactly as on-device. Tagging only feeds
    // Android's accounting, which does not exist here, so all are no-ops.
    public static void tagSocket(Socket socket) throws java.net.SocketException {}
    public static void untagSocket(Socket socket) throws java.net.SocketException {}
    public static void tagDatagramSocket(DatagramSocket socket) throws java.net.SocketException {}
    public static void untagDatagramSocket(DatagramSocket socket) throws java.net.SocketException {}
    public static void tagFileDescriptor(FileDescriptor fd) throws java.io.IOException {}
    public static void untagFileDescriptor(FileDescriptor fd) throws java.io.IOException {}

    public static void incrementOperationCount(int operationCount) {}
    public static void incrementOperationCount(int tag, int operationCount) {}
}
