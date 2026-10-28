package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.TIRAMISU;
import static com.google.common.base.Preconditions.checkState;

import android.graphics.BLASTBufferQueue;
import android.graphics.PixelFormat;
import android.media.ImageReader;
import android.view.Surface;
import android.view.SurfaceControl;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;

@Implements(value = BLASTBufferQueue.class, isInAndroidSdk = false, minSdk = TIRAMISU, callNativeMethodsByDefault = true) // apklayer patch: the real natives (libgui) run
public class ShadowBlastBufferQueue {

  // apklayer patch: no fake BLASTBufferQueue; the real one (libgui) feeds the window's layer in SurfaceFlinger

  // The apply token is a Java android.os.Binder; reading its native peer needs the real android.os.Binder JNI, which this runtime
  // does not register (Binder stays Robolectric's in-process one). The token only orders transactions of one client: skipped.
  @Implementation
  protected static void nativeSetApplyToken(long ptr, android.os.IBinder applyToken) {}
}
