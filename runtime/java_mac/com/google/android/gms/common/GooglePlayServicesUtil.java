// com.google.android.gms.common.GooglePlayServicesUtil -- the DEPRECATED legacy
// static facade for the GMS availability check. Modern apps use
// GoogleApiAvailability, but older SDKs (and many shipped third-party libraries)
// still reference the static methods here, so a missing class is a hard
// NoClassDefFound. Every method delegates to GoogleApiAvailability so the
// "de-Googled device" behavior is identical: availability reports SERVICE_MISSING
// (the honest answer for a device with no GMS; gated on MACAPK_GMS_HONEST_MISSING
// -- see GoogleApiAvailabilityLight), the underlying service operations degrade,
// and the
// error-dialog path returns a benign dialog instead of crashing. Mirrors the GMS
// public static surface.
package com.google.android.gms.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

public final class GooglePlayServicesUtil {
    private GooglePlayServicesUtil() {}

    // A recent GMS client version code (matches GoogleApiAvailabilityLight).
    public static final int GOOGLE_PLAY_SERVICES_VERSION_CODE =
            GoogleApiAvailabilityLight.GOOGLE_PLAY_SERVICES_VERSION_CODE;
    public static final String GOOGLE_PLAY_SERVICES_PACKAGE = "com.google.android.gms";
    public static final String GOOGLE_PLAY_STORE_PACKAGE = "com.android.vending";

    public static int isGooglePlayServicesAvailable(Context context) {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
    }

    public static boolean isUserRecoverableError(int errorCode) {
        return GoogleApiAvailability.getInstance().isUserResolvableError(errorCode);
    }

    public static String getErrorString(int errorCode) {
        return GoogleApiAvailability.getInstance().getErrorString(errorCode);
    }

    public static Dialog getErrorDialog(int errorCode, Activity activity, int requestCode) {
        return GoogleApiAvailability.getInstance().getErrorDialog(activity, errorCode, requestCode);
    }

    public static Dialog getErrorDialog(int errorCode, Activity activity, int requestCode,
                                        DialogInterface.OnCancelListener cancelListener) {
        return GoogleApiAvailability.getInstance()
                .getErrorDialog(activity, errorCode, requestCode, cancelListener);
    }

    public static void showErrorNotification(int errorCode, Context context) {
        GoogleApiAvailability.getInstance().showErrorNotification(context, errorCode);
    }

    public static boolean showErrorDialogFragment(int errorCode, Activity activity, int requestCode) {
        return false;
    }

    public static boolean showErrorDialogFragment(int errorCode, Activity activity, int requestCode,
                                                  DialogInterface.OnCancelListener cancelListener) {
        return false;
    }

    public static Intent getErrorPendingIntent(int errorCode, Context context, int requestCode) {
        return null;
    }

    public static Context getRemoteContext(Context context) {
        return null;
    }

    public static java.util.concurrent.Future<?> ensurePlayServicesAvailable(Context context, int minApkVersion) {
        return null;
    }
}
