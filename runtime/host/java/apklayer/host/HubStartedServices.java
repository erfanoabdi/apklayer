package apklayer.host;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import java.util.HashMap;
import java.util.Map;

/**
 * startService / stopService across apps: the hub resolves and checks the service, runs it in its package's process (started when it is not running) and remembers
 * what is started, so a killed process comes back for the services that asked to be restarted (START_STICKY / START_REDELIVER_INTENT).
 */
final class HubStartedServices {
    private static final int START_STICKY = 1, START_REDELIVER_INTENT = 3;
    private record Started(Intent intent, int result, int pid) {}

    private final HubDirectory directory;
    private final HubPackages packages;
    private final HubBroadcasts broadcasts;
    private final Map<ComponentName, Started> started = new HashMap<>();
    // per target process, in order: starts (and stops) are delivered off the caller's thread, as ActivityManagerService schedules them
    private final Map<String, java.util.concurrent.ExecutorService> queues = new java.util.concurrent.ConcurrentHashMap<>();

    private java.util.concurrent.ExecutorService queue(String key) {
        return queues.computeIfAbsent(key, k -> java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "hub-start-" + k);
            thread.setDaemon(true);
            return thread;
        }));
    }

    HubStartedServices(HubDirectory directory, HubPackages packages, HubBroadcasts broadcasts) { this.directory = directory; this.packages = packages; this.broadcasts = broadcasts; }

    /** Whether a started service is running (or waits to restart). */
    boolean any() { synchronized (started) { return !started.isEmpty(); } }

    /** The started service's name; null when there is no such service. */
    ComponentName start(HubDirectory.Peer caller, Intent intent) {
        ServiceInfo info = packages.service(intent);
        if (info == null) return null;
        if (!caller.name().equals(info.packageName) && !info.exported) throw new SecurityException("Service is not exported");
        packages.enforceServicePermission(caller.name(), info);
        Intent explicit = new Intent(intent).setComponent(new ComponentName(info.packageName, info.name));
        String key = processKey(info);
        directory.noteIsolated(key, info);
        // the caller goes on while the start is delivered (ActivityManagerService schedules it, and queues it until a process that is not running
        // attaches). Waiting here deadlocked Play services twice: its main process starts a service of its ":persistent" process from
        // attachBaseContext while that process, still starting, asks the main one (its gservices provider); and a worker of ":persistent" that
        // holds a lock sends a PendingIntent for a service of its own process, whose main thread waits for that lock
        queue(key).execute(() -> {
            HubDirectory.Peer target = directory.peer(key);
            if (target == null) target = broadcasts.process(key);
            if (target == null) { System.out.println("[apklayer-hub] could not start " + key + " for " + explicit.getComponent().flattenToShortString()); return; }
            try { deliver(target, explicit, explicit); }
            catch (RuntimeException failed) { System.out.println("[apklayer-hub] start of " + explicit.getComponent().flattenToShortString() + " failed: " + failed); }
        });
        return explicit.getComponent();
    }

    /** A service declared in a process of its own (android:process: microG's McsService in ":persistent") runs in that process's host, keyed by
     *  the process's name as a bind keys it (HubServices.ownProcess: services sharing an android:process share it), an isolatedProcess one by the
     *  service; the package's process would refuse it. */
    static String processKey(ServiceInfo info) {
        String process = info.processName != null ? info.processName : info.packageName;
        if ((info.flags & ServiceInfo.FLAG_ISOLATED_PROCESS) != 0) return HubDirectory.key(info.packageName, info.name);
        return HubDirectory.key(info.packageName, process.equals(info.packageName) ? null : process);
    }

    private ComponentName deliver(HubDirectory.Peer target, Intent explicit, Intent delivered) {
        synchronized (started) { started.put(explicit.getComponent(), new Started(explicit, 0, target.pid())); }  // before the call: stopSelf inside onStartCommand removes it
        int[] result = HubProtocol.call(target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_START,
                request -> { request.writeTypedObject(explicit, 0); request.writeTypedObject(delivered, 0); }, reply -> new int[] {reply.readInt(), reply.readInt()});
        directory.exitIfIdle(target);  // a service that stopped itself in onStartCommand may have been the host's last
        synchronized (started) {
            if (result[0] < 0) { started.remove(explicit.getComponent()); return null; }
            if (started.containsKey(explicit.getComponent())) started.put(explicit.getComponent(), new Started(explicit, result[1], target.pid()));
        }
        return explicit.getComponent();
    }

    boolean stop(HubDirectory.Peer caller, Intent intent) {
        ServiceInfo info = packages.service(intent);
        if (info == null) return false;
        if (!caller.name().equals(info.packageName) && !info.exported) throw new SecurityException("Service is not exported");
        packages.enforceServicePermission(caller.name(), info);
        ComponentName name = new ComponentName(info.packageName, info.name);
        // the process that runs it, as for a start: the package's main process does not have a ":persistent" service and sent the stop back here
        // (Play services' stopService of one at BOOT_COMPLETED recursed until a StackOverflowError ended its main process)
        String key = processKey(info);
        // behind the starts queued before it (a stop right after a start must not overtake it); its answer is the service's own, so it is waited for
        java.util.concurrent.Future<Boolean> stopped = queue(key).submit(() -> {
            HubDirectory.Peer target = directory.peer(key);
            synchronized (started) { started.remove(name); }
            if (target == null) return false;
            boolean here = HubProtocol.call(target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_STOP, request -> request.writeTypedObject(new Intent().setComponent(name), 0), reply -> reply.readBoolean());
            directory.exitIfIdle(target);
            return here;
        });
        try { return stopped.get(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
        catch (java.util.concurrent.ExecutionException failure) { throw failure.getCause() instanceof RuntimeException r ? r : new IllegalStateException(failure.getCause()); }
    }

    /** The service stopped itself (stopSelf). */
    void stoppedSelf(HubDirectory.Peer caller, ComponentName name) {
        synchronized (started) { Started record = started.get(name); if (record != null && record.pid() == caller.pid()) started.remove(name); }
    }

    /** A process died: its sticky services start again (redelivering the intent for START_REDELIVER_INTENT, a null intent otherwise). */
    void died(HubDirectory.Peer peer) {
        java.util.List<Started> restart;
        synchronized (started) {
            restart = started.values().stream().filter(record -> record.pid() == peer.pid()).toList();
            for (Started record : restart) started.remove(record.intent().getComponent());
        }
        for (Started record : restart) {
            if (record.result() != START_STICKY && record.result() != START_REDELIVER_INTENT) continue;
            Thread thread = new Thread(() -> {
                HubDirectory.Peer target = broadcasts.process(HubDirectory.key(peer));
                if (target == null) return;
                try { deliver(target, record.intent(), record.result() == START_REDELIVER_INTENT ? record.intent() : null); }
                catch (RuntimeException failure) { System.out.println("[apklayer-hub] cannot restart " + record.intent().getComponent() + ": " + failure); }
            }, "hub-service-restart");
            thread.setDaemon(true); thread.start();
        }
    }
}
