// android.util.LogPrinter
//
// AOSP frameworks/base/core/java/android/util/LogPrinter.java: a Printer
// that forwards each println() to android.util.Log at a given priority +
// tag. v1.0.464 (subsystem-04 close-list): was an empty class stub that
// did NOT implement Printer and had no working ctor; restored to the
// faithful public surface so `new LogPrinter(Log.DEBUG, "tag")` works and
// it can be passed wherever a Printer is expected (Looper.setMessageLogging,
// dump methods, etc.).
package android.util;

public class LogPrinter implements Printer {
    private final int mPriority;
    private final String mTag;

    /** @param priority one of the android.util.Log priority constants.
     *  @param tag the log tag to use. */
    public LogPrinter(int priority, String tag) {
        mPriority = priority;
        mTag = tag;
    }

    public void println(String x) {
        Log.println(mPriority, mTag, x);
    }
}
