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
        // the hub's own package (HubHost) attaches and creates its providers once the hub is published
        if (HubHost.booting) { Shadow.directlyOn(real, "android.app.Instrumentation", "callApplicationOnCreate", org.robolectric.util.ReflectionHelpers.ClassParameter.from(Application.class, app)); return; }
        if (System.getProperty("apklayer.package") != null && !AppEnvironment.installing) HubClient.attach(app);
        // before the app's own code in every process, component hosts included (Build: AppEnvironment.setUpApplication): the storage volumes (MediaProvider's
        // onCreate attaches the primary volume it finds)
        if (System.getProperty("apklayer.package") != null) HostStorage.install(app);
        // in every process of the app, component hosts included: one ANDROID_ID per app (its SSAID), the providers of this process (a service process of its
        // own, such as the WebView's renderer, gets none of the main process's)
        if (!providersInstalled && System.getProperty("apklayer.package") != null) {
            providersInstalled = true;
            HostSystemConfig.cached().apply(app);  // the build's grants hold from the start (SystemUI's onCreate checks READ_FRAME_BUFFER)
            AppEnvironment.androidId(app);
            AppEnvironment.installProviders(app);
        }
        Shadow.directlyOn(real, "android.app.Instrumentation", "callApplicationOnCreate", org.robolectric.util.ReflectionHelpers.ClassParameter.from(Application.class, app));
    }

    /**
     * Instrumentation.checkStartActivityResult: an implicit intent that no installed app (and none of the host's answers) handles throws
     * ActivityNotFoundException in the caller, as on Android. -Dapklayer.tracestart=true: who started an activity (an app that relaunches itself, a start nobody expected).
     */
    @Implementation
    @Override
    protected ActivityResult execStartActivity(android.content.Context who, android.os.IBinder contextThread, android.os.IBinder token, android.app.Activity target,
            android.content.Intent intent, int requestCode, android.os.Bundle options) {
        System.setProperty("apklayer.laststart", Long.toString(System.nanoTime() / 1_000_000));  // ApklayerExit: an exit right after is a restart
        if (Boolean.getBoolean("apklayer.tracestart")) new Throwable("[apklayer] startActivity " + intent).printStackTrace(System.out);
        if (intent.getComponent() == null && HubActivityClient.enabled() && !HostPortals.hostAnswers(intent)
                && who.getPackageManager().resolveActivity(intent, 0) == null && !HubActivityClient.resolvable(intent))
            throw new android.content.ActivityNotFoundException("No Activity found to handle " + intent);
        return super.execStartActivity(who, contextThread, token, target, intent, requestCode, options);
    }
}
