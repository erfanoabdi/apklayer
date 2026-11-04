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
        HubDirectory directory = new HubDirectory(apps);
        if (!RealBinder.publish(HubProtocol.SERVICE, directory))
            throw new IllegalStateException("Cannot publish instance hub");
        if (!RealBinder.publish(HubAccounts.SERVICE, directory.accounts()))
            throw new IllegalStateException("Cannot publish the account service");
        HostBattery.installHub(directory::sendSystem);
        System.out.println("[apklayer-hub] READY pid=" + android.os.Process.myPid());
        directory.startSpare();  // the first component host starts warm
        new Thread(() -> HubBootSettings.write(RuntimeEnvironment.getApplication(), directory), "hub-boot-settings").start();  // the hub is the instance's system (uid 1000)
        new Thread(() -> HubAudioPermissions.feed(directory.packages()), "hub-audio-permissions").start();
        String ready = System.getenv("APKLAYER_HUB_READY_FD");  // the launcher's pipe (cli runHub): it sets apklayer.hub.ready on this byte
        if (ready != null) {
            try (java.io.OutputStream out = new java.io.FileOutputStream("/proc/self/fd/" + ready)) { out.write('1'); }
            catch (java.io.IOException failure) { System.out.println("[apklayer-hub] cannot tell the launcher: " + failure); }
        }
        try { new CountDownLatch(1).await(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
