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
        final IServiceConnection connection;
        final int flags;
        HubDirectory.Peer target;
        Binding(HubDirectory.Peer caller, Intent intent, IServiceConnection connection, int flags) {
            this.caller = caller; this.intent = intent; this.connection = connection; this.flags = flags;
        }
    }
    private final HubDirectory directory;
    private final HubPackages packages;
    private final Map<IBinder, Binding> bindings = new HashMap<>();
    private final java.util.concurrent.ExecutorService commands = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "hub-service-dispatch"); thread.setDaemon(true); return thread;
    });
    HubServiceBindings(HubDirectory directory, HubPackages packages) { this.directory = directory; this.packages = packages; }
    boolean bind(HubDirectory.Peer caller, Intent intent, IServiceConnection connection, int flags) {
        if (intent == null || connection == null) throw new IllegalArgumentException("Missing binding arguments");
        ServiceInfo info = packages.service(intent);
        if (info == null) return false;
        if (!caller.name().equals(info.packageName) && !info.exported) throw new SecurityException("Service is not exported");
        packages.enforceServicePermission(caller.name(), info);
        Intent explicit = new Intent(intent).setComponent(new ComponentName(info.packageName, info.name));
        Binding binding = new Binding(caller, explicit, connection, flags);
        synchronized (bindings) {
            Binding old = bindings.get(connection.asBinder());
            if (old != null) throw new IllegalArgumentException("Connection already bound");
            bindings.put(connection.asBinder(), binding);
        }
        HubDirectory.Peer target = directory.peer(info.packageName);
        if (target != null) dispatch(binding, target);
        else if ((flags & Context.BIND_AUTO_CREATE) != 0) directory.launch(info.packageName);
        return true;
    }
    void unbind(HubDirectory.Peer caller, IBinder connection) {
        Binding binding;
        synchronized (bindings) {
            binding = bindings.get(connection);
            if (binding == null) throw new IllegalArgumentException("Service connection is not bound");
            if (binding.caller.pid() != caller.pid()) throw new SecurityException("Connection belongs to another process");
            bindings.remove(connection);
            commands.execute(() -> detach(binding));
        }
    }
    boolean needsHost(String name) {
        synchronized (bindings) {
            return bindings.values().stream().anyMatch(binding -> binding.target == null &&
                    (binding.flags & Context.BIND_AUTO_CREATE) != 0 && binding.intent.getComponent().getPackageName().equals(name));
        }
    }
    void launchFailed(String name) {
        List<Binding> failed;
        synchronized (bindings) {
            failed = bindings.values().stream().filter(binding -> binding.target == null &&
                    binding.intent.getComponent().getPackageName().equals(name)).toList();
            for (Binding binding : failed) bindings.remove(binding.connection.asBinder());
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
                    binding.intent.getComponent().getPackageName().equals(target.name())).toList();
        }
        for (Binding binding : pending) dispatch(binding, target);
    }
    private void dispatch(Binding binding, HubDirectory.Peer target) {
        synchronized (bindings) {
            if (bindings.get(binding.connection.asBinder()) != binding || binding.target != null) return;
            binding.target = target;
            commands.execute(() -> deliver(binding, target));
        }
    }
    private void deliver(Binding binding, HubDirectory.Peer target) {
        synchronized (bindings) {
            if (bindings.get(binding.connection.asBinder()) != binding || binding.target != target) return;
        }
        try {
            HubProtocol.call(target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_BIND, request -> {
                request.writeTypedObject(binding.intent, 0); request.writeStrongBinder(binding.connection.asBinder()); request.writeInt(binding.flags);
            }, reply -> null);
        } catch (RuntimeException failure) {
            synchronized (bindings) { if (binding.target == target) binding.target = null; }
            System.out.println("[apklayer-hub] bind delivery failed: " + binding.intent.getComponent() + ": " + failure);
        }
    }
    private void detach(Binding binding) {
        if (binding.target == null) return;
        try {
            HubProtocol.call(binding.target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_UNBIND,
                    request -> request.writeStrongBinder(binding.connection.asBinder()), reply -> null);
        } catch (RuntimeException failure) {
            System.out.println("[apklayer-hub] unbind target already disconnected: " + binding.intent.getComponent());
        }
    }
    void died(HubDirectory.Peer peer) {
        List<Binding> detached = new ArrayList<>();
        List<String> restart = new ArrayList<>();
        synchronized (bindings) {
            for (Binding binding : List.copyOf(bindings.values())) {
                if (binding.caller.pid() == peer.pid()) {
                    bindings.remove(binding.connection.asBinder()); detached.add(binding);
                } else if (binding.target != null && binding.target.pid() == peer.pid()) {
                    binding.target = null;
                    // LoadedApk's original death monitor delivers onServiceDisconnected.
                    if ((binding.flags & Context.BIND_AUTO_CREATE) != 0) restart.add(peer.name());
                }
            }
        }
        for (Binding binding : detached) commands.execute(() -> detach(binding));
        for (String name : restart.stream().distinct().toList()) directory.launch(name);
    }
}
