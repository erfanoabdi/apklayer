package apklayer.host;

import android.app.Application;
import android.os.Looper;
import java.nio.file.Path;
import java.time.Duration;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.internal.AndroidSandbox;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPausedLooper;
import org.robolectric.shadows.ShadowSystemClock;

/** Headless application process: service/provider lifecycle uses the normal sandbox main looper. */
public final class ComponentHost implements Runnable {
    private final AndroidSandbox sandbox;
    private final Path apk;
    public ComponentHost(AndroidSandbox sandbox, Path apk, Path ignored) { this.sandbox = sandbox; this.apk = apk; }
    @Override public void run() {
        Thread.currentThread().setContextClassLoader(sandbox.getRobolectricClassLoader());
        AppEnvironment.setUpApplication(sandbox, apk, false);
        Application app = RuntimeEnvironment.getApplication();
        AppEnvironment.startRuntime(app);
        HubClient client = HubClient.attach(app);
        System.out.println("[apklayer] component-host READY " + app.getPackageName() + " pid=" + android.os.Process.myPid());
        ShadowPausedLooper looper = Shadow.extract(Looper.getMainLooper());
        while (client.alive()) {
            long before = System.nanoTime();
            looper.poll(8);
            ShadowSystemClock.advanceBy(Duration.ofNanos(System.nanoTime() - before));
            looper.idle();
        }
    }
}
