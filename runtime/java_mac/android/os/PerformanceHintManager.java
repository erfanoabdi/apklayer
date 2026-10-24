package android.os;

import java.io.Closeable;

/**
 * AOSP-faithful android.os.PerformanceHintManager (API 31). Games create a hint
 * session to tell ADPF their per-frame CPU target so the governor can scale
 * clocks. macOS has no ADPF, so we expose the full API and accept the hints as
 * no-ops, returning a live (but inert) Session so the game's frame loop keeps
 * running. Mirrors
 * frameworks/base/core/java/android/os/PerformanceHintManager.java.
 */
public final class PerformanceHintManager {

    /** Device preferred update rate; AOSP returns nanos, 0 = "no preference". */
    public long getPreferredUpdateRateNanos() {
        return 0L;
    }

    public Session createHintSession(int[] tids, long initialTargetWorkDurationNanos) {
        if (tids == null || tids.length == 0) return null;
        return new Session(initialTargetWorkDurationNanos);
    }

    public static final class Session implements Closeable {
        public static final int CPU_LOAD_UP     = 0;
        public static final int CPU_LOAD_DOWN   = 1;
        public static final int CPU_LOAD_RESET  = 2;
        public static final int CPU_LOAD_RESUME = 3;

        private long mTargetNanos;
        private boolean mClosed;

        Session(long targetNanos) {
            mTargetNanos = targetNanos;
        }

        public void updateTargetWorkDuration(long targetDurationNanos) {
            if (targetDurationNanos <= 0) {
                throw new IllegalArgumentException("Expected positive target work duration");
            }
            mTargetNanos = targetDurationNanos;
        }

        public void reportActualWorkDuration(long actualDurationNanos) {
            if (actualDurationNanos <= 0) {
                throw new IllegalArgumentException("Expected positive actual work duration");
            }
            // No governor to inform; accept and discard.
        }

        /** AOSP core/java/android/os/PerformanceHintManager.java:274 --
         *  `nativeSetPreferPowerEfficiency(ptr, enabled)`, a HINT to the ADPF
         *  governor that this session would rather save power than hit its
         *  deadline. There is no governor here (see the class javadoc), so it
         *  is accepted and recorded like the other hints rather than throwing:
         *  AOSP defines no failure for it and a throw would break a frame
         *  loop that simply asked for a preference. */
        public void setPreferPowerEfficiency(boolean enabled) {
            mPreferPowerEfficiency = enabled;
        }
        private boolean mPreferPowerEfficiency;

        public void setThreads(int[] tids) {
            if (tids == null || tids.length == 0) {
                throw new IllegalArgumentException("Thread id list can't be empty");
            }
        }

        public void sendHint(int hint) {
            if (hint < 0) {
                throw new IllegalArgumentException("the hint ID should be greater than zero");
            }
        }

        @Override
        public void close() {
            mClosed = true;
        }
    }
}
