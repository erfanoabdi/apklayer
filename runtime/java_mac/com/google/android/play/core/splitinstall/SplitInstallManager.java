package com.google.android.play.core.splitinstall;

import com.google.android.play.core.tasks.Task;

import java.util.List;
import java.util.Set;

// com.google.android.play.core.splitinstall.SplitInstallManager -- the entry
// point for on-demand dynamic feature modules. On a de-Googled device there is
// no Play Store to fetch a module from, so:
//   - getInstalledModules() returns the empty set (only the base is installed;
//     Mac.apk does not split-install anything at runtime),
//   - startInstall() fails its Task with a SplitInstallException carrying
//     SplitInstallErrorCode.API_NOT_AVAILABLE (the honest "no backend" answer),
//   - deferredInstall/deferredUninstall/deferredLanguageInstall resolve cleanly
//     (a request to "eventually" install is accepted as a no-op),
//   - register/unregister/cancel are no-ops.
// Signatures mirror the published Play Core SplitInstallManager interface so the
// app's standard "request module -> on failure, degrade" flow runs instead of
// crashing on a missing class.
public interface SplitInstallManager {

    // Immediate install request. We cannot install anything, so the returned
    // Task fails with SplitInstallException(API_NOT_AVAILABLE).
    Task<Integer> startInstall(SplitInstallRequest request);

    // Deferred (best-effort, eventually) requests are accepted as no-ops and
    // resolve successfully -- the app is told its request was queued, which is
    // honest (there is simply no backend to act on it).
    Task<Void> deferredInstall(List<String> moduleNames);
    Task<Void> deferredUninstall(List<String> moduleNames);
    Task<Void> deferredLanguageInstall(List<java.util.Locale> languages);
    Task<Void> deferredLanguageUninstall(List<java.util.Locale> languages);

    // Only the base module is present at runtime.
    Set<String> getInstalledModules();
    Set<String> getInstalledLanguages();

    // No active session ever exists, so cancel and confirmation are no-ops.
    Task<Void> cancelInstall(int sessionId);
    boolean startConfirmationDialogForResult(SplitInstallSessionState sessionState,
                                             android.app.Activity activity, int requestCode)
            throws android.content.IntentSender.SendIntentException;
    boolean startConfirmationDialogForResult(SplitInstallSessionState sessionState,
                                             Object intentSenderForResultStarter, int requestCode)
            throws android.content.IntentSender.SendIntentException;

    // Session-state queries. We never have a session.
    Task<SplitInstallSessionState> getSessionState(int sessionId);
    Task<List<SplitInstallSessionState>> getSessionStates();

    // Progress observers. No state is ever delivered.
    void registerListener(SplitInstallStateUpdatedListener listener);
    void unregisterListener(SplitInstallStateUpdatedListener listener);
}
