package apklayer.host;

import android.graphics.PixelFormat;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.robolectric.shadows.ShadowSurfaceView;

/**
 * Android's window manager tells a SurfaceView's holder callbacks when the surface exists. Robolectric's
 * fake holder never does, so a GLSurfaceView's render thread waits forever (CPU-Z's OGLActivity blocked
 * on it). Once a SurfaceView has a size, deliver surfaceCreated + surfaceChanged to its callbacks.
 */
final class HostSurfaces {
    private HostSurfaces() {}

    private static final Map<SurfaceView, Boolean> notified = new WeakHashMap<>();

    static void follow() {
        List<View> windows = org.robolectric.util.ReflectionHelpers.getField(android.view.WindowManagerGlobal.getInstance(), "mViews");
        for (View w : windows) scan(w);
    }

    private static void scan(View v) {
        if (v instanceof SurfaceView sv) {
            if (sv.getWidth() > 0 && sv.getHeight() > 0 && notified.put(sv, true) == null) {
                ShadowSurfaceView.FakeSurfaceHolder holder = ((ShadowSurfaceView) org.robolectric.shadow.api.Shadow.extract(sv)).getFakeSurfaceHolder();
                holder.getCallbacks().forEach(c -> c.surfaceCreated(holder));
                holder.emitSurfaceChanged(PixelFormat.RGBA_8888, sv.getWidth(), sv.getHeight());
            }
        } else if (v instanceof ViewGroup g) {
            for (int i = 0; i < g.getChildCount(); i++) scan(g.getChildAt(i));
        }
    }
}
