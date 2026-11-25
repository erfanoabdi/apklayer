package apklayer.host;

import android.os.Looper;
import android.os.SystemClock;
import java.time.Duration;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowPausedSystemClock;
import org.robolectric.shadows.ShadowSystemClock;

/**
 * Robolectric's paused clock moves only between main-looper steps (ComponentHost.step, AppHost's loop advance it to CLOCK_MONOTONIC), so code on the
 * main thread that waits for SystemClock to move spins forever inside one message. Android's clock always moves: Play services' receivers take a
 * start stamp with {@code t = uptimeMillis(); while (uptimeMillis() == t);} (PersistentTrustedChimeraReceiver, at BOOT_COMPLETED: no check-in, no
 * android-id). After {@link #SPIN_READS} main-thread reads of one unchanged value the clock catches up to the monotonic clock, as a step would.
 * A clock ahead of real time (headless runs step it to the next deadline) is left as it is.
 */
@Implements(value = SystemClock.class, isInAndroidSdk = false, shadowPicker = ShadowHostSystemClock.Picker.class)
public class ShadowHostSystemClock extends ShadowPausedSystemClock {
    static final int SPIN_READS = 1000;
    private static Thread main;
    private static long lastNanos;
    private static int reads;

    @Implementation protected static long uptimeMillis() { return uptimeNanos() / 1_000_000L; }
    @Implementation protected static long uptimeNanos() { catchUp(); return read(ShadowPausedSystemClock.uptimeNanos()); }
    @Implementation protected static long elapsedRealtime() { return elapsedRealtimeNanos() / 1_000_000L; }
    @Implementation protected static long elapsedRealtimeNanos() { catchUp(); return read(ShadowPausedSystemClock.elapsedRealtimeNanos()); }

    /**
     * Other threads do not wait for the main thread's next step: a stamp taken from System.nanoTime (or currentTimeMillis) on one thread and a
     * SystemClock reading on another then gave a negative duration (Play Store's update check: "duration (-0.137s) must not be negative", every
     * install of an update failed with gRPC UNKNOWN). A paused clock behind (for threads other than main) the monotonic clock reads the monotonic clock; one ahead is kept.
     */
    private static long read(long paused) {
        Thread thread = main;
        if (thread == null || Thread.currentThread() == thread) return paused;  // the main thread's reads stay on the looper's clock
        long real = System.nanoTime();  // this class is not instrumented: the JVM's CLOCK_MONOTONIC
        return real > paused ? real : paused;
    }

    private static void catchUp() {
        Thread thread = main;
        if (thread == null) {
            Looper looper = Looper.getMainLooper();
            if (looper == null) return;
            thread = main = looper.getThread();
        }
        if (Thread.currentThread() != thread) return;  // other threads read through read()
        long now = ShadowPausedSystemClock.uptimeNanos();  // the paused clock
        if (now != lastNanos) { lastNanos = now; reads = 0; return; }
        if (++reads < SPIN_READS) return;
        reads = 0;
        long lag = System.nanoTime() - now;  // this class is not instrumented: the JVM's CLOCK_MONOTONIC
        if (lag > 0) ShadowSystemClock.advanceBy(Duration.ofNanos(lag));
    }

    public static class Picker extends ShadowSystemClock.Picker {
        @Override public Class<? extends ShadowSystemClock> pickShadowClass() {
            Class<? extends ShadowSystemClock> selected = super.pickShadowClass();
            return selected == ShadowPausedSystemClock.class ? ShadowHostSystemClock.class : selected;
        }
    }
}
