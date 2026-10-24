package android.service.restrictions;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.RestrictionsManager;
import android.os.PersistableBundle;

/**
 * Base for the receiver a device-owner/profile-owner app registers to handle
 * RestrictionsManager.ACTION_REQUEST_PERMISSION -- a restricted user asking an
 * admin for permission to do something.
 *
 * Mac.apk has no managed-profile or device-admin subsystem, so the broadcast is
 * never sent and onRequestPermission never fires. The class still has to exist
 * and its onReceive has to DECODE the intent correctly, because an app can
 * construct the intent itself and hand it to the receiver directly (that is how
 * such receivers are usually unit-tested).
 *
 * Source: frameworks/base/core/java/android/service/restrictions/RestrictionsReceiver.java
 */
public abstract class RestrictionsReceiver extends BroadcastReceiver {

    public RestrictionsReceiver() { }

    public abstract void onRequestPermission(Context context, String packageName,
            String requestType, String requestId, PersistableBundle request);

    /**
     * ⚠ This is NOT a no-op override. BroadcastReceiver.onReceive is abstract
     * in AOSP, and AOSP's RestrictionsReceiver implements it by unpacking the
     * four extras and calling onRequestPermission. Leaving it empty would mean
     * a receiver that is delivered its own intent silently does nothing.
     */
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!RestrictionsManager.ACTION_REQUEST_PERMISSION.equals(intent.getAction())) {
            return;
        }
        String packageName = intent.getStringExtra(RestrictionsManager.EXTRA_PACKAGE_NAME);
        String requestType = intent.getStringExtra(RestrictionsManager.EXTRA_REQUEST_TYPE);
        String requestId = intent.getStringExtra(RestrictionsManager.EXTRA_REQUEST_ID);
        PersistableBundle request = (PersistableBundle)
                intent.getParcelableExtra(RestrictionsManager.EXTRA_REQUEST_BUNDLE);
        onRequestPermission(context, packageName, requestType, requestId, request);
    }
}
