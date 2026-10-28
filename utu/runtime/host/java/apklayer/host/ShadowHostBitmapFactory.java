package apklayer.host;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import java.io.FileDescriptor;
import java.io.InputStream;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.GraphicsShadowPicker;
import org.robolectric.shadows.ShadowBitmapFactory;
import org.robolectric.shadows.ShadowNativeBitmapFactory;

/**
 * Decoded images stay in software memory ({@code -Dapklayer.hwbitmaps=true} allows HARDWARE bitmaps again). A hardware Bitmap is a gralloc
 * buffer that Skia imports as an EGLImage on every draw and reads back on every copy; NewPipe's seekbar preview crashed HWUI in both.
 * HWUI uploads a software bitmap once, the path every other app takes.
 */
@Implements(value = BitmapFactory.class, shadowPicker = ShadowHostBitmapFactory.Picker.class, callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostBitmapFactory extends ShadowNativeBitmapFactory {
    public static class Picker extends GraphicsShadowPicker<Object> {
        public Picker() {
            super(ShadowBitmapFactory.class, ShadowHostBitmapFactory.class);
        }
    }

    static BitmapFactory.Options soft(BitmapFactory.Options opts) {
        if (opts != null && opts.inPreferredConfig == Bitmap.Config.HARDWARE && !Boolean.getBoolean("apklayer.hwbitmaps"))
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return opts;
    }

    // Robolectric's decodeResource/decodeStream shadows apply at every SDK, its native ones only up to 34: the real JNI runs beyond
    @Implementation protected static Bitmap decodeResource(android.content.res.Resources res, int id, BitmapFactory.Options opts) {
        return ShadowNativeBitmapFactory.decodeResource(res, id, soft(opts));
    }

    @Implementation protected static Bitmap decodeStream(InputStream is, Rect outPadding, BitmapFactory.Options opts) {
        return ShadowNativeBitmapFactory.decodeStream(is, outPadding, soft(opts));
    }

    @Implementation(minSdk = 35) protected static Bitmap nativeDecodeStream(InputStream is, byte[] storage, Rect padding, BitmapFactory.Options opts, long inBitmap, long colorSpace) {
        return NativeBinding.call(BitmapFactory.class, "nativeDecodeStream", new Class<?>[] {InputStream.class, byte[].class, Rect.class, BitmapFactory.Options.class, long.class, long.class},
                is, storage, padding, soft(opts), inBitmap, colorSpace);
    }

    @Implementation(minSdk = 35) protected static Bitmap nativeDecodeFileDescriptor(FileDescriptor fd, Rect padding, BitmapFactory.Options opts, long inBitmap, long colorSpace) {
        return NativeBinding.call(BitmapFactory.class, "nativeDecodeFileDescriptor", new Class<?>[] {FileDescriptor.class, Rect.class, BitmapFactory.Options.class, long.class, long.class},
                fd, padding, soft(opts), inBitmap, colorSpace);
    }

    @Implementation(minSdk = 35) protected static Bitmap nativeDecodeAsset(long asset, Rect padding, BitmapFactory.Options opts, long inBitmap, long colorSpace) {
        return NativeBinding.call(BitmapFactory.class, "nativeDecodeAsset", new Class<?>[] {long.class, Rect.class, BitmapFactory.Options.class, long.class, long.class},
                asset, padding, soft(opts), inBitmap, colorSpace);
    }

    @Implementation(minSdk = 35) protected static Bitmap nativeDecodeByteArray(byte[] data, int offset, int length, BitmapFactory.Options opts, long inBitmap, long colorSpace) {
        return NativeBinding.call(BitmapFactory.class, "nativeDecodeByteArray", new Class<?>[] {byte[].class, int.class, int.class, BitmapFactory.Options.class, long.class, long.class},
                data, offset, length, soft(opts), inBitmap, colorSpace);
    }
}
