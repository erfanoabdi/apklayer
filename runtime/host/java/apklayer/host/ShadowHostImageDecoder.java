package apklayer.host;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import java.io.IOException;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.GraphicsShadowPicker;
import org.robolectric.shadows.ShadowImageDecoder;
import org.robolectric.shadows.ShadowNativeImageDecoder;

/** Software allocator in place of the default/hardware one, as {@link ShadowHostBitmapFactory} does for BitmapFactory. */
@Implements(value = ImageDecoder.class, shadowPicker = ShadowHostImageDecoder.Picker.class, isInAndroidSdk = false)
public class ShadowHostImageDecoder extends ShadowNativeImageDecoder {
    public static class Picker extends GraphicsShadowPicker<Object> {
        public Picker() {
            super(ShadowImageDecoder.class, ShadowHostImageDecoder.class);
        }
    }

    static int allocator(int allocator) {  // ALLOCATOR_DEFAULT 0, SOFTWARE 1, SHARED_MEMORY 2, HARDWARE 3
        return (allocator == 0 || allocator == 3) && !Boolean.getBoolean("apklayer.hwbitmaps") ? 1 : allocator;
    }

    @Implementation protected static Bitmap nDecodeBitmap(long nativePtr, ImageDecoder decoder, boolean doPostProcess, int width, int height, Rect cropRect,
            boolean mutable, int allocator, boolean unpremulRequired, boolean conserveMemory, boolean decodeAsAlphaMask, ColorSpace desiredColorSpace) throws IOException {
        return ShadowNativeImageDecoder.nDecodeBitmap(nativePtr, decoder, doPostProcess, width, height, cropRect, mutable, allocator(allocator),
                unpremulRequired, conserveMemory, decodeAsAlphaMask, desiredColorSpace);
    }

    @Implementation protected static Bitmap nDecodeBitmap(long nativePtr, ImageDecoder decoder, boolean doPostProcess, int width, int height, Rect cropRect,
            boolean mutable, int allocator, boolean unpremulRequired, boolean conserveMemory, boolean decodeAsAlphaMask, long desiredColorSpace, boolean extended) throws IOException {
        return ShadowNativeImageDecoder.nDecodeBitmap(nativePtr, decoder, doPostProcess, width, height, cropRect, mutable, allocator(allocator),
                unpremulRequired, conserveMemory, decodeAsAlphaMask, desiredColorSpace, extended);
    }
}
