// Mac.apk GMS-availability shim (app-agnostic, any Google-Play-Services app).
//
// Why this exists, and what the HONEST answer is:
// Mac.apk is a device with NO Google Play Services installed -- exactly like an
// Amazon Fire tablet or a de-Googled (no-gapps) phone. On such a device the real
// Google client library (play-services-basement) does:
//     getPackageInfo("com.google.android.gms", GET_SIGNATURES)
//         -> throws NameNotFoundException
//         -> GooglePlayServicesUtilLight returns ConnectionResult.SERVICE_MISSING (1)
// So SERVICE_MISSING is the TRUTHFUL answer here, and this class returns it
// (gated on MACAPK_GMS_HONEST_MISSING, default-ON). Well-behaved apps -- Firebase
// FIS/FCM, Crossy Road, Among Us, Minecraft Bedrock -- read this int, see it != 0,
// and take their non-GMS / degrade path; they run fine on Fire tablets and they
// run fine here. (SUCCESS(0) is ONLY what a microG-EQUIPPED device returns, because
// microG *is* a GMS implementation. Mac.apk ships no GMS provider, so copying
// microG's SUCCESS would be a Play-Services spoof, not faithfulness.)
//
// HISTORY -- why this used to (wrongly) return SUCCESS, and why that was removed:
// An earlier design returned SUCCESS(0) to stop the Firebase C++ SDK's messaging
// module (google_play_services::CheckAvailability()) from returning
// kInitResultFailedMissingDependency and throwing during FirebaseApp init (the
// old Crossy Road boot-crash theory). But that spoof caused a WORSE failure for
// apps whose Firebase runs on the host JVM: with "GMS present" asserted, Firebase
// Cloud Messaging's SyncTask believed it could obtain an FCM token and spun in an
// unbounded "Rpc: No response" registration-retry loop -- which HUNG Minecraft
// Bedrock's boot. The real fix for the Crossy class is CLASSLOADER child-first
// (MACAPK_GMS_CHILD_FIRST, default-ON): a bundling app resolves this check to its
// OWN R8'd GMS client, which computes SERVICE_MISSING on its own -- so those apps
// no longer depend on this shadow's verdict at all. That decoupling is what lets
// this shadow answer honestly without re-breaking them.
//
// The FCM loop is actually killed one layer lower, at the PackageManager: Firebase
// Metadata gates "GMS core present" on
//     checkPermission("com.google.android.c2dm.permission.SEND",
//                     "com.google.android.gms") == PERMISSION_DENIED
// which MacApkPackageManager.checkPermission now answers honestly (an
// uninstalled package holds no permissions) -> SyncTask self-terminates, no Rpc.
//
// This is the same "reimplement the real Android surface" pattern the project
// uses everywhere, not a per-APK hack: any GMS app gets the same honest answer,
// and non-GMS apps (cocos2d-x, etc.) never load this class at all.
//
// Mirrors the AOSP / GMS public API surface (com.google.android.gms.common).
package com.google.android.gms.common;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * Lightweight half of the GMS availability API. The full
 * {@link GoogleApiAvailability} extends this. On Mac.apk, Google Play Services
 * is reported MISSING (ConnectionResult.SERVICE_MISSING) because that is what a
 * device with no GMS installed truthfully reports; the GMS-backed service calls
 * degrade and apps take their non-GMS path. See the file header for why the old
 * SUCCESS spoof was removed.
 */
public class GoogleApiAvailabilityLight {
    // A recent Google Play Services client version code. Apps compare their
    // bundled com.google.android.gms.version (the @integer the manifest declares)
    // against this; a high value means "the device's GMS is new enough", so no
    // SERVICE_VERSION_UPDATE_REQUIRED path is taken.
    public static final int GOOGLE_PLAY_SERVICES_VERSION_CODE = 244400000;

    private static final GoogleApiAvailabilityLight INSTANCE =
            new GoogleApiAvailabilityLight();

    GoogleApiAvailabilityLight() {}

    public static GoogleApiAvailabilityLight getInstance() {
        return INSTANCE;
    }

    // THE VERDICT LIVES IN ONE PLACE: com.macapk.loader.PlayServices.
    //
    // Do not re-derive it here or anywhere else. Mac.apk previously answered the
    // Play-Services question in four contradictory ways at once (see the
    // PlayServices class header for the list), and which contradiction an app
    // heard came down to whether MACAPK_GMS_CHILD_FIRST routed the question to
    // our shadow or to the app's own bundled client. That is why the flag was
    // load-bearing: not because either setting was right, but because each one
    // selected a different one of our disagreeing answers.
    //
    // Note this class is NOT the one that decides for a GMS-bundling app: such
    // an app's own GoogleApiAvailabilityLight reaches the verdict by
    // `invokestatic` into GooglePlayServicesUtilLight, which is parent-first
    // (AppClassLoader.PARENT_FIRST_DEVICE_ORACLES). This class answers for apps
    // that bundle no GMS client of their own. Both now return the same value,
    // which is the property that makes MACAPK_GMS_CHILD_FIRST inert for the
    // availability question.
    //
    // MEASURED, Crossy Road, four live runs on 2026-08-01: the app's managed
    // startup survives IF AND ONLY IF this verdict is SUCCESS. A non-SUCCESS
    // answer makes Firebase's C++ layer report messaging unavailable, after
    // which Unity's C# FirebaseApp.CreateAndTrack throws
    // InitializationException("messaging (missing dependency)"); the resulting
    // TypeInitializationException permanently poisons
    // Firebase.Crashlytics.PlatformAccessor and kills the app's startup while the
    // engine keeps rendering an empty scene at ~56fps -- which is why this
    // presented for months as a render bug.
    static final boolean HONEST_MISSING =
            com.macapk.loader.PlayServices.availability()
                    != ConnectionResult.SUCCESS;

    /**
     * The honest answer for a device with NO Google Play Services installed is
     * ConnectionResult.SERVICE_MISSING (1) -- exactly what the real Google
     * client library returns when getPackageInfo("com.google.android.gms")
     * throws NameNotFoundException. Well-behaved apps (Firebase FIS/FCM, Crossy
     * Road, Among Us, Minecraft Bedrock) read this int, see it != 0, and take
     * their non-GMS / degrade path. Reporting SUCCESS was a Play-Services spoof
     * that made callers proceed into GMS APIs that do not exist and (for
     * Firebase FCM) spin forever waiting on an FCM token that a GMS-less device
     * genuinely cannot obtain.
     */
    public int isGooglePlayServicesAvailable(Context context) {
        return HONEST_MISSING ? ConnectionResult.SERVICE_MISSING : ConnectionResult.SUCCESS;
    }

    public int isGooglePlayServicesAvailable(Context context, int minApkVersion) {
        return HONEST_MISSING ? ConnectionResult.SERVICE_MISSING : ConnectionResult.SUCCESS;
    }

    public String getErrorString(int errorCode) {
        if (errorCode == ConnectionResult.SUCCESS) return "SUCCESS";
        if (errorCode == ConnectionResult.SERVICE_MISSING) return "SERVICE_MISSING";
        return "ConnectionResult: " + errorCode;
    }

    /** On a de-Googled device SERVICE_MISSING is technically "user-resolvable"
     *  (real GMS would offer a Play-Store install), but there is no Play Store
     *  to resolve against here, so getErrorResolutionIntent returns null and we
     *  report NOT user-resolvable -- offering a resolution dialog that cannot
     *  resolve is worse than letting the app take its degrade path. This keeps
     *  the honest-missing branch crash-free (no bogus resolution UI). */
    public boolean isUserResolvableError(int errorCode) {
        return false;
    }

    /**
     * GMS verifyGooglePlayServicesIsAvailable(Context, int): on a device where
     * services are missing this THROWS GooglePlayServicesNotAvailableException
     * (a checked exception callers wrap in try/catch). With the honest-missing
     * contract we throw it (code SERVICE_MISSING); with the legacy spoof it is
     * a successful no-op.
     */
    public void verifyGooglePlayServicesIsAvailable(Context context, int minApkVersion)
            throws GooglePlayServicesNotAvailableException {
        if (HONEST_MISSING) {
            throw new GooglePlayServicesNotAvailableException(ConnectionResult.SERVICE_MISSING);
        }
        // legacy spoof: available
    }

    public Intent getErrorResolutionIntent(int errorCode) {
        return null;
    }

    public PendingIntent getErrorResolutionPendingIntent(
            Context context, int errorCode, int requestCode) {
        return null;
    }
}
