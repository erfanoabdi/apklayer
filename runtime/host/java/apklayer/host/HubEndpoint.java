package apklayer.host;

import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import java.util.HashMap;
import java.util.Map;

/** Incoming component lifecycle commands execute on the app's actual main looper. */
final class HubEndpoint extends Binder {
    private final Context context;
    private final int hubPid;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** The hub's connection and the service it is bound to (one connection may be bound to several services here) -> the local connection. */
    private final Map<java.util.List<Object>, ServiceConnection> connections = new HashMap<>();
    HubEndpoint(Context context, int hubPid) { this.context = context; this.hubPid = hubPid; attachInterface(null, HubProtocol.ENDPOINT); }
    int hubPid() { return hubPid; }
    @Override protected boolean onTransact(int code, Parcel request, Parcel reply, int flags) throws RemoteException {
        try { return handle(code, request, reply, flags); }
        catch (RuntimeException failure) { System.out.println("[apklayer] endpoint request " + code + " failed: " + failure); failure.printStackTrace(System.out); throw failure; }
    }
    private boolean handle(int code, Parcel request, Parcel reply, int flags) throws RemoteException {
        if (code < FIRST_CALL_TRANSACTION || code > LAST_CALL_TRANSACTION) return super.onTransact(code, request, reply, flags);
        if (Binder.getCallingPid() != hubPid) throw new SecurityException("Lifecycle caller is not the instance hub");
        request.enforceInterface(HubProtocol.ENDPOINT);
        if (code == HubProtocol.ENDPOINT_BIND) {
            Intent intent = request.readTypedObject(Intent.CREATOR);
            IServiceConnection remote = IServiceConnection.Stub.asInterface(request.readStrongBinder());
            int bindFlags = request.readInt(); request.enforceNoDataAvail();
            if (intent == null || remote == null || intent.getComponent() == null ||
                    !context.getPackageName().equals(intent.getComponent().getPackageName())) throw new SecurityException("Wrong endpoint target");
            main.post(() -> {
                ServiceConnection local = new ServiceConnection() {
                    @Override public void onServiceConnected(ComponentName name, IBinder binder) { connected(name, binder); }
                    @Override public void onNullBinding(ComponentName name) { connected(name, null); }
                    @Override public void onServiceDisconnected(ComponentName name) { if (Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] bind: service disconnected " + name); }
                    private void connected(ComponentName name, IBinder binder) {
                        if (Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] bind: delivering connection of " + name + " binder=" + binder);
                        try { remote.connected(name, binder, null, false); }
                        catch (RemoteException failure) { System.out.println("[apklayer] service client disconnected: " + name); }
                    }
                };
                java.util.List<Object> key = java.util.List.of(remote.asBinder(), intent.getComponent());
                connections.put(key, local);
                if (!Services.bindService(context, intent, local, bindFlags)) {
                    connections.remove(key); local.onNullBinding(intent.getComponent());
                }
            });
            reply.writeNoException(); return true;
        }
        if (code == HubProtocol.ENDPOINT_UNBIND) {
            IBinder remote = request.readStrongBinder(); ComponentName service = request.readTypedObject(ComponentName.CREATOR); request.enforceNoDataAvail();
            main.post(() -> {
                ServiceConnection local = connections.remove(java.util.List.of(remote, service));
                if (local != null) Services.unbindService(context, local);
            });
            reply.writeNoException(); return true;
        }
        if (code == HubProtocol.ENDPOINT_START_ACTIVITY) {
            Intent intent = request.readTypedObject(Intent.CREATOR); android.os.ResultReceiver resultTo = request.readTypedObject(android.os.ResultReceiver.CREATOR); request.enforceNoDataAvail();
            if (!HubActivityClient.arrive()) { reply.writeException(new IllegalStateException("app exiting")); return true; }  // the hub starts a new process
            main.post(() -> { HubActivityClient.ARRIVING.decrementAndGet(); HubActivityClient.deliver(intent, resultTo); });
            reply.writeNoException(); return true;
        }
        if (code == HubProtocol.ENDPOINT_START || code == HubProtocol.ENDPOINT_STOP) {
            Intent explicit = request.readTypedObject(Intent.CREATOR); Intent delivered = code == HubProtocol.ENDPOINT_START ? request.readTypedObject(Intent.CREATOR) : null;
            request.enforceNoDataAvail();
            if (explicit == null || explicit.getComponent() == null || !context.getPackageName().equals(explicit.getComponent().getPackageName())) throw new SecurityException("Wrong endpoint target");
            // a component host whose last service ends here must still send this reply: the hub asks it to exit afterwards (ENDPOINT_EXIT_IF_IDLE)
            Services.HUB_CALLS.incrementAndGet();
            try {
                if (code == HubProtocol.ENDPOINT_START) {
                    int[] result = Services.startFromHub(context, explicit, delivered);
                    reply.writeNoException(); reply.writeInt(result == null ? -1 : result[0]); reply.writeInt(result == null ? 0 : result[1]);
                } else { reply.writeNoException(); reply.writeBoolean(Services.stopService(context, explicit)); }
            } finally { Services.HUB_CALLS.decrementAndGet(); }
            return true;
        }
        if (code == HubProtocol.ENDPOINT_PACKAGE_CHANGED) {
            String pkg = request.readString(); boolean removed = request.readBoolean(); request.enforceNoDataAvail();
            main.post(() -> HubClient.current().packageChanged(context, pkg, removed));
            reply.writeNoException(); return true;
        }
        if (code == HubProtocol.ENDPOINT_CONFIGURATION) {
            android.os.Bundle settings = request.readBundle(HubEndpoint.class.getClassLoader()); request.enforceNoDataAvail();
            main.post(() -> AppHost.configurationChanged(HostConfiguration.update(settings)));
            reply.writeNoException(); return true;
        }
        if (code == HubProtocol.ENDPOINT_JOB) {
            android.app.job.JobInfo job = request.readTypedObject(android.app.job.JobInfo.CREATOR); request.enforceNoDataAvail();
            main.post(() -> HostJobs.run(job));
            reply.writeNoException(); return true;
        }
        if (code == HubProtocol.ENDPOINT_EXIT_IF_IDLE) {
            request.enforceNoDataAvail();
            main.post(Services::exitIfIdle);
            reply.writeNoException(); return true;
        }
        if (code == HubProtocol.ENDPOINT_NOTIFY) {
            android.net.Uri uri = request.readTypedObject(android.net.Uri.CREATOR); int changeFlags = request.readInt(); request.enforceNoDataAvail();
            main.post(() -> ShadowHostContentResolver.deliver(context, uri, changeFlags));
            reply.writeNoException(); return true;
        }
        if (code == HubProtocol.ENDPOINT_GET_PROVIDER) {
            String authority = request.readString(); request.enforceNoDataAvail();
            // the provider is created on first use, and its onCreate may need a looper: on the main thread, not on this binder thread
            java.util.concurrent.CompletableFuture<android.content.ContentProvider> created = new java.util.concurrent.CompletableFuture<>();
            main.post(() -> { try { created.complete(org.robolectric.shadows.ShadowContentResolver.getProvider(android.net.Uri.parse("content://" + authority))); } catch (Throwable failure) { created.completeExceptionally(failure); } });
            android.content.ContentProvider provider = created.join();
            if (Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] provider " + authority + " -> " + provider + " (context " + (provider == null ? null : provider.getContext()) + ")");
            reply.writeNoException();
            reply.writeStrongBinder(provider == null ? null : provider.getIContentProvider().asBinder());
            return true;
        }
        return super.onTransact(code, request, reply, flags);
    }
}
