// android.os.Trace -- systrace / Perfetto entry points. Apps wrap
// hot code paths in beginSection/endSection (or isEnabled() guards
// around expensive label strings) for performance tracing on real
// Android. Mac.apk does not feed systrace, so every method is a
// safe no-op -- but the class must exist or any androidx.startup
// caller (and many other modern libs) hits ClassNotFoundException
// during init and never reaches the app code.
//
// Per AOSP frameworks/base/core/java/android/os/Trace.java the
// surface third-party apps can target is the begin/end + async
// + counter API plus isEnabled(). The TRACE_TAG_* longs are
// @SystemApi/@hide in AOSP, so app code should not see them, but
// we export the common ones because androidx-internal code does
// reference TRACE_TAG_APP via reflection in some versions.
//
// API levels (informational, all available here):
//   18: beginSection / endSection
//   29: beginAsyncSection / endAsyncSection / setCounter / isEnabled
package android.os;

public final class Trace {
    private Trace() {}

    // TRACE_TAG_* bit-flags used by isTagEnabled(). Per AOSP
    // frameworks/base/core/java/android/os/Trace.java these are
    // hide/@SystemApi but appear in reflective callers.
    public static final long TRACE_TAG_NEVER       = 0L;
    public static final long TRACE_TAG_ALWAYS      = 1L << 0;
    public static final long TRACE_TAG_GRAPHICS    = 1L << 1;
    public static final long TRACE_TAG_INPUT       = 1L << 2;
    public static final long TRACE_TAG_VIEW        = 1L << 3;
    public static final long TRACE_TAG_WEBVIEW     = 1L << 4;
    public static final long TRACE_TAG_WINDOW_MANAGER     = 1L << 5;
    public static final long TRACE_TAG_ACTIVITY_MANAGER   = 1L << 6;
    public static final long TRACE_TAG_SYNC_MANAGER       = 1L << 7;
    public static final long TRACE_TAG_AUDIO       = 1L << 8;
    public static final long TRACE_TAG_VIDEO       = 1L << 9;
    public static final long TRACE_TAG_CAMERA      = 1L << 10;
    public static final long TRACE_TAG_HAL         = 1L << 11;
    public static final long TRACE_TAG_APP         = 1L << 12;
    public static final long TRACE_TAG_RESOURCES   = 1L << 13;
    public static final long TRACE_TAG_DALVIK      = 1L << 14;
    public static final long TRACE_TAG_RS          = 1L << 15;

    // Synchronous sections. Calls must be balanced -- but our no-op
    // impl can't enforce that, and on real Android the systrace
    // collector handles mismatch gracefully too.
    public static void beginSection(String sectionName) {}
    public static void endSection() {}

    // Async sections (API 29). cookie disambiguates concurrent
    // sections sharing a name.
    public static void beginAsyncSection(String methodName, int cookie) {}
    public static void endAsyncSection(String methodName, int cookie) {}

    // Counter (API 29). Records a named numeric sample.
    public static void setCounter(String counterName, long counterValue) {}

    // API 29. With no tracing collector, nothing is ever enabled --
    // callers using this as a guard around expensive label-building
    // get the fast path.
    public static boolean isEnabled() { return false; }

    // @SystemApi in AOSP but reachable via reflection from some
    // androidx internal helpers; safe to expose as the same no-op.
    public static boolean isTagEnabled(long traceTag) { return false; }

    // Hidden helpers some compat shims poke at by name. All no-op.
    public static void traceBegin(long traceTag, String methodName) {}
    public static void traceEnd(long traceTag) {}
    public static void traceCounter(long traceTag, String counterName, int counterValue) {}
    public static void asyncTraceBegin(long traceTag, String methodName, int cookie) {}
    public static void asyncTraceEnd(long traceTag, String methodName, int cookie) {}
    public static void setAppTracingAllowed(boolean allowed) {}
}
