package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowSystemServiceRegistry;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * A system service the device lacks (selection_toolbar, which FloatingToolbar asks for on every text selection): Android's own
 * onServiceNotFound (one warning line for an app uid) instead of Robolectric's printed stack trace.
 */
@Implements(className = "android.app.SystemServiceRegistry", isInAndroidSdk = false)
public class ShadowHostSystemServiceRegistry extends ShadowSystemServiceRegistry {
    @Implementation
    protected static void onServiceNotFound(android.os.ServiceManager.ServiceNotFoundException e) {
        Shadow.directlyOn(android.app.SystemServiceRegistry.class, "onServiceNotFound",
                ClassParameter.from(android.os.ServiceManager.ServiceNotFoundException.class, e));
    }
}
