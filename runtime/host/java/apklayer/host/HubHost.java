package apklayer.host;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.internal.AndroidSandbox;

/** Headless JVM for one Android instance's package/process directory. */
public final class HubHost {
    private HubHost() {}
    /** Settings that system_server writes on every boot before any app runs: PowerManagerService counts boots in Settings.Global.BOOT_COUNT (Device Info HW reads it with getInt and no default). */
    private static void bootSettings() {
        try {
            android.content.ContentResolver resolver = RuntimeEnvironment.getApplication().getContentResolver();
            String key = android.provider.Settings.Global.BOOT_COUNT;
            int boots = android.provider.Settings.Global.getInt(resolver, key, 0) + 1;
            android.provider.Settings.Global.putInt(resolver, key, boots);
            System.out.println("[apklayer-hub] boot count " + boots);
        } catch (RuntimeException failed) {
            System.out.println("[apklayer-hub] boot settings not written: " + failed);
        }
    }

    public static void run(AndroidSandbox sandbox) {
        Thread.currentThread().setContextClassLoader(sandbox.getRobolectricClassLoader());
        AppEnvironment.setUpApplication(sandbox, null, false);
        RuntimeEnvironment.getApplication();
        RealBinder.init();
        Path apps = Path.of(System.getProperty("apklayer.apps"));
        HubDirectory directory = new HubDirectory(apps);
        if (!RealBinder.publish(HubProtocol.SERVICE, directory))
            throw new IllegalStateException("Cannot publish instance hub");
        HostBattery.installHub(directory::sendSystem);
        System.out.println("[apklayer-hub] READY pid=" + android.os.Process.myPid());
        new Thread(HubHost::bootSettings, "hub-boot-settings").start();  // the hub is the instance's system (uid 1000): it writes what system_server writes at boot
        try { new CountDownLatch(1).await(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
