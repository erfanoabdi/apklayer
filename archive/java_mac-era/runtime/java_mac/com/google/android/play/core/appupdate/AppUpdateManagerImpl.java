package com.google.android.play.core.appupdate;

import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.tasks.Task;
import com.google.android.play.core.tasks.Tasks;

// Internal concrete AppUpdateManager. NOT part of the published Play Core API
// (apps only see the AppUpdateManager interface) -- this is our backing impl.
// Every path reports the honest "no Play Store backend" outcome: no update is
// ever available and no flow is ever started.
final class AppUpdateManagerImpl implements AppUpdateManager {
    private final String packageName;

    AppUpdateManagerImpl(Context context) {
        String pkg = null;
        try {
            if (context != null) pkg = context.getPackageName();
        } catch (Throwable ignored) {
            // Defensive: a bare/partial Context must not break update probing.
        }
        this.packageName = (pkg != null) ? pkg : "com.macapk.unknown";
    }

    @Override
    public Task<AppUpdateInfo> getAppUpdateInfo() {
        // AppUpdateInfo's constructor is package-private; we are in the same
        // package, so we can build the "no update available" snapshot directly.
        return Tasks.forResult(new AppUpdateInfo(packageName));
    }

    @Override
    public void registerListener(InstallStateUpdatedListener listener) {
        // No download ever starts, so there is nothing to notify; keeping no
        // reference also avoids leaking the listener.
    }

    @Override
    public void unregisterListener(InstallStateUpdatedListener listener) {
        // Symmetric no-op.
    }

    @Override
    public boolean startUpdateFlowForResult(AppUpdateInfo appUpdateInfo, int appUpdateType,
                                            Activity activity, int requestCode)
            throws IntentSender.SendIntentException {
        return false;
    }

    @Override
    public boolean startUpdateFlowForResult(AppUpdateInfo appUpdateInfo, int appUpdateType,
                                            Object intentSenderForResultStarter, int requestCode)
            throws IntentSender.SendIntentException {
        return false;
    }

    @Override
    public boolean startUpdateFlowForResult(AppUpdateInfo appUpdateInfo, Activity activity,
                                            AppUpdateOptions options, int requestCode)
            throws IntentSender.SendIntentException {
        return false;
    }

    @Override
    public boolean startUpdateFlowForResult(AppUpdateInfo appUpdateInfo,
                                            Object intentSenderForResultStarter,
                                            AppUpdateOptions options, int requestCode)
            throws IntentSender.SendIntentException {
        return false;
    }

    @Override
    public Task<Integer> startUpdateFlow(AppUpdateInfo appUpdateInfo, Activity activity,
                                         AppUpdateOptions options) {
        // ActivityResult.RESULT_IN_APP_UPDATE_FAILED indicates the flow did not
        // start; mirrors what real Play Core returns when there is nothing to do.
        return Tasks.forResult(Integer.valueOf(
                com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED));
    }

    @Override
    public Task<Void> completeUpdate() {
        // Nothing downloaded; resolve immediately so callers' onComplete fires.
        return Tasks.forResult(null);
    }
}
