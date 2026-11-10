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
        Thread.currentThread().setContextClassLoader(AppClassLoader.app != null ? AppClassLoader.app : sandbox.getRobolectricClassLoader());  // the app's own loader, as on Android
        StartTrace.mark("componenthost run");
        AppEnvironment.setUpApplication(sandbox, apk, false);
        StartTrace.mark("application set up");
        Application app = RuntimeEnvironment.getApplication();
        StartTrace.mark("application created (onCreate done)");
        AppEnvironment.startRuntime(app);
        StartTrace.mark("runtime started");
        AppEnvironment.installSystemPackage(app);
        HubClient client = HubClient.attach(app);
        StartTrace.mark("hub attached");
        System.out.println("[apklayer] component-host READY " + app.getPackageName() + " pid=" + android.os.Process.myPid());
        AppSpare.launchStarted();
        String startService = System.getProperty("apklayer.startservice");  // exploration (PLAN archive P8): what system_server's startSystemUi does, by hand
        if (startService != null) app.startService(new android.content.Intent().setClassName(app.getPackageName(), startService));
        // no cached processes (the owner's rule; Android keeps them until memory runs short): a host that runs no service, has no work queued and
        // was not called for IDLE_EXIT_MS ends, as its last service's end does (Services.exitIfIdle); a provider's next client starts it again
        // (RemoteProvider retries a dead provider through the hub). What runs in system_server on a device (android:process="system":
        // SettingsProvider, which every start reads), uid 1000 and persistent apps stay.
        long idleExitNs = Long.getLong("apklayer.hostidlems", 60_000) * 1_000_000L;
        android.content.pm.ApplicationInfo info = app.getApplicationInfo();
        boolean resident = "system".equals(info.processName) || android.os.Process.myUid() == android.os.Process.SYSTEM_UID
                || (info.flags & android.content.pm.ApplicationInfo.FLAG_PERSISTENT) != 0;
        System.out.println("[apklayer] component-host process " + info.processName + (resident ? ": resident" : ": ends when idle"));
        // from here on SIGTERM is an orderly stop (the loop below ends, due work is saved); before, while the application is set up and attached to
        // the hub, the JVM's default exit: a stop that ends the hub meanwhile left the setup waiting for it and the host to the hub's SIGKILL
        sun.misc.Signal.handle(new sun.misc.Signal("TERM"), signal -> stopping = true);
        ShadowPausedLooper looper = Shadow.extract(Looper.getMainLooper());
        boolean[] told = {false};
        loop(() -> {
            if (!client.alive()) return true;
            if (resident || idleExitNs <= 0 || System.nanoTime() - ShadowHostRealBinder.LAST_TRANSACTION <= idleExitNs) { told[0] = false; return false; }
            // a message due later than the idle window does not keep it (Android ends such a cached process too): MediaProvider's WorkManager posts
            // its delayed work 14 h ahead (GreedyScheduler), the same work is a job the hub runs then (JobScheduler)
            long next = looper.getNextScheduledTaskTime().toMillis();
            boolean idle = Services.none() && ShadowHostRealBinder.IN_FLIGHT.get() == 0 && (next == 0 || next - android.os.SystemClock.uptimeMillis() > idleExitNs / 1_000_000);
            if (idle) { System.out.println("[apklayer] component-host idle for " + idleExitNs / 1_000_000 + " ms (no service, no call): exiting"); stop(); }
            else if (!told[0]) {  // uncalled, yet kept: say once by what
                told[0] = true;
                System.out.println("[apklayer] component-host stays: " + (Services.none() ? "" : "a service runs, ") + ShadowHostRealBinder.IN_FLIGHT.get() + " calls in flight, next message "
                        + (looper.getNextScheduledTaskTime().toMillis() == 0 ? "none" : "in " + (looper.getNextScheduledTaskTime().toMillis() - android.os.SystemClock.uptimeMillis()) + " ms: "
                        + org.robolectric.util.ReflectionHelpers.getField(Looper.getMainLooper().getQueue(), "mMessages")));
            }
            return idle;
        });
        saveDueWork(app);
        NativeLoader.endWithoutNativeDestructors();
    }

    /** Runs the main looper until {@link #stop} (SIGTERM, an idle host) or {@code end} says so. */
    static void loop(java.util.function.BooleanSupplier end) {
        ShadowPausedLooper looper = Shadow.extract(Looper.getMainLooper());
        do step(looper); while (!stopping && !end.getAsBoolean());
    }
    static void stop() { stopping = true; }

    /** After a stop: the work due within STOP_WORK_MS runs first (pending writes; SharedPreferences.apply, SettingsState's), then the process may end. */
    static void saveDueWork(Application app) {
        if (!stopping) return;  // the hub went away: nothing asked for an orderly stop
        ShadowPausedLooper looper = Shadow.extract(Looper.getMainLooper());
        long due = android.os.SystemClock.uptimeMillis() + STOP_WORK_MS, deadline = System.nanoTime() + STOP_DEADLINE_MS * 1_000_000L;
        android.app.QueuedWork.waitToFinish();
        while (PendingWork.dueBefore(due) && System.nanoTime() < deadline) step(looper);
        PendingWork.awaitOtherLoopers(deadline);  // the settings provider saves on its own handler thread
        System.out.println("[apklayer] component-host stopped " + app.getPackageName() + " after " + ((System.nanoTime() - deadline) / 1_000_000 + STOP_DEADLINE_MS) + " ms of due work");
    }

    private static final long STOP_WORK_MS = 2000, STOP_DEADLINE_MS = 3500;  // SettingsState's longest write delay; under `stop`'s 5 s before SIGKILL
    private static volatile boolean stopping;

    private static final long IDLE_POLL_MS = 250;  // an idle host's check of the hub and of `stop`

    private static void step(ShadowPausedLooper looper) {
        // until the next message is due (a post wakes the queue at once): an 8 ms tick kept each idle host at ~20% of a phone core;
        // the main thread's native looper (no wake of its own here) keeps the short tick
        long next = looper.getNextScheduledTaskTime().toMillis();
        long wait = next == 0 ? IDLE_POLL_MS : Math.max(1, Math.min(IDLE_POLL_MS, next - android.os.SystemClock.uptimeMillis()));
        looper.poll(NativeLooper.forThread() != 0 ? Math.min(wait, 8) : wait);
        if (NativeLooper.forThread() != 0) NativeLooper.pollOnce(0);
        ShadowSystemClock.advanceBy(Duration.ofNanos(Math.max(0, System.nanoTime() - org.robolectric.shadows.ShadowSystem.nanoTime())));
        looper.idle();
    }
}
