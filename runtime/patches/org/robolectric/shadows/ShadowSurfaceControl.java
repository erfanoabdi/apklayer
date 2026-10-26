package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.N_MR1;
import static android.os.Build.VERSION_CODES.O;
import static android.os.Build.VERSION_CODES.P;
import static android.os.Build.VERSION_CODES.Q;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import android.os.Parcel;
import android.view.SurfaceControl;
import android.view.SurfaceSession;
import dalvik.system.CloseGuard;
import java.util.concurrent.atomic.AtomicInteger;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.ReflectorObject;
import org.robolectric.annotation.Resetter;
import org.robolectric.util.reflector.Accessor;
import org.robolectric.util.reflector.Direct;
import org.robolectric.util.reflector.ForType;

/** Shadow for {@link android.view.SurfaceControl} */
@Implements(value = SurfaceControl.class, isInAndroidSdk = false)
public class ShadowSurfaceControl {
  private static final AtomicInteger nativeObject = new AtomicInteger();

  @ReflectorObject private SurfaceControlReflector surfaceControlReflector;
  @org.robolectric.annotation.RealObject private SurfaceControl realObject;

  @Resetter
  public static void reset() {
    nativeObject.set(0);
  }

  @Implementation
  protected void finalize() throws Throwable {
    // Suppress noisy CloseGuard errors.
    CloseGuard closeGuard = surfaceControlReflector.getCloseGuard();
    if (closeGuard != null) {
      closeGuard.close();
    }
    surfaceControlReflector.finalize();
  }

  // apklayer patch: no fake nativeCreate; SurfaceControls are real layers of the instance's SurfaceFlinger

  // apklayer patch: relayout's out SurfaceControl becomes a real BLAST layer of SurfaceFlinger (the window's buffer queue layer)
  void initializeNativeObject() {
    System.err.println("[apklayer] relayout: creating a real SurfaceControl");
    SurfaceControl real =
        new SurfaceControl.Builder().setName("apklayer-window").setBLASTLayer().setFormat(android.graphics.PixelFormat.RGBA_8888).build();
    System.err.println("[apklayer] SurfaceControl built: valid=" + real.isValid());
    ((SurfaceControl) realObject).copyFrom(real, "apklayer relayout");
    real.release();
  }

  @ForType(SurfaceControl.class)
  interface SurfaceControlReflector {
    @Accessor("mCloseGuard")
    CloseGuard getCloseGuard();

    @Accessor("mNativeObject")
    void setNativeObject(long nativeObject);

    @Direct
    void finalize();

    @Accessor("mFreeNativeResources")
    void setFreeNativeResources(Runnable runnable);
  }
}
