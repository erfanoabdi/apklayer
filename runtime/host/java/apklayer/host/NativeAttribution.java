package apklayer.host;

import android.content.AttributionSource;
import android.os.Parcel;
import org.robolectric.annotation.internal.DoNotInstrument;
import org.robolectric.util.ReflectionHelpers;

/** Actual AIDL attribution payloads; the rest of Parcel/Binder remains in Java. */
@DoNotInstrument
public final class NativeAttribution {
    private NativeAttribution() {}
    static long create(AttributionSource source) { return nativeCreate(source.asState()); }
    static void destroy(long peer) { nativeDestroy(peer); }
    public static boolean matches(AttributionSource source, Parcel parcel) {
        return nativeMatches(ReflectionHelpers.<Long>getField(parcel, "mNativePtr"), source.asState());
    }
    public static int activeParcels() { return nativeActive(); }
    private static native long nativeCreate(Object state);
    private static native void nativeDestroy(long peer);
    private static native boolean nativeMatches(long peer, Object state);
    private static native int nativeActive();
}
