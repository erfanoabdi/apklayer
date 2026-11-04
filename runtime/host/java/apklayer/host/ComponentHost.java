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
    /** What a spare component host does before it knows its package (ComponentSpare). */
    public static void prepare() {
        AppEnvironment.loadNativeRuntime();
    }
    @Override public void run() {
        // from here on SIGTERM is an orderly stop; before (a spare host waiting for its package) the JVM's default exit
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
        ShadowPausedLooper looper = Shadow.extract(Looper.getMainLooper());
        while (client.alive() && !stopping) step(looper);
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

    private static void step(ShadowPausedLooper looper) {
        looper.poll(8);
        if (NativeLooper.forThread() != 0) NativeLooper.pollOnce(0);
        ShadowSystemClock.advanceBy(Duration.ofNanos(Math.max(0, System.nanoTime() - org.robolectric.shadows.ShadowSystem.nanoTime())));
        looper.idle();
    }
}
