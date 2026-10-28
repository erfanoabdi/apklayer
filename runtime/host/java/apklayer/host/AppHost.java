package apklayer.host;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.content.res.TypedArray;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.internal.AndroidSandbox;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplication;
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
    private final Deque<ActivityController<? extends Activity>> stack = new ArrayDeque<>();
    /** Intents handed to other apps that will answer: routed handle -> {requesting activity, request intent}. */
    private final java.util.Map<HostApps.Routed, Object[]> crossApp = new java.util.HashMap<>();
    /** The activity another app started this process for (its result goes back, PLAN P6). */
    private Activity incomingActivity;
    /** Activities started with startActivityForResult -> the request intent, to return their setResult. */
    private final java.util.Map<Activity, Intent> resultFor = new java.util.IdentityHashMap<>();
    private final Set<Activity> stopped = new HashSet<>();
    /** Activities another app started through the hub for a result -> where their result goes. */
    private final java.util.Map<Activity, android.os.ResultReceiver> hubResults = new java.util.IdentityHashMap<>();
    private Handler main;
    private boolean pausedByFocus, stoppedByMinimize; // lifecycle driven by the window state
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
        Thread.currentThread().setContextClassLoader(sandbox.getRobolectricClassLoader());
        AppEnvironment.setUpApplication(sandbox, apk, describe != null);
        // apklayer patch: Android prepares the UI thread's native Looper before
        // native activity initialization; the paused Java queue alone does not.
        if (describe == null && NativeLooper.prepareForThread() == 0)
            throw new IllegalStateException("Cannot prepare the main native looper");
        Application app = RuntimeEnvironment.getApplication(); // created here (lazy), on persistent dirs
        if (describe != null) {
            AppMetadata.describe(app, describe);
            return;
        }
        AppEnvironment.startRuntime(app);
        HubClient.attach(app);

        Display display = app.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        int width = display.getWidth(), height = display.getHeight();
        float density = app.getResources().getDisplayMetrics().density;
        logicalW = Math.round(width / density);
        logicalH = Math.round(height / density);
        logicalScale = Math.round(density * 100);
        main = new Handler(Looper.getMainLooper());
        input = new HostInput(main, InstrumentationRegistry.getInstrumentation().getUiAutomation(), new HostInput.WindowEvents() {
            @Override
            public void configure(int w, int h, int scalePct) {
                hostResize(w, h, scalePct);
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
                    hostResize(ev[1], ev[2], ev[3]);
                    width = display.getWidth();
                    height = display.getHeight();
                    configured = true;
                }
            }
        }
        this.shell = shell;
        text.shell = shell;
        app.getSystemService(android.content.ClipboardManager.class).addPrimaryClipChangedListener(text::onAppClipboardChanged);
        if (java.security.Security.getProvider("AndroidKeyStore") == null) java.security.Security.addProvider(new HostKeyStoreProvider());
        AppEnvironment.installSystemPackage(app);
        AppEnvironment.installProviders(app);
        if (System.getProperty("apklayer.webviewprovider") != null) { // Android's RuntimeInit installs a handler that logs and kills the process; Chromium's JniAndroid calls it
            org.robolectric.shadows.ShadowBuild.setType("userdebug"); // a debuggable build: WebView then reads /data/local/tmp/webview-command-line
            Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> { System.out.println("FATAL EXCEPTION: " + thread.getName()); failure.printStackTrace(System.out); System.out.flush(); Runtime.getRuntime().halt(10); });
        }
        if (System.getProperty("apklayer.webviewprovider") != null) org.robolectric.Shadows.shadowOf(app.getPackageManager()).setSystemFeature(android.content.pm.PackageManager.FEATURE_WEBVIEW, true);
        HubActivityClient.starter = this::startForHub;
        Intent incoming = HubActivityClient.incoming(); // another app started this one with an intent, through the hub
        if (incoming == null) incoming = HostApps.incoming(); // ... or through the older intent file (PLAN P6)
        launch(incoming != null ? incoming : AppMetadata.launcherIntent(app));
        if (incoming != null) { incomingActivity = stack.peek().get(); android.os.ResultReceiver answerTo = HubActivityClient.takeReceiver(); if (answerTo != null) hubResults.put(incomingActivity, answerTo); }
        // Like Simulator.postMotionEvent: an off-screen move so the first frame is drawn.
        input.inject(MotionEvent.obtain(0, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, width, height, 0));
        loop(shell);
    }

    /** Simulator.loop(), plus host input, navigation and the verification hooks. */
    private void loop(ShellClient shell) {
        ShadowPausedLooper looper = Shadow.extract(Looper.myLooper());
        looper.idle();
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                Choreographer.getInstance().postFrameCallback(this);
            }
        });

        TreeMap<Long, String> script = new TreeMap<>();
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
        // runms must be honoured in headless mode too (tests schedule taps at absolute times and rely on the run ending on schedule).
        // Headless runs must advance PAUSED clocks by the scheduled deadline (Simulator
        // does this with ShadowSystemClock::advanceTo + idleFor); wall-clock waiting only
        // works with a live window (shell mode), where real time drives the frame clock.
        boolean pausedMode = shell == null;
        long runMs = Long.getLong("apklayer.runms", pausedMode ? 3000 : Long.MAX_VALUE);
        // Schedule clock: in headless (PAUSED) mode the sandbox clock is stepped by the loop
        // below, so schedule script times on it — a "6 s" run then completes in well
        // under a second of real time. In shell mode real time drives everything.
        clockNanos = perfAt = System.nanoTime();
        perfUptime = SystemClock.uptimeMillis();
        long start = pausedMode ? SystemClock.uptimeMillis() : System.nanoTime() / 1_000_000;

        try {
            while (!stack.isEmpty()) {
                long now = System.nanoTime();
                long elapsed = (pausedMode ? SystemClock.uptimeMillis() : System.nanoTime() / 1_000_000) - start;
                // exit when the run time is up (or the window closed) and every scheduled script step has fired
                if (script.isEmpty() && (elapsed >= runMs || (shell != null && shell.closed())))
                    break;

                for (int[] ev; shell != null && (ev = shell.poll()) != null; ) input.onHostEvent(ev);
                for (Object[] ime; shell != null && (ime = shell.pollIme()) != null; ) text.ime((int) ime[0], (int) ime[1], (String) ime[2], (String) ime[3]);
                for (String clip; shell != null && (clip = shell.pollClipboard()) != null; ) text.clipboardIn(clip);
                while (!script.isEmpty() && script.firstKey() <= elapsed) scriptRunner.runStep(script.pollFirstEntry().getValue());

                long nextTask = looper.getNextScheduledTaskTime().toMillis();
                long nextVsync = ShadowChoreographer.getNextVsyncTime();
                long timeout = nextTask == 0 || nextVsync == 0 ? max(nextTask, nextVsync) : min(nextTask, nextVsync);
                if (pausedMode) {
                    // PAUSED looper mode: scheduled Runnables and vsync callbacks only run
                    // when the (paused) clock is advanced. Step it to the next deadline and
                    // drain — wall time is irrelevant here, so a "6 s" run completes in well
                    // under a second of real time (the scripted ms are scheduler time).
                    if (timeout != 0) {
                        ShadowSystemClock.advanceBy(Duration.ofMillis(max(1, timeout - SystemClock.uptimeMillis())));
                    } else {
                        ShadowSystemClock.advanceBy(Duration.ofMillis(8));
                    }
                    looper.idle();
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
                    ShadowSystemClock.advanceBy(Duration.ofNanos(clockNow - clockNanos));
                    clockNanos = clockNow;
                    if (Boolean.getBoolean("apklayer.perf") && clockNow - perfAt > 2_000_000_000L) { // app clock against wall time, and the loop's pace
                        System.out.println("[apklayer] clock: app " + (SystemClock.uptimeMillis() - perfUptime) + " ms, wall " + (clockNow - perfAt) / 1_000_000 + " ms, loops " + perfLoops);
                        perfAt = clockNow; perfUptime = SystemClock.uptimeMillis(); perfLoops = 0;
                    }
                    perfLoops++;
                    looper.idle();
                }
                if (TRACE_ROOTS && now - lastRootTrace > 1_000_000_000L) { lastRootTrace = now; traceRoots(); }
                followNavigation();
                followOrientation();
                text.followTextInput();
                deliverCrossAppResults();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("[apklayer] exit: depth=" + stack.size());
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

    /** -Dapklayer.traceroots=true: once a second, the state of every window's ViewRootImpl (why does a window not draw?). */
    private void traceRoots() {
        java.util.List<?> roots;
        try {
            java.lang.reflect.Field rf = android.view.WindowManagerGlobal.class.getDeclaredField("mRoots");
            rf.setAccessible(true);
            roots = new ArrayList<>((java.util.List<?>) rf.get(android.view.WindowManagerGlobal.getInstance()));
        } catch (Throwable e) {
            System.out.println("[apklayer] root trace failed: " + e);
            return;
        }
        if (roots.isEmpty()) System.out.println("[apklayer] root: no windows");
        for (Object o : roots) {
            try {
                android.view.ViewRootImpl root = (android.view.ViewRootImpl) o;
                StringBuilder sb = new StringBuilder("[apklayer] root:");
                for (String f : new String[] {"mFirst", "mAdded", "mTraversalScheduled", "mWillDrawSoon", "mStopped", "mIsDrawing", "mLayoutRequested", "mRelayoutRequested"}) {
                    java.lang.reflect.Field fld = android.view.ViewRootImpl.class.getDeclaredField(f);
                    fld.setAccessible(true);
                    sb.append(' ').append(f).append('=').append(fld.get(root));
                }
                java.lang.reflect.Field sf = android.view.ViewRootImpl.class.getDeclaredField("mSurface");
                sf.setAccessible(true);
                System.out.println(sb + " surfaceValid=" + ((android.view.Surface) sf.get(root)).isValid() + " visibility=" + root.getView().getVisibility()
                        + " nextVsync=" + ShadowChoreographer.getNextVsyncTime());
            } catch (Throwable e) {
                System.out.println("[apklayer] root trace failed: " + e);
            }
        }
    }

    private boolean nativeLooperSeen;
    /** Native code (Chromium) that runs a message pump on the main thread waits on the thread's native looper: run what is ready. */
    private void pollNativeLooper() {
        long looper = NativeLooper.forThread();
        if (looper == 0) return;
        if (!nativeLooperSeen) { nativeLooperSeen = true; System.out.println("[apklayer] the main thread has a native looper: polling it"); }
        NativeLooper.pollOnce(0);
    }

    /** An intent another app sent here through the hub: started like one of our own, its result goes to the sender's receiver. */
    private void startForHub(Intent intent, android.os.ResultReceiver resultTo) {
        launch(intent);
        if (resultTo != null && !stack.isEmpty()) hubResults.put(stack.peek().get(), resultTo);
    }

    /** Builds and fully starts the activity, as the simulator does (Robolectric.setupActivity). */
    private void launch(Intent intent) {
        // startActivityForResult: Robolectric only records it; the host delivers the result (PLAN P5.3)
        ShadowActivity.IntentForResult request = stack.isEmpty() ? null
                : ((ShadowActivity) Shadow.extract(stack.peek().get())).getNextStartedActivityForResult();
        boolean wantsResult = request != null && request.requestCode >= 0;
        Class<? extends Activity> cls;
        ActivityInfo info;
        try {
            info = intent.getComponent() != null
                    ? RuntimeEnvironment.getApplication().getPackageManager().getActivityInfo(intent.getComponent(), 0)
                    : RuntimeEnvironment.getApplication().getPackageManager().resolveActivity(intent, 0).activityInfo;
            // an <activity-alias> names its real class in targetActivity
            cls = Class.forName(info.targetActivity != null ? info.targetActivity : info.name)
                    .asSubclass(Activity.class);
        } catch (ClassNotFoundException | NullPointerException | android.content.pm.PackageManager.NameNotFoundException e) {
            if (HostPortals.open(intent)) return;
            if (HostPortals.requestPermissions(intent, request == null ? -1 : request.requestCode, stack.peek().get())) return;
            Intent picked = HostPortals.pick(intent);
            if (picked != null || HostPortals.isPicker(intent)) { // a desktop file dialog answers the document picker
                if (wantsResult) ((ShadowActivity) Shadow.extract(stack.peek().get()))
                        .receiveResult(intent, picked != null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, picked);
                return;
            }
            if (HubActivityClient.route(intent, wantsResult ? stack.peek().get() : null, intent)) return; // through the hub
            HostApps.Routed routed = HubActivityClient.enabled() ? null : HostApps.route(intent, wantsResult); // another installed app handles it
            if (routed != null) {
                if (wantsResult) crossApp.put(routed, new Object[] {stack.peek().get(), intent});
                return;
            }
            // Android would crash the app here; on the desktop the app stays on its current screen.
            System.out.println("[apklayer] cannot start " + intent + ": " + e);
            return;
        }
        // task rules: reuse an instance already on the stack, clear what is above it, or clear everything
        LaunchRules.Plan plan = LaunchRules.plan(stack.stream().<Class<?>>map(a -> a.get().getClass()).toList(), cls, intent.getFlags(), info.launchMode);
        if (plan.clearAll()) {
            while (!stack.isEmpty()) destroyTop();
        } else if (plan.above() >= 0) {
            for (int i = 0; i < plan.above(); i++) destroyTop();
            if (plan.reuse()) {
                ActivityController<? extends Activity> target = stack.peek();
                target.newIntent(intent);
                if (stopped.remove(target.get())) setVisible(target.restart().resume().visible(), true);
                else target.resume();
                System.out.println("[apklayer] reused " + cls.getName() + " depth=" + stack.size());
                return;
            }
            destroyTop(); // CLEAR_TOP on a standard activity: it is recreated
        }
        ActivityController<? extends Activity> previous = stack.peek();
        if (previous != null) previous.pause();
        ActivityController<? extends Activity> c = Robolectric.buildActivity(cls, intent).create();
        if (c.get().isFinishing()) {  // finish() in onCreate (Telegram starts its intro screen so): Android destroys it without start or resume
            c.destroy();
            System.out.println("[apklayer] finished in onCreate: " + c.get().getClass().getName());
            Intent next = ((ShadowApplication) Shadow.extract(RuntimeEnvironment.getApplication())).getNextStartedActivity();  // what it started before (the intro screen)
            if (next != null) launch(next);
            else if (previous != null) previous.resume();
            return;
        }
        c.start().postCreate(null).resume().visible().topActivityResumed(true);  // the rest of ActivityController.setup()
        // As in Android, the previous activity is stopped only once an opaque activity covers it;
        // behind a translucent one (dialog-style) it stays paused and visible.
        if (previous != null && !isTranslucent(c.get())) {
            setVisible(previous.stop(), false);
            stopped.add(previous.get());
        }
        stack.push(c);
        if (wantsResult) resultFor.put(c.get(), intent);
        // An activity that adds a window in onCreate (e.g. a dialog) otherwise never records its
        // own decor's first draw, and the composed screen would show nothing behind the dialog.
        c.get().getWindow().getDecorView().invalidate();
        System.out.println("[apklayer] started " + c.get().getClass().getName() + " depth=" + stack.size());
    }

    /** Hands an answer from another app (or its disappearance, as RESULT_CANCELED) to the activity that asked. */
    private void deliverCrossAppResults() {
        for (java.util.Iterator<java.util.Map.Entry<HostApps.Routed, Object[]>> it = crossApp.entrySet().iterator(); it.hasNext(); ) {
            java.util.Map.Entry<HostApps.Routed, Object[]> e = it.next();
            Object[] answer = HostApps.readResult(e.getKey());
            if (answer == null && e.getKey().process() != null && e.getKey().process().isAlive()) continue;
            if (answer == null) answer = new Object[] {Activity.RESULT_CANCELED, null};
            it.remove();
            Activity requester = (Activity) e.getValue()[0];
            if (stack.stream().anyMatch(c -> c.get() == requester))
                ((ShadowActivity) Shadow.extract(requester)).receiveResult((Intent) e.getValue()[1], (Integer) answer[0], (Intent) answer[1]);
        }
    }

    /** Destroys the top activity (the paused/stopped state it is in decides which lifecycle calls remain). */
    private void destroyTop() {
        ActivityController<? extends Activity> top = stack.pop();
        resultFor.remove(top.get());
        android.os.ResultReceiver abandoned = hubResults.remove(top.get());
        if (abandoned != null) HubActivityClient.answer(abandoned, Activity.RESULT_CANCELED, null);
        if (stopped.remove(top.get())) top.destroy();
        else top.pause().stop().destroy();
    }

    /** Follows the app's startActivity() calls and finish() of the top activity. */
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
        if ((landscape && logicalW < logicalH) || (portrait && logicalW > logicalH)) {
            System.out.println("[apklayer] orientation " + (landscape ? "landscape" : "portrait") + " requested");
            hostResize(logicalH, logicalW, logicalScale);
        }
    }

    private void followNavigation() {
        ShadowApplication shadowApp = Shadow.extract(RuntimeEnvironment.getApplication());
        Intent next = shadowApp.getNextStartedActivity();
        if (next != null) {
            launch(next);
            return;
        }
        Activity top = stack.peek().get();
        if (top.isFinishing()) {
            stack.pop().pause().stop().destroy();
            if (top == incomingActivity) {
                ShadowActivity answered = Shadow.extract(top);
                HostApps.writeResult(answered.getResultCode(), answered.getResultIntent());
            }
            android.os.ResultReceiver answerTo = hubResults.remove(top);
            if (answerTo != null) HubActivityClient.answer(answerTo, ((ShadowActivity) Shadow.extract(top)).getResultCode(), ((ShadowActivity) Shadow.extract(top)).getResultIntent());
            Intent request = resultFor.remove(top);
            if (request != null && !stack.isEmpty()) {
                ShadowActivity child = Shadow.extract(top);
                ((ShadowActivity) Shadow.extract(stack.peek().get())).receiveResult(request, child.getResultCode(), child.getResultIntent());
            }
            System.out.println("[apklayer] finished " + top.getClass().getName() + " depth=" + stack.size());
            if (!stack.isEmpty()) {
                ActivityController<? extends Activity> c = stack.peek();
                if (stopped.remove(c.get())) setVisible(c.restart().resume().visible(), true);
                else c.resume();
            }
        }
    }

    /**
     * ActivityThread#updateVisibility: a stopped activity's decor is INVISIBLE and becomes VISIBLE
     * again when it returns. Stopped windows then stay out of the composed screen, and the
     * visibility change redraws, which makes ScreenUpdateHandler capture a fresh frame.
     */
    private static boolean isTranslucent(Activity activity) {
        TypedArray a = activity.getTheme().obtainStyledAttributes(
                new int[] {android.R.attr.windowIsTranslucent, android.R.attr.windowIsFloating});
        try {
            return a.getBoolean(0, false) || a.getBoolean(1, false);
        } finally {
            a.recycle();
        }
    }

    private static void setVisible(ActivityController<? extends Activity> c, boolean visible) {
        c.get().getWindow().getDecorView().setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }


    /**
     * Resize / DPI (PLAN P2): logical size and output scale -> sandbox qualifiers (density =
     * 160 x scale). The screen capture then comes out at the new pixel size and the shell
     * client rebuffers the window to match (ShellClient.present).
     */
    /**
     * Window state from the shell (PLAN P2.9): an inactive window pauses the top activity, a
     * minimized one stops it; becoming active again restarts/resumes it.
     */
    private void hostState(int flags) {
        boolean active = (flags & 1) != 0, minimized = (flags & 2) != 0;
        ActivityController<? extends Activity> top = stack.peek();
        if (top == null) return;
        System.out.println("[apklayer] window state active=" + active + " minimized=" + minimized);
        if (minimized && !stoppedByMinimize) {
            if (!pausedByFocus) top.pause();
            setVisible(top.stop(), false);
            pausedByFocus = true;
            stoppedByMinimize = true;
            System.out.println("[apklayer] lifecycle: stopped (minimized)");
        } else if (!active && !pausedByFocus) {
            top.pause();
            pausedByFocus = true;
            System.out.println("[apklayer] lifecycle: paused (window inactive)");
        } else if (active && !minimized && (pausedByFocus || stoppedByMinimize)) {
            if (stoppedByMinimize) setVisible(top.restart().resume().visible(), true);
            else top.resume();
            pausedByFocus = false;
            stoppedByMinimize = false;
            System.out.println("[apklayer] lifecycle: resumed");
        }
    }

    private void hostResize(int w, int h, int scalePct) {
        int dpi = 160 * scalePct / 100;
        int wdp = Math.max(1, w), hdp = Math.max(1, h);
        logicalW = w;
        logicalH = h;
        logicalScale = scalePct;
        System.out.println("[apklayer] configure " + w + "x" + h + " @" + scalePct + "% -> " + wdp
                + "x" + hdp + "dp @" + dpi + "dpi");
        RuntimeEnvironment.setQualifiers("w" + wdp + "dp-h" + hdp + "dp-" + dpi + "dpi");
        // Applied in place, like an activity that handles the change itself: new resources
        // configuration, onConfigurationChanged, and the window roots relayout at the new size.
        // (Android would recreate activities that don't declare it; recreation doesn't re-attach
        // windows in this sandbox yet, and the startup scale is applied before any launch.)
        android.content.res.Resources appRes = RuntimeEnvironment.getApplication().getResources();
        for (ActivityController<? extends Activity> c : new ArrayList<>(stack)) {
            try {
                Activity a = c.get();
                a.getResources().updateConfiguration(appRes.getConfiguration(), appRes.getDisplayMetrics());
                a.onConfigurationChanged(appRes.getConfiguration());
                android.view.ViewRootImpl root = a.getWindow().getDecorView().getViewRootImpl();
                if (root != null) root.updateConfiguration(Display.INVALID_DISPLAY);
            } catch (Throwable e) {
                System.out.println("[apklayer] configuration change failed: " + e);
                e.printStackTrace(System.out);
            }
        }
        Object wmg = android.view.WindowManagerGlobal.getInstance();
        List<View> views = org.robolectric.util.ReflectionHelpers.getField(wmg, "mViews");
        for (View v : views) { // force a fresh frame at the new size
            v.requestLayout();
            v.invalidate();
        }
    }

}
