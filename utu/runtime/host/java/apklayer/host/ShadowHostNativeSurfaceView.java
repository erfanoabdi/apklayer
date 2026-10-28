package apklayer.host;

import android.view.SurfaceView;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowView;

/** apklayer patch: framework SurfaceView owns its real holder, BLAST queue and child layers. */
@Implements(value = SurfaceView.class, callNativeMethodsByDefault = true)
public class ShadowHostNativeSurfaceView extends ShadowView {}
