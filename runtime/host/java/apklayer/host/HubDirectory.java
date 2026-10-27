package apklayer.host;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.nio.file.Files;
import android.content.Intent;
import android.app.IServiceConnection;

/** Instance-owned process directory. Identities come from the kernel Binder caller. */
final class HubDirectory extends Binder {
    record Peer(int pid, int uid, String name, IBinder endpoint, IBinder appThread) {}
    private final HubPackages packages;
    private final Map<Integer, Peer> peers = new HashMap<>();
    private final HubServiceBindings bindings;
    private final HubBroadcasts broadcasts;
    private final HubProviders providers;
    private final Map<String, Process> hosts = new HashMap<>();
    private final Map<String, Integer> failures = new HashMap<>();
    private volatile boolean stopping;
    HubDirectory(Path apps) {
        packages = new HubPackages(apps); bindings = new HubServiceBindings(this, packages); broadcasts = new HubBroadcasts(this, packages); providers = new HubProviders(this, packages, broadcasts);
        attachInterface(null, HubProtocol.DESCRIPTOR);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopping = true;
            synchronized (hosts) {
                for (Process host : hosts.values()) {
                    host.descendants().forEach(child -> child.destroyForcibly()); host.destroyForcibly();
                }
            }
        }, "hub-component-cleanup"));
    }
    java.util.List<Peer> peersExcept(Peer one) { synchronized (peers) { return peers.values().stream().filter(peer -> peer != one).toList(); } }
    Peer peerByPid(int pid) { synchronized (peers) { return peers.get(pid); } }
    Peer peer(String name) {
        synchronized (peers) { return peers.values().stream().filter(peer -> peer.name.equals(name)).findFirst().orElse(null); }
    }
    private Peer caller() {
        synchronized (peers) {
            Peer peer = peers.get(Binder.getCallingPid());
            if (peer == null || peer.uid != Binder.getCallingUid()) throw new SecurityException("Unregistered calling process");
            return peer;
        }
    }
    void launch(String name) {
        if (stopping || peer(name) != null) return;
        synchronized (hosts) {
            Process old = hosts.get(name);
            if (old != null && old.isAlive()) return;
            try {
                Path log = Path.of(System.getenv().getOrDefault("APKLAYER_LOGS", "/tmp/apklayer-stack"), "component-" + name + ".log");
                Files.createDirectories(log.getParent());
                ProcessBuilder command = new ProcessBuilder(System.getProperty("apklayer.cli"), "component-host", name)
                        .redirectErrorStream(true).redirectOutput(log.toFile());
                command.environment().put("APKLAYER_JOPTS", "-Dapklayer.realbinder=true -Dapklayer.instancehub=true");
                Process process = command.start(); hosts.put(name, process);
                System.out.println("[apklayer-hub] starting " + name + " launcherPid=" + process.pid());
                process.onExit().thenAccept(exited -> {
                    synchronized (hosts) { hosts.remove(name, process); }
                    if (!stopping && peer(name) == null && bindings.needsHost(name)) {
                        int failed;
                        synchronized (failures) { failed = failures.merge(name, 1, Integer::sum); }
                        if (failed < 3) launch(name); else bindings.launchFailed(name);
                    }
                });
            } catch (IOException failure) { throw new IllegalStateException("Cannot launch service host " + name, failure); }
        }
    }
    @Override protected boolean onTransact(int code, Parcel request, Parcel reply, int flags) throws RemoteException {
        if (code < FIRST_CALL_TRANSACTION || code > LAST_CALL_TRANSACTION) return super.onTransact(code, request, reply, flags);
        Thread.currentThread().setContextClassLoader(HubDirectory.class.getClassLoader());
        request.enforceInterface(HubProtocol.DESCRIPTOR);
        if ((flags & FLAG_ONEWAY) != 0 || reply == null) throw new IllegalArgumentException("Hub requests must be synchronous");
        switch (code) {
            case HubProtocol.REGISTER -> {
                String name = request.readString(); IBinder endpoint = request.readStrongBinder(); IBinder appThread = request.readStrongBinder(); request.enforceNoDataAvail();
                if (packages.get(name) == null || endpoint == null || appThread == null || !HubProtocol.ENDPOINT.equals(endpoint.getInterfaceDescriptor()))
                    throw new SecurityException("Unknown app endpoint");
                int pid = Binder.getCallingPid(), uid = Binder.getCallingUid();
                Peer peer = new Peer(pid, uid, name, endpoint, appThread);
                synchronized (peers) {
                    Peer old = peers.get(pid);
                    if (old != null) {
                        if (!old.name.equals(name) || !old.endpoint.equals(endpoint)) throw new SecurityException("Process already registered");
                        reply.writeNoException(); reply.writeInt(pid); return true;
                    }
                    endpoint.linkToDeath(() -> remove(peer), 0);
                    peers.put(pid, peer);
                }
                System.out.println("[apklayer-hub] registered " + name + " pid=" + pid + " uid=" + uid);
                synchronized (failures) { failures.remove(name); }
                bindings.registered(peer);
                broadcasts.registered(peer);
                reply.writeNoException(); reply.writeInt(pid); return true;
            }
            case HubProtocol.STATUS -> {
                request.enforceNoDataAvail(); reply.writeNoException(); reply.writeInt(android.os.Process.myPid());
                synchronized (peers) { reply.writeInt(peers.size()); }
                return true;
            }
            case HubProtocol.BIND -> {
                Peer caller = caller(); Intent intent = request.readTypedObject(Intent.CREATOR);
                IServiceConnection connection = IServiceConnection.Stub.asInterface(request.readStrongBinder());
                int bindFlags = request.readInt(); request.enforceNoDataAvail();
                boolean accepted = bindings.bind(caller, intent, connection, bindFlags);
                reply.writeNoException(); reply.writeBoolean(accepted); return true;
            }
            case HubProtocol.UNBIND -> {
                Peer caller = caller(); IBinder connection = request.readStrongBinder(); request.enforceNoDataAvail();
                bindings.unbind(caller, connection); reply.writeNoException(); return true;
            }
            case HubProtocol.REGISTER_RECEIVER -> {
                Peer caller = caller(); IBinder receiver = request.readStrongBinder();
                android.content.IntentFilter filter = request.readTypedObject(android.content.IntentFilter.CREATOR);
                String permission = request.readString(); int receiverFlags = request.readInt(); request.enforceNoDataAvail();
                Intent stickyMatch = broadcasts.register(caller, receiver, filter, permission, receiverFlags);
                reply.writeNoException(); reply.writeTypedObject(stickyMatch, 0); return true;
            }
            case HubProtocol.UNREGISTER_RECEIVER -> {
                Peer caller = caller(); IBinder receiver = request.readStrongBinder(); request.enforceNoDataAvail();
                broadcasts.unregister(caller, receiver); reply.writeNoException(); return true;
            }
            case HubProtocol.BROADCAST -> {
                Peer caller = caller(); Intent intent = request.readTypedObject(Intent.CREATOR); IBinder resultTo = request.readStrongBinder();
                int resultCode = request.readInt(); String resultData = request.readString(); android.os.Bundle resultExtras = request.readBundle();
                String[] required = request.createStringArray(); boolean ordered = request.readBoolean(), sticky = request.readBoolean(); int userId = request.readInt();
                request.enforceNoDataAvail();
                int status = broadcasts.send(caller, intent, resultTo, resultCode, resultData, resultExtras, required, ordered, sticky, userId);
                reply.writeNoException(); reply.writeInt(status); return true;
            }
            case HubProtocol.FINISH_RECEIVER -> {
                Peer caller = caller(); IBinder who = request.readStrongBinder(); int resultCode = request.readInt(); String resultData = request.readString();
                android.os.Bundle resultExtras = request.readBundle(); boolean abort = request.readBoolean(); request.enforceNoDataAvail();
                broadcasts.finish(caller, who, resultCode, resultData, resultExtras, abort); reply.writeNoException(); return true;
            }
            case HubProtocol.UNBROADCAST -> {
                Peer caller = caller(); Intent intent = request.readTypedObject(Intent.CREATOR); request.enforceNoDataAvail();
                broadcasts.removeSticky(caller, intent); reply.writeNoException(); return true;
            }
            case HubProtocol.LIST_PROVIDERS -> {
                caller(); request.enforceNoDataAvail();
                var list = providers.list();
                reply.writeNoException(); reply.writeInt(list.size());
                for (var info : list) { reply.writeString(info.authority); reply.writeString(info.packageName);
                    reply.writeString(info.readPermission); reply.writeString(info.writePermission); }
                return true;
            }
            case HubProtocol.NOTIFY_CHANGE -> {
                Peer caller = caller(); android.net.Uri uri = request.readTypedObject(android.net.Uri.CREATOR); int changeFlags = request.readInt(); request.enforceNoDataAvail();
                providers.notifyChange(caller, uri, changeFlags);
                reply.writeNoException(); return true;
            }
            case HubProtocol.GET_PROVIDER -> {
                Peer caller = caller(); String authority = request.readString(); request.enforceNoDataAvail();
                IBinder provider = providers.get(caller, authority);
                reply.writeNoException(); reply.writeStrongBinder(provider); return true;
            }
            case HubProtocol.CHECK_PERMISSION -> {
                caller(); int pid = request.readInt(); String permission = request.readString(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeInt(providers.check(pid, permission)); return true;
            }
            default -> { return super.onTransact(code, request, reply, flags); }
        }
    }
    private void remove(Peer peer) {
        synchronized (peers) { if (!peers.remove(peer.pid, peer)) return; }
        System.out.println("[apklayer-hub] died " + peer.name + " pid=" + peer.pid);
        bindings.died(peer);
        broadcasts.died(peer);
    }
}
