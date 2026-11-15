package apklayer.host;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * What changes the host's network for the VPN (docs/VPN.md): a persistent TUN device owned by the user, with the app's addresses, routes and DNS. Two ways,
 * the first that works: NetworkManager (`nmcli`, which is its D-Bus API: polkit lets an active local user add and activate a connection without a password;
 * the connection is in memory only, "save no"), else the polkit helper `apklayer-vpn-helper` run by pkexec (asks the user's password). The device's
 * descriptor is then opened by the guard (HubVpnGuard) without any privilege.
 */
final class HubVpnNet {
    private HubVpnNet() {}

    /** What the TUN is made of; routes and addresses are CIDR texts ("10.9.0.2/32", "fd00::2/64"), the default route already split into halves. */
    record Spec(String ifname, String connection, int uid, int mtu, List<String> addresses, List<String> routes, List<String> dns, List<String> search, boolean everything) {}

    /** The VPN made: how, the command the guard runs to remove it ({@code teardown}, empty for the helper, which ends with its pipe). */
    record Made(String how, List<String> teardown, Process helper) {
        void close() {
            if (helper == null) return;
            try { helper.getOutputStream().close(); } catch (IOException ignored) { /* gone */ }
            try { if (!helper.waitFor(10, TimeUnit.SECONDS)) helper.destroy(); } catch (InterruptedException interrupted) { helper.destroy(); }
        }
    }

    private static boolean v6(String cidr) { return cidr.indexOf(':') >= 0; }

    private static List<String> only(List<String> all, boolean v6) {
        List<String> found = new ArrayList<>();
        for (String s : all) if (v6(s) == v6) found.add(s);
        return found;
    }

    /** The command's output (stdout and stderr) and, in {@code status[0]}, its exit status. */
    private static String run(int[] status, List<String> command) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getOutputStream().close();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        try { status[0] = process.waitFor(); } catch (InterruptedException interrupted) { process.destroy(); throw new IOException("interrupted"); }
        return out;
    }

    static Made create(Spec spec) throws IOException {
        String why;
        try { return viaNetworkManager(spec); } catch (IOException failure) { why = failure.getMessage(); }
        System.out.println("[apklayer-hub] vpn: NetworkManager: " + why);
        Path helper = Path.of(System.getProperty("apklayer.cli")).resolveSibling("apklayer-vpn-helper");
        if (!Files.isExecutable(helper)) throw new IOException("no NetworkManager (" + why + ") and no polkit helper at " + helper);
        try { return viaHelper(spec, helper); }
        catch (IOException failure) { throw new IOException("NetworkManager: " + why + "; polkit helper: " + failure.getMessage(), failure); }
    }

    private static Made viaNetworkManager(Spec spec) throws IOException {
        List<String> add = new ArrayList<>(List.of("nmcli", "--wait", "15", "connection", "add", "save", "no", "type", "tun", "con-name", spec.connection(), "ifname", spec.ifname(),
                "autoconnect", "no", "mode", "tun", "owner", String.valueOf(spec.uid())));
        List<String> a4 = only(spec.addresses(), false), a6 = only(spec.addresses(), true);
        add.addAll(List.of("ipv4.method", a4.isEmpty() ? "disabled" : "manual", "ipv6.method", a6.isEmpty() ? "disabled" : "manual"));
        if (!a4.isEmpty()) add.addAll(List.of("ipv4.addresses", String.join(",", a4), "ipv4.route-metric", "50"));
        if (!a6.isEmpty()) add.addAll(List.of("ipv6.addresses", String.join(",", a6), "ipv6.route-metric", "50"));
        List<String> r4 = only(spec.routes(), false), r6 = only(spec.routes(), true);
        if (!r4.isEmpty() && !a4.isEmpty()) add.addAll(List.of("ipv4.routes", String.join(",", r4)));
        if (!r6.isEmpty() && !a6.isEmpty()) add.addAll(List.of("ipv6.routes", String.join(",", r6)));
        for (boolean six : new boolean[] {false, true}) {
            List<String> servers = new ArrayList<>();
            for (String d : spec.dns()) if ((d.indexOf(':') >= 0) == six) servers.add(d);
            String p = six ? "ipv6." : "ipv4.";
            if (!servers.isEmpty() && !only(spec.addresses(), six).isEmpty()) {
                add.addAll(List.of(p + "dns", String.join(",", servers)));
                // NetworkManager hands a link's servers to systemd-resolved only with a domain: the app's search domains, and "~." (every name) when the VPN takes everything
                List<String> domains = new ArrayList<>(spec.search());
                if (spec.everything()) domains.add("~.");
                if (!domains.isEmpty()) add.addAll(List.of(p + "dns-search", String.join(",", domains)));
                if (spec.everything()) add.addAll(List.of(p + "dns-priority", "-1"));  // the VPN's resolver alone, as Android's
            }
        }
        int[] status = new int[1];
        String out = run(status, add);
        if (status[0] != 0) throw new IOException(out);
        List<String> delete = List.of("nmcli", "connection", "delete", "id", spec.connection());
        out = run(status, List.of("nmcli", "--wait", "15", "connection", "up", "id", spec.connection()));
        if (status[0] != 0) { run(status, delete); throw new IOException(out); }
        return new Made("NetworkManager", delete, null);
    }

    /** `pkexec apklayer-vpn-helper`: the spec on its stdin, "ready" back; it removes everything when its stdin closes. The device's owner is the caller (PKEXEC_UID). */
    private static Made viaHelper(Spec spec, Path helper) throws IOException {
        Process process = new ProcessBuilder("pkexec", helper.toString()).redirectErrorStream(true).start();
        OutputStream in = process.getOutputStream();
        StringBuilder text = new StringBuilder("ifname " + spec.ifname() + "\nmtu " + spec.mtu() + "\n");
        for (String a : spec.addresses()) text.append("addr ").append(a).append('\n');
        for (String r : spec.routes()) text.append("route ").append(r).append('\n');
        for (String d : spec.dns()) text.append("dns ").append(d).append('\n');
        text.append("go\n");
        in.write(text.toString().getBytes(StandardCharsets.UTF_8));
        in.flush();
        BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = out.readLine();  // pkexec waits for the person's password here
        if (line == null || !line.equals("ready")) {
            process.destroy();
            throw new IOException(line == null ? "pkexec ended (no polkit agent, or the person declined)" : line);
        }
        Thread drain = new Thread(() -> { try { while (out.readLine() != null) { /* the helper's notes */ } } catch (IOException ended) { /* gone */ } }, "hub-vpn-helper");
        drain.setDaemon(true);
        drain.start();
        return new Made("pkexec helper", List.of(), process);
    }
}
