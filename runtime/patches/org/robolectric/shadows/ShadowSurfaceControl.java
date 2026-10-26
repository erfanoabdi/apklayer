package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.N_MR1;
import static android.os.Build.VERSION_CODES.O;
import static android.os.Build.VERSION_CODES.P;
import static android.os.Build.VERSION_CODES.Q;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

import android.os.Parcel;
import org.robolectric.util.reflector.Static;
import static org.robolectric.util.reflector.Reflector.reflector;
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
@Implements(value = SurfaceControl.class, isInAndroidSdk = false, callNativeMethodsByDefault = true) // apklayer patch: the real natives (libgui) run
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

  // The JNI function itself: Robolectric renames the native method to $$robo$$nativeCreate$nativeBinding (the shim registers
  // libandroid_runtime's functions under that name). A @Direct reflector call would reach the generated stub that returns 0.
  private static long nativeCreateBinding(
      SurfaceSession session, String name, int w, int h, int format, int flags, long parentObject) {
    try {
      java.lang.reflect.Method m =
          SurfaceControl.class.getDeclaredMethod(
              "$$robo$$nativeCreate$nativeBinding",
              SurfaceSession.class, String.class, int.class, int.class, int.class, int.class, long.class, Parcel.class);
      m.setAccessible(true);
      return (long) m.invoke(null, session, name, w, h, format, flags, parentObject, null);
    } catch (java.lang.reflect.InvocationTargetException e) {
      throw new RuntimeException(e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new LinkageError("SurfaceControl native binding", e);
    }
  }

  // apklayer patch: no fake nativeCreate; SurfaceControls are real layers of the instance's SurfaceFlinger. The metadata Parcel is
  // Robolectric's Java Parcel (no native object behind it), so the real native create gets none (it only carries the window type).
  @Implementation(minSdk = Q)
  protected static long nativeCreate(
      SurfaceSession session, String name, int w, int h, int format, int flags, long parentObject, Parcel metadata) {
    // The window manager would parent an app window's layer under its task and display; there is none here, so the layer joins the
    // root of the layer stack instead (NOT_ADD_TO_ROOT would leave it in no stack at all)
    flags &= ~SurfaceControl.NOT_ADD_TO_ROOT;
    long handle = 0;
    for (int attempt = 0; attempt < 3 && handle == 0; attempt++) {
      if (attempt > 0) {
        System.err.println("[apklayer] SurfaceControl.nativeCreate returned 0, retrying (" + attempt + ")");
        try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      }
      handle = nativeCreateBinding(session, name, w, h, format, flags, parentObject);
    }
    System.err.println("[apklayer] SurfaceControl.nativeCreate(" + name + ", " + w + "x" + h + ", format " + format + ", flags 0x" + Integer.toHexString(flags) + ", session " + (session != null) + ", parent " + parentObject + ") = " + handle);
    return handle;
  }

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

    @Static
    @Direct
    long nativeCreate(
        SurfaceSession session, String name, int w, int h, int format, int flags, long parentObject, Parcel metadata);
  }
}
