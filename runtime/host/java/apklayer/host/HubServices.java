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
    private record Connection(LoadedApk loaded, Context outer, IServiceConnection binder) {}
    private static final Map<ServiceConnection, Connection> connections = new IdentityHashMap<>();
    private HubServices() {}
    static boolean external(Context context, Intent intent) {
        if (!Boolean.getBoolean("apklayer.instancehub")) return false;
        String target = intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName();
        return target != null && !target.equals(context.getPackageName());
    }
    static boolean bind(Context context, Intent intent, int flags, Executor executor, ServiceConnection connection) {
        java.util.Objects.requireNonNull(connection);
        LoadedApk loaded = ReflectionHelpers.getField(context, "mPackageInfo");
        if (loaded == null) throw new IllegalStateException("No package dispatcher in this context");
        Context outer = ReflectionHelpers.callInstanceMethod(context, "getOuterContext");
        IServiceConnection dispatcher = executor == null
                ? loaded.getServiceDispatcher(connection, outer, new Handler(Looper.getMainLooper()), flags)
                : loaded.getServiceDispatcher(connection, outer, executor, flags);
        HubClient client = HubClient.attach(context);
        boolean known;
        synchronized (connections) { known = connections.containsKey(connection); }
        boolean accepted;
        try {
            accepted = HubProtocol.call(client.binder(), HubProtocol.DESCRIPTOR, HubProtocol.BIND, request -> {
                request.writeTypedObject(intent, 0); request.writeStrongBinder(dispatcher.asBinder()); request.writeInt(flags);
            }, reply -> reply.readBoolean());
        } catch (RuntimeException failure) {
            if (!known) loaded.forgetServiceDispatcher(outer, connection);
            throw failure;
        }
        if (!accepted && !known) loaded.forgetServiceDispatcher(outer, connection);
        if (accepted) synchronized (connections) { connections.put(connection, new Connection(loaded, outer, dispatcher)); }
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
        IServiceConnection dispatcher = old.loaded.forgetServiceDispatcher(old.outer, connection);
        HubProtocol.call(HubClient.current().binder(), HubProtocol.DESCRIPTOR, HubProtocol.UNBIND,
                request -> request.writeStrongBinder(dispatcher.asBinder()), reply -> null);
        return true;
    }
}
