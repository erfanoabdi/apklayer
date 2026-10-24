// InstallIntentHandler -- the host side of the system package-installer
// surface. Routes ACTION_INSTALL_PACKAGE (and ACTION_VIEW of an APK) to the
// macOS install helper and delivers the Android-shaped activity result back
// to the requesting guest activity.
//
// WHAT A DEVICE DOES with these intents: resolves them to the platform
// packageinstaller app (android-9.0.0_r1
// packages/apps/PackageInstaller/AndroidManifest.xml, fetched 2026-08-11 --
// .InstallStart matches actions {VIEW, INSTALL_PACKAGE} on schemes
// {file, content} with mimeType application/vnd.android.package-archive, and
// ACTION_INSTALL_PACKAGE alone on schemes {file, package, content}), which
// confirms with the user, installs, and when the caller asked via
// startActivityForResult finishes with RESULT_OK +
// EXTRA_INSTALL_RESULT=INSTALL_SUCCEEDED (InstallSuccess.java), RESULT_FIRST_USER
// + the legacy failure code (InstallFailed.java), or RESULT_CANCELED when the
// user declines (PackageInstallerActivity). Mac.apk's equivalent of that
// system app is the macapk-installhelper binary inside the .app bundle; this
// class is the bridge from the guest's intent to that helper and back.
//
// F-Droid is the motivating caller (DefaultInstallerActivity.installPackage,
// read from its own R8 bytecode 2026-08-11): startActivityForResult(
// new Intent(ACTION_INSTALL_PACKAGE).setData(content://...)
// .addFlags(FLAG_GRANT_READ_URI_PERMISSION).putExtra(EXTRA_NOT_UNKNOWN_SOURCE,
// true).putExtra(EXTRA_RETURN_RESULT, true), requestCode=0), then its
// onActivityResult maps RESULT_OK -> INSTALL_COMPLETE, RESULT_CANCELED ->
// INSTALL_INTERRUPTED(no msg), anything else -> INSTALL_INTERRUPTED(unknown).
//
// ⛔ SECURITY -- THE AUTHORIZATION IS THE HELPER'S, NEVER OURS TO SKIP.
// This class runs in the same process as untrusted guest code, so nothing it
// computes can be an authorization decision. The design is: we always invoke
// the helper with --source android (a compile-time constant below, never an
// env var, never a flag someone can inject), and the HELPER owns the
// consent+auth gate for that source. Today it deliberately refuses android
// -source installs outright (launcher/InstallHelperMain.swift exits 6) until
// its LAContext step is real; this class maps that refusal onto an ordinary
// install failure. Do not add any code path that selects a different
// --source value at runtime: --source gui skips auth BY DESIGN for the
// launcher's own UI, and reaching it from here on any condition a guest can
// influence (env var, intent extra, file presence) would be an auth bypass.
//
// The MACAPK_INSTALLHELPER env override is NOT such a bypass: it picks
// WHICH binary we ask, not what the binary demands. A guest that redirects it
// to a fake helper gets a fabricated result delivered to itself and no
// install happens anywhere (no bundle, no registry entry) -- the privileged
// action only ever happens inside the real helper, behind its own gate.
//
// SHARED MECHANICS LIVE IN PackageInstallerBackend (delegated 2026-08-12).
// Where the bytes land (stagingRoot), which binary we ask (locateHelper), and
// the argv we ask it with (runInstallHelper) are ONE implementation, in
// PackageInstallerBackend, used by both install surfaces. Until this change
// each class carried its own copy and they had ALREADY drifted: the session
// path passed --requestor-label so the helper's auth prompt could name the
// asking app, and this path did not, so the same install prompted differently
// depending on which API the store happened to use. Two copies of install
// logic is how an app silently installs wrong, so the copies are gone; what
// stays here is only what is genuinely different about the INTENT surface --
// reading the caller's Uri, and mapping the helper's exit onto an Android
// activity result rather than a PackageInstaller status.
package com.macapk.loader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

public final class InstallIntentHandler {
    private static final String TAG = "InstallIntent";

    /** The MIME type of an APK, as apps put it in setDataAndType. */
    private static final String APK_MIME = "application/vnd.android.package-archive";

    /** AOSP legacy install status codes, verbatim from android14-release
     *  core/java/android/content/pm/PackageManager.java (fetched 2026-08-11):
     *  INSTALL_SUCCEEDED = 1 (:1814), INSTALL_FAILED_INVALID_URI = -3 (:1841),
     *  INSTALL_FAILED_INTERNAL_ERROR = -110 (:2195). Literals here because
     *  they ride in the result Intent's EXTRA_INSTALL_RESULT and the hidden
     *  PackageManager constants are not part of our public surface. */
    private static final int INSTALL_SUCCEEDED             = 1;
    private static final int INSTALL_FAILED_INVALID_APK    = -2;
    private static final int INSTALL_FAILED_INVALID_URI    = -3;
    private static final int INSTALL_FAILED_INTERNAL_ERROR = -110;

    /** One install at a time, off the guest main thread. A device shows one
     *  install confirmation at a time too, and the helper (once its auth step
     *  is wired) can block on a human for a long while -- the guest's main
     *  loop must keep running during that. Daemon so a pending install never
     *  pins process exit. */
    private static final java.util.concurrent.ExecutorService EXEC =
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "macapk-install");
            t.setDaemon(true);
            return t;
        });

    private InstallIntentHandler() {}

    /**
     * PURE predicate: is this the package installer's intent surface?
     * Consulted by ActivityHost.hostActionFor, which is itself the ONE
     * predicate both the start path and PackageManager.queryIntentActivities
     * use -- so resolveActivity() answers consistently with what start()
     * performs (the v1.0.938 rule). Matches on action / scheme / explicit
     * type only, never extras (IntentFilter.match cannot see extras).
     *
     * Deviations from the AOSP filter set, both deliberate:
     *  - scheme "package" for ACTION_INSTALL_PACKAGE is NOT claimed. On a
     *    device it names an already-installed package for reinstall; we
     *    cannot perform that, and claiming an intent we then drop is the
     *    dead-button lie hostActionFor's header warns about.
     *  - a typeless ACTION_VIEW of a content:// APK is NOT claimed. AOSP
     *    matching resolves the type by asking the provider's getType();
     *    ours would need the same plumbing to answer honestly. Every
     *    installer-store flow observed so far (F-Droid both paths) sets the
     *    action or the explicit type, so this stays a KNOWN OPEN gap rather
     *    than a guessed answer.
     */
    static boolean isInstallIntent(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        if (action == null) return false;
        Uri data = intent.getData();
        String scheme = data != null ? data.getScheme() : null;
        boolean apkScheme = "content".equals(scheme) || "file".equals(scheme);
        if (Intent.ACTION_INSTALL_PACKAGE.equals(action)) {
            return apkScheme;
        }
        if (Intent.ACTION_VIEW.equals(action)) {
            return apkScheme && APK_MIME.equals(intent.getType());
        }
        return false;
    }

    /**
     * Perform the install handoff. Called from ActivityHost's implicit-intent
     * resolution with BOTH the intent the app handed startActivity[ForResult]
     * (`outer`, the identity a pending result was recorded against) and the
     * unwrapped intent actually being resolved (`real`, differs only under a
     * chooser wrapper). Returns true: the intent is handled (the result, when
     * one was asked for, arrives asynchronously).
     *
     * Runs the claim SYNCHRONOUSLY on the caller's thread -- this is inside
     * startActivityForResult's try block, and claiming here is what stops its
     * finally from settling the pairing as RESULT_CANCELED (Activity.
     * macapkSettleUnstartedResult). Everything slow then moves to the
     * macapk-install thread.
     *
     * Stage S5 (MACAPK_RESULT_ORDER_AOSP, default ON): PackageInstaller's
     * activities are dialog-themed (Theme.AlertDialogActivity, a floating
     * Theme.DeviceDefault.Light.Dialog.Alert -- packages/PackageInstaller
     * themes.xml android14), so on a device the caller is PAUSED while the
     * installer is up and gets its result just before onResume. `cover` (a
     * translucent ActivityHost.hostCover) reproduces that, and the result is
     * delivered in restoreTop's result slot. With the gate at 0 nothing is
     * covered and the result posts back via the ordinary main-looper delivery,
     * exactly as before.
     */
    static boolean handle(Intent outer, Intent real, SpecialAccessHandler.Cover cover) {
        final Uri data = real.getData();
        final Activity.MacApkHostResult token = Activity.macapkClaimHostResult(outer);
        final SpecialAccessHandler.Cover c = SpecialAccessHandler.RESULT_ORDER_AOSP ? cover : null;
        NativeLog.i(TAG, "install intent received: action=" + real.getAction()
                + " data=" + data + " type=" + real.getType()
                + " returnResult=" + real.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false)
                + " forResult=" + (token != null));
        if (c != null) c.cover();
        EXEC.execute(() -> performInstall(data, token, c));
        return true;
    }

    // ---- the slow half, on the macapk-install thread ----

    private static void performInstall(Uri data, Activity.MacApkHostResult token,
                                       SpecialAccessHandler.Cover cover) {
        java.io.File staged = null;
        try {
            staged = stage(data);
            if (staged == null) {
                deliver(token, cover, Activity.RESULT_FIRST_USER, INSTALL_FAILED_INVALID_URI,
                        "source unreadable: " + data);
                return;
            }
            java.io.File helper = PackageInstallerBackend.locateHelper();
            if (helper == null) {
                NativeLog.e(TAG, "macapk-installhelper not found (MACAPK_INSTALLHELPER unset,"
                        + " no Contents/bin sibling of the runtime binary, no canonical app)");
                deliver(token, cover, Activity.RESULT_FIRST_USER, INSTALL_FAILED_INTERNAL_ERROR,
                        "install helper missing");
                return;
            }
            // ONE helper invocation for both install surfaces. A timeout comes
            // back as exit 5 exactly as this path's own runner used to report
            // it, so the mapping below is unchanged by the delegation.
            PackageInstallerBackend.HelperResult r = PackageInstallerBackend
                    .runInstallHelper(helper, staged, PackageInstallerBackend.requestorLabel());
            int exit = r.exit;
            switch (exit) {
                case 0:  // success -- InstallSuccess.java's shape
                    // Announce it the way a device does, BEFORE the result goes
                    // back. Identical call to the one the session surface makes
                    // (PackageInstallerBackend.broadcastPackageInstalled), for
                    // the same reason staging and helper invocation are shared:
                    // an app must not learn different things about the same
                    // install depending on which API it happened to use. AOSP
                    // orders it this way too -- handlePackagePostInstall sends
                    // the broadcasts and only then notifies the observer
                    // (InstallPackageHelper.java:2890-2990, :3101).
                    PackageInstallerBackend.broadcastPackageInstalled(
                            r.jsonField("package"),
                            "true".equals(r.jsonField("wasUpdate")));
                    deliver(token, cover, Activity.RESULT_OK, INSTALL_SUCCEEDED, "installed");
                    break;
                case 3:  // cancelled -- the user declined the confirm
                case 4:  // auth failed -- the user failed/dismissed the auth
                    // Both are "the human said no": PackageInstallerActivity's
                    // decline is a plain RESULT_CANCELED with no result extra.
                    deliver(token, cover, Activity.RESULT_CANCELED, 0, "declined (exit " + exit + ")");
                    break;
                case 6:  // unsupported input (incl. the invalid-apk stage) --
                         // the same legacy code a device answers for an APK it
                         // cannot parse.
                    deliver(token, cover, Activity.RESULT_FIRST_USER, INSTALL_FAILED_INVALID_APK,
                            "helper exit 6" + PackageInstallerBackend.helperReason(r));
                    break;
                default: // 2 bad args, 5 error -- InstallFailed.java's
                         // shape: RESULT_FIRST_USER + the legacy failure code.
                         // The helper's own stage/message ride along so the
                         // dialog names the reason, not just the exit code.
                    deliver(token, cover, Activity.RESULT_FIRST_USER, INSTALL_FAILED_INTERNAL_ERROR,
                            "helper exit " + exit + PackageInstallerBackend.helperReason(r));
                    break;
            }
        } catch (Throwable t) {
            NativeLog.e(TAG, "install failed: " + t);
            t.printStackTrace();
            deliver(token, cover, Activity.RESULT_FIRST_USER, INSTALL_FAILED_INTERNAL_ERROR,
                    String.valueOf(t));
        } finally {
            if (staged != null && staged.exists() && !staged.delete()) {
                NativeLog.w(TAG, "could not delete staged file " + staged);
            }
        }
    }

    /** Copy the intent's data stream to a helper-readable file under the
     *  shared staging root (PackageInstallerBackend.stagingRoot(), = the
     *  launcher's launcher/InstallCore.swift Paths.stagingDir -- its retention
     *  sweep also cleans day-old leftovers there, so an interrupted stage never
     *  accumulates). The filename is OURS (timestamp + nonce), never derived
     *  from guest-controlled strings, so no path from the guest ever reaches
     *  the filesystem layer. Returns null when the source cannot be read.
     *
     *  The content:// case is the load-bearing one: F-Droid hands a
     *  androidx-FileProvider URI and the bytes come back through
     *  ContentResolver.openInputStream -> ContentProviderRegistry ->
     *  FileProvider.openFile. file:// rides the same resolver entry point. */
    private static java.io.File stage(Uri data) throws java.io.IOException {
        if (data == null) return null;
        android.app.Application app = android.app.Application.getInstance();
        if (app == null) {
            NativeLog.e(TAG, "no Application context; cannot stage " + data);
            return null;
        }
        java.io.InputStream in;
        try {
            in = app.getContentResolver().openInputStream(data);
        } catch (java.io.FileNotFoundException fnf) {
            NativeLog.w(TAG, "openInputStream(" + data + ") -> FileNotFound: " + fnf.getMessage());
            return null;
        }
        if (in == null) {
            NativeLog.w(TAG, "openInputStream(" + data + ") -> null (no provider for authority?)");
            return null;
        }
        java.io.File dir;
        try {
            dir = PackageInstallerBackend.stagingRoot();
        } catch (java.io.IOException e) {
            in.close();
            throw e;
        }
        java.io.File out = new java.io.File(dir, "guest-install-"
                + System.currentTimeMillis() + "-"
                + Integer.toHexString((int) (Math.random() * 0x7fffffff)) + ".apk");
        long total = 0;
        int magic0 = -1, magic1 = -1;
        try (java.io.InputStream src = in;
             java.io.OutputStream dst = new java.io.FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = src.read(buf)) > 0) {
                if (total == 0 && n >= 2) { magic0 = buf[0] & 0xff; magic1 = buf[1] & 0xff; }
                dst.write(buf, 0, n);
                total += n;
            }
        } catch (java.io.IOException e) {
            out.delete();
            throw e;
        }
        // 'P','K' -- a zip local-file header. Diagnostic only: the helper
        // re-parses the APK itself and trusts nothing staged here says.
        boolean zip = (magic0 == 0x50 && magic1 == 0x4b);
        NativeLog.i(TAG, "staged " + total + " bytes from " + data + " -> " + out
                + " (zip=" + zip + ")");
        if (total == 0) { out.delete(); return null; }
        return out;
    }

    // runHelper() and locateHelper() used to live here as private copies of
    // PackageInstallerBackend.runInstallHelper() / .locateHelper(). They are
    // gone on purpose -- see the SHARED MECHANICS note in the class header.

    /** Ship the result back through the ordinary activity-result delivery
     *  (main-looper post). No-op when the intent came through plain
     *  startActivity -- there is nobody to answer. */
    private static void deliver(Activity.MacApkHostResult token, SpecialAccessHandler.Cover cover,
                                int resultCode, int installResult, String why) {
        if (token == null) {
            NativeLog.i(TAG, "install finished (" + why + "), no result requested");
            if (cover != null) cover.uncover();
            return;
        }
        Intent data = new Intent();
        // InstallSuccess/InstallFailed both put the legacy code under
        // EXTRA_INSTALL_RESULT; the plain-cancel path carries no extra.
        if (resultCode != Activity.RESULT_CANCELED) {
            data.putExtra(Intent.EXTRA_INSTALL_RESULT, installResult);
        }
        NativeLog.i(TAG, "result delivered: requestCode=" + token.requestCode()
                + " resultCode=" + resultCode + " installResult=" + installResult
                + " (" + why + ")"
                + (cover != null ? " in restoreTop's result slot" : " posted"));
        Intent out = resultCode == Activity.RESULT_CANCELED ? null : data;
        if (cover != null) {
            SpecialAccessHandler.finishWithResult(cover, token, resultCode, out);
        } else {
            token.deliver(resultCode, out);
        }
    }
}
