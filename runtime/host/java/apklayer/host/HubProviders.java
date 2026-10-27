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

    /** Whether the process (a pid that registered with the hub) holds the permission: what a provider in another app asks about its caller. */
    int check(int pid, String permission) {
        HubDirectory.Peer peer = directory.peerByPid(pid);
        return peer != null && packages.holds(peer.name(), permission) ? 0 : -1;
    }
}
