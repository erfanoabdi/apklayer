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
    /** A broadcast from the system itself (battery, power): no sender permissions apply. */
    void sendSystem(Intent intent, boolean sticky) {
        Peer system = new Peer(-1, 1000, "android", null, null, true, 1000, null);
        broadcasts.send(system, intent, null, 0, null, null, null, false, sticky, 0);
    }
    record Peer(int pid, int uid, String name, IBinder endpoint, IBinder appThread, boolean headless, int appUid, String instance) {}
    private final HubPackages packages;
    private final Map<Integer, Peer> peers = new HashMap<>();
    private final HubServiceBindings bindings;
    private final HubBroadcasts broadcasts;
    private final HubProviders providers;
    private final HubActivities activities;
    private final HubStartedServices startedServices;
    private final HubPendingIntents pendingIntents;
    private final HubNotifications notifications;
    private final Map<String, Process> appProcesses = new HashMap<>();
    private final Map<String, Process> hosts = new HashMap<>();
    private final Map<String, Integer> failures = new HashMap<>();
    private static final long IDLE_EXIT_MS = 10_000;
    private volatile boolean stopping;
    HubDirectory(Path apps) {
        packages = new HubPackages(apps); bindings = new HubServiceBindings(this, packages); broadcasts = new HubBroadcasts(this, packages); providers = new HubProviders(this, packages, broadcasts); activities = new HubActivities(this, packages); startedServices = new HubStartedServices(this, packages, broadcasts); pendingIntents = new HubPendingIntents(this, broadcasts, activities, startedServices);
        notifications = new HubNotifications(packages, pendingIntents);
        attachInterface(null, HubProtocol.DESCRIPTOR);
        exitWhenIdle(60_000); // nobody ever connected
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopping = true;
            synchronized (hosts) {
                for (Process host : java.util.stream.Stream.concat(hosts.values().stream(), appProcesses.values().stream()).toList()) {
                    host.descendants().forEach(child -> child.destroyForcibly()); host.destroyForcibly();
                }
            }
        }, "hub-component-cleanup"));
    }
    /** The hub ends once nothing is connected or being started (an auto-started hub belongs to the apps that use it). */
    private void exitWhenIdle(long delayMs) {
        Thread timer = new Thread(() -> {
            try { Thread.sleep(delayMs); } catch (InterruptedException interrupted) { return; }
            boolean busy;
            synchronized (peers) { busy = !peers.isEmpty(); }
            synchronized (hosts) { busy |= hosts.values().stream().anyMatch(Process::isAlive); }
            synchronized (appProcesses) { busy |= appProcesses.values().stream().anyMatch(Process::isAlive); }
            if (busy) return;
            System.out.println("[apklayer-hub] idle, exiting");
            System.exit(0);
        }, "hub-idle");
        timer.setDaemon(true); timer.start();
    }
    java.util.List<Peer> peersExcept(Peer one) { synchronized (peers) { return peers.values().stream().filter(peer -> peer != one).toList(); } }
    Peer peerByPid(int pid) { synchronized (peers) { return peers.get(pid); } }
    /** The key of a process: the package, plus "#instance" for an isolated service instance (bindIsolatedService), which runs in a process of its own. */
    static String key(String pkg, String instance) { return instance == null ? pkg : pkg + "#" + instance; }
    static String key(Peer peer) { return key(peer.name, peer.instance); }
    Peer peer(String key) {
        synchronized (peers) { return peers.values().stream().filter(peer -> key(peer).equals(key)).findFirst().orElse(null); }
    }
    private Peer caller() {
        synchronized (peers) {
            Peer peer = peers.get(Binder.getCallingPid());
            if (peer == null || peer.uid != Binder.getCallingUid()) throw new SecurityException("Unregistered calling process");
            return peer;
        }
    }
    /** The package's process that shows activities (not a headless component host). */
    Peer appPeer(String name) {
        synchronized (peers) { return peers.values().stream().filter(peer -> peer.name.equals(name) && !peer.headless).findFirst().orElse(null); }
    }
    /** Starts the package as an app (window and all), as "apklayer run" does. */
    void launchApp(String name) {
        if (stopping) return;
        synchronized (appProcesses) {
            Process old = appProcesses.get(name);
            if (old != null && old.isAlive()) return;
            try {
                Path log = Path.of(System.getenv().getOrDefault("APKLAYER_LOGS", System.getProperty("user.home") + "/.local/state/apklayer/default"), "app-" + name + ".log");
                Files.createDirectories(log.getParent());
                ProcessBuilder command = new ProcessBuilder(System.getProperty("apklayer.cli"), "run", name).redirectErrorStream(true).redirectOutput(log.toFile());
                Process process = command.start(); appProcesses.put(name, process);
                System.out.println("[apklayer-hub] starting app " + name + " launcherPid=" + process.pid());
                process.onExit().thenAccept(exited -> { synchronized (appProcesses) { appProcesses.remove(name, process); } });
            } catch (IOException failure) { throw new IllegalStateException("Cannot launch app " + name, failure); }
        }
    }
    void launch(String name) {
        if (stopping || peer(name) != null) return;
        int hash = name.indexOf('#');
        String pkg = hash < 0 ? name : name.substring(0, hash), instance = hash < 0 ? null : name.substring(hash + 1);
        synchronized (hosts) {
            Process old = hosts.get(name);
            if (old != null && old.isAlive()) return;
            try {
                Path log = Path.of(System.getenv().getOrDefault("APKLAYER_LOGS", System.getProperty("user.home") + "/.local/state/apklayer/default"), "component-" + name.replace('#', '-') + ".log");
                Files.createDirectories(log.getParent());
                ProcessBuilder command = new ProcessBuilder(System.getProperty("apklayer.cli"), "component-host", pkg)
                        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
                if (instance != null) command.environment().put("APKLAYER_ISOLATED", instance);
                Process process = command.start(); hosts.put(name, process);
                System.out.println("[apklayer-hub] " + java.time.LocalTime.now() + " starting " + name + " launcherPid=" + process.pid());
                process.onExit().thenAccept(exited -> {
                    System.out.println("[apklayer-hub] host " + name + " exited with " + exited.exitValue());
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
        try { return handle(code, request, reply, flags); }
        catch (RuntimeException failure) { System.out.println("[apklayer-hub] request " + code + " failed: " + failure); throw failure; }
    }
    private boolean handle(int code, Parcel request, Parcel reply, int flags) throws RemoteException {
        if (code < FIRST_CALL_TRANSACTION || code > LAST_CALL_TRANSACTION) return super.onTransact(code, request, reply, flags);
        Thread.currentThread().setContextClassLoader(HubDirectory.class.getClassLoader());
        request.enforceInterface(HubProtocol.DESCRIPTOR);
        if ((flags & FLAG_ONEWAY) != 0 || reply == null) throw new IllegalArgumentException("Hub requests must be synchronous");
        switch (code) {
            case HubProtocol.REGISTER -> {
                String name = request.readString(); IBinder endpoint = request.readStrongBinder(); boolean headless = request.readBoolean(); IBinder appThread = request.readStrongBinder(); int appUid = request.readInt(); String instance = request.readString(); request.enforceNoDataAvail();
                if (packages.get(name) == null || endpoint == null || appThread == null || !HubProtocol.ENDPOINT.equals(endpoint.getInterfaceDescriptor()))
                    throw new SecurityException("Unknown app endpoint");
                int pid = Binder.getCallingPid(), uid = Binder.getCallingUid();
                Peer peer = new Peer(pid, uid, name, endpoint, appThread, headless, appUid, instance);
                synchronized (peers) {
                    Peer old = peers.get(pid);
                    if (old != null) {
                        if (!old.name.equals(name) || !old.endpoint.equals(endpoint)) throw new SecurityException("Process already registered");
                        reply.writeNoException(); reply.writeInt(pid); return true;
                    }
                    endpoint.linkToDeath(() -> remove(peer), 0);
                    peers.put(pid, peer);
                }
                System.out.println("[apklayer-hub] " + java.time.LocalTime.now() + " registered " + name + " pid=" + pid + " uid=" + uid);
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
                int bindFlags = request.readInt(); String instance = request.readString(); request.enforceNoDataAvail();
                boolean accepted = bindings.bind(caller, intent, connection, bindFlags, instance);
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
            case HubProtocol.GRANT_URI -> {
                Peer caller = caller(); String target = request.readString(); android.net.Uri uri = request.readTypedObject(android.net.Uri.CREATOR); int mode = request.readInt();
                boolean revoke = request.readBoolean(); request.enforceNoDataAvail();
                providers.grant(caller, target, uri, mode, revoke);
                reply.writeNoException(); return true;
            }
            case HubProtocol.CHECK_URI -> {
                Peer caller = caller(); android.net.Uri uri = request.readTypedObject(android.net.Uri.CREATOR); int mode = request.readInt(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeInt(providers.granted(caller.name(), uri, mode) ? 0 : -1); return true;
            }
            case HubProtocol.START_ACTIVITY -> {
                Peer caller = caller(); Intent intent = request.readTypedObject(Intent.CREATOR); android.os.ResultReceiver resultTo = request.readTypedObject(android.os.ResultReceiver.CREATOR);
                request.enforceNoDataAvail();
                boolean found = activities.start(caller, intent, resultTo);
                reply.writeNoException(); reply.writeInt(found ? 0 : -1); return true;
            }
            case HubProtocol.START_SERVICE -> {
                Peer caller = caller(); Intent intent = request.readTypedObject(Intent.CREATOR); request.enforceNoDataAvail();
                android.content.ComponentName name = startedServices.start(caller, intent);
                reply.writeNoException(); reply.writeTypedObject(name, 0); return true;
            }
            case HubProtocol.STOP_SERVICE -> {
                Peer caller = caller(); Intent intent = request.readTypedObject(Intent.CREATOR); request.enforceNoDataAvail();
                boolean stopped = startedServices.stop(caller, intent);
                reply.writeNoException(); reply.writeBoolean(stopped); return true;
            }
            case HubProtocol.SERVICE_STOPPED -> {
                Peer caller = caller(); android.content.ComponentName name = request.readTypedObject(android.content.ComponentName.CREATOR); request.enforceNoDataAvail();
                startedServices.stoppedSelf(caller, name);
                reply.writeNoException(); return true;
            }
            case HubProtocol.PI_GET -> {
                Peer caller = caller(); int type = request.readInt(); String pkg = request.readString(), resultWho = request.readString(); int requestCode = request.readInt();
                Intent[] intents = request.createTypedArray(Intent.CREATOR); String[] types = request.createStringArray(); int piFlags = request.readInt(); android.os.Bundle options = request.readBundle();
                request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeStrongBinder(pendingIntents.get(caller, type, pkg, resultWho, requestCode, intents, types, piFlags, options)); return true;
            }
            case HubProtocol.PI_SEND -> {
                caller(); IBinder sender = request.readStrongBinder(); int resultCode = request.readInt(); Intent fillIn = request.readTypedObject(Intent.CREATOR);
                IBinder finished = request.readStrongBinder(); String permission = request.readString(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeInt(pendingIntents.send(sender, resultCode, fillIn, finished, permission)); return true;
            }
            case HubProtocol.PI_CANCEL -> {
                Peer caller = caller(); IBinder sender = request.readStrongBinder(); request.enforceNoDataAvail();
                pendingIntents.cancel(caller, sender);
                reply.writeNoException(); return true;
            }
            case HubProtocol.PI_INFO -> {
                caller(); IBinder sender = request.readStrongBinder(); request.enforceNoDataAvail();
                Object[] info = pendingIntents.info(sender);
                reply.writeNoException(); reply.writeBoolean(info != null);
                if (info != null) { reply.writeString((String) info[0]); reply.writeInt((Integer) info[1]); reply.writeBoolean((Boolean) info[2]); reply.writeInt((Integer) info[3]); }
                return true;
            }
            case HubProtocol.NOTIFY_POST -> {
                Peer caller = caller(); String pkg = request.readString(), tag = request.readString(); int id = request.readInt();
                android.app.Notification notification = request.readTypedObject(android.app.Notification.CREATOR); int importance = request.readInt(); request.enforceNoDataAvail();
                notifications.post(caller, pkg, tag, id, notification, importance);
                reply.writeNoException(); return true;
            }
            case HubProtocol.NOTIFY_CANCEL -> {
                Peer caller = caller(); String tag = request.readString(); int id = request.readInt(); boolean all = request.readBoolean(); request.enforceNoDataAvail();
                notifications.cancel(caller, tag, id, all);
                reply.writeNoException(); return true;
            }
            case HubProtocol.UID_OF_PID -> {
                caller(); int pid = request.readInt(); request.enforceNoDataAvail();
                Peer peer = peerByPid(pid);
                reply.writeNoException(); reply.writeInt(peer == null ? -1 : peer.appUid()); return true;
            }
            case HubProtocol.TAKE_LAUNCH -> {
                Peer caller = caller(); request.enforceNoDataAvail();
                HubActivities.Launch launch = activities.take(caller.name());
                reply.writeNoException(); reply.writeTypedObject(launch == null ? null : launch.intent(), 0); reply.writeTypedObject(launch == null ? null : launch.resultTo(), 0); return true;
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
        startedServices.died(peer);
        exitWhenIdle(IDLE_EXIT_MS);
    }
}
