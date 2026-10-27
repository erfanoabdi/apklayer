package apklayer.host;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.internal.AndroidSandbox;

/** Headless JVM for one Android instance's package/process directory. */
public final class HubHost {
    private HubHost() {}
    public static void run(AndroidSandbox sandbox) {
        Thread.currentThread().setContextClassLoader(sandbox.getRobolectricClassLoader());
        AppEnvironment.setUpApplication(sandbox, null, false);
        RuntimeEnvironment.getApplication();
        RealBinder.init();
        Path apps = Path.of(System.getProperty("apklayer.apps"));
        if (!RealBinder.publish(HubProtocol.SERVICE, new HubDirectory(apps)))
            throw new IllegalStateException("Cannot publish instance hub");
        System.out.println("[apklayer-hub] READY pid=" + android.os.Process.myPid());
        try { new CountDownLatch(1).await(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
