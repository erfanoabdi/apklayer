package apklayer.host;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bound-service records owned by the instance; calls execute outside the directory lock. */
final class HubServiceBindings {
    private static final class Binding {
        final HubDirectory.Peer caller;
        final Intent intent;
        final String key;
        final IServiceConnection connection;
        final int flags;
        HubDirectory.Peer target;
        Binding(HubDirectory.Peer caller, Intent intent, IServiceConnection connection, int flags, String key) {
            this.key = key; this.caller = caller; this.intent = intent; this.connection = connection; this.flags = flags;
        }
    }
    private final HubDirectory directory;
    private final HubPackages packages;
    /** One connection may be bound to several services (bindService with one ServiceConnection and different intents): a binding per pair. */
    private record Key(IBinder connection, ComponentName service) {}
    private static Key key(Binding binding) { return new Key(binding.connection.asBinder(), binding.intent.getComponent()); }
    private final Map<Key, Binding> bindings = new HashMap<>();
    private final java.util.concurrent.ExecutorService commands = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "hub-service-dispatch"); thread.setDaemon(true); return thread;
    });
    HubServiceBindings(HubDirectory directory, HubPackages packages) { this.directory = directory; this.packages = packages; }
    boolean bind(HubDirectory.Peer caller, Intent intent, IServiceConnection connection, int flags, String instance) {
        return bind(caller, intent, connection, flags, instance, false);
    }

    /** system: the hub binds for one of its own services, as system_server does (AccountManagerService and an authenticator that is not exported). */
    boolean bind(HubDirectory.Peer caller, Intent intent, IServiceConnection connection, int flags, String instance, boolean system) {
        if (intent == null || connection == null) throw new IllegalArgumentException("Missing binding arguments");
        ServiceInfo info = packages.service(intent);
        if (info == null) return false;
        if (!system && !caller.name().equals(info.packageName) && !info.exported) throw new SecurityException("Service is not exported");
        if (!system) packages.enforceServicePermission(caller.name(), info);
        Intent explicit = new Intent(intent).setComponent(new ComponentName(info.packageName, info.name));
        String key = HubDirectory.key(info.packageName, instance);
        System.out.println("[apklayer-hub] " + java.time.LocalTime.now() + " bind " + key + " " + info.name + " flags=0x" + Integer.toHexString(flags) + " from " + caller.name());
        Binding binding = new Binding(caller, explicit, connection, flags, key);
        synchronized (bindings) {
            Binding old = bindings.get(key(binding));
            // binding the same connection to the same service again only changes its flags on Android (GeckoView does it to move a child process between priorities); the hub keeps the first ones
            if (old != null && old.caller.pid() == caller.pid()) return true;
            if (old != null) throw new IllegalArgumentException("Connection already bound");
            bindings.put(key(binding), binding);
        }
        HubDirectory.Peer target = directory.peer(key);
        if (target != null) dispatch(binding, target);
        else if ((flags & Context.BIND_AUTO_CREATE) != 0) directory.launch(key);
        return true;
    }
    void unbind(HubDirectory.Peer caller, IBinder connection) {
        System.out.println("[apklayer-hub] " + java.time.LocalTime.now() + " unbind from " + caller.name());
        synchronized (bindings) {
            List<Binding> bound = bindings.values().stream().filter(binding -> binding.connection.asBinder().equals(connection)).toList();
            if (bound.isEmpty()) throw new IllegalArgumentException("Service connection is not bound");
            if (bound.stream().anyMatch(binding -> binding.caller.pid() != caller.pid())) throw new SecurityException("Connection belongs to another process");
            for (Binding binding : bound) { bindings.remove(key(binding)); commands.execute(() -> detach(binding)); }  // unbindService ends every binding of the connection
        }
    }
    boolean needsHost(String name) {
        synchronized (bindings) {
            return bindings.values().stream().anyMatch(binding -> binding.target == null &&
                    (binding.flags & Context.BIND_AUTO_CREATE) != 0 && binding.key.equals(name));
        }
    }
    void launchFailed(String name) {
        List<Binding> failed;
        synchronized (bindings) {
            failed = bindings.values().stream().filter(binding -> binding.target == null &&
                    binding.key.equals(name)).toList();
            for (Binding binding : failed) bindings.remove(key(binding));
        }
        for (Binding binding : failed) {
            try { binding.connection.connected(binding.intent.getComponent(), null, null, true); }
            catch (RemoteException ignored) { }
        }
    }
    void registered(HubDirectory.Peer target) {
        List<Binding> pending;
        synchronized (bindings) {
            pending = bindings.values().stream().filter(binding -> binding.target == null &&
                    binding.key.equals(HubDirectory.key(target))).toList();
        }
        for (Binding binding : pending) dispatch(binding, target);
    }
    private void dispatch(Binding binding, HubDirectory.Peer target) {
        synchronized (bindings) {
            if (bindings.get(key(binding)) != binding || binding.target != null) return;
            binding.target = target;
            commands.execute(() -> deliver(binding, target));
        }
    }
    private void deliver(Binding binding, HubDirectory.Peer target) {
        synchronized (bindings) {
            if (bindings.get(key(binding)) != binding || binding.target != target) return;
        }
        try {
            HubProtocol.call(target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_BIND, request -> {
                request.writeTypedObject(binding.intent, 0); request.writeStrongBinder(binding.connection.asBinder()); request.writeInt(binding.flags);
            }, reply -> null);
        } catch (RuntimeException failure) {
            synchronized (bindings) { if (binding.target == target) binding.target = null; }
            System.out.println("[apklayer-hub] bind delivery failed: " + binding.intent.getComponent() + ": " + failure);
            directory.ensureHost(binding.key);  // the host ended under the delivery: a new one, if its exit and death were already handled
        }
    }
    private void detach(Binding binding) {
        if (binding.target == null) return;
        try {
            HubProtocol.call(binding.target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_UNBIND,
                    request -> { request.writeStrongBinder(binding.connection.asBinder()); request.writeTypedObject(binding.intent.getComponent(), 0); }, reply -> null);
        } catch (RuntimeException failure) {
            System.out.println("[apklayer-hub] unbind target already disconnected: " + binding.intent.getComponent());
        }
    }
    void died(HubDirectory.Peer peer) {
        List<Binding> detached = new ArrayList<>();
        synchronized (bindings) {
            for (Binding binding : List.copyOf(bindings.values())) {
                if (binding.caller.pid() == peer.pid()) {
                    bindings.remove(key(binding)); detached.add(binding);
                } else if (binding.target != null && binding.target.pid() == peer.pid()) {
                    binding.target = null;  // LoadedApk's original death monitor delivers onServiceDisconnected.
                }
            }
        }
        for (Binding binding : detached) commands.execute(() -> detach(binding));
        directory.ensureHost(HubDirectory.key(peer));  // its auto-create bindings (one whose delivery to it failed just before too) get a new host
    }
}
