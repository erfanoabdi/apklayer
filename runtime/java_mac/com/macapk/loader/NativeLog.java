// Mac.apk's single log sink. android.util.Log delegates here, so any
// app log line ends up at the same printf in native code -- which makes
// it trivial to redirect later (file, launcher tail, etc).
package com.macapk.loader;

public final class NativeLog {
    private NativeLog() {}

    public static final int VERBOSE = 2;
    public static final int DEBUG   = 3;
    public static final int INFO    = 4;
    public static final int WARN    = 5;
    public static final int ERROR   = 6;

    // Minimum level to emit, from MACAPK_LOG_LEVEL (default 0 = everything, so
    // direct CLI runs + the debug harnesses that grep the log are unchanged).
    // The native Log_print funnel enforces the same gate authoritatively (it
    // also covers android.util.Log); this Java check just skips the JNI
    // crossing for a suppressed framework log. Callers building an expensive
    // message should guard it with isLoggable().
    //
    // The launcher UI passes MACAPK_LOG_LEVEL=4 (INFO -- AOSP's own default
    // floor, see android/util/Log.java) plus MACAPK_QUIET_CLASSLOAD=1. It used
    // to pass 5 (WARN+ERROR), which was a severity gate standing in for a
    // per-call-site gate: 95.5% of all INFO volume is the AppClassLoader
    // loadClass/findClass pair, and silencing the whole severity to remove it
    // also silenced the PackageInstaller install lifecycle -- so a real install
    // left no trail in the launcher log and could not be diagnosed. Do not
    // reintroduce a severity floor above INFO here or in any spawner: if a call
    // site is too chatty, give that call site its own knob, the way
    // AppClassLoader.QUIET_CLASSLOAD does.
    private static final int MIN_LEVEL = readMinLevel();
    private static int readMinLevel() {
        try {
            String s = System.getenv("MACAPK_LOG_LEVEL");
            return (s != null && !s.isEmpty()) ? Integer.parseInt(s.trim()) : 0;
        } catch (Throwable t) { return 0; }
    }
    /** True if a log at {@code level} would be emitted -- guard costly message
     *  construction with this at hot call sites. */
    public static boolean isLoggable(int level) { return level >= MIN_LEVEL; }

    public static native void print(int level, String tag, String msg);
    /** Diagnostic: install JNI-call tracer on the CURRENT thread's
     *  JNIEnv vtable. No-op unless MACAPK_TRACE_JNI is set. Each
     *  HotSpot thread receives a per-thread vtable, so the patch
     *  must be applied from the thread we want to observe. */
    public static native void nativeInstallJniTrace();

    /** MACAPK_PAIRIP_GATE_EMU: re-bind the app's com.pairip.VMRunner.executeVM native to
     *  Mac.apk's in-layer integrity gate (detection-preserving; corrects the HotSpot-on-macOS
     *  structural false-positive that FALSELY detonates pairip's integrity VM). Bound natively
     *  to NativeLog (no new Java class needed). Returns true iff the rebind succeeded. */
    public static native boolean nativeRegisterPairipGate(Class<?> vmRunner);
    /** Elevate the CALLING thread to USER_INTERACTIVE QoS (pthread_set_qos_class
     *  _self_np). Called once on the macapk-activity (Android UI) thread so it
     *  preempts background Dispatchers.Default coroutine workers under CPU
     *  contention -- otherwise heavy background load + per-frame recomposition
     *  starves the UI thread, the per-vsync draw stops, the Compose frame clock
     *  dies, and in-flight animations freeze. Mirrors Android's elevated UI-thread
     *  priority. Bound natively to NativeLog (no new Java class needed). */
    public static native void nativeBoostCurrentThreadQos();

    /** CLOCK_BOOTTIME on the System.nanoTime() tick base: mach_continuous_time()
     *  converted exactly as HotSpot converts mach_absolute_time() (so it is
     *  System.nanoTime() plus the time the host slept). Backs
     *  android.os.SystemClock.elapsedRealtimeNanos() (MACAPK_GUEST_CLOCK_NANOTIME).
     *  Bound natively (native_methods.cpp kNativeBootTime); absent on a bare JVM,
     *  where SystemClock falls back to System.nanoTime(). */
    public static native long nativeBootTimeNanos();

    public static void v(String tag, String msg) { if (VERBOSE >= MIN_LEVEL) print(VERBOSE, tag, msg); }
    public static void d(String tag, String msg) { if (DEBUG   >= MIN_LEVEL) print(DEBUG,   tag, msg); }
    public static void i(String tag, String msg) { if (INFO    >= MIN_LEVEL) print(INFO,    tag, msg); }
    public static void w(String tag, String msg) { if (WARN    >= MIN_LEVEL) print(WARN,    tag, msg); }
    public static void e(String tag, String msg) { if (ERROR   >= MIN_LEVEL) print(ERROR,   tag, msg); }
}
