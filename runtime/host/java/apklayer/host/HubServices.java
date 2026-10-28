package apklayer.host;

import android.app.IServiceConnection;
import android.app.LoadedApk;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.Looper;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import org.robolectric.util.ReflectionHelpers;

/** Route cross-package service lifecycle through the hub; reuse Android's own connection dispatcher. */
final class HubServices {
    private record Connection(LoadedApk loaded, Context outer, IServiceConnection binder, ServiceConnection used) {}

    /** -Dapklayer.logcat: prints every callback the app's ServiceConnection gets. */
    private static final class Traced implements ServiceConnection {
        private final ServiceConnection target;
        Traced(ServiceConnection target) { this.target = target; }
        @Override public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) { System.out.println("[apklayer] ServiceConnection.onServiceConnected " + target.getClass().getName() + " " + service); target.onServiceConnected(name, service); }
        @Override public void onServiceDisconnected(android.content.ComponentName name) { System.out.println("[apklayer] ServiceConnection.onServiceDisconnected " + target.getClass().getName()); target.onServiceDisconnected(name); }
        @Override public void onBindingDied(android.content.ComponentName name) { System.out.println("[apklayer] ServiceConnection.onBindingDied " + target.getClass().getName()); target.onBindingDied(name); }
        @Override public void onNullBinding(android.content.ComponentName name) { System.out.println("[apklayer] ServiceConnection.onNullBinding " + target.getClass().getName()); target.onNullBinding(name); }
    }
    private static final Map<ServiceConnection, Connection> connections = new IdentityHashMap<>();
    private HubServices() {}
    static boolean external(Context context, Intent intent) {
        String target = intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName();
        if (target == null) return false;
        if (!target.equals(context.getPackageName())) return true;
        // a service of the app's own package that runs in its own process (android:process, isolatedProcess: GeckoView's child processes) gets that process, hosted through the hub
        if (intent.getComponent() == null || Boolean.getBoolean("apklayer.componenthost")) return false;
        try {
            android.content.pm.ServiceInfo info = context.getPackageManager().getServiceInfo(intent.getComponent(), 0);
            return (info.flags & android.content.pm.ServiceInfo.FLAG_ISOLATED_PROCESS) != 0 || (info.processName != null && !info.processName.equals(context.getPackageName()));
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unknown) { return false; }
    }
    /** The process of an own-package service that has one of its own (each GeckoView child is a process): its own hub peer, as an isolated instance is. Null for the app's process. */
    private static String ownProcess(Context context, Intent intent) {
        if (intent.getComponent() == null || !intent.getComponent().getPackageName().equals(context.getPackageName())) return null;
        try {
            android.content.pm.ServiceInfo info = context.getPackageManager().getServiceInfo(intent.getComponent(), 0);
            return (info.flags & android.content.pm.ServiceInfo.FLAG_ISOLATED_PROCESS) != 0 || (info.processName != null && !info.processName.equals(context.getPackageName())) ? info.name : null;
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unknown) { return null; }
    }
    static boolean bind(Context context, Intent intent, int flags, Executor executor, ServiceConnection connection) { return bind(context, intent, flags, executor, connection, null); }
    static boolean bind(Context context, Intent intent, int flags, Executor executor, ServiceConnection connection, String requested) {
        java.util.Objects.requireNonNull(connection);
        final String instance = requested != null ? requested : ownProcess(context, intent);
        LoadedApk loaded = ReflectionHelpers.getField(context, "mPackageInfo");
        if (loaded == null) throw new IllegalStateException("No package dispatcher in this context");
        Context outer = ReflectionHelpers.callInstanceMethod(context, "getOuterContext");
        ServiceConnection used = connection;
        synchronized (connections) {
            Connection existing = connections.get(connection);
            if (existing != null) used = existing.used();
            else if (Boolean.getBoolean("apklayer.logcat")) used = new Traced(connection);
        }
        IServiceConnection dispatcher = executor == null
                ? loaded.getServiceDispatcher(used, outer, new Handler(Looper.getMainLooper()), flags)
                : loaded.getServiceDispatcher(used, outer, executor, flags);
        HubClient client = HubClient.attach(context);
        boolean known;
        synchronized (connections) { known = connections.containsKey(connection); }
        boolean accepted;
        try {
            accepted = HubProtocol.call(client.binder(), HubProtocol.DESCRIPTOR, HubProtocol.BIND, request -> {
                request.writeTypedObject(intent, 0); request.writeStrongBinder(dispatcher.asBinder()); request.writeInt(flags); request.writeString(instance);
            }, reply -> reply.readBoolean());
        } catch (RuntimeException failure) {
            if (!known) loaded.forgetServiceDispatcher(outer, used);
            throw failure;
        }
        if (Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] bindService " + intent.getComponent() + " flags=0x" + Integer.toHexString(flags) + " instance=" + instance + " -> " + accepted + " known=" + known);
        if (!accepted && !known) loaded.forgetServiceDispatcher(outer, used);
        if (accepted) synchronized (connections) { connections.put(connection, new Connection(loaded, outer, dispatcher, used)); }
        return accepted;
    }
    static android.content.ComponentName start(Context context, Intent intent) {
        return HubProtocol.call(HubClient.attach(context).binder(), HubProtocol.DESCRIPTOR, HubProtocol.START_SERVICE, request -> request.writeTypedObject(intent, 0),
                reply -> reply.readTypedObject(android.content.ComponentName.CREATOR));
    }
    static boolean stop(Context context, Intent intent) {
        return HubProtocol.call(HubClient.attach(context).binder(), HubProtocol.DESCRIPTOR, HubProtocol.STOP_SERVICE, request -> request.writeTypedObject(intent, 0), reply -> reply.readBoolean());
    }
    /** Service.stopSelf in this process: the service stops here, the hub forgets it. */
    static boolean stopSelf(android.content.ComponentName name, int startId) {
        boolean stopped = Services.stopSelf(name, startId);
        if (stopped) HubProtocol.call(HubClient.current().binder(), HubProtocol.DESCRIPTOR, HubProtocol.SERVICE_STOPPED, request -> request.writeTypedObject(name, 0), reply -> null);
        return stopped;
    }
    static boolean unbind(ServiceConnection connection) {
        Connection old;
        synchronized (connections) { old = connections.remove(connection); }
        if (old == null) return false;
        IServiceConnection dispatcher = old.loaded.forgetServiceDispatcher(old.outer, old.used());
        HubProtocol.call(HubClient.current().binder(), HubProtocol.DESCRIPTOR, HubProtocol.UNBIND,
                request -> request.writeStrongBinder(dispatcher.asBinder()), reply -> null);
        return true;
    }
}
