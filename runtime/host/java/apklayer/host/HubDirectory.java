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
    private static final Peer SYSTEM = new Peer(-1, 1000, "android", null, null, true, 1000, null, "system");
    void sendSystem(Intent intent, boolean sticky) {
        broadcasts.send(SYSTEM, intent, null, 0, null, null, null, false, sticky, 0);
    }
    /** A broadcast from the system to receivers holding a permission (StorageManagerService's internal ACTION_VOLUME_STATE_CHANGED). */
    void sendSystem(Intent intent, String requiredPermission) {
        broadcasts.send(SYSTEM, intent, null, 0, null, null, new String[] {requiredPermission}, false, false, 0);
    }
    /** An ordered broadcast from the system to receivers holding a permission (the boot broadcasts: one receiver, and so one process start, at a time). */
    void sendSystemOrdered(Intent intent, String requiredPermission) {
        broadcasts.send(SYSTEM, intent, null, 0, null, null, new String[] {requiredPermission}, true, false, 0);
    }
    /** An activity the system starts (HubUsb's attach intent). */
    void startSystem(Intent intent) { activities.start(SYSTEM, intent, null); }
    /** instance: what keys the process besides its package (an isolated service instance, a service's own process, an activity's android:process); process: its name. */
    record Peer(int pid, int uid, String name, IBinder endpoint, IBinder appThread, boolean headless, int appUid, String instance, String process) {}
    private final HubPackages packages;
    /** The instance's host settings (set by HubHost once it runs). */
    volatile HubHostSettings hostSettings;
    private final Map<Integer, Peer> peers = new HashMap<>();
    private final HubServiceBindings bindings;
    private final HubBroadcasts broadcasts;
    private final HubProviders providers;
    private final HubActivities activities;
    private final HubStartedServices startedServices;
    private final HubPendingIntents pendingIntents;
    private final HubNotifications notifications;
    private final HubScheduler scheduler;
    private final HubAccounts accounts;
    private final HubInstaller installer;
    private final HubInputMethods inputMethods;
    private final HubBluetooth bluetooth = new HubBluetooth(this);
    private final HubGnss gnss = new HubGnss(this);
    private final HubCallAudio callAudio;
    final HubUsb usb;
    final HubVolumes volumes = new HubVolumes(this);
    private final HubPermissionPrompt permissionPrompt;
    private final Map<String, Process> appProcesses = new HashMap<>();
    private final Map<String, Process> hosts = new HashMap<>();
    private final Map<String, Integer> failures = new HashMap<>();
    static final Path LOGS = Path.of(System.getenv().getOrDefault("APKLAYER_LOGS", System.getProperty("user.home") + "/.local/state/apklayer/default"));
    private final Path logs = LOGS;
    private static final long IDLE_EXIT_MS = 10_000;
    private volatile boolean stopping;
    HubDirectory(Path apps) {
        packages = new HubPackages(apps); bindings = new HubServiceBindings(this, packages); broadcasts = new HubBroadcasts(this, packages); providers = new HubProviders(this, packages, broadcasts); activities = new HubActivities(this, packages, providers); startedServices = new HubStartedServices(this, packages, broadcasts); pendingIntents = new HubPendingIntents(this, broadcasts, activities, startedServices);
        notifications = new HubNotifications(packages, pendingIntents);
        scheduler = new HubScheduler(this, pendingIntents, broadcasts);
        inputMethods = new HubInputMethods(bindings, packages);
        // the instance's own data directory (the volumes' parent): accounts belong to the instance, as /data/system_ce does to a device
        accounts = new HubAccounts(this, bindings, packages, new HubAuthenticators(apps, packages), Path.of(System.getProperty("apklayer.storage")).getParent().resolve("accounts.json"));
        installer = new HubInstaller(this, packages, pendingIntents, Path.of(System.getProperty("apklayer.storage")).getParent());
        usb = new HubUsb(this, packages, apps);
        permissionPrompt = new HubPermissionPrompt(packages);
        callAudio = new HubCallAudio(packages, this);
        attachInterface(null, HubProtocol.DESCRIPTOR);
        exitWhenIdle(60_000); // nobody ever connected
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopping = true;
            java.util.List<ProcessHandle> ending;
            synchronized (hosts) { ending = java.util.stream.Stream.concat(hosts.values().stream(), appProcesses.values().stream()).map(Process::toHandle).toList(); }
            end(ending, 4000);  // a component host saves its due work on SIGTERM (ComponentHost); `apklayer stop` kills the hub after 5 s
        }, "hub-component-cleanup"));
    }
    /** SIGTERM to the processes and their children, SIGKILL to what is left after {@code waitMs}. */
    private static void end(java.util.List<ProcessHandle> ending, long waitMs) {
        for (ProcessHandle process : ending) { process.descendants().forEach(ProcessHandle::destroy); process.destroy(); }
        long deadline = System.nanoTime() + waitMs * 1_000_000L;
        for (ProcessHandle process : ending) {
            try { process.onExit().get(Math.max(0, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS); }
            catch (Exception stuck) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
        }
    }
    /** `apklayer instance autostart <id> on`: the instance's hub (and what it holds: alarms, jobs, started services) stays up with nobody connected. */
    boolean alwaysOn() { return Files.exists(logs.resolve("autostart")); }
    /** The hub ends once nothing is connected or being started (an auto-started hub belongs to the apps that use it). */
    private void exitWhenIdle(long delayMs) {
        Thread timer = new Thread(() -> {
            try { Thread.sleep(delayMs); } catch (InterruptedException interrupted) { return; }
            boolean busy;
            synchronized (hosts) { busy = hosts.values().stream().anyMatch(Process::isAlive); }
            synchronized (appProcesses) { busy |= appProcesses.values().stream().anyMatch(Process::isAlive); }
            busy |= installer.busy();
            busy |= startedServices.any() || alwaysOn();  // a started service (microG's push connection) and an always-on instance keep the hub
            synchronized (peers) {  // decided under the registration lock: a process that registers after this is refused and waits for the next hub
                if (busy || !peers.isEmpty()) return;
                stopping = true;
            }
            System.out.println("[apklayer-hub] idle, exiting");
            System.exit(0);
        }, "hub-idle");
        timer.setDaemon(true); timer.start();
    }
    java.util.List<Peer> peersExcept(Peer one) { synchronized (peers) { return peers.values().stream().filter(peer -> peer != one).toList(); } }
    HubAccounts accounts() { return accounts; }
    /** The host's network or power supply changed (job constraints). */
    void hostChanged() { scheduler.constraintsChanged(); }
    HubProviders providers() { return providers; }
    HubServiceBindings bindings() { return bindings; }
    HubPackages packages() { return packages; }
    HubInstaller installer() { return installer; }
    /** The installer finished its work: a hub that lost its last app meanwhile (an installer that updated itself) ends as it would have. */
    void installerIdle() { exitWhenIdle(IDLE_EXIT_MS); }
    Peer peerByPid(int pid) { synchronized (peers) { return peers.get(pid); } }
    /** A running process of the app with this Android uid (an app and its component hosts share it), or null. */
    Peer peerByUid(int appUid) { synchronized (peers) { return peers.values().stream().filter(peer -> peer.appUid == appUid).findFirst().orElse(null); } }
    /** The key of a process: the package, plus "#instance" for an isolated service instance (bindIsolatedService), which runs in a process of its own. */
    static String key(String pkg, String instance) { return instance == null ? pkg : pkg + "#" + instance; }
    static String key(Peer peer) { return key(peer.name, peer.instance); }
    Peer peer(String key) {
        synchronized (peers) { return peers.values().stream().filter(peer -> key(peer).equals(key)).findFirst().orElse(null); }
    }
    private Peer caller() {
        synchronized (peers) {
            Peer peer = peers.get(Binder.getCallingPid());
            // the caller's uid as registered, or its app uid: the hub resolves that once it hosts a package of its own (HubHost: ShadowHostRealBinder)
            int uid = Binder.getCallingUid();
            if (peer == null || (peer.uid != uid && peer.appUid != uid)) throw new SecurityException("Unregistered calling process");
            return peer;
        }
    }
    private static boolean sameUser(int pid) {
        try { return Files.getAttribute(Path.of("/proc/" + pid), "unix:uid").equals(Files.getAttribute(Path.of("/proc/self"), "unix:uid")); }
        catch (IOException | UnsupportedOperationException gone) { return false; }
    }
    /** After a start or stop: a component host left without services ends now that its reply is in (Services.exitIfIdle). */
    void exitIfIdle(Peer target) {
        if (!target.headless) return;
        try { HubProtocol.call(target.endpoint, HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_EXIT_IF_IDLE, request -> {}, reply -> null); }
        catch (IllegalStateException gone) { /* it may end before this reply */ }
    }
    /** The process that shows activities (not a headless component host) of this key: the package's own, or one of its android:process names. */
    Peer windowPeer(String key) {
        synchronized (peers) { return peers.values().stream().filter(peer -> key(peer).equals(key) && !peer.headless).findFirst().orElse(null); }
    }
    /** Whether the package shows a window or one is starting (a registered window process, or one the hub started that has not ended). */
    boolean windowsUp(String name) {
        synchronized (peers) { if (peers.values().stream().anyMatch(peer -> peer.name.equals(name) && !peer.headless)) return true; }
        synchronized (appProcesses) { return appProcesses.entrySet().stream().anyMatch(e -> (e.getKey().equals(name) || e.getKey().startsWith(name + "#")) && e.getValue().isAlive()); }
    }
    /** The caller's app's running processes (ActivityManager.getRunningAppProcesses; Android lists only the caller's uid). */
    private java.util.List<Peer> processesOf(Peer caller) {
        synchronized (peers) { return peers.values().stream().filter(peer -> peer.appUid == caller.appUid && peer.name.equals(caller.name)).toList(); }
    }
    /** Ends every process of the package (before the installer replaces or removes it, as Android kills the app), and waits for them to go. */
    void stopPackage(String name) {
        java.util.List<ProcessHandle> ending = new java.util.ArrayList<>();
        // the hub's own package (HubHost) is not ended: as a system_server package's, its update takes effect with the next hub
        synchronized (peers) { for (Peer peer : peers.values()) if (peer.name.equals(name) && peer.pid != android.os.Process.myPid()) ProcessHandle.of(peer.pid).ifPresent(ending::add); }
        synchronized (hosts) { hosts.forEach((key, process) -> { if (key.equals(name) || key.startsWith(name + "#")) ending.add(process.toHandle()); }); }
        synchronized (appProcesses) { appProcesses.forEach((key, app) -> { if (key.equals(name) || key.startsWith(name + "#")) ending.add(app.toHandle()); }); }
        end(ending, 10_000);
        if (!ending.isEmpty()) System.out.println("[apklayer-hub] stopped " + name + " (" + ending.size() + " processes)");
    }

    /** Every other running app's package manager learns that the package was installed, updated or removed (before the package broadcasts go out). */
    void packageChanged(String name, boolean removed) {
        for (Peer peer : peersExcept(null)) {
            if (peer.name.equals(name)) continue;
            try { HubProtocol.call(peer.endpoint, HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_PACKAGE_CHANGED, request -> { request.writeString(name); request.writeBoolean(removed); }, reply -> null); }
            catch (IllegalStateException gone) { /* it ended meanwhile */ }
        }
        HubAudioPermissions.feed(packages);  // AudioService follows ACTION_PACKAGE_ADDED/REMOVED the same way
    }

    /** Every running process's StorageManager reads its volumes again and runs its listeners (before the volume's broadcasts go out). */
    void volumeChanged(android.os.storage.VolumeInfo volume, int oldState) {
        for (Peer peer : peersExcept(null)) {
            try { HubProtocol.call(peer.endpoint, HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_VOLUME, request -> { request.writeTypedObject(volume, 0); request.writeInt(oldState); }, reply -> null); }
            catch (IllegalStateException gone) { /* it ended meanwhile */ }
        }
    }

    /** Starts a window process (as "apklayer run" does): the package's, or "<package>#<process>" for an activity's android:process (zygote's fork of a new process). */
    void launchApp(String key) {
        if (stopping) return;
        int hash = key.indexOf('#');
        String name = hash < 0 ? key : key.substring(0, hash), process = hash < 0 ? null : key.substring(hash + 1);
        synchronized (appProcesses) {
            Process old = appProcesses.get(key);
            if (old != null && old.isAlive()) return;
            try {
                Path log = logs.resolve("app-" + (process != null ? process : name) + ".log");
                Files.createDirectories(log.getParent());
                ProcessBuilder command = new ProcessBuilder(System.getProperty("apklayer.cli"), "run", name).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));  // the CLI tees into the same file
                if (process != null) command.environment().put("APKLAYER_PROCESS", process);  // cli/run.cpp: its name, its own window, never the running app's
                Process started = command.start(); appProcesses.put(key, started);
                System.out.println("[apklayer-hub] starting app " + key + " launcherPid=" + started.pid());
                started.onExit().thenAccept(exited -> {
                    synchronized (appProcesses) { appProcesses.remove(key, started); }
                    if (activities.waiting(key)) launchApp(key);  // a launch came while this process was exiting
                    activities.restartIfAlone(name);
                });
            } catch (IOException failure) { throw new IllegalStateException("Cannot launch app " + key, failure); }
        }
    }
    /** A new host for `name` when a binding waits for one and none is registered. A host's end shows three ways in any order (its process exits,
     * its binder dies, a bind delivery to it fails): each asks, and the last finds the old host gone (launch skips one still running). */
    void ensureHost(String name) { if (!stopping && peer(name) == null && bindings.needsHost(name)) launch(name); }
    void launch(String name) {
        if (stopping || peer(name) != null) return;
        int hash = name.indexOf('#');
        String pkg = hash < 0 ? name : name.substring(0, hash), instance = hash < 0 ? null : name.substring(hash + 1);
        if (pkg.equals(System.getProperty("apklayer.package"))) return;  // runs in this JVM (HubHost), never in a host of its own
        synchronized (hosts) {
            Process old = hosts.get(name);
            if (old != null && old.isAlive()) return;
            try {
                Path log = logs.resolve("component-" + name.replace('#', '-') + ".log");
                Files.createDirectories(log.getParent());
                // the launcher takes the instance's spare JVM when one waits (cli/spare.cpp), as zygote forks an app process
                ProcessBuilder command = new ProcessBuilder(System.getProperty("apklayer.cli"), "component-host", pkg)
                        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
                if (instance != null) command.environment().put("APKLAYER_ISOLATED", instance);
                Process process = command.start();
                hosts.put(name, process);
                System.out.println("[apklayer-hub] " + java.time.LocalTime.now() + " starting " + name + " launcherPid=" + process.pid() + " t=" + System.nanoTime() / 1_000_000);
                Process started = process;
                process.onExit().thenAccept(exited -> {
                    System.out.println("[apklayer-hub] host " + name + " exited with " + exited.exitValue());
                    synchronized (hosts) { hosts.remove(name, started); }
                    // or a client asked for it while this one was ending (launch above saw it alive and started none): it waits for a registration
                    if (!stopping && peer(name) == null && (bindings.needsHost(name) || broadcasts.awaitingStart(name))) {
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
                String name = request.readString(); IBinder endpoint = request.readStrongBinder(); boolean headless = request.readBoolean(); IBinder appThread = request.readStrongBinder(); int appUid = request.readInt(); String instance = request.readString(), process = request.readString(); request.enforceNoDataAvail();
                if (packages.get(name) == null || endpoint == null || appThread == null || !HubProtocol.ENDPOINT.equals(endpoint.getInterfaceDescriptor()))
                    throw new SecurityException("Unknown app endpoint");
                int installedUid = packages.uid(name);  // what others know this process by (UID_OF_PID, oneway callers): the installed uid, not one it claims
                int pid = Binder.getCallingPid(), uid = Binder.getCallingUid();
                // the hub's own package (HubHost: system_server's SettingsProvider) runs as the system uid, as on a device
                if (installedUid >= 0 && appUid != installedUid && pid != android.os.Process.myPid()) throw new SecurityException(name + " is uid " + installedUid + ", not " + appUid);
                int kernel = ShadowHostRealBinder.kernelUid(pid);  // sandboxed (cli/sandbox.cpp): the process runs as its app uid, which it cannot choose
                if (kernel >= android.os.Process.FIRST_APPLICATION_UID && kernel <= android.os.Process.LAST_APPLICATION_UID && kernel != installedUid)
                    throw new SecurityException(name + " is uid " + installedUid + ", its process runs as " + kernel);
                Peer peer = new Peer(pid, uid, name, endpoint, appThread, headless, appUid, instance, process);
                synchronized (peers) {
                    if (stopping) throw new IllegalStateException(HubProtocol.EXITING);
                    Peer old = peers.get(pid);
                    if (old != null) {
                        if (!old.name.equals(name) || !old.endpoint.equals(endpoint)) throw new SecurityException("Process already registered");
                        reply.writeNoException(); reply.writeInt(pid); return true;
                    }
                    endpoint.linkToDeath(() -> remove(peer), 0);
                    peers.put(pid, peer);
                }
                System.out.println("[apklayer-hub] " + java.time.LocalTime.now() + " registered " + name + (instance != null ? "#" + instance : "") + " pid=" + pid + " uid=" + uid + " kernel-uid=" + kernel + " t=" + System.nanoTime() / 1_000_000);
                synchronized (failures) { failures.remove(key(peer)); }  // launches are counted per host (package#process): a host that registered did not fail
                HubAudioPermissions.registered(packages, name);  // before the reply: the app's first track finds its package
                HubLockScreen.appStarted();
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
                var list = providers.components();
                reply.writeNoException(); reply.writeInt(list.size());
                for (var component : list) { var info = component.info; reply.writeString(info.authority); reply.writeString(info.packageName);
                    reply.writeString(info.readPermission); reply.writeString(info.writePermission); reply.writeTypedObject(info, 0);
                    reply.writeInt(component.intents.size()); for (var filter : component.intents) reply.writeTypedObject(new android.content.IntentFilter(filter), 0); }
                return true;
            }
            case HubProtocol.INPUT_METHODS -> {  // InputMethodManager's enabled list (apps targeting 34+ may not read ENABLED_INPUT_METHODS)
                caller(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeString(HubBootSettings.inputMethods(packages)); return true;  // what the boot writes: no wait (a host registers while the boot waits for it)
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
                caller(); android.net.Uri uri = request.readTypedObject(android.net.Uri.CREATOR); int mode = request.readInt(), pid = request.readInt(), uid = request.readInt();
                request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeInt(providers.checkUri(pid, uid, uri, mode)); return true;
            }
            case HubProtocol.START_ACTIVITY -> {
                Peer caller = caller(); Intent intent = request.readTypedObject(Intent.CREATOR); android.os.ResultReceiver resultTo = request.readTypedObject(android.os.ResultReceiver.CREATOR);
                request.enforceNoDataAvail();
                boolean found = activities.start(caller, intent, resultTo);
                reply.writeNoException(); reply.writeInt(found ? 0 : -1); return true;
            }
            case HubProtocol.START_INPUT -> {
                Peer caller = caller(); IBinder remote = request.readStrongBinder(); android.view.inputmethod.EditorInfo editor = request.readTypedObject(android.view.inputmethod.EditorInfo.CREATOR);
                request.enforceNoDataAvail();
                inputMethods.startInput(caller, remote, editor);
                reply.writeNoException(); return true;
            }
            case HubProtocol.FINISH_INPUT -> {
                Peer caller = caller(); request.enforceNoDataAvail();
                inputMethods.finishInput(caller, false);
                reply.writeNoException(); return true;
            }
            case HubProtocol.RESOLVE_ACTIVITY -> {
                Peer caller = caller(); Intent intent = request.readTypedObject(Intent.CREATOR); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeInt(activities.resolvable(caller, intent) ? 1 : 0); return true;
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
            case HubProtocol.PI_SEND_FOR_RESULT -> {
                caller(); IBinder sender = request.readStrongBinder(); Intent fillIn = request.readTypedObject(Intent.CREATOR);
                android.os.ResultReceiver resultTo = request.readTypedObject(android.os.ResultReceiver.CREATOR); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeInt(pendingIntents.sendForResult(sender, fillIn, resultTo)); return true;
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
            case HubProtocol.ALARM_SET -> {
                Peer caller = caller(); IBinder operation = request.readStrongBinder(); long delay = request.readLong(), interval = request.readLong(); request.enforceNoDataAvail();
                scheduler.setAlarm(caller, operation, delay, interval); reply.writeNoException(); return true;
            }
            case HubProtocol.ALARM_CANCEL -> {
                Peer caller = caller(); IBinder operation = request.readStrongBinder(); request.enforceNoDataAvail();
                scheduler.cancelAlarm(caller, operation); reply.writeNoException(); return true;
            }
            case HubProtocol.JOB_SCHEDULE -> {
                Peer caller = caller(); android.app.job.JobInfo job = request.readTypedObject(android.app.job.JobInfo.CREATOR); long delay = request.readLong(); request.enforceNoDataAvail();
                scheduler.scheduleJob(caller, job, delay); reply.writeNoException(); return true;
            }
            case HubProtocol.JOB_RETRY -> {
                Peer caller = caller(); int id = request.readInt(); boolean systemStop = request.readBoolean(); request.enforceNoDataAvail();
                scheduler.retryJob(caller, id, systemStop); reply.writeNoException(); return true;
            }
            case HubProtocol.JOB_CANCEL -> {
                Peer caller = caller(); int id = request.readInt(); request.enforceNoDataAvail();
                scheduler.cancelJob(caller, id); reply.writeNoException(); return true;
            }
            case HubProtocol.LIST_PACKAGES -> {
                caller(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeStringList(packages.packageNames()); return true;
            }
            case HubProtocol.GET_PACKAGE -> {
                caller(); String name = request.readString(); request.enforceNoDataAvail();
                reply.writeNoException();
                android.content.pm.PackageInfo info = packages.packageInfo(name);
                reply.writeTypedObject(info, 0);
                if (info != null) for (boolean services : new boolean[] {false, true}) {
                    var filters = packages.filters(name, services);
                    reply.writeInt(filters.size());
                    for (var component : filters.entrySet()) { reply.writeString(component.getKey()); reply.writeTypedList(component.getValue()); }
                }
                return true;
            }
            case HubProtocol.RESTART_ON_DEATH -> {
                Peer caller = caller(); Intent intent = request.readTypedObject(Intent.CREATOR); request.enforceNoDataAvail();
                if (!caller.headless && intent != null && intent.getComponent() != null && intent.getComponent().getPackageName().equals(caller.name)) activities.restartOnDeath(caller, intent);
                reply.writeNoException(); return true;
            }
            case HubProtocol.RUNNING_PROCESSES -> {
                Peer caller = caller(); request.enforceNoDataAvail();
                java.util.List<Peer> own = processesOf(caller);
                reply.writeNoException(); reply.writeInt(own.size());
                for (Peer peer : own) { reply.writeInt(peer.pid); reply.writeString(peer.process != null ? peer.process : peer.name); reply.writeBoolean(peer.headless); }
                return true;
            }
            case HubProtocol.UID_OF_PID -> {
                caller(); int pid = request.readInt(); request.enforceNoDataAvail();
                Peer peer = peerByPid(pid);
                reply.writeNoException(); reply.writeInt(peer == null ? -1 : peer.appUid()); return true;
            }
            case HubProtocol.RELAUNCH -> {  // from the CLI, not a registered peer: the instance's binder device is the boundary, plus the same Linux user
                String name = request.readString(), file = request.readString(); request.enforceNoDataAvail();
                if (!sameUser(Binder.getCallingPid())) throw new SecurityException("Relaunch from another user");
                reply.writeNoException(); reply.writeInt(activities.relaunch(name, file == null || file.isEmpty() ? null : Path.of(file)) ? 0 : 1); return true;
            }
            case HubProtocol.HOST_SETTINGS -> {
                request.enforceNoDataAvail(); HubHostSettings settings = hostSettings;
                reply.writeNoException(); reply.writeBundle(settings == null ? new android.os.Bundle() : settings.current()); return true;
            }
            case HubProtocol.SETTINGS_CHANGED -> {  // from the CLI, as RELAUNCH
                request.enforceNoDataAvail();
                if (!sameUser(Binder.getCallingPid())) throw new SecurityException("Settings change from another user");
                HubHostSettings settings = hostSettings;
                long identity = Binder.clearCallingIdentity();  // written as the system, not as the CLI (the settings provider may run in this JVM: HubHost)
                try {
                    if (settings != null) settings.changed();
                    try { HubBootSettings.writeInputMethods(org.robolectric.RuntimeEnvironment.getApplication().getContentResolver(), packages); }  // `apklayer ime` too
                    catch (RuntimeException unwritten) { System.out.println("[apklayer-hub] input method settings not written: " + unwritten); }
                } finally { Binder.restoreCallingIdentity(identity); }
                reply.writeNoException(); return true;
            }
            case HubProtocol.TAKE_LAUNCH -> {
                Peer caller = caller(); request.enforceNoDataAvail();
                HubActivities.Launch launch = activities.take(caller);
                reply.writeNoException(); reply.writeTypedObject(launch == null ? null : launch.intent(), 0); reply.writeTypedObject(launch == null ? null : launch.resultTo(), 0); return true;
            }
            case HubProtocol.GET_PROVIDER -> {
                Peer caller = caller(); String authority = request.readString(); request.enforceNoDataAvail();
                IBinder provider = providers.get(caller, authority);
                reply.writeNoException(); reply.writeStrongBinder(provider); return true;
            }
            case HubProtocol.INSTALL_COMMIT -> {
                Peer caller = caller();
                java.util.List<String> names = new java.util.ArrayList<>(); java.util.List<android.os.ParcelFileDescriptor> files = new java.util.ArrayList<>();
                for (int n = request.readInt(); n > 0; n--) { names.add(request.readString()); files.add(request.readFileDescriptor()); }
                IBinder status = request.readStrongBinder(); request.enforceNoDataAvail();
                try { installer.commit(caller, names, files, status); }
                catch (IOException failure) { throw new IllegalStateException("Cannot stage the APK", failure); }
                reply.writeNoException(); return true;
            }
            case HubProtocol.INSTALL_SESSION -> {
                try { installer.sessions().transact(caller(), request, reply); }
                catch (IOException failure) { throw new IllegalStateException("Install session: " + failure, failure); }
                return true;
            }
            case HubProtocol.INSTALL_PREAPPROVE -> {
                Peer caller = caller(); int sessionId = request.readInt(); String pkg = request.readString(), label = request.readString(); IBinder status = request.readStrongBinder();
                request.enforceNoDataAvail();
                installer.preapprove(caller, sessionId, pkg, label, status); reply.writeNoException(); return true;
            }
            case HubProtocol.UNINSTALL -> {
                Peer caller = caller(); String pkg = request.readString(); IBinder status = request.readStrongBinder(); boolean now = request.readBoolean(); request.enforceNoDataAvail();
                installer.uninstall(caller, pkg, status, now); reply.writeNoException(); return true;
            }
            case HubProtocol.INSTALL_SOURCE -> {
                Peer caller = caller(); boolean ask = request.readBoolean(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeBoolean(installer.source(caller, ask)); return true;
            }
            case HubProtocol.BLUETOOTH -> {
                Peer caller = caller(); String address = request.readString(), uuid = request.readString(), name = request.readString(); boolean secure = request.readBoolean();
                request.enforceNoDataAvail();
                android.os.ParcelFileDescriptor[] pipes;
                try { pipes = bluetooth.open(caller, address, uuid, name, secure); } catch (IOException failure) { throw new IllegalStateException("Bluetooth: " + failure, failure); }
                reply.writeNoException(); for (var pipe : pipes) { reply.writeTypedObject(pipe, 0); HubBluetooth.close(pipe); }  // the parcel holds its own copy
                return true;
            }
            case HubProtocol.BLUETOOTH_PIN -> {
                Peer caller = caller(); String address = request.readString(), name = request.readString(); int variant = request.readInt(); String key = request.readString(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeString(bluetooth.pin(caller, address, name, variant, key)); return true;
            }
            case HubProtocol.GNSS -> {
                Peer caller = caller(); boolean follow = request.readBoolean(); request.enforceNoDataAvail();
                android.os.ParcelFileDescriptor fixes = null;
                try { if (follow) fixes = gnss.follow(caller); else gnss.died(caller); } catch (IOException failure) { throw new IllegalStateException("GNSS: " + failure, failure); }
                reply.writeNoException(); reply.writeTypedObject(fixes, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE); return true;
            }
            case HubProtocol.NOTIFY_CHANNELS -> {
                Peer caller = caller(); HubNotificationChannels.call(caller.name(), request.readInt(), request, reply); return true;
            }
            case HubProtocol.USB_PERMISSION -> {
                Peer caller = caller(); String device = request.readString(); boolean ask = request.readBoolean(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeBoolean(usb.permission(caller.name(), device, ask)); return true;
            }
            case HubProtocol.PERMISSIONS_REQUEST -> {
                Peer caller = caller(); String[] names = request.createStringArray(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeIntArray(permissionPrompt.request(caller.name(), names)); return true;
            }
            case HubProtocol.ROLE_REQUEST -> {
                Peer caller = caller(); String role = request.readString(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeBoolean(permissionPrompt.role(caller.name(), role)); return true;
            }
            case HubProtocol.AUDIO_MODE -> {
                Peer caller = caller(); int mode = request.readInt(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeInt(callAudio.setMode(caller, mode)); return true;
            }
            case HubProtocol.AUDIO_COMMUNICATION_DEVICE -> {
                Peer caller = caller(); boolean set = request.readBoolean();
                android.media.AudioDeviceAttributes device = request.readTypedObject(android.media.AudioDeviceAttributes.CREATOR); request.enforceNoDataAvail();
                if (set) callAudio.setCommunicationDevice(caller, device);
                reply.writeNoException(); reply.writeTypedObject(callAudio.communicationDevice(), 0); return true;
            }
            case HubProtocol.MICROPHONE_MUTE -> {
                Peer caller = caller(); boolean on = request.readBoolean(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeBoolean(callAudio.setMicrophoneMute(caller, on)); return true;
            }
            case HubProtocol.CONFIRM_CREDENTIAL -> {
                Peer caller = caller(); String title = request.readString(), description = request.readString(); long challenge = request.readLong(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeBoolean(HubLockScreen.confirm(caller.name(), title, description, challenge)); return true;
            }
            case HubProtocol.CHECK_PERMISSION -> {
                caller(); int pid = request.readInt(), uid = request.readInt(); String permission = request.readString(); request.enforceNoDataAvail();
                reply.writeNoException(); reply.writeInt(providers.check(pid, uid, permission)); return true;
            }
            default -> { return super.onTransact(code, request, reply, flags); }
        }
    }
    /** A call to the peer's endpoint found it dead before its death notice came (linkToDeath runs on a binder thread): the same as the notice. */
    void endpointDied(Peer peer) { remove(peer); }

    private void remove(Peer peer) {
        synchronized (peers) { if (!peers.remove(peer.pid, peer)) return; }
        System.out.println("[apklayer-hub] died " + peer.name + " pid=" + peer.pid);
        inputMethods.finishInput(peer, true);
        bindings.died(peer);
        broadcasts.died(peer);
        activities.died(peer);
        activities.restartIfAlone(peer.name);
        startedServices.died(peer);
        gnss.died(peer);
        callAudio.died(peer);
        exitWhenIdle(IDLE_EXIT_MS);
    }
}
