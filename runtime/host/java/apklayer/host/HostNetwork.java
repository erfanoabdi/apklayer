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
 * the one network: Robolectric's stand-in mobile and Wi-Fi networks are removed. Changes come from `ip monitor route address` (events, no polling) and
 * reach registered network callbacks as onAvailable / onCapabilitiesChanged / onLinkPropertiesChanged / onLost.
 */
final class HostNetwork {
    private HostNetwork() {}

    /** What the host offers right now: the default-route interface, its transport and link, or null when offline. */
    record State(String iface, boolean wifi, LinkProperties link) {}

    static State read() {
        try {
            List<String> lines = Files.readAllLines(Path.of("/proc/net/route"));
            for (String line : lines.subList(Math.min(1, lines.size()), lines.size())) {
                String[] f = line.trim().split("\\s+");
                if (f.length > 7 && f[1].equals("00000000") && (Integer.parseInt(f[3], 16) & 1) != 0) {  // destination 0.0.0.0, flag UP
                    return new State(f[0], new File("/sys/class/net/" + f[0] + "/wireless").isDirectory(), HostLinkProperties.read(f[0]));
                }
            }
        } catch (IOException | NumberFormatException unreadable) { /* offline */ }
        return null;
    }

    static NetworkCapabilities capabilities(State state) {
        NetworkCapabilities caps = new NetworkCapabilities();
        caps.addTransportType(state.wifi() ? NetworkCapabilities.TRANSPORT_WIFI : NetworkCapabilities.TRANSPORT_ETHERNET);
        for (int c : new int[] {NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_VALIDATED, NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
                NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED, NetworkCapabilities.NET_CAPABILITY_NOT_VPN, NetworkCapabilities.NET_CAPABILITY_TRUSTED,
                NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING, NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED, NetworkCapabilities.NET_CAPABILITY_FOREGROUND,
                NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED,
                NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED /* NetworkRequest.Builder asks for it by default (API 31): without it no request is satisfied (WorkManager's network constraint) */}) caps.addCapability(c);
        return caps;
    }

    private static State current;

    static synchronized State current() { return current; }

    static void install(Application app) {
        apply(app, read());
        try {
            Process monitor = new ProcessBuilder(HostProcess.dying("ip", "monitor", "route", "address")).redirectErrorStream(true).start();
            Thread thread = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(monitor.getInputStream()))) {
                    while (in.readLine() != null) {
                        State next = read();
                        if (hubSend != null) apply(app, next);  // the hub's main thread does not loop
                        else new Handler(Looper.getMainLooper()).post(() -> apply(app, next));
                    }
                } catch (IOException ended) { /* the monitor stopped with the JVM */ }
            }, "apklayer-network");
            thread.setDaemon(true);
            thread.start();
            Runtime.getRuntime().addShutdownHook(new Thread(monitor::destroy));
        } catch (IOException noIp) { System.out.println("[apklayer] network: no `ip` tool, the state is read once"); }
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
                    next.wifi() ? ConnectivityManager.TYPE_WIFI : ConnectivityManager.TYPE_ETHERNET, 0, true, NetworkInfo.State.CONNECTED));
            Network network = manager.getActiveNetwork();
            if (network != null) {
                shadow.setNetworkCapabilities(network, capabilities(next));
                shadow.setLinkProperties(network, next.link());
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

    private static boolean initialized;
    private static java.util.function.BiConsumer<android.content.Intent, Boolean> hubSend;

    /** In the hub: each change of the host's connection is announced as CONNECTIVITY_ACTION. */
    static synchronized void installHub(java.util.function.BiConsumer<android.content.Intent, Boolean> send) { hubSend = send; }
}
