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
        // the instance's spare (cli/spare.cpp), before the hub's own setup (~1 s): the first component host, the settings provider an app's
        // Application.onCreate waits for, is then warm by the time it is asked for (none starts when the instance has one)
        try { new ProcessBuilder(System.getProperty("apklayer.cli"), "app-spare").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start(); }
        catch (java.io.IOException failure) { System.out.println("[apklayer-hub] cannot start the instance's spare: " + failure); }
        AppEnvironment.setUpApplication(sandbox, null, false);
        RuntimeEnvironment.getApplication();
        RealBinder.init();
        HostCertificate.signatures();  // the instance's platform certificate exists before any app registers: a sandboxed app cannot make it (cli/sandbox.cpp)
        Path apps = Path.of(System.getProperty("apklayer.apps"));
        HubDirectory directory = new HubDirectory(apps);
        if (!RealBinder.publish(HubProtocol.SERVICE, directory))
            throw new IllegalStateException("Cannot publish instance hub");
        if (!RealBinder.publish(HubAccounts.SERVICE, directory.accounts()))
            throw new IllegalStateException("Cannot publish the account service");
        if (!HubPermissionChecker.publish(directory.packages()))
            throw new IllegalStateException("Cannot publish the permission checker");
        HostBattery.installHub(directory::sendSystem);
        HubVibrator.publishIfProvided(directory.packages()::uidHolds);  // before READY: an app's SystemVibratorManager looks it up once
        System.out.println("[apklayer-hub] READY pid=" + android.os.Process.myPid());
        new Thread(() -> HubBootSettings.write(RuntimeEnvironment.getApplication(), directory), "hub-boot-settings").start();  // the hub is the instance's system (uid 1000)
        directory.hostSettings = new HubHostSettings(RuntimeEnvironment.getApplication(), directory);
        Thread settings = new Thread(directory.hostSettings::watch, "hub-host-settings");
        settings.setDaemon(true);
        settings.start();
        Thread audio = new Thread(() -> HubAudioPermissions.watch(directory.packages()), "hub-audio-permissions");
        audio.setDaemon(true);
        audio.start();
        String ready = System.getenv("APKLAYER_HUB_READY_FD");  // the launcher's pipe (cli runHub): it sets apklayer.hub.ready on this byte
        if (ready != null) {
            try (java.io.OutputStream out = new java.io.FileOutputStream("/proc/self/fd/" + ready)) { out.write('1'); }
            catch (java.io.IOException failure) { System.out.println("[apklayer-hub] cannot tell the launcher: " + failure); }
        }
        try { new CountDownLatch(1).await(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
