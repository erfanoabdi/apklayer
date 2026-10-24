package com.google.android.play.core.appupdate;

import android.app.Activity;
import android.content.IntentSender;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.tasks.Task;
import com.google.android.play.core.tasks.Tasks;

// com.google.android.play.core.appupdate.AppUpdateManager -- the entry point for
// Play in-app updates. On a de-Googled device there is no Play Store backend to
// query, so getAppUpdateInfo() resolves to an AppUpdateInfo reporting
// UpdateAvailability.UPDATE_NOT_AVAILABLE (the honest answer), every
// startUpdateFlow* path is a clean no-op that reports "no flow started", and
// completeUpdate() resolves immediately (there is nothing to install).
//
// Apps that follow the standard pattern (getAppUpdateInfo -> check
// updateAvailability -> branch) therefore take their "no update" branch and
// continue normally, instead of crashing on a NoClassDefFound for this type.
// Signatures mirror the published Play Core AppUpdateManager interface.
public interface AppUpdateManager {

    // Returns a Task that resolves with the current update info. We never offer
    // an update, so the resolved AppUpdateInfo reports UPDATE_NOT_AVAILABLE.
    Task<AppUpdateInfo> getAppUpdateInfo();

    // Flexible-update progress observers. We never start a download, so no state
    // is ever delivered to a registered listener; register/unregister just track
    // nothing.
    void registerListener(InstallStateUpdatedListener listener);
    void unregisterListener(InstallStateUpdatedListener listener);

    // Legacy 4-arg flow launcher. No update is ever offered, so this returns
    // false (no flow was started) without launching anything.
    boolean startUpdateFlowForResult(AppUpdateInfo appUpdateInfo, int appUpdateType,
                                     Activity activity, int requestCode)
            throws IntentSender.SendIntentException;

    // Legacy IntentSenderForResultStarter variant. The starter type lives in
    // com.google.android.play.core.common; we accept Object to avoid depending
    // on a type outside this bucket while still matching the erased call site.
    boolean startUpdateFlowForResult(AppUpdateInfo appUpdateInfo, int appUpdateType,
                                     Object intentSenderForResultStarter, int requestCode)
            throws IntentSender.SendIntentException;

    // Modern options-based flow launcher (Activity target).
    boolean startUpdateFlowForResult(AppUpdateInfo appUpdateInfo, Activity activity,
                                     AppUpdateOptions options, int requestCode)
            throws IntentSender.SendIntentException;

    // Modern options-based flow launcher (IntentSenderForResultStarter target).
    boolean startUpdateFlowForResult(AppUpdateInfo appUpdateInfo,
                                     Object intentSenderForResultStarter,
                                     AppUpdateOptions options, int requestCode)
            throws IntentSender.SendIntentException;

    // Task-returning options flow (newer artifacts). Resolves with false: no
    // flow was started because no update is available.
    Task<Integer> startUpdateFlow(AppUpdateInfo appUpdateInfo, Activity activity,
                                  AppUpdateOptions options);

    // Completes a downloaded flexible update by restarting. We never have a
    // downloaded update, so this resolves immediately with no-op success.
    Task<Void> completeUpdate();
}
