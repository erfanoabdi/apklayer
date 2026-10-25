// JNI bridge to the per-thread native NDK ALooper (MacApkLooper in the bionic
// shim). On real Android a thread's Java Looper and its NDK ALooper are the SAME
// native Looper -- one poll loop services Java messages AND native fds added via
// ALooper_addFd. Mac.apk split them (Java Looper = a BlockingQueue; NDK ALooper
// = a separate C++ object), so a native vsync fd registered via ALooper_addFd
// landed on a looper nobody polled (Unity's frame driver -> black screen).
//
// This class re-unifies them: a per-thread android.os.Looper.loop() blocks in
// pollOnce() on its OWN native looper (the same object ALooper_forThread()/
// ALooper_addFd use on this thread), servicing native fds + a wake self-pipe ON
// THIS THREAD; android.os.Handler.enqueue calls wake(). Generic NDK behaviour;
// no per-APK code.
package com.macapk.loader;

public final class NativeLooper {
    private NativeLooper() {}

    /** The calling thread's native looper pointer (created if needed). This is
     *  the SAME object ALooper_forThread()/ALooper_addFd use on this thread. */
    public static native long forCurrentThread();

    /** Block up to timeoutMs (-1 = until an fd event / wake; 0 = peek) polling
     *  this looper's ALooper fds + its wake pipe; runs ready fd callbacks on
     *  THIS thread. Mirrors android_os_MessageQueue.nativePollOnce. */
    public static native void pollOnce(long nativeLooper, int timeoutMs);

    /** Non-blocking: run the callbacks of this looper's sensor / input-queue
     *  fds that are ready (not its vsync receivers). For a Looper pass that
     *  never blocks; see android.os.Looper.MACAPK_POLL_FDS_WHEN_BUSY. */
    public static native void pollPlainFds(long nativeLooper);

    /** Wake a thread blocked in pollOnce on nativeLooper (Handler.enqueue). */
    public static native void wake(long nativeLooper);

    /** Called from a "main"/owner thread's no-op Looper.loop(): if this thread's
     *  native looper owns Unity's display/gfx fds, run the poll loop here (BLOCKS
     *  forever, feeding + servicing them so Unity's frame/gfx handlers run on the
     *  owner thread). Returns 0 immediately for a thread with no native fds (any
     *  non-Unity main thread), so the caller keeps its no-op behavior. */
    public static native int runMainPoll(long nativeLooper);

    /** MACAPK_CHOREO_VSYNC_THREADS: request ONE display vsync for this native
     *  looper (AOSP DisplayEventReceiver.nativeScheduleVsync). The display tick
     *  (cocoa_bridge.mm, the same tick that fires the NDK AChoreographer) marks
     *  it delivered and wakes the looper's poll. Thread-safe. Registered SOFT by
     *  native_methods.cpp, so an older runtime leaves it unbound and
     *  android.os.Looper.macapkCanReceiveVsync() then answers false. */
    public static native void scheduleVsync(long nativeLooper);

    /** Take a delivered vsync for this native looper: its timestamp in the
     *  {@link System#nanoTime()} timebase, or -1 when none is pending (and
     *  always -1 for nativeLooper == 0). Consumes it. */
    public static native long takeVsync(long nativeLooper);
}
