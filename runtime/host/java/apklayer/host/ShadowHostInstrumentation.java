package apklayer.host;

import android.app.Application;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityResult;
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
        // as Android's bindApplication: the system's and other apps' providers (Settings among them) answer from Application.onCreate on
        if (System.getProperty("apklayer.package") != null && !AppEnvironment.installing) HubClient.attach(app);
        if (!providersInstalled && System.getProperty("apklayer.package") != null && !Boolean.getBoolean("apklayer.componenthost")) {  // not in the service hosts (the WebView package's own providers abort the renderer host)
            providersInstalled = true;
            HostBuild.install();  // before the app's own code: it reads Build in Application.onCreate
            AppEnvironment.androidId(app);
            AppEnvironment.installProviders(app);
        }
        Shadow.directlyOn(real, "android.app.Instrumentation", "callApplicationOnCreate", org.robolectric.util.ReflectionHelpers.ClassParameter.from(Application.class, app));
    }

    /** -Dapklayer.tracestart=true: who started an activity (an app that relaunches itself, a start nobody expected). */
    @Implementation
    @Override
    protected ActivityResult execStartActivity(android.content.Context who, android.os.IBinder contextThread, android.os.IBinder token, android.app.Activity target,
            android.content.Intent intent, int requestCode, android.os.Bundle options) {
        if (Boolean.getBoolean("apklayer.tracestart")) new Throwable("[apklayer] startActivity " + intent).printStackTrace(System.out);
        return super.execStartActivity(who, contextThread, token, target, intent, requestCode, options);
    }
}
