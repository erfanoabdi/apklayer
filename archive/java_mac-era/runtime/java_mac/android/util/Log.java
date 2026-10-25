// android.util.Log -- single, well-known logging entry point.
// Delegates to Mac.apk's NativeLog so every line winds up on the same
// printf path as our own internal traces.
package android.util;

import com.macapk.loader.NativeLog;

public final class Log {
    private Log() {}

    public static final int VERBOSE = NativeLog.VERBOSE;
    public static final int DEBUG   = NativeLog.DEBUG;
    public static final int INFO    = NativeLog.INFO;
    public static final int WARN    = NativeLog.WARN;
    public static final int ERROR   = NativeLog.ERROR;
    public static final int ASSERT  = 7;

    // Every write method returns the number of bytes written, and on Android 11
    // that number is the literal 1. liblog's __android_log_buf_write
    // (system/logging/liblog/logger_write.cpp) is
    //     if (!__android_log_is_loggable(prio, tag, ANDROID_LOG_VERBOSE))
    //         return -EPERM;
    //     __android_log_write_log_message(&log_message);
    //     return 1;
    // -- the message-writing call returns void, so the only non-error answer is
    // 1. Returning 0 here (what we did before) is neither: app code that guards
    // on `if (Log.d(...) > 0)` or asserts a non-zero write took the failure
    // branch on every line.
    //
    // Note the level this gates on is ANDROID_LOG_VERBOSE, whereas isLoggable
    // below gates on ANDROID_LOG_INFO. That asymmetry is deliberate in AOSP and
    // is why Log.d() succeeds on a device where Log.isLoggable(tag, DEBUG) is
    // false. Do NOT collapse the two onto one helper.
    private static final int LOG_WRITE_OK = 1;

    public static int v(String tag, String msg) { NativeLog.v(tag, msg); return LOG_WRITE_OK; }
    public static int d(String tag, String msg) { NativeLog.d(tag, msg); return LOG_WRITE_OK; }
    public static int i(String tag, String msg) { NativeLog.i(tag, msg); return LOG_WRITE_OK; }
    public static int w(String tag, String msg) { NativeLog.w(tag, msg); return LOG_WRITE_OK; }
    public static int e(String tag, String msg) { NativeLog.e(tag, msg); return LOG_WRITE_OK; }

    public static int v(String tag, String msg, Throwable tr) {
        return v(tag, msg + (tr != null ? "\n" + getStackTraceString(tr) : ""));
    }
    public static int d(String tag, String msg, Throwable tr) {
        return d(tag, msg + (tr != null ? "\n" + getStackTraceString(tr) : ""));
    }
    public static int i(String tag, String msg, Throwable tr) {
        return i(tag, msg + (tr != null ? "\n" + getStackTraceString(tr) : ""));
    }
    public static int w(String tag, String msg, Throwable tr) {
        return w(tag, msg + (tr != null ? "\n" + getStackTraceString(tr) : ""));
    }
    public static int w(String tag, Throwable tr) {
        return w(tag, "", tr);
    }
    public static int e(String tag, String msg, Throwable tr) {
        return e(tag, msg + (tr != null ? "\n" + getStackTraceString(tr) : ""));
    }

    public static int println(int priority, String tag, String msg) {
        // android_util_Log.cpp's println_native rejects a null message before
        // it reaches liblog:
        //   if (msgObj == NULL) { jniThrowNullPointerException(env,
        //       "println needs a message"); return -1; }
        // so println(_, _, null) is an NPE on a real device, not a silent
        // no-op. Swallowing it here hid the caller's bug until something
        // downstream read a log line that was never written.
        if (msg == null) {
            throw new NullPointerException("println needs a message");
        }
        NativeLog.print(priority, tag, msg);
        return LOG_WRITE_OK;
    }

    // AOSP's real isLoggable defaults a tag's level to INFO (VERBOSE/DEBUG
    // suppressed unless a developer raises log.tag.<TAG>) -- verified from
    // aosp-mirror/platform_frameworks_base core/java/android/util/Log.java's
    // own doc: "The default level of any tag is set to INFO." Hardcoding
    // true here defeated the standard idiom app code and bundled SDKs
    // (OkHttp's logging interceptor, Firebase, ad/analytics SDKs) use to
    // SKIP building an expensive debug/verbose message:
    // `if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, expensiveToString());`
    // -- every such app unconditionally did the expensive-message-
    // construction work Mac.apk's stub always told it was wanted. Wiring
    // this to the SAME MACAPK_LOG_LEVEL gate our own framework logging
    // already respects (NativeLog.isLoggable) is at least as permissive as
    // AOSP's real default (our default MIN_LEVEL=0 for CLI runs is MORE
    // permissive than AOSP's INFO floor), so no app that currently works
    // stops working -- it only stops doing needlessly-suppressed DEBUG/
    // VERBOSE work under the launcher's quieter MACAPK_LOG_LEVEL=5 default.
    // Apps are required to tolerate isLoggable() returning false (that is
    // the whole point of the API), so this can only make an app log/compute
    // LESS, never crash or misbehave. See
    // docs/efficiency-classload-logging_2026-07-24.md Finding 4.
    //
    // CORRECTED: delegating straight to NativeLog.isLoggable was wrong in the
    // DEFAULT case, because NativeLog's floor is 0 ("emit everything") when
    // MACAPK_LOG_LEVEL is unset, and AOSP's floor is INFO. AOSP's native side is
    //     android_util_Log.cpp: if (tag == NULL) return false;
    //                           __android_log_is_loggable(level, tag, ANDROID_LOG_INFO)
    // where the third argument is the DEFAULT minimum, overridable per tag by a
    // log.tag.<TAG> system property. MACAPK_LOG_LEVEL is our analogue of that
    // property, so it applies when EXPLICITLY SET and otherwise we take AOSP's
    // INFO floor -- an unset environment must not read as "the developer lowered
    // the floor to VERBOSE". A null tag is false regardless of level, which is
    // the native null check above and not an accident of the level comparison.
    private static final int DEFAULT_MIN_LEVEL = readMinLevel();

    private static int readMinLevel() {
        String s = System.getenv("MACAPK_LOG_LEVEL");
        if (s != null) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // Fall through to the AOSP default.
            }
        }
        return INFO;
    }

    public static boolean isLoggable(String tag, int level) {
        if (tag == null) return false;
        return level >= DEFAULT_MIN_LEVEL;
    }

    // AOSP suppresses the trace entirely when an UnknownHostException appears
    // ANYWHERE in the cause chain (Log.java:349-362), "to reduce the amount of
    // log spew that apps do in the non-error condition of the network being
    // unavailable". Callers branch on the empty string, so producing a real
    // trace here is a behaviour difference an offline app can see.
    public static String getStackTraceString(Throwable tr) {
        if (tr == null) return "";

        Throwable t = tr;
        while (t != null) {
            if (t instanceof java.net.UnknownHostException) {
                return "";
            }
            t = t.getCause();
        }

        java.io.StringWriter sw = new java.io.StringWriter(256);
        java.io.PrintWriter  pw = new java.io.PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    public static int wtf(String tag, String msg) { return e(tag, "WTF: " + msg); }
    public static int wtf(String tag, Throwable tr) { return wtf(tag, ""); }
    public static int wtf(String tag, String msg, Throwable tr) { return e(tag, "WTF: " + msg, tr); }

    public static int e(String tag, Throwable tr) { return e(tag, tr != null ? tr.toString() : "", tr); }
}

