package apklayer.host;

import android.view.Display;
import java.util.Collections;
import java.util.List;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowDisplay;

/** Robolectric's DisplayInfo has no frame-rate velocity mapping (null); scrolling a list asked for it and killed the app. An empty mapping means no velocity-based vote. */
@Implements(value = Display.class, isInAndroidSdk = false)
public class ShadowHostDisplay extends ShadowDisplay {
    @Implementation
    protected List getFrameRateVelocityMapping() {
        return Collections.emptyList();
    }
}
