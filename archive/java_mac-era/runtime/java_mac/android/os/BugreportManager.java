// android.os.BugreportManager -- triggers a system bug report (privileged,
// getSystemService(BUGREPORT_SERVICE)). No bug-report subsystem exists on the
// Mac.apk host, so startBugreport immediately reports the error path to the
// callback and cancel/request are no-ops. The type is provided so references
// from diagnostic/feedback SDKs resolve.
//
// AOSP ref: frameworks/base/core/java/android/os/BugreportManager.java
package android.os;

import java.util.concurrent.Executor;

public final class BugreportManager {
    public BugreportManager() {}

    // BugreportCallback result/error codes mirrored from AOSP.
    public abstract static class BugreportCallback {
        public static final int BUGREPORT_ERROR_INVALID_INPUT = 1;
        public static final int BUGREPORT_ERROR_RUNTIME = 2;
        public static final int BUGREPORT_ERROR_USER_DENIED_CONSENT = 3;
        public static final int BUGREPORT_ERROR_USER_CONSENT_TIMED_OUT = 4;
        public static final int BUGREPORT_ERROR_ANOTHER_REPORT_IN_PROGRESS = 5;
        public static final int BUGREPORT_ERROR_NO_BUGREPORT_TO_RETRIEVE = 6;

        public void onProgress(float progress) {}
        public void onError(int errorCode) {}
        public void onFinished() {}
        public void onFinished(String bugreportFile) {}
        public void onEarlyReportFinished() {}
    }

    public void startBugreport(android.os.ParcelFileDescriptor bugreportFd,
                               android.os.ParcelFileDescriptor screenshotFd,
                               Object params,
                               Executor executor,
                               BugreportCallback callback) {
        // No bug-report capability on this host: report the runtime-error path so
        // the caller's callback completes rather than hanging.
        if (callback != null) {
            Runnable r = new Runnable() {
                @Override public void run() {
                    callback.onError(BugreportCallback.BUGREPORT_ERROR_RUNTIME);
                }
            };
            if (executor != null) executor.execute(r); else r.run();
        }
    }

    /** AOSP core/java/android/os/BugreportManager.java:357 -- verbatim
     *  delegation to startBugreport with no screenshot fd and
     *  BugreportParams.BUGREPORT_MODE_TELEPHONY. `params` is typed Object here
     *  because android.os.BugreportParams is @SystemApi and not part of the
     *  surface this tree implements; the mode is irrelevant to the outcome
     *  (this host has no bug-report subsystem, so the callback takes the same
     *  BUGREPORT_ERROR_RUNTIME path startBugreport already reports). */
    public void startConnectivityBugreport(android.os.ParcelFileDescriptor bugreportFd,
                                           Executor executor,
                                           BugreportCallback callback) {
        startBugreport(bugreportFd, null /* screenshotFd */, null /* params */,
                       executor, callback);
    }

    public void cancelBugreport() {}

    public void requestBugreport(Object params, CharSequence shareTitle, CharSequence shareDescription) {}
}
