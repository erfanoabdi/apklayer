package apklayer.host;

import android.app.ActivityManager;
import android.app.IActivityManager;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowActivityManager;

/** Robolectric's activity manager, with the calls that cross processes (receivers, broadcasts) going to the instance hub once the app is connected to it. */
@Implements(value = ActivityManager.class, isInAndroidSdk = false)
public class ShadowHostActivityManager extends ShadowActivityManager {
    @Implementation protected static IActivityManager getService() {
        return HubActivityManager.service(ShadowActivityManager.getService());
    }
}
