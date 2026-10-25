// android.os.HandlerThread -- a Thread that owns its own Looper.
// Apps create one to drive a Handler off the UI thread for
// background callbacks (Google Play Services uses this for the
// internal "default executor", androidx.work uses it for the
// scheduler, AppLovin uses it for ad-network polling).
//
// AOSP behavior (frameworks/base/core/java/android/os/HandlerThread.java):
//   - start() invokes run() on a new Thread
//   - run() creates a Looper via Looper.prepare(), captures it,
//     notifies any thread waiting in getLooper(), then calls
//     Looper.loop()
//   - getLooper() blocks until the Looper exists (so the calling
//     thread can construct a Handler on it without racing)
//   - quit() / quitSafely() tear down the Looper
//
// Mac.apk's android.os.Looper / android.os.Handler implement enough
// of the Looper contract for posted Runnables to dispatch on the
// owning thread. HandlerThread on top of those is straightforward.
package android.os;

public class HandlerThread extends Thread {
    private final int priority;
    private Looper looper;
    private final Object lock = new Object();
    private boolean started;
    // AOSP captures the thread's tid inside run() (mTid = Process.myTid())
    // and getThreadId() returns it -- so callers on OTHER threads still get
    // THIS thread's id, not the caller's. -1 until run() executes, matching
    // AOSP's initial value.
    private int mTid = -1;

    public HandlerThread(String name) {
        super(name);
        this.priority = Thread.NORM_PRIORITY;
    }
    public HandlerThread(String name, int priority) {
        super(name);
        this.priority = priority;
    }

    /** Hook for subclasses to do setup work on the new thread BEFORE
     *  Looper.loop() begins consuming messages. Empty by default. */
    protected void onLooperPrepared() {}

    @Override
    public void run() {
        // Capture this thread's id ON this thread (AOSP: mTid = Process.myTid()).
        mTid = android.os.Process.myTid();
        synchronized (lock) {
            // Looper.prepare() binds a Looper to the current thread
            // via a ThreadLocal. After prepare(), Looper.myLooper()
            // returns the new instance. Capture it so callers
            // blocked in getLooper() can pick it up.
            Looper.prepare();
            looper = Looper.myLooper();
            lock.notifyAll();
        }
        // AOSP HandlerThread.run() calls Process.setThreadPriority(mPriority)
        // -- the Android/Linux NICE scale (-20 most favorable..19 least
        // favorable; Process.THREAD_PRIORITY_BACKGROUND=10,
        // THREAD_PRIORITY_DEFAULT=0, THREAD_PRIORITY_URGENT_DISPLAY=-8, ...),
        // explicitly documented as "not [for] use with the normal
        // java.lang.Thread class." java.lang.Thread.setPriority's scale runs
        // the OPPOSITE direction (1 low..10 high) and only covers [1,10], so
        // feeding the raw nice value straight in either throws (silently
        // swallowed by the catch below, for every value outside [1,10]) or,
        // worse, for THREAD_PRIORITY_BACKGROUND(10) -- the one nice value
        // that happens to fall in Java's range -- SUCCEEDS at making a
        // background-priority thread run at Thread.MAX_PRIORITY, the
        // opposite of what was requested. There is no real priority system
        // on macOS to route this through (Thread priority is itself only
        // advisory to the JVM/OS scheduler), so this converts nice -> Java
        // scale with the correct DIRECTION and a clamp into [1,10]: divide
        // by 2 (halving the ~40-wide nice range onto Java's 10-wide range
        // keeps THREAD_PRIORITY_FOREGROUND/DISPLAY/URGENT_DISPLAY distinct
        // from DEFAULT instead of every non-multiple-of-4 value collapsing
        // to NORM_PRIORITY), then subtract from NORM_PRIORITY so a MORE
        // negative (more favorable) nice value maps to a HIGHER Java
        // priority and vice versa.
        int javaPriority = Thread.NORM_PRIORITY - priority / 2;
        javaPriority = Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, javaPriority));
        try { setPriority(javaPriority); } catch (Throwable ignored) {}
        onLooperPrepared();
        Looper.loop();
    }

    /** Returns the Looper owned by this thread, blocking until the
     *  thread has called Looper.prepare(). Most callers do
     *  `t.start(); new Handler(t.getLooper())` which would race
     *  without this gate. */
    public Looper getLooper() {
        if (!isAlive()) return null;
        synchronized (lock) {
            while (isAlive() && looper == null) {
                try { lock.wait(); } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            return looper;
        }
    }

    /** Stop the thread by quitting its Looper. AOSP returns true if a
     *  Looper was running; false if not yet started. */
    public boolean quit() {
        Looper l = looper;
        if (l == null) return false;
        l.quit();
        return true;
    }
    public boolean quitSafely() {
        Looper l = looper;
        if (l == null) return false;
        l.quitSafely();
        return true;
    }
    /** AOSP HandlerThread.getThreadId(): returns the tid captured inside
     *  run() via Process.myTid() (-1 before the thread runs). Mac.apk's
     *  Process.myTid() returns the Java thread id of the running thread (no
     *  real Linux tid without native), but the AOSP STRUCTURE holds: the
     *  value is THIS HandlerThread's id, captured on its own thread, not the
     *  caller's. (Native Linux-tid fidelity is a separate native-layer item.) */
    public int getThreadId() { return mTid; }
}
