package apklayer.host;

import android.content.pm.ProviderInfo;
import android.os.IBinder;
import java.util.List;

/** Content providers across apps: which authority lives in which package, and the real IContentProvider binder of a provider in its own process. */
final class HubProviders {
    private final HubDirectory directory;
    private final HubPackages packages;
    private final HubBroadcasts processes;
    HubProviders(HubDirectory directory, HubPackages packages, HubBroadcasts processes) { this.directory = directory; this.packages = packages; this.processes = processes; }

    List<ProviderInfo> list() { return packages.providers(); }

    /** The provider's IContentProvider binder, the provider's process started when it is not running. Null when the authority is unknown or not for this caller. */
    IBinder get(HubDirectory.Peer caller, String authority) {
        ProviderInfo info = packages.provider(authority);
        if (info == null) return null;
        if (!info.exported && !info.packageName.equals(caller.name())) {
            System.out.println("[apklayer-hub] " + caller.name() + " cannot reach the private provider " + authority);
            throw new SecurityException("Provider " + authority + " is not exported");
        }
        HubDirectory.Peer target = processes.process(info.packageName);
        if (target == null) throw new IllegalStateException("Cannot start " + info.packageName + " for provider " + authority);
        return HubProtocol.call(target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_GET_PROVIDER, request -> request.writeString(authority),
                reply -> reply.readStrongBinder());
    }

    /** A content change, told to every other app that is running: each has its own observers, registered in its ContentResolver. */
    void notifyChange(HubDirectory.Peer caller, android.net.Uri uri, int flags) {
        for (HubDirectory.Peer peer : directory.peersExcept(caller)) {
            try {
                HubProtocol.call(peer.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_NOTIFY, request -> { request.writeTypedObject(uri, 0); request.writeInt(flags); }, reply -> null);
            } catch (RuntimeException gone) { System.out.println("[apklayer-hub] cannot notify " + peer.name() + ": " + gone); }
        }
    }

    private record Grant(String target, android.net.Uri uri, int mode) {}
    private final java.util.List<Grant> grants = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** grantUriPermission / revokeUriPermission: only the app that owns the uri's provider may (the target null means every app, for revoke). */
    void grant(HubDirectory.Peer caller, String target, android.net.Uri uri, int mode, boolean revoke) {
        ProviderInfo info = uri.getAuthority() == null ? null : packages.provider(uri.getAuthority());
        if (info == null || !info.packageName.equals(caller.name())) throw new SecurityException(caller.name() + " does not own the provider of " + uri);
        if (revoke) grants.removeIf(g -> (target == null || g.target.equals(target)) && covers(uri, g.uri, true));
        else grants.add(new Grant(target, uri, mode));
    }

    private static boolean covers(android.net.Uri granted, android.net.Uri wanted, boolean prefix) {
        return granted.equals(wanted) || (prefix && granted.getAuthority().equals(wanted.getAuthority()) && wanted.getPath() != null && granted.getPath() != null && wanted.getPath().startsWith(granted.getPath()));
    }

    /** Whether the package holds a grant on the uri for the mode (1 read, 2 write; a grant made with FLAG_GRANT_PREFIX_URI_PERMISSION (0x80) covers the paths below it). */
    boolean granted(String pkg, android.net.Uri uri, int mode) {
        for (Grant g : grants) if (g.target.equals(pkg) && (g.mode & mode) == mode && covers(g.uri, uri, (g.mode & 0x80) != 0)) return true;
        return false;
    }

    /** Whether the process (a pid that registered with the hub) holds the permission: what a provider in another app asks about its caller. */
    int check(int pid, String permission) {
        HubDirectory.Peer peer = directory.peerByPid(pid);
        return peer != null && packages.holds(peer.name(), permission) ? 0 : -1;
    }
}
