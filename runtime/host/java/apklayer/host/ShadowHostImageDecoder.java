package apklayer.host;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import java.io.IOException;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.GraphicsShadowPicker;
import org.robolectric.shadows.ShadowImageDecoder;
import org.robolectric.shadows.ShadowNativeImageDecoder;

/** Software allocator in place of the default/hardware one, as {@link ShadowHostBitmapFactory} does for BitmapFactory. */
@Implements(value = ImageDecoder.class, shadowPicker = ShadowHostImageDecoder.Picker.class, callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostImageDecoder extends ShadowNativeImageDecoder {
    public static class Picker extends GraphicsShadowPicker<Object> {
        public Picker() {
            super(ShadowImageDecoder.class, ShadowHostImageDecoder.class);
        }
    }

    static int allocator(int allocator) {  // ALLOCATOR_DEFAULT 0, SOFTWARE 1, SHARED_MEMORY 2, HARDWARE 3
        return (allocator == 0 || allocator == 3) && !Boolean.getBoolean("apklayer.hwbitmaps") ? 1 : allocator;
    }

    // Robolectric's asset shadows are open-ended from SDK 30 (the asset's native pointer is not the JNI's); delegated unchanged
    @Implementation(minSdk = 30) protected static ImageDecoder createFromAsset(android.content.res.AssetManager.AssetInputStream ais, boolean preferAnimation, ImageDecoder.Source src) throws IOException {
        return ShadowNativeImageDecoder.createFromAsset(ais, preferAnimation, src);
    }

    @Implementation(minSdk = 30) protected static ImageDecoder nCreate(long asset, boolean preferAnimation, ImageDecoder.Source src) throws IOException {
        return ShadowNativeImageDecoder.nCreate(asset, preferAnimation, src);
    }

    @Implementation(minSdk = 35) protected static Bitmap nDecodeBitmap(long nativePtr, ImageDecoder decoder, boolean doPostProcess, int width, int height, Rect cropRect,
            boolean mutable, int allocator, boolean unpremulRequired, boolean conserveMemory, boolean decodeAsAlphaMask, long desiredColorSpace, boolean extended,
            long allocationLimit) throws IOException {
        return NativeBinding.call(ImageDecoder.class, "nDecodeBitmap",
                new Class<?>[] {long.class, ImageDecoder.class, boolean.class, int.class, int.class, Rect.class, boolean.class, int.class, boolean.class, boolean.class,
                        boolean.class, long.class, boolean.class, long.class},
                nativePtr, decoder, doPostProcess, width, height, cropRect, mutable, allocator(allocator), unpremulRequired, conserveMemory, decodeAsAlphaMask,
                desiredColorSpace, extended, allocationLimit);
    }
}
