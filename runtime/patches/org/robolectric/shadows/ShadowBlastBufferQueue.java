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

@Implements(value = BLASTBufferQueue.class, isInAndroidSdk = false, minSdk = TIRAMISU)
public class ShadowBlastBufferQueue {

  // apklayer patch: no fake BLASTBufferQueue; the real one (libgui) feeds the window's layer in SurfaceFlinger
}
