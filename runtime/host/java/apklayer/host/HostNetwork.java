package apklayer.host;

import android.app.Application;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowNetworkInfo;

/**
 * The host's real connectivity for ConnectivityManager and WifiManager: the interface of the default route (/proc/net/route) is the active network, Wi-Fi when it
 * has a wireless directory in sysfs and Ethernet otherwise, none (offline) without a default route; its LinkProperties come from HostLinkProperties. It is
 * the active network, the stand-ins of Robolectric (mobile, Wi-Fi) are removed. The other up interfaces with an address (Wi-Fi beside Ethernet, a VPN's tun, wg)
 * are further networks in getAllNetworks (own capabilities and LinkProperties; never the active one). Changes come from `ip monitor route address link`
 * (events, no polling) and reach registered network callbacks as onAvailable / onCapabilitiesChanged / onLinkPropertiesChanged / onLost.
 */
final class HostNetwork {
    private HostNetwork() {}

    /** What the host offers right now: the default-route interface, its transport and link, and the other networks; null when offline. */
    record State(String iface, boolean wifi, LinkProperties link, List<Other> others) {
        /** The host's default route is a VPN's TUN (docs/VPN.md): the default network is the VPN, TRANSPORT_VPN without NOT_VPN. */
        boolean vpn() { return VPNS.matcher(iface).matches(); }
    }

    /** A network that is not the default one: {@code transport} is a NetworkCapabilities.TRANSPORT_*. */
    record Other(String iface, int transport, LinkProperties link) {}

    private static final java.util.regex.Pattern NOT_NETWORKS = java.util.regex.Pattern.compile("(lo|docker|veth|br-|virbr|vnet|lxc|cni|flannel|vboxnet|vmnet|waydroid|binder).*");
    private static final java.util.regex.Pattern VPNS = java.util.regex.Pattern.compile("(tun|wg|ppp|tailscale|ipsec).*");

    /** The up interfaces with an address besides {@code main} (an interface in /sys/class/net, not a bridge or container's), as Android lists a Wi-Fi, an Ethernet and a VPN network at once. */
    private static List<Other> others(String main) {
        List<Other> found = new java.util.ArrayList<>();
        String[] names = new File("/sys/class/net").list();
        if (names == null) return found;
        java.util.Arrays.sort(names);
        for (String name : names) {
            if (name.equals(main) || NOT_NETWORKS.matcher(name).matches()) continue;
            try {
                int flags = Integer.decode(Files.readString(Path.of("/sys/class/net/" + name + "/flags")).trim());
                String state = Files.readString(Path.of("/sys/class/net/" + name + "/operstate")).trim();
                if ((flags & 1) == 0 || !(state.equals("up") || state.equals("unknown"))) continue;  // IFF_UP; a tun or dummy has no carrier state ("unknown")
                LinkProperties link = HostLinkProperties.read(name);
                if (link.getLinkAddresses().isEmpty()) continue;
                int transport = VPNS.matcher(name).matches() ? NetworkCapabilities.TRANSPORT_VPN
                        : new File("/sys/class/net/" + name + "/wireless").isDirectory() ? NetworkCapabilities.TRANSPORT_WIFI : NetworkCapabilities.TRANSPORT_ETHERNET;
                found.add(new Other(name, transport, link));
            } catch (IOException | NumberFormatException unreadable) { /* gone while looking */ }
        }
        return found;
    }

    static State read() {
        try {
            List<String> lines = Files.readAllLines(Path.of("/proc/net/route"));
            // the default route: 0.0.0.0/0, or 0.0.0.0/1 (mask 00000080), the more specific half a VPN takes it with (docs/VPN.md): that one wins in the kernel too
            for (String mask : new String[] {"00000080", "00000000"})
                for (String line : lines.subList(Math.min(1, lines.size()), lines.size())) {
                    String[] f = line.trim().split("\\s+");
                    if (f.length > 7 && f[1].equals("00000000") && f[7].equals(mask) && (Integer.parseInt(f[3], 16) & 1) != 0) {  // destination 0.0.0.0, flag UP
                        return new State(f[0], new File("/sys/class/net/" + f[0] + "/wireless").isDirectory(), HostLinkProperties.read(f[0]), others(f[0]));
                    }
                }
        } catch (IOException | NumberFormatException unreadable) { /* offline */ }
        return null;
    }

    static NetworkCapabilities capabilities(State state) { return capabilities(state.vpn() ? NetworkCapabilities.TRANSPORT_VPN : state.wifi() ? NetworkCapabilities.TRANSPORT_WIFI : NetworkCapabilities.TRANSPORT_ETHERNET); }

    static NetworkCapabilities capabilities(int transport) {
        NetworkCapabilities caps = new NetworkCapabilities();
        caps.addTransportType(transport);
        for (int c : new int[] {NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_VALIDATED, NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
                NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED, transport == NetworkCapabilities.TRANSPORT_VPN ? NetworkCapabilities.NET_CAPABILITY_TRUSTED : NetworkCapabilities.NET_CAPABILITY_NOT_VPN, NetworkCapabilities.NET_CAPABILITY_TRUSTED,
                NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING, NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED, NetworkCapabilities.NET_CAPABILITY_FOREGROUND,
                NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED,
                NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED /* NetworkRequest.Builder asks for it by default (API 31): without it no request is satisfied (WorkManager's network constraint) */}) caps.addCapability(c);
        return caps;
    }

    private static State current;

    static synchronized State current() { return current; }

    static void install(Application app) {
        apply(app, read());
        watch(() -> {
            State next = read();
            if (hubSend != null) apply(app, next);  // the hub's main thread does not loop
            else new Handler(Looper.getMainLooper()).post(() -> apply(app, next));
        });
    }

    /** Runs {@code changed} on every route or address event of the host (the hub's job constraints, an app's networks). */
    static void watch(Runnable changed) {
        // the monitor starts on its own thread: a process launch in the app JVM takes ~1.1 s (measured on Parallels), which was on the start-up path
        Thread thread = new Thread(() -> {
            Process monitor;
            try { monitor = new ProcessBuilder(HostProcess.dying("ip", "monitor", "route", "address", "link")).redirectErrorStream(true).start(); }
            catch (IOException noIp) { System.out.println("[apklayer] network: no `ip` tool, the state is read once"); return; }
            try { Runtime.getRuntime().addShutdownHook(new Thread(monitor::destroy)); } catch (IllegalStateException shuttingDown) { monitor.destroy(); return; }
            changed.run();  // a change while the monitor was starting
            try (BufferedReader in = new BufferedReader(new InputStreamReader(monitor.getInputStream()))) {
                while (in.readLine() != null) changed.run();
            } catch (IOException ended) { /* the monitor stopped with the JVM */ }
        }, "apklayer-network");
        thread.setDaemon(true);
        thread.start();
    }

    private static synchronized void apply(Application app, State next) {
        State previous = current;
        if (initialized && java.util.Objects.equals(previous, next)) return;
        if (initialized && hubSend != null)  // as ConnectivityService: registered receivers only (microG's McsService reconnects on it); non-sticky
            hubSend.accept(new android.content.Intent(ConnectivityManager.CONNECTIVITY_ACTION).putExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, next == null)
                    .addFlags(android.content.Intent.FLAG_RECEIVER_REGISTERED_ONLY), false);
        initialized = true;
        current = next;
        ConnectivityManager manager = app.getSystemService(ConnectivityManager.class);
        ShadowConnectivityManager shadow = Shadow.extract(manager);
        shadow.setActiveNetworkInfo(null);  // also the legacy per-type NetworkInfo (Robolectric's connected stand-in mobile)
        shadow.clearAllNetworks();
        if (next == null) {
            shadow.setActiveNetworkInfo(null);
        } else {
            // the network's netId is its type (Robolectric keys the active network by type): 1 or 9, a non-zero network handle
            shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,
                    next.vpn() ? ConnectivityManager.TYPE_VPN : next.wifi() ? ConnectivityManager.TYPE_WIFI : ConnectivityManager.TYPE_ETHERNET, 0, true, NetworkInfo.State.CONNECTED));
            Network network = manager.getActiveNetwork();
            if (network != null) {
                shadow.setNetworkCapabilities(network, capabilities(next));
                shadow.setLinkProperties(network, next.link());
            }
            int handle = FIRST_OTHER;
            for (Other other : next.others()) {  // netIds clear of the types Robolectric's active network takes
                Network extra = new Network(handle++);
                shadow.addNetwork(extra, ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, other.transport() == NetworkCapabilities.TRANSPORT_WIFI ? ConnectivityManager.TYPE_WIFI
                        : other.transport() == NetworkCapabilities.TRANSPORT_VPN ? ConnectivityManager.TYPE_VPN : ConnectivityManager.TYPE_ETHERNET, 0, true, NetworkInfo.State.CONNECTED));
                shadow.setNetworkCapabilities(extra, capabilities(other.transport()));
                shadow.setLinkProperties(extra, other.link());
            }
        }
        WifiManager wifi = app.getSystemService(WifiManager.class);
        if (wifi != null) wifi.setWifiEnabled(next != null && next.wifi());  // deprecated for apps, the shadow still takes it
        Network network = manager.getActiveNetwork();
        // callbacks arrive from the connectivity service's binder thread after the app has started, never inside registerNetworkCallback or startup: posted, as the app's own code expects
        Network lost = previous != null && (next == null || previous.wifi() != next.wifi())
                ? new Network(previous.wifi() ? ConnectivityManager.TYPE_WIFI : ConnectivityManager.TYPE_ETHERNET) : null;
        for (ConnectivityManager.NetworkCallback callback : shadow.getNetworkCallbacks()) {
            if (lost != null) new Handler(Looper.getMainLooper()).post(() -> callback.onLost(lost));
            if (next != null && network != null) new Handler(Looper.getMainLooper()).post(() -> { callback.onAvailable(network); callback.onCapabilitiesChanged(network, capabilities(next)); callback.onLinkPropertiesChanged(network, next.link()); callback.onBlockedStatusChanged(network, false); });
        }
    }

    private static final int FIRST_OTHER = 100;
    private static boolean initialized;
    private static java.util.function.BiConsumer<android.content.Intent, Boolean> hubSend;

    /** In the hub: each change of the host's connection is announced as CONNECTIVITY_ACTION. */
    static synchronized void installHub(java.util.function.BiConsumer<android.content.Intent, Boolean> send) { hubSend = send; }
}
