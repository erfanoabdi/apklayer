// android.system.SystemCleaner -- the ONE process-wide java.lang.ref.Cleaner
// (API 33), so libraries do not each spin up a Cleaner thread.
//   AOSP: libcore/luni/src/main/java/android/system/SystemCleaner.java
//         (android14-release). AOSP returns jdk.internal.ref.CleanerFactory.
//         cleaner(); that class lives in java.base's non-exported
//         jdk.internal.ref package, which an unnamed-module class on HotSpot
//         cannot link against without --add-exports. So the shared instance is
//         created here instead, once, with the same contract: a single Cleaner,
//         shared by every caller, thread-safe, never null. (Cleaner.create()
//         gives it its own daemon thread, which is what CleanerFactory's is.)
package android.system;

import java.lang.ref.Cleaner;

/**
 * Provides access to a Cleaner shared across the entire process, for cleaning
 * actions known to complete quickly.
 */
public final class SystemCleaner {

    private SystemCleaner() {}

    /** Initialization-on-demand holder: created on first use, exactly once. */
    private static final class Holder {
        static final Cleaner CLEANER = Cleaner.create();
    }

    /**
     * Return a single Cleaner that's shared across the entire process. Thread-safe.
     */
    public static Cleaner cleaner() {
        return Holder.CLEANER;
    }
}
