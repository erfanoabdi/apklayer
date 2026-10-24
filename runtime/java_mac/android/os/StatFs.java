// Mac.apk v1.0.256 -- real android.os.StatFs backed by macOS filesystem stats.
//
// Firebase Crashlytics' static initializer
// `Firebase.Crashlytics.Crashlytics.PlatformAccessor..cctor` does
// `new StatFs(path)` + getAvailableBytes()/getBlockSize() to check disk space for
// crash-report storage. The previous v1.0.92 empty stub had ONLY the no-arg
// constructor, so the JNI `new StatFs(String)` threw
// `java.lang.NoSuchMethodError: void android.os.StatFs.<init>(java.lang.String)`,
// which surfaced as an il2cpp AndroidJavaException -> the PlatformAccessor type
// initializer threw a TypeInitializationException -> PlatformAccessor.Provider was
// never stored (null) -> BootController.Awake's CrashlyticsBootStage got a null
// provider and threw NullReferenceException -> Unity left BootController disabled
// -> no Start/Update -> Init*Async/LoadGameScene never ran -> the Crossy menu never
// rendered. (Root cause found 2026-06-03 via the il2cpp exception logger.)
//
// Mirror the AOSP public API; back it with java.nio.file.FileStore (real volume
// stats). Android reports the fragment size (commonly 4096); we report a fixed
// 4096-byte block, which matches the available/total *byte* values Crashlytics
// actually reads. Never throw and never report non-positive values -- a thrown
// ctor or a zero result would re-break the same boot path.
package android.os;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StatFs {
    private static final long BLOCK_SIZE = 4096L;

    private long mTotalBytes;
    private long mFreeBytes;       // unallocated (incl. root-reserved)
    private long mAvailableBytes;  // usable by an unprivileged caller

    public StatFs(String path) { restat(path); }

    public void restat(String path) {
        Path p = null;
        try { p = (path != null) ? Paths.get(path) : null; } catch (Exception e) { p = null; }
        // Android stats the *volume* containing the path; a not-yet-created app
        // dir should still yield its volume's stats, so walk up to the nearest
        // existing ancestor.
        try {
            while (p != null && !Files.exists(p)) p = p.getParent();
        } catch (Exception e) { p = null; }
        FileStore fs = null;
        try {
            if (p == null) p = Paths.get(System.getProperty("user.home", "/"));
            fs = Files.getFileStore(p);
        } catch (IOException | RuntimeException e) {
            try { fs = Files.getFileStore(Paths.get("/")); } catch (Exception e2) { fs = null; }
        }
        if (fs != null) {
            try { mTotalBytes = fs.getTotalSpace(); } catch (Exception e) { mTotalBytes = 0; }
            try { mFreeBytes = fs.getUnallocatedSpace(); } catch (Exception e) { mFreeBytes = 0; }
            try { mAvailableBytes = fs.getUsableSpace(); } catch (Exception e) { mAvailableBytes = mFreeBytes; }
        }
        // Keep sane positive values: Crashlytics gates crash storage on free space,
        // and some callers divide by the block counts.
        if (mTotalBytes <= 0) mTotalBytes = 64L * 1024 * 1024 * 1024;         // 64 GiB
        if (mAvailableBytes <= 0) mAvailableBytes = 16L * 1024 * 1024 * 1024; // 16 GiB
        if (mFreeBytes <= 0) mFreeBytes = mAvailableBytes;
    }

    public long getBlockSizeLong() { return BLOCK_SIZE; }
    @Deprecated public int getBlockSize() { return (int) BLOCK_SIZE; }

    public long getBlockCountLong() { return mTotalBytes / BLOCK_SIZE; }
    @Deprecated public int getBlockCount() { return (int) getBlockCountLong(); }

    public long getFreeBlocksLong() { return mFreeBytes / BLOCK_SIZE; }
    @Deprecated public int getFreeBlocks() { return (int) getFreeBlocksLong(); }

    public long getAvailableBlocksLong() { return mAvailableBytes / BLOCK_SIZE; }
    @Deprecated public int getAvailableBlocks() { return (int) getAvailableBlocksLong(); }

    public long getFreeBytes() { return mFreeBytes; }
    public long getAvailableBytes() { return mAvailableBytes; }
    public long getTotalBytes() { return mTotalBytes; }
}
