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
    private final Map<IBinder, ServiceConnection> connections = new HashMap<>();
    HubEndpoint(Context context, int hubPid) { this.context = context; this.hubPid = hubPid; attachInterface(null, HubProtocol.ENDPOINT); }
    @Override protected boolean onTransact(int code, Parcel request, Parcel reply, int flags) throws RemoteException {
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
                    @Override public void onServiceDisconnected(ComponentName name) { }
                    private void connected(ComponentName name, IBinder binder) {
                        try { remote.connected(name, binder, null, false); }
                        catch (RemoteException failure) { System.out.println("[apklayer] service client disconnected: " + name); }
                    }
                };
                connections.put(remote.asBinder(), local);
                if (!Services.bindService(context, intent, local, bindFlags)) {
                    connections.remove(remote.asBinder()); local.onNullBinding(intent.getComponent());
                }
            });
            reply.writeNoException(); return true;
        }
        if (code == HubProtocol.ENDPOINT_UNBIND) {
            IBinder remote = request.readStrongBinder(); request.enforceNoDataAvail();
            main.post(() -> {
                ServiceConnection local = connections.remove(remote);
                if (local != null) Services.unbindService(context, local);
            });
            reply.writeNoException(); return true;
        }
        if (code == HubProtocol.ENDPOINT_START_ACTIVITY) {
            Intent intent = request.readTypedObject(Intent.CREATOR); android.os.ResultReceiver resultTo = request.readTypedObject(android.os.ResultReceiver.CREATOR); request.enforceNoDataAvail();
            main.post(() -> HubActivityClient.deliver(intent, resultTo));
            reply.writeNoException(); return true;
        }
        if (code == HubProtocol.ENDPOINT_START || code == HubProtocol.ENDPOINT_STOP) {
            Intent explicit = request.readTypedObject(Intent.CREATOR); Intent delivered = code == HubProtocol.ENDPOINT_START ? request.readTypedObject(Intent.CREATOR) : null;
            request.enforceNoDataAvail();
            if (explicit == null || explicit.getComponent() == null || !context.getPackageName().equals(explicit.getComponent().getPackageName())) throw new SecurityException("Wrong endpoint target");
            if (code == HubProtocol.ENDPOINT_START) {
                int[] result = Services.startFromHub(context, explicit, delivered);
                reply.writeNoException(); reply.writeInt(result == null ? -1 : result[0]); reply.writeInt(result == null ? 0 : result[1]);
            } else { reply.writeNoException(); reply.writeBoolean(Services.stopService(context, explicit)); }
            return true;
        }
        if (code == HubProtocol.ENDPOINT_NOTIFY) {
            android.net.Uri uri = request.readTypedObject(android.net.Uri.CREATOR); int changeFlags = request.readInt(); request.enforceNoDataAvail();
            main.post(() -> ShadowHostContentResolver.deliver(context, uri, changeFlags));
            reply.writeNoException(); return true;
        }
        if (code == HubProtocol.ENDPOINT_GET_PROVIDER) {
            String authority = request.readString(); request.enforceNoDataAvail();
            android.content.ContentProvider provider = org.robolectric.shadows.ShadowContentResolver.getProvider(android.net.Uri.parse("content://" + authority));
            reply.writeNoException();
            reply.writeStrongBinder(provider == null ? null : provider.getIContentProvider().asBinder());
            return true;
        }
        return super.onTransact(code, request, reply, flags);
    }
}
