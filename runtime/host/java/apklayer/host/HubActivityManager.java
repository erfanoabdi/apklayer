package apklayer.host;

import android.app.IActivityManager;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * The app's activity manager for the calls that cross processes. Android's own ContextImpl, LoadedApk.ReceiverDispatcher and BroadcastReceiver.PendingResult run
 * unmodified and end in four IActivityManager calls; those go to the instance hub, which plays the activity manager's part. Everything else stays with what
 * was there before.
 */
final class HubActivityManager implements InvocationHandler {
    private final IActivityManager original;
    private HubActivityManager(IActivityManager original) { this.original = original; }

    private static IActivityManager proxy;

    /** The activity manager an app sees: the hub-backed proxy around Robolectric's stand-in once the app is connected to the hub, the stand-in before. */
    static synchronized IActivityManager service(IActivityManager standIn) {
        if (!Boolean.getBoolean("apklayer.instancehub") || !HubClient.connected()) return standIn;
        if (proxy == null)
            proxy = (IActivityManager) Proxy.newProxyInstance(IActivityManager.class.getClassLoader(), new Class<?>[] {IActivityManager.class}, new HubActivityManager(standIn));
        return proxy;
    }

    private static IBinder binder() { return HubClient.current().binder(); }

    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        switch (method.getName()) {
            case "registerReceiverWithFeature": // (thread, callerPackage, feature, receiverId, receiver, filter, permission, userId, flags)
                return register((IIntentReceiver) args[4], (IntentFilter) args[5], (String) args[6], (Integer) args[8]);
            case "registerReceiver": // (thread, callerPackage, receiver, filter, permission, userId, flags)
                return register((IIntentReceiver) args[2], (IntentFilter) args[3], (String) args[4], (Integer) args[6]);
            case "unregisterReceiver": {
                IBinder receiver = ((IIntentReceiver) args[0]).asBinder();
                HubProtocol.call(binder(), HubProtocol.DESCRIPTOR, HubProtocol.UNREGISTER_RECEIVER, request -> request.writeStrongBinder(receiver), reply -> null);
                return null;
            }
            case "broadcastIntentWithFeature": // (thread, feature, intent, type, resultTo, code, data, extras, required[], excludedPermissions[], excludedPackages[], appOp, options, serialized, sticky, userId)
                return send((Intent) args[2], (IIntentReceiver) args[4], (Integer) args[5], (String) args[6], (Bundle) args[7], (String[]) args[8],
                        (Boolean) args[13], (Boolean) args[14], (Integer) args[15]);
            case "broadcastIntent": // (thread, intent, type, resultTo, code, data, extras, required[], appOp, options, serialized, sticky, userId)
                return send((Intent) args[1], (IIntentReceiver) args[3], (Integer) args[4], (String) args[5], (Bundle) args[6], (String[]) args[7],
                        (Boolean) args[10], (Boolean) args[11], (Integer) args[12]);
            case "unbroadcastIntent": { // (thread, intent, userId): removeStickyBroadcast
                Intent intent = (Intent) args[1];
                HubProtocol.call(binder(), HubProtocol.DESCRIPTOR, HubProtocol.UNBROADCAST, request -> request.writeTypedObject(intent, 0), reply -> null);
                return null;
            }
            case "grantUriPermission": case "revokeUriPermission": { // (thread, targetPackage, uri, mode, userId)
                String target = (String) args[1]; Uri uri = (Uri) args[2]; int mode = (Integer) args[3]; boolean revoke = method.getName().startsWith("revoke");
                HubClient.current().grantUri(target, uri, mode, revoke);
                return null;
            }
            case "finishReceiver": { // (who, resultCode, resultData, extras, abort, flags)
                IBinder who = (IBinder) args[0];
                int code = (Integer) args[1]; String data = (String) args[2]; Bundle extras = (Bundle) args[3]; boolean abort = (Boolean) args[4];
                HubProtocol.call(binder(), HubProtocol.DESCRIPTOR, HubProtocol.FINISH_RECEIVER, request -> {
                    request.writeStrongBinder(who); request.writeInt(code); request.writeString(data); request.writeBundle(extras); request.writeBoolean(abort);
                }, reply -> null);
                return null;
            }
            default:
                if (original == null) return defaultValue(method.getReturnType());
                try { return method.invoke(original, args); }
                catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
    }

    private static Intent register(IIntentReceiver receiver, IntentFilter filter, String permission, int flags) {
        // receivers registered for a null receiver only ask for sticky intents: nothing to track
        if (receiver == null) return HubProtocol.call(binder(), HubProtocol.DESCRIPTOR, HubProtocol.REGISTER_RECEIVER, request -> {
            request.writeStrongBinder(null); request.writeTypedObject(filter, 0); request.writeString(permission); request.writeInt(flags);
        }, reply -> reply.readTypedObject(Intent.CREATOR));
        IBinder binder = receiver.asBinder();
        return HubProtocol.call(binder(), HubProtocol.DESCRIPTOR, HubProtocol.REGISTER_RECEIVER, request -> {
            request.writeStrongBinder(binder); request.writeTypedObject(filter, 0); request.writeString(permission); request.writeInt(flags);
        }, reply -> reply.readTypedObject(Intent.CREATOR));
    }

    private static int send(Intent intent, IIntentReceiver resultTo, int code, String data, Bundle extras, String[] required, boolean ordered, boolean sticky, int userId) {
        IBinder to = resultTo == null ? null : resultTo.asBinder();
        return HubProtocol.call(binder(), HubProtocol.DESCRIPTOR, HubProtocol.BROADCAST, request -> {
            request.writeTypedObject(intent, 0); request.writeStrongBinder(to); request.writeInt(code); request.writeString(data); request.writeBundle(extras);
            request.writeStringArray(required); request.writeBoolean(ordered); request.writeBoolean(sticky); request.writeInt(userId);
        }, reply -> reply.readInt());
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        return null;
    }
}
