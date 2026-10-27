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

    HubStartedServices(HubDirectory directory, HubPackages packages, HubBroadcasts broadcasts) { this.directory = directory; this.packages = packages; this.broadcasts = broadcasts; }

    /** The started service's name; null when there is no such service. */
    ComponentName start(HubDirectory.Peer caller, Intent intent) {
        ServiceInfo info = packages.service(intent);
        if (info == null) return null;
        if (!caller.name().equals(info.packageName) && !info.exported) throw new SecurityException("Service is not exported");
        packages.enforceServicePermission(caller.name(), info);
        Intent explicit = new Intent(intent).setComponent(new ComponentName(info.packageName, info.name));
        HubDirectory.Peer target = broadcasts.process(info.packageName);
        if (target == null) throw new IllegalStateException("Cannot start " + info.packageName);
        return deliver(target, explicit, explicit);
    }

    private ComponentName deliver(HubDirectory.Peer target, Intent explicit, Intent delivered) {
        synchronized (started) { started.put(explicit.getComponent(), new Started(explicit, 0, target.pid())); }  // before the call: stopSelf inside onStartCommand removes it
        int[] result = HubProtocol.call(target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_START,
                request -> { request.writeTypedObject(explicit, 0); request.writeTypedObject(delivered, 0); }, reply -> new int[] {reply.readInt(), reply.readInt()});
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
        HubDirectory.Peer target = directory.peer(info.packageName);
        synchronized (started) { started.remove(name); }
        if (target == null) return false;
        return HubProtocol.call(target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_STOP, request -> request.writeTypedObject(new Intent().setComponent(name), 0), reply -> reply.readBoolean());
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
                HubDirectory.Peer target = broadcasts.process(peer.name());
                if (target == null) return;
                try { deliver(target, record.intent(), record.result() == START_REDELIVER_INTENT ? record.intent() : null); }
                catch (RuntimeException failure) { System.out.println("[apklayer-hub] cannot restart " + record.intent().getComponent() + ": " + failure); }
            }, "hub-service-restart");
            thread.setDaemon(true); thread.start();
        }
    }
}
