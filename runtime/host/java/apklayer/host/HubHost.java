package apklayer.host;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.internal.AndroidSandbox;

/**
 * Headless JVM for one Android instance's package/process directory: the instance's system_server. With {@code apk} it is also the process of the
 * package that runs in system_server on a device (SettingsProvider, android:process="system"; cli runHub): its application is the hub's, its
 * providers answer from here and it registers with the hub as any process does, so no JVM of its own is started for it.
 */
public final class HubHost {
    private HubHost() {}
    /** True until the hub is published: its own package must not attach through HubStartup, which would start (and wait for) a hub. */
    static volatile boolean booting;
    static volatile boolean hub;  // this JVM is the hub (its own package registers as a headless process)
    public static void run(AndroidSandbox sandbox, Path apk) {
        booting = hub = true;
        Thread.currentThread().setContextClassLoader(AppClassLoader.app != null ? AppClassLoader.app : sandbox.getRobolectricClassLoader());
        // the instance's spare (cli/spare.cpp), before the hub's own setup (~1 s): the first app launch, or the first component host, finds it warm
        try { new ProcessBuilder(System.getProperty("apklayer.cli"), "app-spare").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start(); }
        catch (java.io.IOException failure) { System.out.println("[apklayer-hub] cannot start the instance's spare: " + failure); }
        StartTrace.mark("hub: spare started");
        AppEnvironment.setUpApplication(sandbox, apk, false);
        RuntimeEnvironment.getApplication();
        StartTrace.mark("hub: application");
        RealBinder.init();
        StartTrace.mark("hub: binder");
        HostCertificate.signatures();  // the instance's platform certificate exists before any app registers: a sandboxed app cannot make it (cli/sandbox.cpp)
        StartTrace.mark("hub: certificate");
        Path apps = Path.of(System.getProperty("apklayer.apps"));
        HubDirectory directory = new HubDirectory(apps);
        StartTrace.mark("hub: directory");
        if (!RealBinder.publish(HubProtocol.SERVICE, directory))
            throw new IllegalStateException("Cannot publish instance hub");
        if (!RealBinder.publish(HubAccounts.SERVICE, directory.accounts()))
            throw new IllegalStateException("Cannot publish the account service");
        if (!HubPermissionChecker.publish(directory.packages()))
            throw new IllegalStateException("Cannot publish the permission checker");
        if (!HubAttestationIds.publish(directory.packages())) System.out.println("[apklayer-hub] cannot publish " + HubAttestationIds.SERVICE);
        StartTrace.mark("hub: published");
        HostBattery.installHub((intent, sticky) -> { directory.sendSystem(intent, sticky); directory.hostChanged(); });
        HostNetwork.installHub(directory::sendSystem);
        HostNetwork.install(RuntimeEnvironment.getApplication());  // the hub follows the host's connection too, to announce its changes
        HostNetwork.watch(directory::hostChanged);  // job constraints follow the host's network
        HubVibrator.publishIfProvided(directory.packages()::uidHolds);  // before READY: an app's SystemVibratorManager looks it up once
        HubBatteryStats.publishIfSensors();  // SensorService's battery notes: without it every sensor enable and disable waits 5 s
        directory.usb.watch();
        HubLockScreen.unlockIfProvided();  // before READY: an app's auth-bound keys need the auth token at once
        StartTrace.mark("hub: battery, vibrator, usb");
        booting = false;
        if (apk != null) {  // the system process's package: registered and its providers created before READY, so no client asks for a host of it
            android.app.Application app = RuntimeEnvironment.getApplication();
            AppEnvironment.installSystemPackage(app);
            HubClient.attach(app);
            AppEnvironment.installProviders(app);
            StartTrace.mark("hub: " + app.getPackageName() + " attached");
            sun.misc.Signal.handle(new sun.misc.Signal("TERM"), signal -> ComponentHost.stop());  // its due writes are saved first (ComponentHost.saveDueWork)
        }
        System.out.println("[apklayer-hub] READY pid=" + android.os.Process.myPid());
        new Thread(() -> HubBootSettings.write(RuntimeEnvironment.getApplication(), directory), "hub-boot-settings").start();  // the hub is the instance's system (uid 1000)
        Thread boot = new Thread(() -> HubBootBroadcasts.sendOnce(directory), "hub-boot-broadcasts");  // after the boot settings, once per stack start
        boot.setDaemon(true);
        boot.start();
        directory.hostSettings = new HubHostSettings(RuntimeEnvironment.getApplication(), directory);
        Thread settings = new Thread(directory.hostSettings::watch, "hub-host-settings");
        settings.setDaemon(true);
        settings.start();
        Thread audio = new Thread(() -> HubAudioPermissions.watch(directory.packages()), "hub-audio-permissions");
        audio.setDaemon(true);
        audio.start();
        if ("1".equals(System.getenv("APKLAYER_SANDBOX"))) CdsDump.afterStart();  // sandboxed apps cannot write the archive (cli/sandbox.cpp sandboxSpawn)
        String ready = System.getenv("APKLAYER_HUB_READY_FD");  // the launcher's pipe (cli runHub): it sets apklayer.hub.ready on this byte
        if (ready != null) {
            try (java.io.OutputStream out = new java.io.FileOutputStream("/proc/self/fd/" + ready)) { out.write('1'); }
            catch (java.io.IOException failure) { System.out.println("[apklayer-hub] cannot tell the launcher: " + failure); }
        }
        if (apk == null) {
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return;
        }
        ComponentHost.loop(() -> false);  // the package's main looper, as its own process runs it, until SIGTERM
        ComponentHost.saveDueWork(RuntimeEnvironment.getApplication());
        System.exit(0);  // then the shutdown hooks: the component hosts end (HubDirectory)
    }
}
