package com.google.android.play.core.appupdate;

import android.app.PendingIntent;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

// com.google.android.play.core.appupdate.AppUpdateInfo -- the result of
// AppUpdateManager.getAppUpdateInfo(). On a de-Googled device there is no Play
// Store to query, so this always reports "no update available": updateAvailability
// == UPDATE_NOT_AVAILABLE, no update type allowed, no version code, no install in
// progress. Signatures mirror the published Play Core AppUpdateInfo.
public final class AppUpdateInfo {
    private final String packageName;

    AppUpdateInfo(String packageName) {
        this.packageName = packageName;
    }

    public String packageName() { return packageName; }

    public int availableVersionCode() {
        // Sentinel returned by real Play Core when no update is available.
        return -1;
    }

    public int updateAvailability() {
        return UpdateAvailability.UPDATE_NOT_AVAILABLE;
    }

    public int installStatus() {
        return InstallStatus.UNKNOWN;
    }

    // No update is ever offered, so neither flexible nor immediate is allowed.
    public boolean isUpdateTypeAllowed(int appUpdateType) {
        return false;
    }

    public boolean isUpdateTypeAllowed(AppUpdateOptions options) {
        return false;
    }

    public Integer clientVersionStalenessDays() { return null; }

    public int updatePriority() { return 0; }

    public long bytesDownloaded() { return 0L; }

    public long totalBytesToDownload() { return 0L; }

    // No resolvable update flow exists; real Play Core returns null when the
    // requested type is not allowed.
    public PendingIntent getUpdatePendingIntent() { return null; }
}
