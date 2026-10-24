// android.util.TimingLogger -- a method-level timing helper apps use to log
// how long each phase of a call took.
//
// This is a REAL implementation, not a device-capability stub: TimingLogger
// touches no hardware and no system service, it just reads a monotonic clock
// and writes to Log. Apps READ its output while debugging, so a no-op stub
// would silently swallow diagnostics the app author expects to see.
//
// Mirrors frameworks/base/core/java/android/util/TimingLogger.java (API 1,
// android11-release) line for line, including the two behaviours that look
// like bugs but are AOSP's actual contract:
//   * reset() is gated on Log.isLoggable(tag, VERBOSE) and, when that is
//     false, sets mDisabled and leaves mSplits NULL. addSplit/dumpToLog then
//     return early forever. Our Log.isLoggable already carries AOSP's
//     default-to-INFO semantics, so the gate behaves as it does on a device.
//   * reset() seeds the list with addSplit(null), so split 0 is the "begin"
//     timestamp and dumpToLog starts its loop at index 1.
//
// Deprecated on AOSP in favour of android.os.Trace, but still referenced by
// apps in the corpus, so the class must exist and behave.
package android.util;

import java.util.ArrayList;

import android.os.SystemClock;

@Deprecated
public class TimingLogger {

    /** The Log tag used for the isLoggable gate and for the timing output. */
    private String mTag;

    /** A label included in every line, naming the method being timed. */
    private String mLabel;

    /** Latched at reset() time, per AOSP: a later log-level change is ignored. */
    private boolean mDisabled;

    /** Time of each split. Stays null while disabled -- see the header note. */
    ArrayList<Long> mSplits;

    /** Label for each split. Index 0 is always null (the implicit "begin"). */
    ArrayList<String> mSplitLabels;

    public TimingLogger(String tag, String label) {
        reset(tag, label);
    }

    public void reset(String tag, String label) {
        mTag = tag;
        mLabel = label;
        reset();
    }

    public void reset() {
        mDisabled = !Log.isLoggable(mTag, Log.VERBOSE);
        if (mDisabled) return;
        if (mSplits == null) {
            mSplits = new ArrayList<Long>();
            mSplitLabels = new ArrayList<String>();
        } else {
            mSplits.clear();
            mSplitLabels.clear();
        }
        addSplit(null);
    }

    public void addSplit(String splitLabel) {
        if (mDisabled) return;
        // elapsedRealtime, not currentTimeMillis: a wall-clock step would
        // produce negative splits. Ours is monotonic on macOS.
        long now = SystemClock.elapsedRealtime();
        mSplits.add(now);
        mSplitLabels.add(splitLabel);
    }

    public void dumpToLog() {
        if (mDisabled) return;
        Log.d(mTag, mLabel + ": begin");
        final long first = mSplits.get(0);
        long now = first;
        for (int i = 1; i < mSplits.size(); i++) {
            now = mSplits.get(i);
            final String splitLabel = mSplitLabels.get(i);
            final long prev = mSplits.get(i - 1);

            Log.d(mTag, mLabel + ":      " + (now - prev) + " ms, " + splitLabel);
        }
        Log.d(mTag, mLabel + ": end, " + (now - first) + " ms");
    }
}
