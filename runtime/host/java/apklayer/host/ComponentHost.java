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
        // from here on SIGTERM is an orderly stop; before (a spare waiting for its launch) the JVM's default exit
        sun.misc.Signal.handle(new sun.misc.Signal("TERM"), signal -> stopping = true);
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
        ShadowPausedLooper looper = Shadow.extract(Looper.getMainLooper());
        // no cached processes (the owner's rule; Android keeps them until memory runs short): a host that runs no service, has no work queued and
        // was not called for IDLE_EXIT_MS ends, as its last service's end does (Services.exitIfIdle); a provider's next client starts it again
        // (RemoteProvider retries a dead provider through the hub). The system's own (uid 1000: SettingsProvider) and persistent apps stay.
        long idleExitNs = Long.getLong("apklayer.hostidlems", 60_000) * 1_000_000L;
        boolean resident = android.os.Process.myUid() == android.os.Process.SYSTEM_UID || (app.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_PERSISTENT) != 0;
        while (client.alive() && !stopping) {
            step(looper);
            if (!resident && idleExitNs > 0 && Services.none() && ShadowHostRealBinder.IN_FLIGHT.get() == 0 && looper.getNextScheduledTaskTime().toMillis() == 0
                    && System.nanoTime() - ShadowHostRealBinder.LAST_TRANSACTION > idleExitNs) {
                System.out.println("[apklayer] component-host idle for " + idleExitNs / 1_000_000 + " ms (no service, no call): exiting");
                stopping = true;
            }
        }
        if (stopping) {
            // `apklayer stop` (SIGTERM): the work due within STOP_WORK_MS runs first (pending writes; SharedPreferences.apply), then the host ends
            long due = android.os.SystemClock.uptimeMillis() + STOP_WORK_MS, deadline = System.nanoTime() + STOP_DEADLINE_MS * 1_000_000L;
            android.app.QueuedWork.waitToFinish();
            while (PendingWork.dueBefore(due) && System.nanoTime() < deadline) step(looper);
            PendingWork.awaitOtherLoopers(deadline);  // the settings provider saves on its own handler thread
            System.out.println("[apklayer] component-host stopped " + app.getPackageName() + " after " + ((System.nanoTime() - deadline) / 1_000_000 + STOP_DEADLINE_MS) + " ms of due work");
        }
        NativeLoader.endWithoutNativeDestructors();
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
