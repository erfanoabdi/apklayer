package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowActivity;

/** The first activity of the app's task is the task root (Robolectric's Activity.isTaskRoot is false, so launcher trampolines finish without starting the real screen). */
@Implements(value = android.app.Activity.class, isInAndroidSdk = false)
public class ShadowHostActivity extends ShadowActivity {
    @RealObject private android.app.Activity real;
    /** startActivityIfNeeded starts the activity (AnkiDroid's launcher trampoline): Robolectric's version records nothing. */
    @Implementation protected boolean startActivityIfNeeded(android.content.Intent intent, int requestCode) {
        if (requestCode >= 0) real.startActivityForResult(intent, requestCode); else real.startActivity(intent);
        return true;
    }
    @Implementation protected boolean startActivityIfNeeded(android.content.Intent intent, int requestCode, android.os.Bundle options) {
        if (requestCode >= 0) real.startActivityForResult(intent, requestCode, options); else real.startActivity(intent, options);
        return true;
    }
    @Implementation protected boolean isTaskRoot() { return !Boolean.getBoolean("apklayer.notaskroot") && AppHost.isTaskRoot(real); }
}
