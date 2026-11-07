package apklayer.host;

import android.graphics.Bitmap;
import android.graphics.HardwareRenderer;
import android.graphics.RenderNode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowNativeHardwareRenderer;

/** Robolectric's native HardwareRenderer shadow, except for createHardwareBitmap (Bitmap.createBitmap(Picture), Compose's
 *  GraphicsLayer.toImageBitmap): HWUI's own, which renders the node into an AImageReader over the instance's allocator as on a device.
 *  Robolectric's Java stand-in drew into a Java ImageReader that never got the frame (acquireNextImage returned null). */
@Implements(value = HardwareRenderer.class, callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostHardwareRenderer extends ShadowNativeHardwareRenderer {
    @Implementation
    protected static Bitmap createHardwareBitmap(RenderNode node, int width, int height) {
        return NativeBinding.call(HardwareRenderer.class, "nCreateHardwareBitmap", new Class<?>[] {long.class, int.class, int.class},
                node.mNativeRenderNode, width, height);
    }
}
