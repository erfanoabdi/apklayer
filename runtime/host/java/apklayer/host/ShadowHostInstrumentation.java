package apklayer.host;

import android.app.Application;
import android.app.Instrumentation;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowInstrumentation;

/** The manifest's content providers are created before Application.onCreate, as on Android (androidx.startup initialises WorkManager, ProcessLifecycleOwner and others there). */
@Implements(value = Instrumentation.class, isInAndroidSdk = false)
public class ShadowHostInstrumentation extends ShadowInstrumentation {
    @RealObject private Instrumentation real;
    private static boolean providersInstalled;

    @Implementation
    protected void callApplicationOnCreate(Application app) {
        if (!providersInstalled && System.getProperty("apklayer.package") != null) {
            providersInstalled = true;
            AppEnvironment.androidId(app);
            AppEnvironment.installProviders(app);
        }
        Shadow.directlyOn(real, "android.app.Instrumentation", "callApplicationOnCreate", org.robolectric.util.ReflectionHelpers.ClassParameter.from(Application.class, app));
    }
}
