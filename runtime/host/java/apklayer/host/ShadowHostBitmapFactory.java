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
@Implements(value = BitmapFactory.class, shadowPicker = ShadowHostBitmapFactory.Picker.class, isInAndroidSdk = false)
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

    @Implementation protected static Bitmap nativeDecodeStream(InputStream is, byte[] storage, Rect padding, BitmapFactory.Options opts, long inBitmapHandle, long colorSpaceHandle) {
        return ShadowNativeBitmapFactory.nativeDecodeStream(is, storage, padding, soft(opts), inBitmapHandle, colorSpaceHandle);
    }

    @Implementation protected static Bitmap nativeDecodeFileDescriptor(FileDescriptor fd, Rect padding, BitmapFactory.Options opts, long inBitmapHandle, long colorSpaceHandle) {
        return ShadowNativeBitmapFactory.nativeDecodeFileDescriptor(fd, padding, soft(opts), inBitmapHandle, colorSpaceHandle);
    }

    @Implementation protected static Bitmap nativeDecodeAsset(long asset, Rect padding, BitmapFactory.Options opts, long inBitmapHandle, long colorSpaceHandle) {
        return ShadowNativeBitmapFactory.nativeDecodeAsset(asset, padding, soft(opts), inBitmapHandle, colorSpaceHandle);
    }

    @Implementation protected static Bitmap nativeDecodeByteArray(byte[] data, int offset, int length, BitmapFactory.Options opts, long inBitmapHandle, long colorSpaceHandle) {
        return ShadowNativeBitmapFactory.nativeDecodeByteArray(data, offset, length, soft(opts), inBitmapHandle, colorSpaceHandle);
    }
}
