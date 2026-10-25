// com.google.android.gms.common.GooglePlayServicesUtilLight -- the DEVICE ORACLE.
//
// This is the class play-services-basement uses to answer "what does this device
// have". Every other availability entry point funnels through it: an app's own
// GoogleApiAvailabilityLight.isGooglePlayServicesAvailable(Context,int) is an
// `invokestatic` straight to the method below (measured on Crossy Road's own
// bundled basement 18.9.0, bytecode offset 19), and GoogleApiAvailability
// inherits that. So this one class decides the verdict for the whole family.
//
// WHY IT IS PARENT-FIRST even though the GMS family is child-first.
// A class that answers "what does this DEVICE have" is answered by the device;
// a class that CARRIES THE APP'S DATA stays the app's. Mac.apk is the device.
// That is not a new rule -- it is the parent-first arm of the decision tree in
// docs/gms-shadow-classloader-contract_2026-06-21.md, already applied to
// play.core.assetpacks.* and to gms.tasks.* for Play-Asset-Delivery apps. What
// is new is recognising the availability oracle as a real implementation of a
// device question rather than a stand-in for an app type. The admission rule
// and why it is safe here live in AppClassLoader.PARENT_FIRST_DEVICE_ORACLES.
//
// WHY THE VERDICT CANNOT BE FIXED AT THE PackageManager LAYER INSTEAD (measured,
// 2026-08-01, from the app's own bytecode -- do not re-derive this).
// The bundled client's own decision tree is:
//   requiresPlayStore = !isWearableWithoutPlayStore(ctx) && !DeviceProperties.zzd(ctx)
//                       // zzd = hasSystemFeature("android.hardware.type.embedded"/"...iot")
//   if requiresPlayStore: getPackageInfo("com.android.vending") -- NameNotFound
//                         -> log "requires the Google Play Store, but it is
//                            missing." -> return 9 (SERVICE_INVALID)
//   then unconditionally: getPackageInfo("com.google.android.gms") -- NameNotFound
//                         -> return 1 (SERVICE_MISSING)
//                         else GoogleSignatureVerifier.zza(pkgInfo, true), which
//                         does Arrays.equals() of the package's signing
//                         certificate against Google's own DER bytes embedded in
//                         com.google.android.gms.common.zzn -> our cert is not
//                         Google's -> return 9.
// So NO answer PackageManager can give yields SUCCESS unless we present Google's
// own signing certificate for a package that does not exist. That is forging
// someone else's signing identity and spoofing a Play-certified device, which
// CLAUDE.md puts explicitly out of scope. Claiming a store-less device class
// (wearable/IoT) does not help either: it only skips the STORE check, and the
// GMS check below it is unconditional, so the best case is 1 instead of 9 -- and
// Firebase hard-fails identically on every non-zero verdict
// (firebase-cpp-sdk ConnectionResultToAvailability maps only 0 to available).
// Answering here, from the provider that actually exists, is the only route that
// does not require a lie.
//
// EVERY MEMBER IS public ON PURPOSE. An app's own GooglePlayServicesUtil
// (play-services-base) EXTENDS this class, so after the parent-first move an
// app-loaded subclass sits over this parent-loaded superclass. JVMS SE21 5.4.4:
// a public member is accessible, while a package-private one requires the same
// RUN-TIME package -- and a run-time package is (name, defining loader), which a
// different loader never matches. Declaring everything public is a legal
// widening of basement's surface and removes the IllegalAccessError hazard that
// bit us as the BouncyCastle ASN1UniversalType failure (see AppClassLoader).
//
// The member list is the measured UNION of this class across the corpus's
// basement versions. Every descriptor is primitives / java.lang.String /
// java.util.concurrent.atomic.AtomicBoolean / android.* -- i.e. LOADER-NEUTRAL,
// which is what makes a single-class parent-first island safe from the
// loader-constraint LinkageError family (JVMS SE21 5.3.4 imposes constraints on
// exactly the return type and formal parameter types). Do NOT add a member whose
// descriptor mentions a com.google.* type; that is precisely the mistake that
// produced the v1.0.510 "GMS loader-constraint JNI poison" crash.
package com.google.android.gms.common;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

import com.macapk.loader.PlayServices;

import java.util.concurrent.atomic.AtomicBoolean;

public class GooglePlayServicesUtilLight {
    public static final String GOOGLE_PLAY_SERVICES_PACKAGE      = "com.google.android.gms";
    public static final String GOOGLE_PLAY_STORE_PACKAGE         = "com.android.vending";
    public static final String GOOGLE_PLAY_GAMES_PACKAGE         = "com.google.android.play.games";
    public static final String GOOGLE_SERVICES_FRAMEWORK_PACKAGE = "com.google.android.gsf";

    public static final int GOOGLE_PLAY_SERVICES_VERSION_CODE = PlayServices.VERSION_CODE;

    // Package-private in basement; public here per the access note above. These
    // are compile-time int constants, so a subclass compiled against basement
    // inlined them and never reads ours -- declared for surface completeness.
    public static final int GMS_AVAILABILITY_NOTIFICATION_ID  = 10436;
    public static final int GMS_GENERAL_ERROR_NOTIFICATION_ID = 10437;

    // NOT a compile-time constant, so a subclass DOES resolve it against this
    // class at run time -- it must be public or the subclass gets IllegalAccessError.
    public static final AtomicBoolean sCanceledAvailabilityNotification = new AtomicBoolean();

    // GMS internals write these directly.
    public static boolean zza;
    public static boolean zzb;

    public GooglePlayServicesUtilLight() {}

    /** THE answer. See PlayServices.availability() for what it means and why. */
    public static int isGooglePlayServicesAvailable(Context context) {
        return PlayServices.availability();
    }

    /**
     * minApkVersion is deliberately IGNORED. Our provider is in-process and
     * always current, so there is no "installed services are out of date" state
     * to report; comparing against our own constant would hand
     * SERVICE_VERSION_UPDATE_REQUIRED(2) to any app built against a newer
     * basement than ours, which is a failure mode a real device would not have.
     */
    public static int isGooglePlayServicesAvailable(Context context, int minApkVersion) {
        return PlayServices.availability();
    }

    // Must stay false: the caller remaps a true here into SERVICE_UPDATING(18),
    // i.e. "ask the user to wait", which would be a lie about a transient state.
    public static boolean isPlayServicesPossiblyUpdating(Context context, int errorCode) { return false; }
    public static boolean isPlayStorePossiblyUpdating(Context context, int errorCode)    { return false; }
    public static boolean isUninstalledAppPossiblyUpdating(Context context, String pkg)  { return false; }
    public static boolean zza(Context context, String pkg)                               { return false; }

    public static int getApkVersion(Context context)    { return PlayServices.VERSION_CODE; }
    public static int getClientVersion(Context context) { return PlayServices.VERSION_CODE; }

    public static String getErrorString(int errorCode) {
        switch (errorCode) {
            case ConnectionResult.SUCCESS:         return "SUCCESS";
            case ConnectionResult.SERVICE_MISSING: return "SERVICE_MISSING";
            default:                               return "ConnectionResult: " + errorCode;
        }
    }

    /** SERVICE_MISSING is nominally user-resolvable on a real device (install
     *  Play Services from the Store). There is no Store here to resolve against
     *  and getGooglePlayServicesAvailabilityRecoveryIntent returns null, so
     *  offering a resolution that cannot resolve is worse than letting the app
     *  take its degrade path. */
    public static boolean isUserRecoverableError(int errorCode) { return false; }

    public static void cancelAvailabilityErrorNotifications(Context context) {}
    public static void enableUsingApkIndependentContext() {}

    /** Throws only when the device reports unavailable, which under the default
     *  stance it does not. NB: the thrown types are OUR classes, so an app that
     *  catches its OWN copy would not match -- harmless while availability()
     *  returns SUCCESS, and the reason this method is not a verdict path. */
    public static void ensurePlayServicesAvailable(Context context, int minApkVersion)
            throws GooglePlayServicesRepairableException, GooglePlayServicesNotAvailableException {
        int result = PlayServices.availability();
        if (result != ConnectionResult.SUCCESS) {
            throw new GooglePlayServicesNotAvailableException(result);
        }
    }

    public static PendingIntent getErrorPendingIntent(int errorCode, Context context, int requestCode) {
        return null;
    }

    public static Intent getGooglePlayServicesAvailabilityRecoveryIntent(int errorCode) { return null; }

    public static Context   getRemoteContext(Context context)  { return null; }
    public static Resources getRemoteResource(Context context) { return null; }

    public static boolean honorsDebugCertificates(Context context)               { return false; }
    public static boolean isGooglePlayServicesUid(Context context, int uid)      { return false; }
    public static boolean isRestrictedUserProfile(Context context)               { return false; }
    public static boolean isSidewinderDevice(Context context)                    { return false; }

    /** "Does this uid own this package?" -- the real library's answer, not a
     *  constant. play-services-basement (read from Temple Run 2's own copy):
     *  GooglePlayServicesUtilLight.uidHasPackageName -> UidVerifier
     *  .uidHasPackageName -> Wrappers.packageManager(ctx).zza(uid, pkg), which
     *  is AppOpsManager.checkPackage(uid, pkg) with SecurityException mapped to
     *  false and a null app-ops service thrown as an NPE with this message.
     *
     *  This used to return false unconditionally. Because this class is a
     *  PARENT-FIRST device oracle (AppClassLoader.PARENT_FIRST_DEVICE_ORACLES),
     *  that constant reached every app's OWN bundled SDK, and admission rule
     *  (2) there -- "our shadow is a REAL implementation of that answer" -- was
     *  quietly broken by it. The caller that exposed it: an app's in-app
     *  AppMeasurementService checks its binder caller with exactly this call
     *  (zzjd.zzO) and threw "Unknown calling package name '<its own
     *  package>'" at the app itself, so no analytics call was ever answered and
     *  Temple Run 2 never left its loading screen (v1.0.1635). */
    public static boolean uidHasPackageName(Context context, int uid, String pkg) {
        android.app.AppOpsManager ops =
                (android.app.AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) {
            throw new NullPointerException("context.getSystemService(Context.APP_OPS_SERVICE) is null");
        }
        try {
            ops.checkPackage(uid, pkg);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
