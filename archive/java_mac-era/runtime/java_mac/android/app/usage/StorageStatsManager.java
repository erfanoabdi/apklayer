// android.app.usage.StorageStatsManager
//
// AOSP's storage-statistics service. Apps fetch it via getSystemService(
// STORAGE_STATS_SERVICE) to read total/free bytes of a storage volume. Mac.apk
// backs storage with the host filesystem, so we report the real free/total
// bytes of the app data dir's filesystem -- a faithful answer rather than a
// stub, where the platform allows it. Mirrors
// frameworks/base/core/java/android/app/usage/StorageStatsManager.java.
package android.app.usage;

import android.content.Context;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class StorageStatsManager {

    private final Context mContext;

    public StorageStatsManager() { mContext = null; }
    public StorageStatsManager(Context context) { mContext = context; }

    private java.io.File baseDir() {
        try {
            if (mContext != null && mContext.getFilesDir() != null) return mContext.getFilesDir();
        } catch (Throwable ignored) {}
        return new java.io.File(".");
    }

    /** AOSP getTotalBytes(uuid): total capacity of the volume. We report the
     *  host filesystem's total space backing the app data dir. */
    public long getTotalBytes(java.util.UUID storageUuid) {
        return baseDir().getTotalSpace();
    }

    /** AOSP getFreeBytes(uuid): usable free space of the volume. */
    public long getFreeBytes(java.util.UUID storageUuid) {
        return baseDir().getUsableSpace();
    }

    /** AOSP isQuotaSupported(uuid): macOS has no per-app storage quota. */
    public boolean isQuotaSupported(java.util.UUID storageUuid) { return false; }

    /** AOSP queryStatsForPackage(uuid, pkg, user) (API 26): the app / data /
     *  cache byte totals for one package. Mac.apk reports the on-disk size of
     *  the app's private data tree as dataBytes (with the cache subtree counted
     *  in cacheBytes); appBytes is the apk size when locatable, else 0. */
    public android.app.usage.StorageStats queryStatsForPackage(
            java.util.UUID storageUuid, String packageName,
            android.os.UserHandle user) {
        android.app.usage.StorageStats s = new android.app.usage.StorageStats();
        try {
            java.io.File data = baseDir();
            s.dataBytes = dirSize(data);
            s.cacheBytes = dirSize(new java.io.File(data, "cache"));
        } catch (Throwable ignored) {}
        return s;
    }

    private static long dirSize(java.io.File f) { return dirSize(f, true); }

    /** `atRoot` is true only for the top-level call. Below the root nothing
     *  should ever be a symlink (native and Java-guest symlink creation into
     *  shared storage is meant to be refused -- see
     *  SharedStorage.mapUserFolders's header), but a REAL symlink does now
     *  live at the shared-storage root: the five collection-folder mappings
     *  (owner reversal 2026-09-28). Recursing through an unrecognised link, or
     *  ANY link below the root, could walk the whole disk (review of
     *  2026-09-28: measured to include ~/Documents, ~/Downloads and Photos
     *  Library.photoslibrary from one queryExternalStatsForUser call, raising
     *  a TCC prompt an app never asked for by calling a stats API); following
     *  only the five recognised root links, and only once, also means this
     *  can never loop even without a visited-set. */
    private static long dirSize(java.io.File f, boolean atRoot) {
        if (f == null || !f.exists()) return 0L;
        if (f.isFile()) return f.length();
        long total = 0L;
        java.io.File[] kids = f.listFiles();
        if (kids == null) return 0L;
        for (java.io.File k : kids) {
            if (java.nio.file.Files.isSymbolicLink(k.toPath())) {
                if (!atRoot || !com.macapk.loader.SharedStorage.isRecognizedFolderLink(k)) continue;
                total += dirSize(k, false);
                continue;
            }
            if (k.isDirectory() && android.provider.MediaStoreProvider.isHostPackage(k.getName(), k)) {
                continue;   // a macOS package (Photos/Music library, .app, ...): opaque
            }
            total += dirSize(k, false);
        }
        return total;
    }

    // ================================================================
    // AOSP API-36 surface completion.
    // AOSP ref: frameworks/base/core/java/android/app/usage/
    // StorageStatsManager.java (android16-release).
    // ================================================================

    /** AOSP queryStatsForUid(UUID, int uid): "Query the size of media and
     *  external storage owned by all apps sharing the given uid." Mac.apk runs
     *  ONE app under ONE uid, so the uid's stats are that app's stats -- the
     *  same measurement queryStatsForPackage above performs, which is why this
     *  routes through it rather than answering a separate zero. */
    public android.app.usage.StorageStats queryStatsForUid(java.util.UUID storageUuid, int uid) {
        String pkg = null;
        try { if (mContext != null) pkg = mContext.getPackageName(); } catch (Throwable ignored) {}
        return queryStatsForPackage(storageUuid, pkg, android.os.Process.myUserHandle());
    }

    /** AOSP queryStatsForUser(UUID, UserHandle): the whole user's app storage.
     *  There is one user and one app in it, so this is the same measurement
     *  again. */
    public android.app.usage.StorageStats queryStatsForUser(java.util.UUID storageUuid,
            android.os.UserHandle user) {
        String pkg = null;
        try { if (mContext != null) pkg = mContext.getPackageName(); } catch (Throwable ignored) {}
        return queryStatsForPackage(storageUuid, pkg, user);
    }

    /** AOSP queryExternalStatsForUser(UUID, UserHandle): "Query the shared/
     *  external storage statistics for the given user", broken down into audio
     *  / video / image / app bytes.
     *
     *  Mac.apk's "external storage" is a directory inside the app's own tree
     *  (Environment.getExternalStorageDirectory), not a shared volume with a
     *  media index: nothing classifies files there by media type, and doing it
     *  by extension would be a guess reported as a measurement. So the TOTAL is
     *  measured for real and the four per-type slices report 0 -- which is
     *  AOSP's own answer for categories the media scanner has not attributed. */
    public android.app.usage.ExternalStorageStats queryExternalStatsForUser(
            java.util.UUID storageUuid, android.os.UserHandle user) {
        android.app.usage.ExternalStorageStats s =
                new android.app.usage.ExternalStorageStats();
        try {
            java.io.File ext = android.os.Environment.getExternalStorageDirectory();
            s.totalBytes = dirSize(ext);
        } catch (Throwable ignored) {}
        return s;
    }
}
