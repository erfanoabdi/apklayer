// android.os.SystemClock -- the monotonic clocks.
//
// MACAPK_GUEST_CLOCK_NANOTIME (default ON, 2026-09-28): AOSP's identities, exactly.
//   uptimeMillis()/uptimeNanos(): system/core libutils SystemClock.cpp:48-50
//     `uptimeNanos() { return systemTime(SYSTEM_TIME_MONOTONIC); }` == CLOCK_MONOTONIC,
//     the clock ART's System.nanoTime() reads. So uptimeNanos() == System.nanoTime().
//   elapsedRealtime()/elapsedRealtimeNanos(): SystemClock.cpp:68
//     `clock_gettime(CLOCK_BOOTTIME, &ts)`: the same clock plus time suspended.
// The guest's native clock_gettime(CLOCK_MONOTONIC / CLOCK_BOOTTIME) are mapped onto the
// same two host clocks (bionic_shim.cpp macapk_guest_clock_host_id), and Choreographer
// frame times are System.nanoTime() (MACAPK_CHOREO_NANOTIME_BASE), so an app that mixes
// any of them -- a MotionEvent time against a frame time, a native stamp against a Java
// one -- sees one clock, as on a device. The values are host-uptime sized (days), which is
// what a device that has been up for days reports too.
// =0: the previous process-relative base (both clocks count from class init), unchanged.
// docs/guest-clock-one-monotonic-base_2026-09-28.md.
package android.os;

public final class SystemClock {
    private static final long START_NANOS = System.nanoTime();
    private SystemClock() {}

    // public (not just package-private) so cross-package callers that must
    // match this same gate -- android.os.Process.setStartTimes,
    // android.hardware.SensorManager's elapsedRealtimeNanos-based event
    // timestamp -- read the identical decision rather than re-parsing the
    // env var themselves (2026-09-28 review fix; no behaviour change, the
    // field's value and meaning are unchanged).
    public static final boolean AOSP_CLOCKS =
        !"0".equals(System.getenv("MACAPK_GUEST_CLOCK_NANOTIME"));

    /** CLOCK_BOOTTIME on the System.nanoTime() base: the runtime's native
     *  (mach_continuous_time), else System.nanoTime() itself on a bare JVM (an
     *  offline harness), which differs only by time the host slept. */
    private static volatile boolean sBootNative = true;
    private static long bootNanos() {
        if (sBootNative) {
            try {
                return com.macapk.loader.NativeLog.nativeBootTimeNanos();
            } catch (LinkageError e) {
                // UnsatisfiedLinkError (the native symbol truly isn't registered,
                // e.g. this offline gate's bare JVM) AND NoClassDefFoundError (the
                // framework jar loaded without com.macapk.loader on the classpath)
                // both mean "no native boot-time path available here" -- either
                // way, fall back for the rest of the process rather than retry
                // every call. Both are LinkageError subclasses.
                sBootNative = false;
            }
        }
        return System.nanoTime();
    }

    public static long uptimeMillis() {
        if (AOSP_CLOCKS) return System.nanoTime() / 1_000_000L;
        return (System.nanoTime() - START_NANOS) / 1_000_000L;
    }
    // elapsedRealtime() must be MONOTONIC: AOSP callers use it for timers,
    // timeouts and backoff windows, and a wall-clock (currentTimeMillis)
    // backing jumps backward/forward on an NTP sync, a manual date change or a
    // macOS DST/timezone correction, which breaks those. Back it with the same
    // monotonic base as elapsedRealtimeNanos so the two stay consistent. Under
    // MACAPK_GUEST_CLOCK_NANOTIME it is CLOCK_BOOTTIME (bootNanos above), which a
    // host sleep advances and uptimeMillis does not, as on a device; under =0 both
    // count since process start.
    public static long elapsedRealtime() {
        if (AOSP_CLOCKS) return bootNanos() / 1_000_000L;
        return (System.nanoTime() - START_NANOS) / 1_000_000L;
    }
    public static long elapsedRealtimeNanos() {
        if (AOSP_CLOCKS) return bootNanos();
        return System.nanoTime() - START_NANOS;
    }
    // AOSP: per-thread CPU time (only advances while THIS thread runs), not
    // process uptime. Back it with ThreadMXBean (OpenJDK 21); fall back to
    // uptimeMillis if thread CPU time is unsupported.
    public static long currentThreadTimeMillis() {
        try {
            java.lang.management.ThreadMXBean b = java.lang.management.ManagementFactory.getThreadMXBean();
            if (b != null && b.isCurrentThreadCpuTimeSupported()) {
                long ns = b.getCurrentThreadCpuTime();
                if (ns >= 0) return ns / 1_000_000L;
            }
        } catch (Throwable ignored) {}
        return uptimeMillis();
    }
    public static boolean setCurrentTimeMillis(long millis) { return false; }
    public static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    public static java.time.Clock currentNetworkTimeClock() { return java.time.Clock.systemUTC(); }

    /** AOSP core/java/android/os/SystemClock.java:224 -- "nanoseconds since
     *  boot, not counting time spent in deep sleep": CLOCK_MONOTONIC, i.e.
     *  System.nanoTime() (MACAPK_GUEST_CLOCK_NANOTIME). elapsedRealtimeNanos()
     *  is the same tick count plus host sleep, so the two share a base and
     *  differ by exactly the time suspended, as on a device. */
    public static long uptimeNanos() {
        if (AOSP_CLOCKS) return System.nanoTime();
        return System.nanoTime() - START_NANOS;
    }

    /** AOSP :414 -- a Clock synchronised from the device's LOCATION provider,
     *  documented to "@throws DateTimeException when the location provider has
     *  not had a location fix since boot". Note where the throw lives
     *  upstream: the factory always returns a Clock, and millis() throws when
     *  no GNSS fix is available. This host has no GNSS receiver, so it is
     *  permanently in the no-fix state -- which is a state real devices are in
     *  too (indoors, airplane mode, no location permission). Returning
     *  systemUTC() instead would silently hand back WALL-CLOCK time under the
     *  name of satellite time, which is the kind of lie a navigation or
     *  timestamping app cannot detect. */
    public static java.time.Clock currentGnssTimeClock() {
        return new java.time.Clock() {
            @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public long millis() {
                throw new java.time.DateTimeException("Gnss based time is not available.");
            }
            @Override public java.time.Instant instant() {
                return java.time.Instant.ofEpochMilli(millis());
            }
        };
    }
}

