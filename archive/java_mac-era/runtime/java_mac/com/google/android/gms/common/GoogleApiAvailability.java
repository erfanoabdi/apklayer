// Mac.apk GMS-availability shim (app-agnostic). See GoogleApiAvailabilityLight
// for the full rationale: on macOS there is no Play Services system app, so the
// APK's own real GoogleApiAvailability returns SERVICE_MISSING; Mac.apk reports
// what a real Android device reports (GMS available) and degrades the underlying
// service-backed operations gracefully. This is the full half of the API
// (com.google.android.gms.common.GoogleApiAvailability); it extends the light
// half. Firebase C++ messaging init binds against
// GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context).
package com.google.android.gms.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

public class GoogleApiAvailability extends GoogleApiAvailabilityLight {
    public static final int GOOGLE_PLAY_SERVICES_VERSION_CODE =
            GoogleApiAvailabilityLight.GOOGLE_PLAY_SERVICES_VERSION_CODE;

    private static final GoogleApiAvailability INSTANCE = new GoogleApiAvailability();

    GoogleApiAvailability() {}

    public static GoogleApiAvailability getInstance() {
        return INSTANCE;
    }

    /**
     * On a device with no Google Play Services, "make available" cannot honestly
     * succeed -- there is nothing to install/enable and no Play Store to do it.
     * The real client library completes this Task with an ApiException carrying
     * SERVICE_MISSING; an app awaiting it must be told the truth so it takes its
     * degrade path (mirrors the already-honest Firebase-C++
     * GoogleApiAvailabilityHelper.makeGooglePlayServicesAvailable() == false).
     * With the legacy spoof (MACAPK_GMS_HONEST_MISSING=0) it resolves as before.
     */
    public Task<Void> makeGooglePlayServicesAvailable(Activity activity) {
        if (HONEST_MISSING) {
            return Tasks.forException(new com.google.android.gms.common.api.ApiException(
                    new com.google.android.gms.common.api.Status(
                            ConnectionResult.SERVICE_MISSING,
                            "Google Play services is missing on this device.")));
        }
        return Tasks.forResult(null);
    }

    /** Real GMS returns a non-null Dialog describing the availability error
     *  (which the app may show). Returning null here NPEs the (common) pattern
     *  `getErrorDialog(...).show()`, so we hand back a benign empty Dialog bound
     *  to the activity. The availability VERDICT is unchanged (SERVICE_MISSING
     *  via isGooglePlayServicesAvailable); this only stops a crash on the rarely
     *  reached error-dialog path. The dialog has no Play-resolution action
     *  because there is nothing to resolve on a de-Googled device. */
    public Dialog getErrorDialog(Activity activity, int errorCode, int requestCode) {
        return getErrorDialog(activity, errorCode, requestCode, null);
    }

    public Dialog getErrorDialog(Activity activity, int errorCode, int requestCode,
                                 DialogInterface.OnCancelListener cancelListener) {
        if (errorCode == ConnectionResult.SUCCESS) return null;
        Dialog d = (activity != null) ? new Dialog(activity) : new Dialog((Context) null);
        if (cancelListener != null) d.setOnCancelListener(cancelListener);
        return d;
    }

    /** No error -> nothing to notify. */
    public void showErrorNotification(Context context, int errorCode) {}

    /** Obfuscated internal (zah) in the shipped GMS SDK: shows an error
     *  notification/dialog for a ConnectionResult and reports whether it did.
     *  Several games (CandyCrush, GeoDash, Alto, DumbWays) call it during their
     *  Play-services availability check; a missing method threw NoSuchMethodError
     *  and aborted that init path. With no real Play services there's no error to
     *  surface, so we report false (nothing shown) and let the app proceed. */
    public boolean zah(Context context, ConnectionResult result, int requestCode) {
        return false;
    }
    /** Same internal as {@link #zah} under a different GMS-version obfuscation
     *  (Temple Run 2's bundled play-services-base uses the name zad). Same
     *  contract: nothing to surface, return false. */
    public boolean zad(Context context, ConnectionResult result, int requestCode) {
        return false;
    }

    /** Real GMS: builds the Play-Store / settings Intent that resolves the
     *  given connection error, or null when the error has no user-resolvable
     *  fix. Among Us' bundled play-services-base calls this (obfuscated
     *  caller zao) on its connection-failed path after the GMS service bind
     *  fails; the overload missing threw NoSuchMethodError and killed the
     *  posted runnable BEFORE it could complete the pending sign-in Task, so
     *  the game's token fetch never resolved. There is no Play Store on
     *  macOS, so "not resolvable" (null) is the honest answer; the SDK then
     *  fails the connection cleanly, exactly like a device without Play
     *  services. */
    public android.content.Intent getErrorResolutionIntent(
            Context context, int errorCode, String tag) {
        return null;
    }

    /** Companion overload on the full class (the Light half already has the
     *  (Context,int,int) form): a ConnectionResult-shaped resolution request.
     *  Same contract as above: nothing to resolve on macOS, return null. */
    public android.app.PendingIntent getErrorResolutionPendingIntent(
            Context context, ConnectionResult result) {
        return null;
    }
}
