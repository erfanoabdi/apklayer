package apklayer.host;

import android.net.IVpnManager;
import android.net.LinkAddress;
import android.net.Network;
import android.net.RouteInfo;
import android.net.VpnProfileState;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import com.android.internal.net.LegacyVpnInfo;
import com.android.internal.net.VpnConfig;
import com.android.internal.net.VpnProfile;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "vpn_management" (system_server's VpnManagerService, which apklayer does not run) for the instance (docs/VPN.md): VpnService.prepare() and
 * Builder.establish() of apps. The VPN is host-wide: a persistent TUN on the host with the app's addresses and routes (HubVpnNet: NetworkManager, else the
 * polkit helper), opened by HubVpnGuard and given to the app. One VPN at a time per instance. The hub tracks the owner, session and start, keeps the owner's
 * VpnService bound to call onRevoke, asks the person once per app (HubVpnUi), shows the "VPN is active" notification and writes {@code <logs>/vpn/active}
 * for the CLI. The VPN ends with the owner's descriptor (the guard sees the carrier go), `apklayer vpn disconnect` and the notification's button.
 */
final class HubVpn extends IVpnManager.Stub {
    static final String SERVICE = android.content.Context.VPN_MANAGEMENT_SERVICE;
    private static final String CONTROL_VPN = "android.permission.CONTROL_VPN";
    private static final HubDirectory.Peer SYSTEM = new HubDirectory.Peer(-1, 1000, "android", null, null, true, 1000, null, "system");

    /** The running VPN. */
    private record Active(String owner, int uid, String session, long since, VpnConfig config, String iface, boolean everything, String underlying, HubVpnNet.Made made,
                          android.app.IServiceConnection connection, java.util.concurrent.CountDownLatch protectedOnce) {}

    private final HubDirectory directory;
    private final HubPackages packages;
    private final Path dir;
    private final String instance = System.getenv().getOrDefault("APKLAYER_INSTANCE", "default");
    private final HubVpnGuard guard;
    private final HubVpnUi ui;
    private Active active;
    private int generation;  // alternates the interface and connection name (the old device goes away asynchronously)
    private IBinder ownerService;  // the owner's VpnService binder once the bind answered (onRevoke)

    HubVpn(HubDirectory directory, HubPackages packages) {
        this.directory = directory;
        this.packages = packages;
        this.dir = Path.of(System.getenv("APKLAYER_LOGS"), "vpn");
        this.guard = new HubVpnGuard(dir, this::ownerClosed);
        this.ui = new HubVpnUi(dir, () -> disconnect(true));
        sweep();
    }

    /** What a hub that died left (its connection, the state file): `apklayer vpn sweep`. */
    private void sweep() {
        if (!Files.exists(dir.resolve("active"))) return;  // none was left
        try {
            Process process = new ProcessBuilder(System.getProperty("apklayer.cli"), "vpn", "sweep").redirectErrorStream(true).start();
            process.getOutputStream().close();
            process.getInputStream().readAllBytes();
            process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
        } catch (IOException | InterruptedException failure) { System.out.println("[apklayer-hub] vpn: sweep failed: " + failure); }
        try { Files.deleteIfExists(dir.resolve("active")); } catch (IOException ignored) { /* none */ }
    }

    /** The app of the calling process (registered with the hub: kernel uids are shared outside the sandbox). */
    private String callerPackage() {
        HubDirectory.Peer peer = directory.peerByPid(Binder.getCallingPid());
        return peer == null ? null : peer.name();
    }

    private void requireControl() {
        if (!packages.uidHolds(directory.peerByPid(Binder.getCallingPid()) == null ? -1 : directory.peerByPid(Binder.getCallingPid()).appUid(), CONTROL_VPN)) throw new SecurityException("Requires " + CONTROL_VPN);
    }

    // ---- consent ----

    /** The person's yes, remembered for the app: Android's per-app VPN authorization. */
    boolean consent(String pkg) {
        if (ui.authorized(pkg)) return true;
        if (!ui.ask(pkg)) return false;
        ui.authorize(pkg);
        return true;
    }

    @Override public boolean prepareVpn(String oldPackage, String newPackage, int userId) {
        if (newPackage != null) { requireControl(); ui.authorize(newPackage); return true; }  // prepareAndAuthorize; the dialog is the hub's own (consent)
        String caller = callerPackage();
        if (oldPackage != null && !oldPackage.equals(caller)) requireControl();
        return oldPackage != null && ui.authorized(oldPackage);  // prepare(): null Intent when the person agreed before
    }

    @Override public void setVpnPackageAuthorization(String packageName, int userId, int vpnType) { requireControl(); ui.authorize(packageName); }

    // ---- the VPN ----

    /** The config as CIDR texts; the default route as its two halves (the host's own default route stays: the VPN's halves are the more specific ones). */
    private static HubVpnNet.Spec spec(VpnConfig config, String ifname, String connection, int uid) {
        List<String> addresses = new ArrayList<>();
        Set<String> routes = new LinkedHashSet<>();
        boolean everything = false;
        if (config.addresses != null) for (LinkAddress a : config.addresses) addresses.add(a.getAddress().getHostAddress() + "/" + a.getPrefixLength());
        if (config.routes != null) for (RouteInfo r : config.routes) {
            if (r.getType() != RouteInfo.RTN_UNICAST || r.getDestination() == null) continue;
            String host = r.getDestination().getAddress().getHostAddress();
            int prefix = r.getDestination().getPrefixLength();
            boolean six = host.indexOf(':') >= 0;
            if (prefix == 0) { routes.add(six ? "::/1" : "0.0.0.0/1"); routes.add(six ? "8000::/1" : "128.0.0.0/1"); everything = true; }
            else { routes.add(host + "/" + prefix); everything |= prefix == 1; }
        }
        List<String> dns = new ArrayList<>(), search = new ArrayList<>();
        if (config.dnsServers != null) dns.addAll(config.dnsServers);
        if (config.searchDomains != null) search.addAll(config.searchDomains);
        return new HubVpnNet.Spec(ifname, connection, uid, config.mtu, addresses, new ArrayList<>(routes), dns, search, everything);
    }

    /** The interface the host's own traffic leaves by: the default route (0.0.0.0/0, a VPN interface never counts) of the lowest metric. protect() binds sockets to it. */
    private static String underlying() {
        try {
            List<String> lines = Files.readAllLines(Path.of("/proc/net/route"));
            String best = null;
            long metric = Long.MAX_VALUE;
            for (String line : lines.subList(Math.min(1, lines.size()), lines.size())) {
                String[] f = line.trim().split("\\s+");
                if (f.length < 8 || !f[1].equals("00000000") || !f[7].equals("00000000") || (Integer.parseInt(f[3], 16) & 1) == 0 || f[0].matches("(tun|wg|ppp|tailscale|ipsec).*")) continue;
                if (Long.parseLong(f[6]) < metric) { metric = Long.parseLong(f[6]); best = f[0]; }
            }
            return best;
        } catch (IOException | NumberFormatException none) { return null; }
    }

    @Override public synchronized ParcelFileDescriptor establishVpn(VpnConfig config) {
        String pkg = callerPackage();
        if (pkg == null || !ui.authorized(pkg)) throw new SecurityException("Unauthorized Caller");
        if (config.allowBypass) System.out.println("[apklayer-hub] vpn: allowBypass is ignored: the VPN is host-wide");
        if ((config.allowedApplications != null && !config.allowedApplications.isEmpty()) || (config.disallowedApplications != null && !config.disallowedApplications.isEmpty()))
            System.out.println("[apklayer-hub] vpn: the allowed/disallowed application lists are ignored: the VPN is host-wide (docs/VPN.md)");
        Active old = active;
        boolean sameOwner = old != null && old.owner.equals(pkg);
        String underlying = old != null ? old.underlying : underlying();
        active = null;
        if (old != null) endVpn(old, !sameOwner);  // another app takes over: the old owner is revoked (onRevoke); the same app's old TUN goes before its new one exists
        int uid;
        try { uid = (Integer) Files.getAttribute(Path.of("/proc/self"), "unix:uid"); } catch (IOException failure) { return null; }
        int gen = generation++ & 1;
        String ifname = String.format("tunapk%04x%c", instance.hashCode() & 0xffff, 'a' + gen);
        String connection = "apklayer-vpn-" + instance + "-" + (char) ('a' + gen);
        HubVpnNet.Spec spec = spec(config, ifname, connection, uid);
        HubVpnNet.Made made;
        boolean early = guard.protectedBefore();  // OpenVPN: its transport socket is protected before establish(): the routes come with the device
        try { made = HubVpnNet.create(spec, !early); }
        catch (IOException failure) { System.out.println("[apklayer-hub] vpn: cannot establish for " + pkg + ": " + failure.getMessage()); return null; }
        FileDescriptor tun;
        try { tun = guard.start(ifname, underlying == null ? "-" : underlying, !config.blocking, made.teardown()); }
        catch (IOException failure) {
            System.out.println("[apklayer-hub] vpn: cannot open the TUN for " + pkg + ": " + failure.getMessage());
            guard.close();  // the guard runs the teardown; the helper's ends with its pipe
            made.close();
            return null;
        }
        long since = sameOwner ? old.since : System.currentTimeMillis();
        String session = config.session != null ? config.session : HubInstaller.labelOf(pkg, pkg);
        config.interfaze = ifname;
        config.startTime = since;
        ownerService = null;
        active = new Active(pkg, 0, session, since, config, ifname, spec.everything(), underlying, made, bindOwner(pkg, config.user), new java.util.concurrent.CountDownLatch(early ? 0 : 1));
        writeActive();
        if (!made.lateRoutes().isEmpty()) {
            Active mine = active;
            Thread routes = new Thread(() -> lateRoutes(mine), "hub-vpn-routes");
            routes.setDaemon(true);
            routes.start();
        }
        ui.show(pkg, session);
        ParcelFileDescriptor given;
        try { given = ParcelFileDescriptor.dup(tun); } catch (IOException failure) { return null; }
        closeQuietly(tun);  // only the owner's copy keeps the device: it ends with the owner's descriptor
        System.out.println("[apklayer-hub] vpn: " + pkg + " established " + ifname + " through " + made.how() + (spec.everything() ? " (all traffic)" : "") + ", protect() binds to " + underlying);
        return given;
    }

    /** The routes of a VPN made without them: once the owner protected a socket (WireGuard's Go library sends its first handshake datagram before it does, which
     *  the tunnel would swallow: a 5 s retry), or after a grace of 1.5 s for apps that never protect. */
    private void lateRoutes(Active mine) {
        try { mine.protectedOnce.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException interrupted) { return; }
        synchronized (this) {
            if (active != mine) return;  // the VPN ended meanwhile
            try { mine.made.installRoutes(); } catch (IOException failure) { System.out.println("[apklayer-hub] vpn: routes of " + mine.iface + ": " + failure.getMessage()); }
        }
    }

    private static void closeQuietly(FileDescriptor fd) {
        try { new ParcelFileDescriptor(fd).close(); } catch (IOException ignored) { /* closed */ }
    }

    /** VpnService.protect(): the owner's socket leaves through the host's own interface instead of the VPN. */
    boolean protect(String caller, FileDescriptor socket) {
        Active a;
        synchronized (this) { a = active; }
        if (a != null) {
            boolean done = a.owner.equals(caller) && guard.protect(socket, null);
            if (done) a.protectedOnce.countDown();  // the routes may come now (lateRoutes)
            return done;
        }
        String before = caller != null && ui.authorized(caller) ? underlying() : null;  // before establish(): the host's own interface now
        boolean done = before != null && guard.protect(socket, before);
        System.out.println("[apklayer-hub] vpn: protect before establish() for " + caller + ": " + (done ? "bound to " + before : before == null ? "refused (no consent or no host interface)" : "failed"));
        return done;
    }

    /** The owner's VpnService, bound so that its onRevoke can be called (VpnService.Callback: LAST_CALL_TRANSACTION). */
    private android.app.IServiceConnection bindOwner(String pkg, String serviceClass) {
        android.app.IServiceConnection connection = new android.app.IServiceConnection.Stub() {
            @Override public void connected(android.content.ComponentName name, IBinder service, android.app.IBinderSession session, boolean dead) {
                synchronized (HubVpn.this) { if (service != null) ownerService = service; HubVpn.this.notifyAll(); }
            }
        };
        android.content.Intent intent = new android.content.Intent(VpnConfig.SERVICE_INTERFACE).setComponent(new android.content.ComponentName(pkg, serviceClass));
        if (!directory.bindings().bind(SYSTEM, intent, connection, android.content.Context.BIND_AUTO_CREATE | android.content.Context.BIND_NOT_VISIBLE, null, true)) {
            System.out.println("[apklayer-hub] vpn: cannot bind " + pkg + "/" + serviceClass + " (no onRevoke)");
            return null;
        }
        return connection;
    }

    private void unbind(Active a) {
        if (a.connection != null) directory.bindings().unbind(SYSTEM, a.connection.asBinder());
    }

    /** onRevoke of the owner, if {@code revoke}, then the VPN's end: the guard (it runs the teardown), the helper, the notification. */
    synchronized boolean disconnect(boolean revoke) {
        Active a = active;
        if (a == null) return false;
        active = null;
        endVpn(a, revoke);
        return true;
    }

    private void endVpn(Active a, boolean revoke) {
        if (revoke) revokeOwner(a);
        unbind(a);
        guard.close();
        a.made.close();
        try { Files.deleteIfExists(dir.resolve("active")); } catch (IOException ignored) { /* none */ }
        ui.hide();
        System.out.println("[apklayer-hub] vpn: " + a.owner + " ended");
    }

    private void revokeOwner(Active a) {
        // the bind answers asynchronously: the binder is there when the owner's service started (it did: it established)
        for (int waited = 0; waited < 20 && ownerService == null; waited++) { try { wait(100); } catch (InterruptedException interrupted) { break; } }
        IBinder service = ownerService;
        if (service == null) return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try { service.transact(IBinder.LAST_CALL_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY); }
        catch (android.os.RemoteException gone) { /* it ended */ }
        finally { data.recycle(); reply.recycle(); }
    }

    /** The TUN lost its carrier: the owner closed it or died. The VPN is over; no onRevoke (the owner ended it itself). */
    private void ownerClosed(String event) {
        boolean revoked = event.startsWith("revoked ");
        String iface = event.substring(event.indexOf(' ') + 1);
        synchronized (this) { if (active == null || !active.iface.equals(iface)) return; }
        System.out.println("[apklayer-hub] vpn: " + (revoked ? "the connection of " + iface + " was deleted from outside: onRevoke" : "the owner closed " + iface));
        disconnect(revoked);  // revoked: the owner still thinks the tunnel is up
    }

    private void writeActive() {
        Active a = active;
        String text = "owner=" + a.owner + "\nsession=" + a.session.replace('\n', ' ') + "\nsince=" + a.since + "\niface=" + a.iface + "\ndefault=" + (a.everything ? 1 : 0) + "\n";
        try { Files.createDirectories(dir); Files.writeString(dir.resolve("active"), text, StandardCharsets.UTF_8); }
        catch (IOException failure) { System.out.println("[apklayer-hub] vpn: cannot write the state: " + failure); }
    }

    // ---- the CLI and the manager ----

    /** `apklayer vpn disconnect` and the manager's button: HubProtocol.VPN. */
    boolean disconnectRequest() { return disconnect(true); }

    // ---- the rest of IVpnManager ----

    @Override public boolean addVpnAddress(String address, int prefixLength) { return false; }
    @Override public boolean removeVpnAddress(String address, int prefixLength) { return false; }
    @Override public boolean setUnderlyingNetworksForVpn(Network[] networks) { return true; }  // the owner's protected sockets leave through the host's default interface
    @Override public boolean provisionVpnProfile(VpnProfile profile, String packageName) { return false; }
    @Override public void deleteVpnProfile(String packageName) {}
    @Override public String startVpnProfile(String packageName) { throw new UnsupportedOperationException("platform VPN profiles"); }
    @Override public void stopVpnProfile(String packageName) {}
    @Override public VpnProfileState getProvisionedVpnProfileState(String packageName) { return null; }
    @Override public boolean setAppExclusionList(int userId, String vpnPackage, List<String> excludedApps) { return false; }
    @Override public List<String> getAppExclusionList(int userId, String vpnPackage) { return List.of(); }
    @Override public boolean isAlwaysOnVpnPackageSupported(int userId, String packageName) { return true; }
    @Override public boolean setAlwaysOnVpnPackage(int userId, String packageName, boolean lockdown, List<String> lockdownAllowlist) { requireControl(); return ui.alwaysOn(packageName, lockdown); }
    @Override public String getAlwaysOnVpnPackage(int userId) { return ui.alwaysOnPackage(); }
    @Override public boolean isVpnLockdownEnabled(int userId) { return ui.lockdown(); }
    @Override public List<String> getVpnLockdownAllowlist(int userId) { return List.of(); }
    @Override public boolean isCallerCurrentAlwaysOnVpnApp() { String p = callerPackage(); return p != null && p.equals(ui.alwaysOnPackage()); }
    @Override public boolean isCallerCurrentAlwaysOnVpnLockdownApp() { return isCallerCurrentAlwaysOnVpnApp() && ui.lockdown(); }
    @Override public void startLegacyVpn(VpnProfile profile) { throw new UnsupportedOperationException("legacy VPN profiles"); }
    @Override public LegacyVpnInfo getLegacyVpnInfo(int userId) { return null; }
    @Override public VpnProfile[] getAllLegacyVpns() { return new VpnProfile[0]; }
    @Override public boolean updateLockdownVpn() { return false; }
    @Override public byte[] getFromVpnProfileStore(String name) { return null; }
    @Override public boolean putIntoVpnProfileStore(String name, byte[] blob) { return false; }
    @Override public boolean removeFromVpnProfileStore(String name) { return false; }
    @Override public String[] listFromVpnProfileStore(String prefix) { return new String[0]; }
    @Override public synchronized VpnConfig getVpnConfig(int userId) { return active == null ? null : active.config; }
    @Override public void factoryReset() { disconnect(true); }
}
