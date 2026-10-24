// com.macapk.vending.LicensingService -- the com.android.vending
// ILicensingService, hosted in-process.
//
// WHAT THIS IS. On a device, an app that wants to know whether the user bought
// it binds to the Play Store's licensing service and gets back a verdict signed
// by Google. Mac.apk hosts that service itself: we take the app's request, ask
// Google with the user's own account, and hand back GOOGLE'S OWN SIGNED ANSWER,
// untouched. The app's verifier runs unmodified and accepts the token only
// because Google really signed it.
//
// ⛔ THE THREE THINGS THIS FILE WILL NEVER DO:
//   1. Mint a verdict. Every licensed answer carries a Google-signed JWS that
//      came off the wire. There is no synthesis path, flagged or otherwise.
//   2. Answer for a package other than the one running. See enforceCaller().
//   3. Turn "I could not ask" into "you own it". No account, no network and no
//      device registration are all reported as ERROR_CONTACTING_SERVER, which is
//      the LVL's own code for that state and which every classic client treats
//      as RETRY rather than as a verdict.
//
// ---- THE WIRE CONTRACT (read it before touching onTransact) -----------------
// Two transactions, and their shapes are NOT symmetric:
//   txn 1  checkLicense(long nonce, String pkg, ILicenseResultListener)
//          ONEWAY. The caller passes reply == null, so writing to `reply` here
//          would NPE. Do not add a writeNoException(): a oneway AIDL stub emits
//          none, and one extra int desynchronises the client's parcel.
//   txn 2  checkLicenseV2(String pkg, ILicenseV2ResultListener, in Bundle extras)
//          Declared oneway, but pairip's client calls it TWO-WAY (flags 0, a
//          real reply parcel) and treats a `false` return as a hard failure --
//          it raises "Licensing service could not process request", shows an
//          error dialog and exits the process ~30 s later. So we must return
//          TRUE and write NOTHING to the reply.
// The trailing Bundle uses the AIDL nullable-object convention: an int 1
// followed by the bundle body, or a bare int 0 for null.
//
// ⚠ attachInterface() IS LOAD-BEARING, not boilerplate. pairip does not
// hardcode the descriptor when it builds its request -- it writes back whatever
// OUR binder's getInterfaceDescriptor() returns. A Stub that skips
// attachInterface answers null there, the token never matches, and our own
// enforceInterface rejects the call. It must also NOT be the
// IBackgroundLicensingService descriptor: pairip branches on that string and
// takes an entirely different path.
//
// ⚠ THE FETCH RUNS OFF THE CALLER'S THREAD. microG can afford a blocking HTTP
// call inside onTransact because it is a separate process; here onTransact runs
// on the CALLER's thread, and pairip transacts from onServiceConnected -- the
// main thread. Blocking there would freeze the UI for the length of a network
// round-trip. Real Play is asynchronous, and so are we: onTransact returns
// immediately and the verdict is delivered later via the listener, which is
// exactly what the oneway callback is for.
//
// PROTOCOL REFERENCE (read-only, never vendored): microG GmsCore @157c9d86
//   vending-app/.../licensing/{LicensingService.kt,LicenseChecker.kt}
// Parcel layouts verified against the APP'S OWN AIDL-generated stubs.
// Full tabulation: docs/gms-licensing-wire_2026-09-05.md
package com.macapk.vending;

import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import com.macapk.gms.PlayLicenseClient;

public final class LicensingService extends android.app.Service {

    public static final String ACTION_V1 = "com.android.vending.licensing.ILicensingService";
    public static final String ACTION_V2 = "com.android.vending.licensing.ILicensingServiceV2";

    static final String DESCRIPTOR    = "com.android.vending.licensing.ILicensingService";
    static final String V1_LISTENER   = "com.android.vending.licensing.ILicenseResultListener";
    static final String V2_LISTENER   = "com.android.vending.licensing.ILicenseV2ResultListener";

    /** The bundle key the v2 verdict travels in. microG LicensingService.kt:212,
     *  and independently what our own RE found pairip reading -- two sources
     *  that agree, which is why this one is not guesswork. */
    static final String KEY_V2_JWT = "LICENSE_DATA";

    private static final int TXN_CHECK_LICENSE    = 1;
    private static final int TXN_CHECK_LICENSE_V2 = 2;
    /** IBinder.INTERFACE_TRANSACTION == 'SYNC' == 0x5F4E5446. A client may ask
     *  for our descriptor over the wire; answering it is part of being a binder. */
    private static final int TXN_INTERFACE = 0x5F4E5446;

    private final Stub mBinder = new Stub();

    @Override public IBinder onBind(Intent intent) { return mBinder; }

    /** The binder itself, reachable without a bind for our own CLI and tests. */
    public static IBinder binder() { return new Stub(); }

    // ===================================================================

    static final class Stub extends Binder {
        Stub() {
            // See the header: pairip reads this back and uses it as the request's
            // interface token.
            attachInterface(null, DESCRIPTOR);
        }

        @Override public String getInterfaceDescriptor() { return DESCRIPTOR; }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            switch (code) {
                case TXN_INTERFACE:
                    if (reply != null) reply.writeString(DESCRIPTOR);
                    return true;

                case TXN_CHECK_LICENSE: {
                    data.enforceInterface(DESCRIPTOR);
                    long nonce   = data.readLong();
                    String pkg   = data.readString();
                    IBinder cb   = data.readStrongBinder();
                    dispatchV1(pkg, nonce, cb);
                    return true;   // reply is null on this oneway txn -- write nothing
                }

                case TXN_CHECK_LICENSE_V2: {
                    data.enforceInterface(DESCRIPTOR);
                    String pkg = data.readString();
                    IBinder cb = data.readStrongBinder();
                    // The AIDL nullable-object convention. We read the extras
                    // even though nothing needs them yet: leaving bytes in the
                    // parcel is harmless here, but reading them proves the layout
                    // matches and makes a mismatch loud instead of silent.
                    Bundle extras = null;
                    try {
                        if (data.readInt() != 0) extras = data.readBundle();
                    } catch (Throwable t) {
                        log("v2 extras did not parse (" + t + ") -- continuing without them");
                    }
                    dispatchV2(pkg, cb, extras);
                    // MUST be true even though the reply parcel goes unwritten:
                    // pairip treats false as a fatal service error.
                    return true;
                }

                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    }

    // ===================================================================
    // Dispatch -- always off the caller's thread
    // ===================================================================

    private static void dispatchV1(final String pkg, final long nonce, final IBinder cb) {
        if (cb == null) { log("checkLicense for " + pkg + " with a null listener -- dropping"); return; }
        run("license-v1", () -> {
            int code;
            String signedData = null, signature = null;
            String why = enforceCaller(pkg);
            if (why != null) {
                code = PlayLicenseClient.ERROR_INVALID_PACKAGE_NAME;
                log("checkLicense refused for " + pkg + ": " + why);
            } else {
                PlayLicenseClient.Result r = PlayLicenseClient.checkV1(pkg, versionCode(), nonce);
                code = r.responseCode;
                signedData = r.signedData;
                signature = r.signature;
                log("checkLicense " + pkg + " -> " + r);
            }
            deliverV1(cb, code, signedData, signature);
        });
    }

    private static void dispatchV2(final String pkg, final IBinder cb, final Bundle extras) {
        if (cb == null) { log("checkLicenseV2 for " + pkg + " with a null listener -- dropping"); return; }
        run("license-v2", () -> {
            String why = enforceCaller(pkg);
            if (why != null) {
                log("checkLicenseV2 refused for " + pkg + ": " + why + " -- no callback (see below)");
                return;
            }
            PlayLicenseClient.Result r = PlayLicenseClient.check(pkg, versionCode());
            log("checkLicenseV2 " + pkg + " -> " + r);
            if (r.licensed() && r.jwt != null) {
                Bundle payload = new Bundle();
                payload.putString(KEY_V2_JWT, r.jwt);
                deliverV2(cb, PlayLicenseClient.LICENSED, payload);
                return;
            }
            // ---- WHY A NEGATIVE v2 RESULT SENDS NOTHING ---------------------
            // This is a deliberate compatibility choice, and it is NOT an
            // assertion that the user owns the app -- we assert nothing at all.
            //
            // The v2 callback has no error vocabulary a modern client survives:
            // pairip understands 0 (licensed) and 2 (not licensed, and it then
            // expects a PAYWALL_INTENT PendingIntent we have none to give);
            // ANY other code takes its "unexpected response code" path, which
            // shows an error dialog and exits the process. Relaying a v1-style
            // 0x101 there would turn "we could not reach Google" into a hard
            // kill of the app.
            //
            // microG reached the same conclusion and suppresses every negative
            // v2 result (LicensingService.kt:67-88, with the reasoning in a
            // comment). What REAL Play sends here is unknown to us -- the
            // emulator oracle that could answer it was deleted -- so we mirror
            // the only behaviour anyone has measured to be safe, and we say so
            // loudly in the log rather than silently.
            log("suppressed a negative v2 licence result for " + pkg
                + " (" + r + ") -- the v2 callback has no error code this client"
                + " survives; see LicensingService's comment");
        });
    }

    // ===================================================================
    // Callbacks -- the exact AIDL parcel layout
    // ===================================================================

    /** PUBLIC because it is the wire-layout surface the binder probe drives
     *  against the APP'S OWN generated stub (debug/licensing_binder_probe).
     *  A layout this important should be validated by the real reader, and
     *  that reader lives outside this package. */
    public static void deliverV1(IBinder cb, int code, String signedData, String signature) {
        Parcel d = Parcel.obtain();
        try {
            d.writeInterfaceToken(V1_LISTENER);
            d.writeInt(code);
            d.writeString(signedData);
            d.writeString(signature);
            d.setDataPosition(0);
            cb.transact(1, d, null, IBinder.FLAG_ONEWAY);
        } catch (Throwable t) {
            log("could not deliver the v1 verdict: " + t);
        } finally {
            try { d.recycle(); } catch (Throwable ignored) {}
        }
    }

    /** PUBLIC for the same reason as {@link #deliverV1}. */
    public static void deliverV2(IBinder cb, int code, Bundle payload) {
        Parcel d = Parcel.obtain();
        try {
            d.writeInterfaceToken(V2_LISTENER);
            d.writeInt(code);
            // The typed-object convention: presence int, then the body. The
            // client reads it with readTypedObject(Bundle.CREATOR), which reads
            // that int first -- writing the bundle alone leaves it reading the
            // bundle's length as the presence flag and the payload as garbage.
            d.writeTypedObject(payload, 0);
            d.setDataPosition(0);
            cb.transact(1, d, null, IBinder.FLAG_ONEWAY);
        } catch (Throwable t) {
            log("could not deliver the v2 verdict: " + t);
        } finally {
            try { d.recycle(); } catch (Throwable ignored) {}
        }
    }

    // ===================================================================

    /**
     * AOSP's check is `packageInfo.applicationInfo.uid == Binder.getCallingUid()`
     * -- "you may only ask about yourself".
     *
     * We express the SAME property differently, and deliberately. Mac.apk hosts
     * exactly one package in one process, so "the caller is the package it is
     * asking about" reduces exactly to "the package asked about is the running
     * app". Comparing uids here would not be more faithful, it would be less:
     * our Binder.getCallingUid() returns 0 while ApplicationInfo.uid is 10000,
     * so a literal port of the AOSP line would refuse EVERY caller. (That uid
     * divergence is a real framework bug, filed separately -- it is not this
     * service's to paper over.)
     *
     * @return null when the request is allowed, else the reason to refuse.
     */
    private static String enforceCaller(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "no package name in the request";
        String running = runningPackage();
        if (running == null) {
            // We cannot establish who is asking. Refuse: a licensing service that
            // answers for packages it cannot attribute is one an app could use to
            // ask about someone else's purchases.
            return "cannot determine the running package";
        }
        if (!pkg.equals(running)) {
            return "an app may only check its own licence (running=" + running + ")";
        }
        return null;
    }

    private static String runningPackage() {
        try {
            android.app.Application app = android.app.Application.getInstance();
            if (app != null) {
                String p = app.getPackageName();
                if (p != null && !p.isEmpty()) return p;
            }
        } catch (Throwable ignored) {}
        try {
            String p = System.getenv("MACAPK_PACKAGE");
            if (p != null && !p.isEmpty()) return p;
        } catch (Throwable ignored) {}
        return null;
    }

    /** The running app's versionCode, which the licence query is keyed on.
     *  A wrong version asks Google about a different build of the app. */
    private static int versionCode() {
        try {
            android.app.Application app = android.app.Application.getInstance();
            if (app != null) {
                android.content.pm.PackageInfo pi = app.getPackageManager()
                        .getPackageInfo(app.getPackageName(), 0);
                if (pi != null && pi.versionCode > 0) return pi.versionCode;
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    /** One daemon thread per request. A licence check happens a handful of times
     *  in an app's lifetime, so a pool would be machinery without a purpose;
     *  daemon so a pending check can never hold the process open. */
    private static void run(String name, Runnable r) {
        Thread t = new Thread(r, "macapk-" + name);
        t.setDaemon(true);
        t.start();
    }

    static void log(String m) {
        try { com.macapk.loader.NativeLog.i("Mac.apk.GMS", "licensing: " + m); }
        catch (Throwable t) { System.err.println("[licensing] " + m); }
    }
}
