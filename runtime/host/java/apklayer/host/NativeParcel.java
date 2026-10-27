package apklayer.host;

import android.os.Parcel;
import java.util.concurrent.ConcurrentHashMap;
import org.robolectric.annotation.internal.DoNotInstrument;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/** Explicit native payloads; ordinary framework/shadow service Parcels retain Java peers. */
@DoNotInstrument
public final class NativeParcel {
    private static final ConcurrentHashMap<Long, Long> originals = new ConcurrentHashMap<>();
    private NativeParcel() {}
    public static Parcel obtain() {
        NativeMedia.ensureInitialized();
        Parcel parcel = Parcel.obtain();
        try { promote(parcel); return parcel; }
        catch (RuntimeException | Error failure) { parcel.recycle(); throw failure; }
    }
    static void promote(Parcel parcel) {
        long original = pointer(parcel);
        if (owns(original)) return;
        if (!(Shadow.extract(parcel) instanceof ShadowHostNativeParcel))
            throw new IllegalStateException("Selective native Parcel shadow is not enabled");
        if (parcel.dataSize() != 0) throw new IllegalArgumentException("Native Parcel requires an empty payload");
        long nativePeer = call("nativeCreate");
        if (nativePeer == 0) throw new OutOfMemoryError("Native Parcel allocation");
        originals.put(nativePeer, original);
        ReflectionHelpers.setField(parcel, "mNativePtr", nativePeer);
    }
    static long pointer(Parcel parcel) { return ReflectionHelpers.getField(parcel, "mNativePtr"); }
    static boolean owns(long peer) { return originals.containsKey(peer); }
    public static int activeParcels() { return originals.size(); }
    static void restore(Parcel parcel) {
        long peer = pointer(parcel);
        Long original = originals.remove(peer);
        if (original == null) return;
        ReflectionHelpers.setField(parcel, "mNativePtr", original);
        call("nativeDestroy", ClassParameter.from(long.class, peer));
    }
    static Long remove(long peer) { return originals.remove(peer); }
    static <T> T call(String method, ClassParameter<?>... parameters) {
        return ReflectionHelpers.callStaticMethod(Parcel.class,
                Shadow.directNativeMethodName(Parcel.class.getName(), method), parameters);
    }
}
