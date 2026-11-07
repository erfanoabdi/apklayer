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
    List<android.content.pm.PackageParser.Provider> components() { return packages.providerComponents(); }

    /** The provider's IContentProvider binder, the provider's process started when it is not running. Null when the authority is unknown or not for this caller. */
    IBinder get(HubDirectory.Peer caller, String authority) {
        if (caller.appUid() != android.os.Process.SYSTEM_UID) HubBootSettings.awaitBoot();  // as on a device, apps see the settings the system writes at boot
        return find(caller, authority);
    }

    private IBinder find(HubDirectory.Peer caller, String authority) {
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

    /** The provider's binder for the hub itself, the instance's system (uid 1000): what system_server writes at boot (HubBootSettings). */
    IBinder system(String authority) { return find(new HubDirectory.Peer(android.os.Process.myPid(), 1000, "android", null, null, true, 1000, null), authority); }

    /**
     * A data home's first boot: every documents provider is created at once, each in its process (its database too), as a device's first boot has
     * them running before the Files app opens. DocumentsUI loads the providers' roots one after another under one lock and gives up after 5 s
     * (ProvidersCache.FIRST_LOAD_TIMEOUT_MS), then closes when its default root is missing: cold hosts creating their databases took longer.
     */
    void createDocumentProviders() {
        for (android.content.pm.PackageParser.Provider provider : components()) {
            if (provider.intents.stream().noneMatch(filter -> filter.hasAction(android.provider.DocumentsContract.PROVIDER_INTERFACE))) continue;
            String authority = provider.info.authority.split(";")[0];
            Thread create = new Thread(() -> {
                try { system(authority); }
                catch (RuntimeException failed) { System.out.println("[apklayer-hub] first boot: " + authority + " not created: " + failed); }
            }, "hub-first-boot-" + authority);
            create.setDaemon(true);
            create.start();
        }
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

    /**
     * What the activity manager does when an intent carries FLAG_GRANT_READ/WRITE_URI_PERMISSION: the target app gets a grant on each content uri of the data and the
     * clip, if the sender may hand it out (it owns the provider, holds the permission the provider asks for, or holds a grant itself) and the provider allows grants.
     */
    void grantFromIntent(HubDirectory.Peer caller, android.content.Intent intent, String targetPackage) {
        int mode = intent.getFlags() & (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (mode == 0 || targetPackage.equals(caller.name())) return;
        java.util.List<android.net.Uri> uris = new java.util.ArrayList<>();
        if (intent.getData() != null) uris.add(intent.getData());
        android.content.ClipData clip = intent.getClipData();
        for (int i = 0; clip != null && i < clip.getItemCount(); i++) if (clip.getItemAt(i).getUri() != null) uris.add(clip.getItemAt(i).getUri());
        for (android.net.Uri uri : uris) {
            ProviderInfo info = "content".equals(uri.getScheme()) && uri.getAuthority() != null ? packages.provider(uri.getAuthority()) : null;
            if (info == null || !info.grantUriPermissions) continue;
            boolean read = (mode & 1) != 0, write = (mode & 2) != 0;
            boolean allowed = info.packageName.equals(caller.name())
                    || ((!read || granted(caller.name(), uri, 1) || info.readPermission == null || packages.holds(caller.name(), info.readPermission))
                    && (!write || granted(caller.name(), uri, 2) || info.writePermission == null || packages.holds(caller.name(), info.writePermission)));
            if (!allowed) { System.out.println("[apklayer-hub] " + caller.name() + " may not grant " + uri); continue; }
            int bits = (read ? 1 : 0) | (write ? 2 : 0) | ((intent.getFlags() & android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION) != 0 ? 0x80 : 0);
            grants.add(new Grant(targetPackage, uri, bits));
        }
    }

    private static boolean covers(android.net.Uri granted, android.net.Uri wanted, boolean prefix) {
        return granted.equals(wanted) || (prefix && granted.getAuthority().equals(wanted.getAuthority()) && wanted.getPath() != null && granted.getPath() != null && wanted.getPath().startsWith(granted.getPath()));
    }

    /** Whether the package holds a grant on the uri for the mode (1 read, 2 write; a grant made with FLAG_GRANT_PREFIX_URI_PERMISSION (0x80) covers the paths below it). */
    boolean granted(String pkg, android.net.Uri uri, int mode) {
        for (Grant g : grants) if (g.target.equals(pkg) && (g.mode & mode) == mode && covers(g.uri, uri, (g.mode & 0x80) != 0)) return true;
        return false;
    }

    /**
     * Whether another app's caller holds the permission: what a provider or service asks about its binder caller. A synchronous call names the caller by the
     * kernel's pid (a process registered with the hub); a oneway one has none (pid 0, or -1 in an AttributionSource) and names it by the uid its request header
     * carried (ShadowHostRealParcel), which must be the uid of a running app or the hub's own (system_server's 1000: it holds every permission, and its
     * oneway calls to an authenticator are checked for ACCOUNT_MANAGER).
     * Limit: that header is the runtime's (an app's Java code cannot set it through the framework) but a process writing raw binder parcels could claim
     * another running app's uid: every app JVM is the same Linux uid, which also lets one read another's files, so the kernel's sender uid is the boundary only
     * with per-app Linux uids (the per-app sandbox, docs/PLAN.md).
     */
    int check(int pid, int uid, String permission) {
        if (system(pid, uid)) return 0;
        HubDirectory.Peer peer = caller(pid, uid);
        return peer != null && packages.holds(peer.name(), permission) ? 0 : -1;
    }

    /** checkUriPermission(uri, pid, uid, mode) about another app's caller, named as for check: whether it holds a grant on the uri. */
    int checkUri(int pid, int uid, android.net.Uri uri, int mode) {
        if (system(pid, uid)) return 0;
        HubDirectory.Peer peer = caller(pid, uid);
        return peer != null && granted(peer.name(), uri, mode) ? 0 : -1;
    }

    private static boolean system(int pid, int uid) { return pid == android.os.Process.myPid() || (pid <= 0 && uid == android.os.Process.SYSTEM_UID); }
    private HubDirectory.Peer caller(int pid, int uid) { return pid > 0 ? directory.peerByPid(pid) : directory.peerByUid(uid); }
}
