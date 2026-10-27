package apklayer.host;

import android.hardware.HardwareBuffer;
import org.robolectric.annotation.Implements;

/** The real HardwareBuffer natives (our gralloc), not Robolectric's registry of fake ones: Bitmap.getHardwareBuffer hands out native buffers. */
@Implements(value = HardwareBuffer.class, callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostNativeHardwareBuffer {
}
