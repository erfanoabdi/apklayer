// android.app.admin.DeviceAdminReceiver -- the BroadcastReceiver base class a
// device-admin / device-policy-controller app extends; onReceive() turns the
// system's DevicePolicyManager broadcasts into typed callbacks (API 8).
//   AOSP: frameworks/base/core/java/android/app/admin/DeviceAdminReceiver.java
//         (android14-release). Every public member of the SDK-34 class is here
//         with AOSP's body: the callbacks are the same no-ops / pass-throughs,
//         onReceive() dispatches the same actions to the same callbacks, and
//         onComplianceAcknowledgementRequired() calls
//         DevicePolicyManager.acknowledgeDeviceCompliant() just as AOSP does.
//
// Measured consumer: Aurora Store's DeviceOwnerReceiver extends this class
// (debug/fwgap_census.py, column T). Before this file the subclass could not
// even be LINKED, so anything that loaded it -- a manifest-driven receiver
// instantiation, a class scan -- died with NoClassDefFoundError.
//
// Honest-capability note: nothing on Mac.apk ever SENDS these broadcasts (there
// is no DevicePolicyManagerService and no admin-activation flow, which is why
// our DevicePolicyManager.isAdminActive() is always false). So an admin app sees
// what it sees on a device where it was never activated: no onEnabled(), and
// any DevicePolicyManager call that needs an admin throws SecurityException.
//
// @hide members: AOSP declares them public with @hide. They are package-private
// here so our public surface is exactly the SDK's (a device's hidden-API policy
// makes them invisible to a modern app anyway); onReceive() still uses them.
// Census + method: docs/fwgap-new-platform-classes_2026-09-26.md.
package android.app.admin;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.util.Log;

public class DeviceAdminReceiver extends BroadcastReceiver {
    private static final String TAG = "DevicePolicy";

    public static final String ACTION_DEVICE_ADMIN_ENABLED =
            "android.app.action.DEVICE_ADMIN_ENABLED";
    public static final String ACTION_DEVICE_ADMIN_DISABLE_REQUESTED =
            "android.app.action.DEVICE_ADMIN_DISABLE_REQUESTED";
    public static final String EXTRA_DISABLE_WARNING =
            "android.app.extra.DISABLE_WARNING";
    public static final String ACTION_DEVICE_ADMIN_DISABLED =
            "android.app.action.DEVICE_ADMIN_DISABLED";
    public static final String ACTION_PASSWORD_CHANGED =
            "android.app.action.ACTION_PASSWORD_CHANGED";
    public static final String ACTION_PASSWORD_FAILED =
            "android.app.action.ACTION_PASSWORD_FAILED";
    public static final String ACTION_PASSWORD_SUCCEEDED =
            "android.app.action.ACTION_PASSWORD_SUCCEEDED";
    public static final String ACTION_PASSWORD_EXPIRING =
            "android.app.action.ACTION_PASSWORD_EXPIRING";
    public static final String ACTION_LOCK_TASK_ENTERING =
            "android.app.action.LOCK_TASK_ENTERING";
    public static final String ACTION_LOCK_TASK_EXITING =
            "android.app.action.LOCK_TASK_EXITING";
    public static final String EXTRA_LOCK_TASK_PACKAGE =
            "android.app.extra.LOCK_TASK_PACKAGE";
    public static final String ACTION_PROFILE_PROVISIONING_COMPLETE =
            "android.app.action.PROFILE_PROVISIONING_COMPLETE";
    static final String ACTION_BUGREPORT_SHARING_DECLINED =
            "android.app.action.BUGREPORT_SHARING_DECLINED";
    static final String ACTION_BUGREPORT_FAILED =
            "android.app.action.BUGREPORT_FAILED";
    static final String ACTION_BUGREPORT_SHARE =
            "android.app.action.BUGREPORT_SHARE";
    public static final String ACTION_SECURITY_LOGS_AVAILABLE =
            "android.app.action.SECURITY_LOGS_AVAILABLE";
    public static final String ACTION_NETWORK_LOGS_AVAILABLE =
            "android.app.action.NETWORK_LOGS_AVAILABLE";
    static final String EXTRA_NETWORK_LOGS_TOKEN =
            "android.app.extra.EXTRA_NETWORK_LOGS_TOKEN";
    static final String EXTRA_NETWORK_LOGS_COUNT =
            "android.app.extra.EXTRA_NETWORK_LOGS_COUNT";
    static final String ACTION_USER_ADDED =
            "android.app.action.USER_ADDED";
    static final String ACTION_USER_REMOVED =
            "android.app.action.USER_REMOVED";
    static final String ACTION_USER_STARTED =
            "android.app.action.USER_STARTED";
    static final String ACTION_USER_STOPPED =
            "android.app.action.USER_STOPPED";
    static final String ACTION_USER_SWITCHED =
            "android.app.action.USER_SWITCHED";
    static final String EXTRA_BUGREPORT_HASH =
            "android.app.extra.BUGREPORT_HASH";
    static final String EXTRA_BUGREPORT_FAILURE_REASON =
            "android.app.extra.BUGREPORT_FAILURE_REASON";
    public static final String ACTION_CHOOSE_PRIVATE_KEY_ALIAS =
            "android.app.action.CHOOSE_PRIVATE_KEY_ALIAS";
    static final String EXTRA_CHOOSE_PRIVATE_KEY_SENDER_UID =
            "android.app.extra.CHOOSE_PRIVATE_KEY_SENDER_UID";
    static final String EXTRA_CHOOSE_PRIVATE_KEY_URI =
            "android.app.extra.CHOOSE_PRIVATE_KEY_URI";
    static final String EXTRA_CHOOSE_PRIVATE_KEY_ALIAS =
            "android.app.extra.CHOOSE_PRIVATE_KEY_ALIAS";
    static final String EXTRA_CHOOSE_PRIVATE_KEY_RESPONSE =
            "android.app.extra.CHOOSE_PRIVATE_KEY_RESPONSE";
    static final String ACTION_NOTIFY_PENDING_SYSTEM_UPDATE =
            "android.app.action.NOTIFY_PENDING_SYSTEM_UPDATE";
    static final String EXTRA_SYSTEM_UPDATE_RECEIVED_TIME =
            "android.app.extra.SYSTEM_UPDATE_RECEIVED_TIME";
    public static final String DEVICE_ADMIN_META_DATA =
            "android.app.device_admin";
    static final String ACTION_TRANSFER_OWNERSHIP_COMPLETE =
            "android.app.action.TRANSFER_OWNERSHIP_COMPLETE";
    static final String ACTION_AFFILIATED_PROFILE_TRANSFER_OWNERSHIP_COMPLETE =
            "android.app.action.AFFILIATED_PROFILE_TRANSFER_OWNERSHIP_COMPLETE";
    public static final String EXTRA_TRANSFER_OWNERSHIP_ADMIN_EXTRAS_BUNDLE =
            "android.app.extra.TRANSFER_OWNERSHIP_ADMIN_EXTRAS_BUNDLE";
    static final String ACTION_OPERATION_SAFETY_STATE_CHANGED =
            "android.app.action.OPERATION_SAFETY_STATE_CHANGED";
    static final String ACTION_COMPLIANCE_ACKNOWLEDGEMENT_REQUIRED =
            "android.app.action.COMPLIANCE_ACKNOWLEDGEMENT_REQUIRED";
    static final String EXTRA_OPERATION_SAFETY_REASON =
            "android.app.extra.OPERATION_SAFETY_REASON";
    static final String EXTRA_OPERATION_SAFETY_STATE =
            "android.app.extra.OPERATION_SAFETY_STATE";

    public static final int BUGREPORT_FAILURE_FAILED_COMPLETING = 0;
    public static final int BUGREPORT_FAILURE_FILE_NO_LONGER_AVAILABLE = 1;

    /** DevicePolicyManager.OPERATION_SAFETY_REASON_NONE (@hide in AOSP, -1). */
    private static final int OPERATION_SAFETY_REASON_NONE = -1;

    private DevicePolicyManager mManager;
    private ComponentName mWho;

    public DeviceAdminReceiver() {
    }

    /** Retrieve the DevicePolicyManager interface for this administrator to work with the system. */
    public DevicePolicyManager getManager(Context context) {
        if (mManager != null) {
            return mManager;
        }
        mManager = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        return mManager;
    }

    /** Retrieve the ComponentName describing who this device administrator is. */
    public ComponentName getWho(Context context) {
        if (mWho != null) {
            return mWho;
        }
        mWho = new ComponentName(context, getClass());
        return mWho;
    }

    public void onEnabled(Context context, Intent intent) {
    }

    public CharSequence onDisableRequested(Context context, Intent intent) {
        return null;
    }

    public void onDisabled(Context context, Intent intent) {
    }

    @Deprecated
    public void onPasswordChanged(Context context, Intent intent) {
    }

    public void onPasswordChanged(Context context, Intent intent, UserHandle user) {
        onPasswordChanged(context, intent);
    }

    @Deprecated
    public void onPasswordFailed(Context context, Intent intent) {
    }

    public void onPasswordFailed(Context context, Intent intent, UserHandle user) {
        onPasswordFailed(context, intent);
    }

    @Deprecated
    public void onPasswordSucceeded(Context context, Intent intent) {
    }

    public void onPasswordSucceeded(Context context, Intent intent, UserHandle user) {
        onPasswordSucceeded(context, intent);
    }

    @Deprecated
    public void onPasswordExpiring(Context context, Intent intent) {
    }

    public void onPasswordExpiring(Context context, Intent intent, UserHandle user) {
        onPasswordExpiring(context, intent);
    }

    public void onProfileProvisioningComplete(Context context, Intent intent) {
    }

    @Deprecated
    public void onReadyForUserInitialization(Context context, Intent intent) {
    }

    public void onLockTaskModeEntering(Context context, Intent intent, String pkg) {
    }

    public void onLockTaskModeExiting(Context context, Intent intent) {
    }

    public String onChoosePrivateKeyAlias(Context context, Intent intent, int uid, Uri uri,
            String alias) {
        return null;
    }

    public void onSystemUpdatePending(Context context, Intent intent, long receivedTime) {
    }

    public void onBugreportSharingDeclined(Context context, Intent intent) {
    }

    public void onBugreportShared(Context context, Intent intent, String bugreportHash) {
    }

    public void onBugreportFailed(Context context, Intent intent, int failureCode) {
    }

    public void onSecurityLogsAvailable(Context context, Intent intent) {
    }

    public void onNetworkLogsAvailable(Context context, Intent intent, long batchToken,
            int networkLogsCount) {
    }

    public void onUserAdded(Context context, Intent intent, UserHandle addedUser) {
    }

    public void onUserRemoved(Context context, Intent intent, UserHandle removedUser) {
    }

    public void onUserStarted(Context context, Intent intent, UserHandle startedUser) {
    }

    public void onUserStopped(Context context, Intent intent, UserHandle stoppedUser) {
    }

    public void onUserSwitched(Context context, Intent intent, UserHandle switchedUser) {
    }

    public void onTransferOwnershipComplete(Context context, PersistableBundle bundle) {
    }

    public void onTransferAffiliatedProfileOwnershipComplete(Context context, UserHandle user) {
    }

    public void onOperationSafetyStateChanged(Context context, int reason, boolean isSafe) {
    }

    private void onOperationSafetyStateChanged(Context context, Intent intent) {
        if (!hasRequiredExtra(intent, EXTRA_OPERATION_SAFETY_REASON)
                || !hasRequiredExtra(intent, EXTRA_OPERATION_SAFETY_STATE)) {
            // AOSP's own spelling of this log line.
            Log.w(TAG, "Igoring intent that's missing required extras");
            return;
        }
        int reason = intent.getIntExtra(EXTRA_OPERATION_SAFETY_REASON,
                OPERATION_SAFETY_REASON_NONE);
        // DevicePolicyManager.isValidOperationSafetyReason (@hide): the only
        // valid reason in API 34 is OPERATION_SAFETY_REASON_DRIVING_DISTRACTION.
        if (reason != DevicePolicyManager.OPERATION_SAFETY_REASON_DRIVING_DISTRACTION) {
            Log.wtf(TAG, "Received invalid reason on " + intent.getAction() + ": " + reason);
            return;
        }
        boolean isSafe = intent.getBooleanExtra(EXTRA_OPERATION_SAFETY_STATE,
                /* defaultValue=*/ false);
        onOperationSafetyStateChanged(context, reason, isSafe);
    }

    public void onComplianceAcknowledgementRequired(Context context, Intent intent) {
        getManager(context).acknowledgeDeviceCompliant();
    }

    private boolean hasRequiredExtra(Intent intent, String extra) {
        if (intent.hasExtra(extra)) return true;

        Log.wtf(TAG, "Missing '" + extra + "' on intent " + intent);
        return false;
    }

    /**
     * Intercept standard device administrator broadcasts. Implementations should
     * not override this method; it is better to implement the convenience
     * callbacks for each action.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (ACTION_PASSWORD_CHANGED.equals(action)) {
            onPasswordChanged(context, intent, intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class));
        } else if (ACTION_PASSWORD_FAILED.equals(action)) {
            onPasswordFailed(context, intent, intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class));
        } else if (ACTION_PASSWORD_SUCCEEDED.equals(action)) {
            onPasswordSucceeded(context, intent, intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class));
        } else if (ACTION_DEVICE_ADMIN_ENABLED.equals(action)) {
            onEnabled(context, intent);
        } else if (ACTION_DEVICE_ADMIN_DISABLE_REQUESTED.equals(action)) {
            CharSequence res = onDisableRequested(context, intent);
            if (res != null) {
                Bundle extras = getResultExtras(true);
                extras.putCharSequence(EXTRA_DISABLE_WARNING, res);
            }
        } else if (ACTION_DEVICE_ADMIN_DISABLED.equals(action)) {
            onDisabled(context, intent);
        } else if (ACTION_PASSWORD_EXPIRING.equals(action)) {
            onPasswordExpiring(context, intent, intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class));
        } else if (ACTION_PROFILE_PROVISIONING_COMPLETE.equals(action)) {
            onProfileProvisioningComplete(context, intent);
        } else if (ACTION_CHOOSE_PRIVATE_KEY_ALIAS.equals(action)) {
            int uid = intent.getIntExtra(EXTRA_CHOOSE_PRIVATE_KEY_SENDER_UID, -1);
            Uri uri = intent.getParcelableExtra(EXTRA_CHOOSE_PRIVATE_KEY_URI, Uri.class);
            String alias = intent.getStringExtra(EXTRA_CHOOSE_PRIVATE_KEY_ALIAS);
            String chosenAlias = onChoosePrivateKeyAlias(context, intent, uid, uri, alias);
            setResultData(chosenAlias);
        } else if (ACTION_LOCK_TASK_ENTERING.equals(action)) {
            String pkg = intent.getStringExtra(EXTRA_LOCK_TASK_PACKAGE);
            onLockTaskModeEntering(context, intent, pkg);
        } else if (ACTION_LOCK_TASK_EXITING.equals(action)) {
            onLockTaskModeExiting(context, intent);
        } else if (ACTION_NOTIFY_PENDING_SYSTEM_UPDATE.equals(action)) {
            long receivedTime = intent.getLongExtra(EXTRA_SYSTEM_UPDATE_RECEIVED_TIME, -1);
            onSystemUpdatePending(context, intent, receivedTime);
        } else if (ACTION_BUGREPORT_SHARING_DECLINED.equals(action)) {
            onBugreportSharingDeclined(context, intent);
        } else if (ACTION_BUGREPORT_SHARE.equals(action)) {
            String bugreportFileHash = intent.getStringExtra(EXTRA_BUGREPORT_HASH);
            onBugreportShared(context, intent, bugreportFileHash);
        } else if (ACTION_BUGREPORT_FAILED.equals(action)) {
            int failureCode = intent.getIntExtra(EXTRA_BUGREPORT_FAILURE_REASON,
                    BUGREPORT_FAILURE_FAILED_COMPLETING);
            onBugreportFailed(context, intent, failureCode);
        } else if (ACTION_SECURITY_LOGS_AVAILABLE.equals(action)) {
            onSecurityLogsAvailable(context, intent);
        } else if (ACTION_NETWORK_LOGS_AVAILABLE.equals(action)) {
            long batchToken = intent.getLongExtra(EXTRA_NETWORK_LOGS_TOKEN, -1);
            int networkLogsCount = intent.getIntExtra(EXTRA_NETWORK_LOGS_COUNT, 0);
            onNetworkLogsAvailable(context, intent, batchToken, networkLogsCount);
        } else if (ACTION_USER_ADDED.equals(action)) {
            onUserAdded(context, intent, intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class));
        } else if (ACTION_USER_REMOVED.equals(action)) {
            onUserRemoved(context, intent, intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class));
        } else if (ACTION_USER_STARTED.equals(action)) {
            onUserStarted(context, intent, intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class));
        } else if (ACTION_USER_STOPPED.equals(action)) {
            onUserStopped(context, intent, intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class));
        } else if (ACTION_USER_SWITCHED.equals(action)) {
            onUserSwitched(context, intent, intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class));
        } else if (ACTION_TRANSFER_OWNERSHIP_COMPLETE.equals(action)) {
            PersistableBundle bundle = intent.getParcelableExtra(
                    EXTRA_TRANSFER_OWNERSHIP_ADMIN_EXTRAS_BUNDLE, PersistableBundle.class);
            onTransferOwnershipComplete(context, bundle);
        } else if (ACTION_AFFILIATED_PROFILE_TRANSFER_OWNERSHIP_COMPLETE.equals(action)) {
            onTransferAffiliatedProfileOwnershipComplete(context,
                    intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class));
        } else if (ACTION_OPERATION_SAFETY_STATE_CHANGED.equals(action)) {
            onOperationSafetyStateChanged(context, intent);
        } else if (ACTION_COMPLIANCE_ACKNOWLEDGEMENT_REQUIRED.equals(action)) {
            onComplianceAcknowledgementRequired(context, intent);
        }
    }
}
