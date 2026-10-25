// android.os.TokenWatcher -- tracks a set of IBinder tokens and invokes
// acquired()/released() callbacks as the set transitions between empty and
// non-empty. Apps rarely use it directly (it underpins some system services),
// but the type is referenced by code that wraps wake-lock-like token bookkeeping.
// Faithful single-VM port: we keep the live token set and fire the callbacks on
// the supplied Handler exactly as AOSP does.
//
// AOSP ref: frameworks/base/core/java/android/os/TokenWatcher.java
package android.os;

import java.util.WeakHashMap;

public abstract class TokenWatcher {
    private final WeakHashMap<IBinder, Object> mTokens = new WeakHashMap<>();
    private final Handler mHandler;
    private final String mTag;
    private boolean mAcquired = false;

    public TokenWatcher(Handler h, String tag) {
        mHandler = h;
        mTag = tag;
    }

    public abstract void acquired();
    public abstract void released();

    public void acquire(IBinder token, String tag) {
        synchronized (mTokens) {
            // Real AOSP links a death recipient so a dead token auto-releases.
            // There is no remote death in-process, so we just record the token.
            boolean wasEmpty = mTokens.isEmpty();
            mTokens.put(token, tag);
            if (wasEmpty && !mAcquired) {
                mAcquired = true;
                sendNotificationLocked(true);
            }
        }
    }

    public void cleanup(IBinder token, boolean unlink) {
        synchronized (mTokens) {
            mTokens.remove(token);
            if (mTokens.isEmpty() && mAcquired) {
                mAcquired = false;
                sendNotificationLocked(false);
            }
        }
    }

    public void release(IBinder token) {
        cleanup(token, true);
    }

    public boolean isAcquired() {
        synchronized (mTokens) {
            return mAcquired;
        }
    }

    public void dump() {}
    public void dump(java.io.PrintWriter pw) {}

    private void sendNotificationLocked(final boolean acquire) {
        Runnable r = new Runnable() {
            @Override public void run() {
                if (acquire) acquired(); else released();
            }
        };
        if (mHandler != null) mHandler.post(r); else r.run();
    }

    @Override public String toString() {
        return "TokenWatcher{" + mTag + " acquired=" + mAcquired + "}";
    }
}
