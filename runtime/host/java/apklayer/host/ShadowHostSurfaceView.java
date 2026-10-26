package apklayer.host;

import android.view.SurfaceHolder;
import android.view.SurfaceView;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowSurfaceView;

/** A SurfaceView whose holder always has a Surface (Robolectric's returns null; apps call getSurface().isValid()). */
@Implements(SurfaceView.class)
public class ShadowHostSurfaceView extends ShadowSurfaceView {
    @RealObject private SurfaceView real;
    private HostSurfaces.HostHolder holder;

    HostSurfaces.HostHolder hostHolder() {
        if (holder == null) holder = new HostSurfaces.HostHolder(new HostSurfaces.HostSurface(real));
        return holder;
    }

    @Implementation
    @Override
    protected SurfaceHolder getHolder() {
        return hostHolder();
    }
}
