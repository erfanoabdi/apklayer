package apklayer.host;

import android.os.Parcel;
import org.robolectric.annotation.internal.DoNotInstrument;
import org.robolectric.util.ReflectionHelpers;

/** Explicit native payloads for the native media shadows: every Parcel is the framework's own native Parcel (ShadowHostRealParcel). */
@DoNotInstrument
public final class NativeParcel {
    private static final ThreadLocal<Integer> allocationDepth = ThreadLocal.withInitial(() -> 0);
    private static volatile boolean callbackFactoryReady;
    private NativeParcel() {}
    public static void markCallbackFactoryReady() { callbackFactoryReady = true; }
    static void requireCallbackFactory() {
        if (!callbackFactoryReady) {  // the native runtime (and with it the adapter) is loaded on first use; an app may build a MediaPlayer before any other native media call
            NativeMedia.ensureInitialized();
            callbackFactoryReady = true;
        }
    }
    static Scope scope() { allocationDepth.set(allocationDepth.get() + 1); return new Scope(); }
    static boolean allocating() { return allocationDepth.get() != 0; }
    static final class Scope implements AutoCloseable {
        @Override public void close() { allocationDepth.set(allocationDepth.get() - 1); }
    }
    static void requireNative(Parcel parcel) {
        if (pointer(parcel) == 0) throw new IllegalArgumentException("Native media requires a live native Parcel");
    }
    public static Parcel obtain() {
        NativeMedia.ensureInitialized();
        return Parcel.obtain();
    }
    static void promote(Parcel parcel) { requireNative(parcel); }
    static long pointer(Parcel parcel) { return ReflectionHelpers.getField(parcel, "mNativePtr"); }
}
