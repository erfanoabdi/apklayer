// android.os.ConditionVariable -- a one-shot synchronization
// primitive (logically equivalent to a CountDownLatch initialized
// to 1). Apps use it for "wait until I've finished initializing"
// patterns. Real Android backs it with an Object monitor.
//
// HCR 1.43's Google Mobile Ads code (com.google.android.gms.internal.ads.zzuo)
// references it in a <clinit> chain; absent, the class init throws
// ExceptionInInitializerError and the AdManager statically fails,
// which now cascades into the activity boot path -- meaning the
// whole app hangs after just a few frames. Adding this restores
// HCR 1.43's previously-working 60 fps boot.
//
// Per AOSP frameworks/base/core/java/android/os/ConditionVariable.java
// the API is: ConditionVariable() / ConditionVariable(boolean state);
// open(); close(); block(); block(long timeoutMs).
package android.os;

public class ConditionVariable {
    private final Object lock = new Object();
    private boolean isOpen;

    public ConditionVariable() { this(false); }
    public ConditionVariable(boolean state) { this.isOpen = state; }

    public void open() {
        synchronized (lock) {
            if (!isOpen) {
                isOpen = true;
                lock.notifyAll();
            }
        }
    }

    public void close() {
        synchronized (lock) {
            isOpen = false;
        }
    }

    public void block() {
        synchronized (lock) {
            while (!isOpen) {
                try { lock.wait(); }
                catch (InterruptedException ie) { /* retry */ }
            }
        }
    }

    public boolean block(long timeoutMs) {
        // AOSP semantics (frameworks/base/core/java/android/os/ConditionVariable.java):
        // ONLY timeoutMs == 0 means "block forever". A NEGATIVE timeout is NOT
        // indefinite -- it makes `end = now + timeoutMs` already in the past, so the
        // wait loop never runs and block() returns the current state (false when
        // closed) IMMEDIATELY. The old `timeoutMs <= 0` treated a negative timeout as
        // indefinite and hung: Instagram's InstagramAppShell.attachBaseContext calls
        // block(-1L) as a non-blocking "is it open yet?" probe (X.3Sa.A05 -- if it
        // returns false it QUEUES the callback and continues), so the wrong branch
        // deadlocked the whole app in attachBaseContext before the first activity.
        if (timeoutMs != 0) {
            synchronized (lock) {
                long now = System.currentTimeMillis();
                long end = now + timeoutMs;
                while (!isOpen && now < end) {
                    try { lock.wait(end - now); }
                    catch (InterruptedException ie) { /* retry */ }
                    now = System.currentTimeMillis();
                }
                return isOpen;
            }
        } else {
            block();
            return true;
        }
    }
}
