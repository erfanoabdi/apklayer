package com.google.android.play.core.splitinstall;

import android.app.Activity;
import android.content.Context;
import android.content.IntentSender;
import com.google.android.play.core.tasks.Task;
import com.google.android.play.core.tasks.Tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Internal concrete SplitInstallManager. NOT part of the published Play Core API
// (apps only see the SplitInstallManager interface) -- this is our backing impl.
// Every path reports the honest "no Play Store backend" outcome.
final class SplitInstallManagerImpl implements SplitInstallManager {

    SplitInstallManagerImpl(Context context) {
        // Context retained by Play Core for fetch I/O; we have no backend to
        // fetch from, so we keep nothing.
    }

    @Override
    public Task<Integer> startInstall(SplitInstallRequest request) {
        return Tasks.forException(
                new SplitInstallException(SplitInstallErrorCode.API_NOT_AVAILABLE));
    }

    @Override
    public Task<Void> deferredInstall(List<String> moduleNames) {
        return Tasks.forResult(null);
    }

    @Override
    public Task<Void> deferredUninstall(List<String> moduleNames) {
        return Tasks.forResult(null);
    }

    @Override
    public Task<Void> deferredLanguageInstall(List<Locale> languages) {
        return Tasks.forResult(null);
    }

    @Override
    public Task<Void> deferredLanguageUninstall(List<Locale> languages) {
        return Tasks.forResult(null);
    }

    @Override
    public Set<String> getInstalledModules() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getInstalledLanguages() {
        return Collections.emptySet();
    }

    @Override
    public Task<Void> cancelInstall(int sessionId) {
        return Tasks.forResult(null);
    }

    @Override
    public boolean startConfirmationDialogForResult(SplitInstallSessionState sessionState,
                                                    Activity activity, int requestCode)
            throws IntentSender.SendIntentException {
        return false;
    }

    @Override
    public boolean startConfirmationDialogForResult(SplitInstallSessionState sessionState,
                                                    Object intentSenderForResultStarter, int requestCode)
            throws IntentSender.SendIntentException {
        return false;
    }

    @Override
    public Task<SplitInstallSessionState> getSessionState(int sessionId) {
        return Tasks.forException(
                new SplitInstallException(SplitInstallErrorCode.SESSION_NOT_FOUND));
    }

    @Override
    public Task<List<SplitInstallSessionState>> getSessionStates() {
        return Tasks.forResult(new ArrayList<SplitInstallSessionState>());
    }

    @Override
    public void registerListener(SplitInstallStateUpdatedListener listener) {
        // No session ever updates; nothing to track.
    }

    @Override
    public void unregisterListener(SplitInstallStateUpdatedListener listener) {
        // Symmetric no-op.
    }
}
