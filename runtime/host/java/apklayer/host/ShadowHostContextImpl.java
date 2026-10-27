package apklayer.host;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import java.util.concurrent.Executor;
import org.robolectric.annotation.RealObject;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowContextImpl;

/**
 * Robolectric queues one delivery per matching registration, so a receiver that unregisters itself in onReceive
 * (the common one-shot pattern) can be called again after it is gone and then fails on unregistering twice.
 * Real Android never delivers to an unregistered receiver; unregistering a gone receiver is a no-op here.
 */
@Implements(className = "android.app.ContextImpl")
public class ShadowHostContextImpl extends ShadowContextImpl {
    @RealObject private Context real;
    @Implementation @Override protected boolean bindService(Intent intent, ServiceConnection connection, int flags) {
        if (HubServices.external(real, intent)) return HubServices.bind(real, intent, flags, null, connection);
        return super.bindService(intent, connection, flags);
    }
    @Implementation @Override protected boolean bindService(Intent intent, int flags, Executor executor, ServiceConnection connection) {
        if (HubServices.external(real, intent)) return HubServices.bind(real, intent, flags, executor, connection);
        return super.bindService(intent, flags, executor, connection);
    }
    @Implementation @Override protected void unbindService(ServiceConnection connection) {
        if (!HubServices.unbind(connection)) super.unbindService(connection);
    }

    @Implementation
    @Override
    protected void unregisterReceiver(BroadcastReceiver receiver) {
        if (hub()) { real("unregisterReceiver", ClassParameter.from(BroadcastReceiver.class, receiver)); return; }
        try {
            super.unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            // already unregistered
        }
    }

    // A provider (or any service) in this process asking about its caller: with the hub the caller's package decides, not this app's own grants.
    @Implementation @Override protected int checkPermission(String permission, int pid, int uid) {
        if (hub() && HubClient.connected() && pid != android.os.Process.myPid()) return HubClient.current().checkPermission(pid, permission);
        return super.checkPermission(permission, pid, uid);
    }

    // With the instance hub, broadcasts and receiver registrations run Android's own ContextImpl code (LoadedApk.ReceiverDispatcher, PendingResult...), which ends
    // in the hub-backed activity manager (HubActivityManager): Robolectric's in-process queue would only reach this app's own receivers.
    private static boolean hub() { return Boolean.getBoolean("apklayer.instancehub"); }
    @SuppressWarnings("unchecked")
    private <T> T real(String name, ClassParameter<?>... parameters) {
        return (T) ReflectionHelpers.callInstanceMethod(real, Shadow.directMethodName("android.app.ContextImpl", name), parameters);
    }
    @Implementation @Override protected void sendBroadcast(Intent intent) {
        if (hub()) real("sendBroadcast", ClassParameter.from(Intent.class, intent)); else super.sendBroadcast(intent);
    }
    @Implementation @Override protected void sendBroadcast(Intent intent, String permission) {
        if (hub()) real("sendBroadcast", ClassParameter.from(Intent.class, intent), ClassParameter.from(String.class, permission)); else super.sendBroadcast(intent, permission);
    }
    @Implementation @Override protected void sendBroadcast(Intent intent, String permission, Bundle options) {
        if (hub()) real("sendBroadcast", ClassParameter.from(Intent.class, intent), ClassParameter.from(String.class, permission), ClassParameter.from(Bundle.class, options));
        else super.sendBroadcast(intent, permission, options);
    }
    @Implementation @Override protected void sendBroadcastAsUser(Intent intent, UserHandle user) {
        if (hub()) real("sendBroadcastAsUser", ClassParameter.from(Intent.class, intent), ClassParameter.from(UserHandle.class, user)); else super.sendBroadcastAsUser(intent, user);
    }
    @Implementation @Override protected void sendBroadcastAsUser(Intent intent, UserHandle user, String permission) {
        if (hub()) real("sendBroadcastAsUser", ClassParameter.from(Intent.class, intent), ClassParameter.from(UserHandle.class, user), ClassParameter.from(String.class, permission));
        else super.sendBroadcastAsUser(intent, user, permission);
    }
    @Implementation @Override protected void sendOrderedBroadcast(Intent intent, String permission) {
        if (hub()) real("sendOrderedBroadcast", ClassParameter.from(Intent.class, intent), ClassParameter.from(String.class, permission)); else super.sendOrderedBroadcast(intent, permission);
    }
    @Implementation @Override protected void sendOrderedBroadcast(Intent intent, String permission, BroadcastReceiver resultReceiver, Handler scheduler, int initialCode,
            String initialData, Bundle initialExtras) {
        if (hub()) real("sendOrderedBroadcast", ClassParameter.from(Intent.class, intent), ClassParameter.from(String.class, permission), ClassParameter.from(BroadcastReceiver.class, resultReceiver),
                ClassParameter.from(Handler.class, scheduler), ClassParameter.from(int.class, initialCode), ClassParameter.from(String.class, initialData), ClassParameter.from(Bundle.class, initialExtras));
        else super.sendOrderedBroadcast(intent, permission, resultReceiver, scheduler, initialCode, initialData, initialExtras);
    }
    @Implementation @Override protected void sendOrderedBroadcastAsUser(Intent intent, UserHandle user, String permission, BroadcastReceiver resultReceiver, Handler scheduler,
            int initialCode, String initialData, Bundle initialExtras) {
        if (hub()) real("sendOrderedBroadcastAsUser", ClassParameter.from(Intent.class, intent), ClassParameter.from(UserHandle.class, user), ClassParameter.from(String.class, permission),
                ClassParameter.from(BroadcastReceiver.class, resultReceiver), ClassParameter.from(Handler.class, scheduler), ClassParameter.from(int.class, initialCode),
                ClassParameter.from(String.class, initialData), ClassParameter.from(Bundle.class, initialExtras));
        else super.sendOrderedBroadcastAsUser(intent, user, permission, resultReceiver, scheduler, initialCode, initialData, initialExtras);
    }
    @Implementation @Override protected void sendOrderedBroadcastAsUser(Intent intent, UserHandle user, String permission, int appOp, Bundle options, BroadcastReceiver resultReceiver,
            Handler scheduler, int initialCode, String initialData, Bundle initialExtras) {
        if (hub()) real("sendOrderedBroadcastAsUser", ClassParameter.from(Intent.class, intent), ClassParameter.from(UserHandle.class, user), ClassParameter.from(String.class, permission),
                ClassParameter.from(int.class, appOp), ClassParameter.from(Bundle.class, options), ClassParameter.from(BroadcastReceiver.class, resultReceiver),
                ClassParameter.from(Handler.class, scheduler), ClassParameter.from(int.class, initialCode), ClassParameter.from(String.class, initialData), ClassParameter.from(Bundle.class, initialExtras));
        else super.sendOrderedBroadcastAsUser(intent, user, permission, appOp, options, resultReceiver, scheduler, initialCode, initialData, initialExtras);
    }
    @Implementation @Override protected void sendStickyBroadcast(Intent intent) {
        if (hub()) real("sendStickyBroadcast", ClassParameter.from(Intent.class, intent)); else super.sendStickyBroadcast(intent);
    }
    @Implementation @Override protected Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        return hub() ? real("registerReceiver", ClassParameter.from(BroadcastReceiver.class, receiver), ClassParameter.from(IntentFilter.class, filter)) : super.registerReceiver(receiver, filter);
    }
    @Implementation @Override protected Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) {
        return hub() ? real("registerReceiver", ClassParameter.from(BroadcastReceiver.class, receiver), ClassParameter.from(IntentFilter.class, filter), ClassParameter.from(int.class, flags))
                : super.registerReceiver(receiver, filter, flags);
    }
    @Implementation @Override protected Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String permission, Handler scheduler) {
        return hub() ? real("registerReceiver", ClassParameter.from(BroadcastReceiver.class, receiver), ClassParameter.from(IntentFilter.class, filter), ClassParameter.from(String.class, permission),
                ClassParameter.from(Handler.class, scheduler)) : super.registerReceiver(receiver, filter, permission, scheduler);
    }
    @Implementation @Override protected Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, String permission, Handler scheduler, int flags) {
        return hub() ? real("registerReceiver", ClassParameter.from(BroadcastReceiver.class, receiver), ClassParameter.from(IntentFilter.class, filter), ClassParameter.from(String.class, permission),
                ClassParameter.from(Handler.class, scheduler), ClassParameter.from(int.class, flags)) : super.registerReceiver(receiver, filter, permission, scheduler, flags);
    }
    @Implementation @Override protected Intent registerReceiverAsUser(BroadcastReceiver receiver, UserHandle user, IntentFilter filter, String permission, Handler scheduler) {
        return hub() ? real("registerReceiverAsUser", ClassParameter.from(BroadcastReceiver.class, receiver), ClassParameter.from(UserHandle.class, user), ClassParameter.from(IntentFilter.class, filter),
                ClassParameter.from(String.class, permission), ClassParameter.from(Handler.class, scheduler)) : super.registerReceiverAsUser(receiver, user, filter, permission, scheduler);
    }
}
