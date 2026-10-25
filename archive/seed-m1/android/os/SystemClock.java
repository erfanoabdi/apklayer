package android.os;

// apklayer patch: minimal SystemClock (upstream pulls IAlarmManager/Context/LocationTime).
// uptimeMillis is wall-since-boot incl. sleep on Android; approximated with nanoTime here,
// which is fine for message scheduling (monotonic reference is what matters).
public final class SystemClock {
    private static final long NANO_BASE = System.nanoTime();
    public static long uptimeMillis() { return (System.nanoTime() - NANO_BASE) / 1_000_000L; }
    public static long elapsedRealtime() { return uptimeMillis(); }
    public static long elapsedRealtimeNanos() { return (System.nanoTime() - NANO_BASE); }
    public static long uptimeMillis(int clockId) { return uptimeMillis(); }
    public static long currentThreadTimeMillis() { return System.currentTimeMillis(); }
    public static long nanoTime() { return System.nanoTime(); }
}
