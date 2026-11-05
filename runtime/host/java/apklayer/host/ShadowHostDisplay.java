package apklayer.host;

import android.view.Display;
import java.util.Collections;
import java.util.List;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowDisplay;
import org.robolectric.util.reflector.Direct;
import org.robolectric.util.reflector.ForType;
import org.robolectric.util.reflector.Reflector;

/** Robolectric's DisplayInfo has no frame-rate velocity mapping (null); scrolling a list asked for it and killed the app. An empty mapping means no velocity-based vote. */
@Implements(value = Display.class, isInAndroidSdk = false)
public class ShadowHostDisplay extends ShadowDisplay {
    @RealObject private Display display;

    @Implementation
    protected List getFrameRateVelocityMapping() {
        return Collections.emptyList();
    }

    /** A display without HDR has an HdrCapabilities object with no types, not null (apps call methods on it). */
    @Implementation
    protected Display.HdrCapabilities getHdrCapabilities() {
        return new Display.HdrCapabilities(new int[0], 0f, 0f, 0f);
    }

    /** Robolectric's DisplayInfo leaves the presentation deadline 0; a device reports about a refresh period (SurfaceFlinger: period - sf
     * offset + 1 ms; our SurfaceFlinger keeps the default 1 ms sf offset). Swappy (Android Frame Pacing) derives SurfaceFlinger's offset as
     * period - (deadline - 1 ms): with 0 that was more than a period, its vsync filter woke the game only every ~70 ms and OTR2 ran at
     * 12-15 fps where it renders 58 fps without Swappy (x86_64 PC, 2026-11-05). */
    @Implementation
    protected long getPresentationDeadlineNanos() {
        long deadline = Reflector.reflector(DisplayDirect.class, display).getPresentationDeadlineNanos();
        return deadline > 0 ? deadline : (long) (1e9 / getRefreshRate());
    }

    @ForType(Display.class)
    interface DisplayDirect {
        @Direct
        long getPresentationDeadlineNanos();
    }
}
