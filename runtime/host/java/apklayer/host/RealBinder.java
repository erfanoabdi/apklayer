package apklayer.host;

import android.os.IBinder;
import org.robolectric.annotation.internal.DoNotInstrument;

/**
 * Real kernel-binder access from an app JVM (always on): the binder thread pool, and publishing / looking up a Java binder
 * by name in the instance's service manager. The natives are registered by the shim (bridge/native/binder_runtime.c).
 */
@DoNotInstrument
public final class RealBinder {
    private RealBinder() {}

    private static boolean started;

    /** Starts the binder thread pool of this JVM (once): incoming transactions run on its threads. */
    public static synchronized void init() {
        if (started) return;
        NativeMedia.ensureInitialized();
        startPool();
        started = true;
    }

    /** Registers the binder under the name in the instance's service manager. */
    public static boolean publish(String name, IBinder binder) { return publish(name, binder, false); }

    /** allowIsolated: also found by isolated processes (android:isolatedProcess, uids 99000 and up), whom the service manager otherwise hides it from: the hub. */
    public static boolean publish(String name, IBinder binder, boolean allowIsolated) {
        java.util.Objects.requireNonNull(name); java.util.Objects.requireNonNull(binder);
        init(); return publishNative(name, binder, allowIsolated);
    }

    /** The binder registered under the name, or null: a BinderProxy when it lives in another JVM. */
    public static IBinder lookup(String name) {
        java.util.Objects.requireNonNull(name); init(); return lookupNative(name);
    }

    /** Wait for actual service publication or the monotonic deadline, without polling. */
    public static IBinder await(String name, long timeoutMs) {
        java.util.Objects.requireNonNull(name); init(); return wait0(name, timeoutMs);
    }
    private static native IBinder wait0(String name, long timeoutMs);

    /**
     * This process's app uid as the sender of its oneway transactions (userspace binder: APKLAYER_BINDER_SET_SENDER_UID, bridge/binderd/protocol.h):
     * a oneway call has no sender pid and every unsandboxed process runs as the desktop user, so a receiver that asks getCallingUid without an
     * interface-token header (grpc-binder) learns the app from it. 0 or -errno; the kernel's binder and older shims leave it unset.
     */
    static int setSenderUid(int uid) {
        try { return setSenderUid0(uid); }
        catch (UnsatisfiedLinkError older) { return -38; }  // ENOSYS: a runtime without it
    }
    private static native int setSenderUid0(int uid);

    // The shim registers these uninstrumented native names; the public methods above initialize the pool.
    private static native boolean publish0(String name, IBinder binder, boolean allowIsolated);
    private static native IBinder lookup0(String name);
    private static native void startPool();
    private static boolean publishNative(String name, IBinder binder, boolean allowIsolated) { return publish0(name, binder, allowIsolated); }
    private static IBinder lookupNative(String name) { return lookup0(name); }
}
