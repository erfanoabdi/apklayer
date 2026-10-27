package apklayer.host;

import android.content.AttributionSource;
import android.os.Parcel;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.ReflectionHelpers;

/** A scoped native payload for JNI consumers; restores Java Parcel ownership on close. */
@Implements(value = AttributionSource.ScopedParcelState.class)
public class ShadowHostNativeAttributionParcel {
    @RealObject private AttributionSource.ScopedParcelState real;
    private Parcel parcel;
    private long original, nativePeer;
    @Implementation protected void __constructor__(AttributionSource source) {
        NativeMedia.ensureInitialized();
        parcel = Parcel.obtain();
        original = ReflectionHelpers.getField(parcel, "mNativePtr");
        try {
            nativePeer = NativeAttribution.create(source);
            if (nativePeer == 0) throw new IllegalStateException("Cannot create native attribution Parcel");
            ReflectionHelpers.setField(parcel, "mNativePtr", nativePeer);
            ReflectionHelpers.setField(real, "mParcel", parcel);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }
    @Implementation protected void close() {
        if (parcel == null) return;
        ReflectionHelpers.setField(parcel, "mNativePtr", original);
        if (nativePeer != 0) NativeAttribution.destroy(nativePeer);
        parcel.recycle();
        parcel = null; nativePeer = 0;
    }
}
