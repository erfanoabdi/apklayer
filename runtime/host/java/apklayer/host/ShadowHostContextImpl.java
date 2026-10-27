package apklayer.host;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
        try {
            super.unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            // already unregistered
        }
    }
}
