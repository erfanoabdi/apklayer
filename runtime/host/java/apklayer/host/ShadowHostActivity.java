package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowActivity;

/** The first activity of the app's task is the task root (Robolectric's Activity.isTaskRoot is false, so launcher trampolines finish without starting the real screen). */
@Implements(value = android.app.Activity.class, isInAndroidSdk = false)
public class ShadowHostActivity extends ShadowActivity {
    @RealObject private android.app.Activity real;
    @Implementation protected boolean isTaskRoot() { return !Boolean.getBoolean("apklayer.notaskroot") && AppHost.isTaskRoot(real); }
}
