package apklayer.host;

import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowView;

/** TextureView's own code on the GPU route: its TextureLayer comes from the app's real ThreadedRenderer (HWUI), not Robolectric's "not supported" stand-in. */
@Implements(className = "android.view.TextureView", callNativeMethodsByDefault = true, isInAndroidSdk = false)
public class ShadowHostRealTextureView extends ShadowView {
}
