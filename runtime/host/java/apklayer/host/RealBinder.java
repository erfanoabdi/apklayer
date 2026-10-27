package apklayer.host;

import android.os.IBinder;
import org.robolectric.annotation.internal.DoNotInstrument;

/**
 * Real kernel-binder access from an app JVM ({@code -Dapklayer.realbinder=true}): the binder thread pool, and publishing / looking up a Java binder
 * by name in the instance's service manager. The natives are registered by the shim (bridge/native/binder_runtime.c).
 */
@DoNotInstrument
public final class RealBinder {
    private RealBinder() {}

    private static boolean started;

    /** Starts the binder thread pool of this JVM (once): incoming transactions run on its threads. */
    public static synchronized void init() {
        if (started) return;
        started = true;
        startPool();
    }

    /** Registers the binder under the name in the instance's service manager. */
    public static boolean publish(String name, IBinder binder) { init(); return publishNative(name, binder); }

    /** The binder registered under the name, or null: a BinderProxy when it lives in another JVM. */
    public static IBinder lookup(String name) { init(); return lookupNative(name); }

    // The shim registers these three under the plain names publish/lookup/startPool; the public methods above add the pool start.
    private static native boolean publish0(String name, IBinder binder);
    private static native IBinder lookup0(String name);
    private static native void startPool();
    private static boolean publishNative(String name, IBinder binder) { return publish0(name, binder); }
    private static IBinder lookupNative(String name) { return lookup0(name); }
}
