package apklayer.host;

import static java.lang.Math.max;
import static apklayer.host.ActivityTask.setVisible;
import static apklayer.host.ActivityTask.stack;
import static java.lang.Math.min;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.internal.AndroidSandbox;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowPausedLooper;
import org.robolectric.shadows.ShadowSystemClock;

/**
 * Runs an APK inside the sandbox (PLAN P1.5). Setup follows Robolectric's simulator
 * {@code AppLoader}; the main loop follows {@code Simulator.start()/loop()} (4.17), with
 * apklayer's host window instead of Swing. Activities started by the app are launched the way
 * the simulator launches its first one ({@code Robolectric.setupActivity}), on a back stack.
 *
 * <p>Debug/verification properties: {@code apklayer.script} ("ms:tap:x,y+ms:back+..."),
 * {@code apklayer.runms} (exit after). {@code apklayer.data} + {@code apklayer.package}:
 * persistent app data root for that package.
 */
public final class AppHost implements Runnable {

    private final AndroidSandbox sandbox;
    private final Path apk;
    private final Path describe;
    private Handler main;
    private boolean windowActive = true, stoppedByMinimize; // lifecycle driven by the window state
    private final ActivityTask task = new ActivityTask(() -> windowActive);
    private int logicalDpi; // 0: 160 x scale
    private int logicalW, logicalH, logicalScale = 100; // current window size in dp and scale %
    private int lastOrientation = -2;                    // top activity's requested orientation last applied
    private final HostTextBridge text = new HostTextBridge();
    private ShellClient shell;
    private HostInput input;
    private ScriptRunner scriptRunner;

    /** @param describe if non-null, write the app's metadata there instead of running it */
    public AppHost(AndroidSandbox sandbox, Path apk, Path describe) {
        this.sandbox = sandbox;
        this.apk = apk;
        this.describe = describe;
    }

    @Override
    public void run() {
        Thread.currentThread().setContextClassLoader(AppClassLoader.app != null ? AppClassLoader.app : sandbox.getRobolectricClassLoader());  // the app's own loader, as on Android
        StartTrace.mark("apphost run");
        AppEnvironment.setUpApplication(sandbox, apk, describe != null);
        StartTrace.mark("application set up");
        // apklayer patch: Android prepares the UI thread's native Looper before
        // native activity initialization; the paused Java queue alone does not.
        if (describe == null && NativeLooper.prepareForThread() == 0)
            throw new IllegalStateException("Cannot prepare the main native looper");
        Application app = RuntimeEnvironment.getApplication(); // created here (lazy), on persistent dirs
        if (describe != null) {
            AppMetadata.describe(app, describe);
            return;
        }
        StartTrace.mark("application created (onCreate done)");
        AppEnvironment.startRuntime(app);
        StartTrace.mark("runtime started");
        HubClient.attach(app);
        StartTrace.mark("hub attached");

        Display display = app.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        int width = display.getWidth(), height = display.getHeight();
        float density = app.getResources().getDisplayMetrics().density;
        logicalW = Math.round(width / density);
        logicalH = Math.round(height / density);
        logicalScale = Math.round(density * 100);
        main = new Handler(Looper.getMainLooper());
        input = new HostInput(main, InstrumentationRegistry.getInstrumentation().getUiAutomation(), new HostInput.WindowEvents() {
            @Override
            public void configure(int w, int h, int scalePct, int dpi, int navPx) {
                hostResize(w, h, scalePct, dpi, navPx);
            }

            @Override
            public void state(int flags) {
                hostState(flags);
            }
        });
        scriptRunner = new ScriptRunner(input, java.util.Map.of(
                "clipin", text::clipboardIn, // the system selection changed
                "clipcopy", text::clipboardCopy, // the app copies text itself
                "ime", arg -> { // ime:<commit>|<preedit>: as the compositor's input method would
                    String[] parts = arg.split("\\|", -1);
                    text.ime(0, 0, parts[0], parts.length > 1 ? parts[1] : "");
                },
                "view", u -> RuntimeEnvironment.getApplication().startActivity( // view:<uri>: as if the app fired ACTION_VIEW
                        new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)),
                "pick", a -> stack.peek().get().startActivityForResult( // pick:<ACTION_*>: as if the app opened a document picker
                        new Intent(a).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 7),
                "click", c -> { // click:<x>,<y>: a pointer press + release through the shell's event path
                    String[] p = c.split(",");
                    input.onHostEvent(new int[] {0, Integer.parseInt(p[0]), Integer.parseInt(p[1])});
                    input.onHostEvent(new int[] {1, Integer.parseInt(p[0]), Integer.parseInt(p[1])});
                },
                "notify", t -> { // notify:<title>[,<id>[,<importance>[,ongoing]]]: as if the app posted a notification
                    String[] p = t.split(",");
                    int id = p.length > 1 ? Integer.parseInt(p[1]) : 1, importance = p.length > 2 ? Integer.parseInt(p[2]) : android.app.NotificationManager.IMPORTANCE_DEFAULT;
                    android.app.Application ctx = RuntimeEnvironment.getApplication();
                    android.app.NotificationManager nm = ctx.getSystemService(android.app.NotificationManager.class);
                    nm.createNotificationChannel(new android.app.NotificationChannel("ch" + importance, "test " + importance, importance));
                    android.app.Notification.Builder nb = new android.app.Notification.Builder(ctx, "ch" + importance)
                            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(p[0]).setContentText("from apklayer");
                    if (p.length > 3) nb.setOngoing(true);
                    nm.notify(id, nb.build());
                },
                "orient", o -> { // orient:<ActivityInfo.SCREEN_ORIENTATION_* value>
                    Activity top = stack.peek().get();
                    top.setRequestedOrientation(Integer.parseInt(o));
                }));

        ShellClient shell = ShellClient.connect(app.getPackageName());
        if (shell == null) {
            System.out.println("[apklayer] no apklayer-shell: headless");
        } else {
            try {
                shell.createWindow(width, height, AppMetadata.label(app));
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (shell != null) {
            // The shell answers the window with its logical size and the output scale (HiDPI):
            // apply it before the first activity so the app starts at its final density.
            long deadline = System.nanoTime() + 1_500_000_000L;
            boolean configured = false;
            while (!configured && System.nanoTime() < deadline) {
                int[] ev = shell.poll();
                if (ev == null) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        break;
                    }
                } else if (ev[0] == 14) {
                    hostResize(ev[1], ev[2], ev[3], ev.length > 5 ? ev[4] : 0, ev.length > 5 ? ev[5] : 0);
                    width = display.getWidth();
                    height = display.getHeight();
                    configured = true;
                }
            }
        }
        StartTrace.mark("window configured by the shell");
        this.shell = shell;
        org.robolectric.shadows.NavigationBarInset.onVisibility(shell::sendNavigationBar);  // immersive mode: the shell hides its bar
        if (shell != null && HostConfiguration.night() != null) shell.sendDecorDark(HostConfiguration.night());
        text.shell = shell;
        app.getSystemService(android.content.ClipboardManager.class).addPrimaryClipChangedListener(text::onAppClipboardChanged);
        HostKeyStore.install();
        AppEnvironment.installSystemPackage(app);
        // the manifest providers were created before Application.onCreate (ShadowHostInstrumentation)
        if (System.getProperty("apklayer.webviewprovider") != null) { // Android's RuntimeInit installs a handler that logs and kills the process; Chromium's JniAndroid calls it
            Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> { System.out.println("FATAL EXCEPTION: " + thread.getName()); failure.printStackTrace(System.out); System.out.flush(); Runtime.getRuntime().halt(10); });
        }
        if (System.getProperty("apklayer.webviewprovider") != null) org.robolectric.Shadows.shadowOf(app.getPackageManager()).setSystemFeature(android.content.pm.PackageManager.FEATURE_WEBVIEW, true);
        StartTrace.mark("system package installed");
        // this launch's own intent file (`apklayer run --intent`, PLAN P6) or what another app started it with through the hub; with both, the file's
        // came first (the hub queued its one while this process started: `apklayer run` again, HubActivities.relaunch) and the hub's follows it
        Intent own = HostApps.incoming(), hub = HubActivityClient.incoming(), incoming = own != null ? own : hub;
        if (own != null && hub != null) HubActivityClient.deliver(hub, HubActivityClient.takeReceiver());  // kept until the first activity is up (setStarter)
        // a message, as Android's launch transaction is: an app whose Application.onCreate posted its own Looper.loop() (a crash guard: SimpleX) runs it from there
        final Intent first = incoming;
        final int moveX = width, moveY = height;
        new android.os.Handler(Looper.getMainLooper()).post(() -> {
            task.launchFirst(first != null ? first : AppMetadata.launcherIntent(app), first != null);
            HubActivityClient.setStarter(task::startForHub);
            // Like Simulator.postMotionEvent: an off-screen move so the first frame is drawn.
            input.inject(MotionEvent.obtain(0, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, moveX, moveY, 0));
            launched = true;
        });
        loop(shell);
    }

    /** Simulator.loop(), plus host input, navigation and the verification hooks. */
    private void loop(ShellClient shell) {
        looper = Shadow.extract(Looper.myLooper());
        loopShell = shell;
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                Choreographer.getInstance().postFrameCallback(this);
            }
        });

        for (String step : System.getProperty("apklayer.script", "").split("[+;]")) {
            if (step.isEmpty()) continue;
            String[] kv = step.split(":", 2);
            long ms = Long.parseLong(kv[0]);
            // allow multiple steps at the same time, separated by ','
            script.merge(ms, kv[1], (a, b) -> a + ";" + b);
        }
        if (Boolean.getBoolean("apklayer.autotap")) {
            long t = Long.getLong("apklayer.autotap.ms", 4500);
            script.put(t, "tap:first");
        }
        // runms holds in headless mode too, on the sandbox clock step() advances (Simulator's advanceTo + idleFor); with a window real time drives it
        pausedMode = shell == null;
        runMs = Long.getLong("apklayer.runms", pausedMode ? 3000 : Long.MAX_VALUE);
        clockNanos = perfAt = System.nanoTime();
        perfUptime = SystemClock.uptimeMillis();
        loopStart = pausedMode ? SystemClock.uptimeMillis() : System.nanoTime() / 1_000_000;
        running = this;
        looper.idle();  // the first launch
        while (step(false)) {}
        end();
    }

    private ShadowPausedLooper looper;
    private ShellClient loopShell;
    private final TreeMap<Long, String> script = new TreeMap<>();
    private boolean pausedMode, launched;
    private long runMs, loopStart;
    private static AppHost running;

    /** An app's own Looper.loop() on the main thread waits here for its next message (ShadowHostMessageQueue.nativePollOnce): the run loop goes on inside it.
     * False before the run loop started (the caller waits as usual). The app's loop is never left (a crash guard calls it again), so the run ends here. */
    static boolean stepInAppLoop() {
        AppHost host = running;
        if (host == null) return false;
        if (!host.step(true)) { host.end(); System.exit(0); }
        return true;
    }

    /** One pass of the run loop; false when the app ends. inAppLoop: the app's own Looper.loop() dispatches the messages, this pass only waits for them. */
    private boolean step(boolean inAppLoop) {
        ShellClient shell = loopShell;
        if (launched && stack.isEmpty() && System.nanoTime() / 1_000_000 >= task.graceUntilMs && HubActivityClient.end()) return false;
        long now = System.nanoTime();
        long elapsed = (pausedMode ? SystemClock.uptimeMillis() : System.nanoTime() / 1_000_000) - loopStart;
        // exit when the run time is up (or the window closed) and every scheduled script step has fired
        if (script.isEmpty() && (elapsed >= runMs || (shell != null && shell.closed()))) {
            System.out.println("[apklayer] ending: " + (elapsed >= runMs ? "run time " + runMs + " ms is up" : "window closed"));
            return false;
        }

        for (int[] ev; shell != null && (ev = shell.poll()) != null; ) input.onHostEvent(ev);
        for (Object[] ime; shell != null && (ime = shell.pollIme()) != null; ) text.ime((int) ime[0], (int) ime[1], (String) ime[2], (String) ime[3]);
        for (String clip; shell != null && (clip = shell.pollClipboard()) != null; ) text.clipboardIn(clip);
        while (!script.isEmpty() && script.firstKey() <= elapsed) scriptRunner.runStep(script.pollFirstEntry().getValue());

        long nextTask = looper.getNextScheduledTaskTime().toMillis();
        long nextVsync = ShadowChoreographer.getNextVsyncTime();
        long timeout = nextTask == 0 || nextVsync == 0 ? max(nextTask, nextVsync) : min(nextTask, nextVsync);
        if (pausedMode) {
            // PAUSED looper mode: the clock steps to the next deadline (scripted ms are scheduler time, a "6 s" run takes well under a second)
            if (timeout != 0) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(max(1, timeout - SystemClock.uptimeMillis())));
            } else {
                ShadowSystemClock.advanceBy(Duration.ofMillis(8));
            }
            if (!inAppLoop) looper.idle();
        } else {
            // Shell mode: a live window; real time drives everything. Bounded poll
            // so host input and scripted steps are serviced while idle.
            long wait = timeout == 0 ? 8 : max(1, min(8, timeout - SystemClock.uptimeMillis()));
            looper.poll(wait);
            pollNativeLooper();
            // Wall time since the previous advance, not since the top of this iteration: the time spent running the looper and
            // the follow-ups below was dropped, so the app's clock ran at a third of real time under load (ExoPlayer played
            // video at 8 fps and starved the audio, its clock being SystemClock).
            long clockNow = System.nanoTime();
            ShadowSystemClock.advanceBy(Duration.ofNanos(Math.max(0, clockNow - org.robolectric.shadows.ShadowSystem.nanoTime())));  // to the monotonic clock, which native code (Gecko's vsync) reads too
            clockNanos = clockNow;
            if (Boolean.getBoolean("apklayer.perf") && clockNow - perfAt > 2_000_000_000L) { // app clock against wall time, and the loop's pace
                System.out.println("[apklayer] clock: app " + (SystemClock.uptimeMillis() - perfUptime) + " ms, wall " + (clockNow - perfAt) / 1_000_000 + " ms, loops " + perfLoops);
                perfAt = clockNow; perfUptime = SystemClock.uptimeMillis(); perfLoops = 0;
            }
            perfLoops++;
            if (!inAppLoop) looper.idle();
        }
        if (TRACE_ROOTS && now - lastRootTrace > 1_000_000_000L) { lastRootTrace = now; HostWindows.traceRoots(); }
        task.followNavigation();
        if (stack.isEmpty()) return true;  // the grace period after a launcher activity finished itself
        followOrientation();
        text.followTextInput();
        task.deliverCrossAppResults();
        return true;
    }

    private void end() {
        running = null;
        System.out.println("[apklayer] exit: depth=" + stack.size());
        NativeLoader.endWithoutNativeDestructors();
        if (Boolean.getBoolean("apklayer.threads")) { // -Dapklayer.threads=true: what every thread is doing at the end (stalls)
            Thread.getAllStackTraces().forEach((t, st) -> {
                if (st.length == 0 || t.isDaemon() && !t.getName().contains("Main") && !t.getName().contains("Render")) return;
                System.out.println("[apklayer] thread " + t.getName() + " " + t.getState());
                for (int i = 0; i < Math.min(st.length, 14); i++) System.out.println("[apklayer]     at " + st[i]);
            });
        }
    }

    private long clockNanos = System.nanoTime(), perfAt = System.nanoTime(), perfUptime;
    private int perfLoops;

    private static final boolean TRACE_ROOTS = Boolean.getBoolean("apklayer.traceroots");
    private long lastRootTrace;

    private boolean nativeLooperSeen;
    /** Native code (Chromium) that runs a message pump on the main thread waits on the thread's native looper: run what is ready. */
    private void pollNativeLooper() {
        long looper = NativeLooper.forThread();
        if (looper == 0) return;
        if (!nativeLooperSeen) { nativeLooperSeen = true; System.out.println("[apklayer] the main thread has a native looper: polling it"); }
        NativeLooper.pollOnce(0);
    }

    /**
     * PLAN P2.3: a portrait/landscape request from the top activity (manifest screenOrientation or
     * setRequestedOrientation) reshapes the window. Other values keep the current shape.
     */
    private void followOrientation() {
        if (stack.isEmpty() || logicalW == 0) return;
        int req = stack.peek().get().getRequestedOrientation();
        if (req == lastOrientation) return;
        lastOrientation = req;
        boolean landscape = req == 0 || req == 6 || req == 11, portrait = req == 1 || req == 7 || req == 12;
        boolean turn = (landscape && logicalW < logicalH) || (portrait && logicalW > logicalH);
        if (turn) System.out.println("[apklayer] orientation " + (landscape ? "landscape" : "portrait") + " requested");
        // the shell keeps a fixed orientation's shape through drag resizes too (0 x 0: none), turns the window when it has the other shape and
        // answers with a configure
        int longer = Math.max(logicalW, logicalH), shorter = Math.min(logicalW, logicalH);
        if (shell != null) shell.sendWindowSize(landscape ? longer : portrait ? shorter : 0, landscape ? shorter : portrait ? longer : 0);
        else if (turn) hostResize(logicalH, logicalW, logicalScale, logicalDpi, 0);
    }


    /** A host settings change (HostConfiguration.update, on the main thread) in the running app: its activities, and the shell's title bar. */
    static void configurationChanged(int changes) {
        AppHost host = running;
        if (host == null) return;  // before the first launch: the activities start with it
        if (host.shell != null && HostConfiguration.night() != null) host.shell.sendDecorDark(HostConfiguration.night());
        if (host.shell != null) host.shell.sendWindowMode(HostConfiguration.windowMode());  // live: the shell restyles the window (bridge/shell/mobile.c)
        ActivityController<? extends Activity> top = stack.peek();
        HostConfiguration.dispatch(new ArrayList<>(stack), a -> host.task.stopped.contains(a) || (host.stoppedByMinimize && top != null && a == top.get()), changes,
                (c, old) -> {
                    host.task.recreated(old, c.get());
                    setVisible(c.visible(), true);  // ActivityController's recreation leaves the new window unattached
                    if (c == top) HostWindows.focus(c, host.windowActive);
                });
    }

    /**
     * Window state from the shell (PLAN P2.9), as in Android's freeform multi-window: an inactive window stays resumed and only
     * loses window focus and top-resumed (SDL and game loops pause on onPause); a minimized one is stopped, restored it restarts.
     */
    private void hostState(int flags) {
        boolean active = (flags & 1) != 0, minimized = (flags & 2) != 0;
        windowActive = active && !minimized;
        ActivityController<? extends Activity> top = stack.peek();
        if (top == null) return;
        System.out.println("[apklayer] window state active=" + active + " minimized=" + minimized);
        if (minimized && !stoppedByMinimize) {
            setVisible(top.pause().stop(), false);
            stoppedByMinimize = true;
            System.out.println("[apklayer] lifecycle: stopped (minimized)");
        } else if (!minimized && stoppedByMinimize) {
            setVisible(top.restart().resume().visible(), true);
            stoppedByMinimize = false;
            System.out.println("[apklayer] lifecycle: resumed");
        }
        HostWindows.focus(top, windowActive);
    }

    /** The dp size whose pixels (Robolectric truncates dp x density / 160) come nearest to px at this density. */
    private static int dp(int px, int dpi) {
        int dp = Math.max(1, Math.round(px * 160f / dpi)), best = dp;
        for (int c = dp - 1; c <= dp + 1; c++)
            if (c >= 1 && Math.abs((int) (c * (dpi / 160f)) - px) < Math.abs((int) (best * (dpi / 160f)) - px)) best = c;
        return best;
    }

    /**
     * Resize / DPI (PLAN P2): logical size and output scale -> sandbox qualifiers. The density is the shell's (a handheld's physical density
     * bucket, bridge/shell/mobile.c) or, on a desktop (dpi 0), 160 x scale; the size in dp is the window's pixels at that density, so the
     * screen capture comes out at the pixel size w x scale and the shell client rebuffers the window to match (ShellClient.present).
     * navPx: the shell's navigation bar over the window's bottom (mobile style), the display's navigation bar inset.
     */
    private void hostResize(int w, int h, int scalePct, int dpi, int navPx) {
        if (dpi <= 0) dpi = 160 * scalePct / 100;
        int wdp = dp(w * scalePct / 100, dpi), hdp = dp(h * scalePct / 100, dpi);
        logicalW = w;
        logicalH = h;
        logicalScale = scalePct;
        logicalDpi = dpi;
        System.out.println("[apklayer] configure " + w + "x" + h + " @" + scalePct + "% -> " + wdp
                + "x" + hdp + "dp @" + dpi + "dpi, navigation bar " + navPx + " px");
        org.robolectric.shadows.NavigationBarInset.set(navPx);
        NativeWindowResize.resizing(new ArrayList<>(stack));
        HostConfiguration.resize("w" + wdp + "dp-h" + hdp + "dp-" + dpi + "dpi", new ArrayList<>(stack));
        Object wmg = android.view.WindowManagerGlobal.getInstance();
        List<View> views = org.robolectric.util.ReflectionHelpers.getField(wmg, "mViews");
        for (View v : views) { // force a fresh frame at the new size
            v.requestLayout();
            v.invalidate();
        }
    }

}
