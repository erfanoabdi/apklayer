package apklayer.host;

import org.robolectric.annotation.internal.DoNotInstrument;

/** The calling thread's native Looper (see bridge/native/app_native.c): where Chromium's message pump waits for its native tasks. */
@DoNotInstrument
public final class NativeLooper {
    private NativeLooper() {}

    /** The thread's ALooper, 0 when nothing created one (no native code runs a message pump on this thread). */
    public static native long forThread();

    /** Runs what is ready on the thread's native looper, waiting up to timeoutMs (0: not at all); the ALooper_pollOnce result. */
    public static native int pollOnce(int timeoutMs);

    public static native void wake(long looper);
}
