package apklayer.host;

import android.content.BroadcastReceiver;
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
