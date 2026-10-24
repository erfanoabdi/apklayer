// android.app.admin.DelegatedAdminReceiver -- the receiver base class for an
// app that holds DELEGATED device-policy scopes (cert selection, network and
// security logging) rather than being the admin itself (API 29).
//   AOSP: frameworks/base/core/java/android/app/admin/DelegatedAdminReceiver.java
//         (android14-release), mirrored member for member: the three callbacks
//         THROW UnsupportedOperationException until overridden (that is AOSP's
//         contract, not a stub), and onReceive() is final.
// Census + method: docs/fwgap-new-platform-classes_2026-09-26.md.
package android.app.admin;

import static android.app.admin.DeviceAdminReceiver.ACTION_CHOOSE_PRIVATE_KEY_ALIAS;
import static android.app.admin.DeviceAdminReceiver.ACTION_NETWORK_LOGS_AVAILABLE;
import static android.app.admin.DeviceAdminReceiver.ACTION_SECURITY_LOGS_AVAILABLE;
import static android.app.admin.DeviceAdminReceiver.EXTRA_CHOOSE_PRIVATE_KEY_ALIAS;
import static android.app.admin.DeviceAdminReceiver.EXTRA_CHOOSE_PRIVATE_KEY_SENDER_UID;
import static android.app.admin.DeviceAdminReceiver.EXTRA_CHOOSE_PRIVATE_KEY_URI;
import static android.app.admin.DeviceAdminReceiver.EXTRA_NETWORK_LOGS_COUNT;
import static android.app.admin.DeviceAdminReceiver.EXTRA_NETWORK_LOGS_TOKEN;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class DelegatedAdminReceiver extends BroadcastReceiver {
    private static final String TAG = "DelegatedAdminReceiver";

    public DelegatedAdminReceiver() {
    }

    public String onChoosePrivateKeyAlias(Context context, Intent intent, int uid, Uri uri,
            String alias) {
        throw new UnsupportedOperationException("onChoosePrivateKeyAlias should be implemented");
    }

    public void onNetworkLogsAvailable(Context context, Intent intent, long batchToken,
            int networkLogsCount) {
        throw new UnsupportedOperationException("onNetworkLogsAvailable should be implemented");
    }

    public void onSecurityLogsAvailable(Context context, Intent intent) {
        throw new UnsupportedOperationException("onSecurityLogsAvailable should be implemented");
    }

    /**
     * Intercept delegated device administrator broadcasts. Implementations
     * should not override this method; implement the convenience callbacks.
     */
    @Override
    public final void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (ACTION_CHOOSE_PRIVATE_KEY_ALIAS.equals(action)) {
            int uid = intent.getIntExtra(EXTRA_CHOOSE_PRIVATE_KEY_SENDER_UID, -1);
            Uri uri = intent.getParcelableExtra(EXTRA_CHOOSE_PRIVATE_KEY_URI, Uri.class);
            String alias = intent.getStringExtra(EXTRA_CHOOSE_PRIVATE_KEY_ALIAS);
            String chosenAlias = onChoosePrivateKeyAlias(context, intent, uid, uri, alias);
            setResultData(chosenAlias);
        } else if (ACTION_NETWORK_LOGS_AVAILABLE.equals(action)) {
            long batchToken = intent.getLongExtra(EXTRA_NETWORK_LOGS_TOKEN, -1);
            int networkLogsCount = intent.getIntExtra(EXTRA_NETWORK_LOGS_COUNT, 0);
            onNetworkLogsAvailable(context, intent, batchToken, networkLogsCount);
        } else if (ACTION_SECURITY_LOGS_AVAILABLE.equals(action)) {
            onSecurityLogsAvailable(context, intent);
        } else {
            Log.w(TAG, "Unhandled broadcast: " + action);
        }
    }
}
