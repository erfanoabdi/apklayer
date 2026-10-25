// RunLock -- this process holds its installed app's run lock, however it was
// started (2026-10-05).
//
// The per-app stub (launcher/AppStubMain.swift) takes flock on
// <support>/run/<dirName>.pid and execs the runtime with that fd still open, so
// a Dock / Finder / URL launch owns the lock for exactly the runtime's life.
// Two things read it:
//   * macapk-installhelper's CallerIdentity: every runtime-permission dialog
//     (the location and storage permissions) and special-access request needs
//     the calling runtime to hold its app's run lock;
//   * the stub's single-instance check: a second launch finds it held and
//     brings the running instance forward instead of starting another.
// A launch from the Mac.apk window (MacApkLauncherApp.launch, Process() through
// a run-wrapper) never passed through a stub and held no lock: the helper
// refused every dialog ("parent N does not hold <app>'s run lock", Organic Maps'
// location request on 2026-10-05, answered DENIED with no dialog), and opening
// the app's own bundle meanwhile started a SECOND instance.
//
// So the runtime takes the lock itself when it has an installed identity and
// nobody holds it. Probing with a new fd is safe in the stub case: flock locks
// belong to the open file description, so the probe fails EWOULDBLOCK against
// our own inherited lock and touches nothing. If ANOTHER instance holds it,
// this one runs on as before (and its dialogs are refused, as before).
// MACAPK_RUN_LOCK=0 skips it.
package com.macapk.loader;

import java.io.File;

public final class RunLock {
    private RunLock() {}

    private static final String TAG = "Mac.apk.RunLock";
    private static volatile int sFd = -1;

    static File runDir() {
        return new File(SharedStorage.hostHome(), "Library/Application Support/Mac.apk/run");
    }

    /** Take the run lock if we have an installed identity and it is free. */
    public static synchronized void ensureHeld() {
        if (sFd >= 0 || "0".equals(System.getenv("MACAPK_RUN_LOCK"))) return;
        String[] self;
        try { self = SpecialAccessStore.selfIdentity(); } catch (Throwable t) { self = null; }
        if (self == null || self[0] == null || self[0].isEmpty()) return;   // dev / harness run
        File dir = runDir();
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        String path = new File(dir, self[0] + ".pid").getPath();
        int fd;
        try { fd = NativeOs.takeRunLock(path); } catch (Throwable t) { return; }
        if (fd >= 0) {
            sFd = fd;
            NativeLog.i(TAG, "took " + path + " (started without a stub)");
        } else if (fd != -35 /* EWOULDBLOCK on Darwin */) {
            NativeLog.w(TAG, "cannot take " + path + ": errno " + (-fd));
        }
    }
}
