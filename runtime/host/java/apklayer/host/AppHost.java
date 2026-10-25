package apklayer.host;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.app.Activity;
import android.app.Application;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.Display;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.SQLiteMode;
import org.robolectric.annotation.experimental.LazyApplication;
import org.robolectric.internal.AndroidSandbox;
import org.robolectric.manifest.AndroidManifest;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.ShadowPausedLooper;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.shadows.ShadowView;
import org.robolectric.simulator.FixedConfiguration;
import org.robolectric.simulator.ScreenUpdateHandler;

/**
 * Runs an APK inside the sandbox (PLAN P1.5). Setup follows Robolectric's simulator
 * {@code AppLoader}; the main loop follows {@code Simulator.start()/loop()} (4.17), with
 * apklayer's host window instead of Swing. Activities started by the app are launched the way
 * the simulator launches its first one ({@code Robolectric.setupActivity}), on a back stack.
 *
 * <p>Debug/verification properties: {@code apklayer.script} ("ms:tap:x,y+ms:back+..."),
 * {@code apklayer.snapms} (snapshot times in ms), {@code apklayer.runms} (exit after),
 * {@code apklayer.snapshot} (PNG path prefix). {@code apklayer.data} + {@code apklayer.package}:
 * persistent app data root for that package.
 */
public final class AppHost implements Runnable {

    private final AndroidSandbox sandbox;
    private final Path apk;
    private final Path describe;
    private final Deque<ActivityController<? extends Activity>> stack = new ArrayDeque<>();
    private final Set<Activity> stopped = new HashSet<>();
    private Handler main;
    private boolean pausedByFocus, stoppedByMinimize; // lifecycle driven by the window state
    private String lastClipboard = ""; // last text exchanged with the shell, to avoid echo loops
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
        setUpApplication();
        Application app = RuntimeEnvironment.getApplication(); // created here (lazy), on persistent dirs
        if (describe != null) {
            describe(app, describe);
            return;
        }
        if (Boolean.getBoolean("apklayer.debugdata")) {
            SharedPreferences prefs = app.getSharedPreferences("apklayer_probe", 0);
            int launches = prefs.getInt("launches", 0) + 1;
            prefs.edit().putInt("launches", launches).commit();
            System.out.println("[apklayer] data: " + app.getDataDir() + " launches=" + launches);
        }

        // Simulator.start(): real draws and animations, a paused Choreographer driven by the loop.
        ShadowView.setUseRealViewAnimations(true);
        ShadowView.setUseRealDrawTraversals(true);
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(15));
        ShadowLog.setCaptureLogsEnabled(false);
        // Apps that fire a startup sound (beep, shutter, notification) must not die in
        // Robolectric's media shadow: an unregistered data source throws from prepare().
        // Robolectric maps any unmatched String/Uri/FD source to the "unknown" string
        // source, so registering a do-nothing MediaInfo for it makes prepare() succeed
        // silently until the real media backend lands (PLAN P5).
        // A catch-all provider answers any data source with a zero-duration, event-free
        // MediaInfo — like a codec that accepts the file but decodes nothing.
        ShadowMediaPlayer.setMediaInfoProvider(ds -> new ShadowMediaPlayer.MediaInfo(0, 0));

        Display display = app.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        int width = display.getWidth(), height = display.getHeight();
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
        scriptRunner = new ScriptRunner(input, this::hostClipboardIn, this::hostClipboardCopy);

        ShellClient shell = ShellClient.connect(app.getPackageName());
        if (shell == null) {
            System.out.println("[apklayer] no apklayer-shell: headless");
        } else {
            try {
                shell.createWindow(width, height, label(app));
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
        ShellFrameListener frames = new ShellFrameListener(shell);
        this.shell = shell;
        app.getSystemService(android.content.ClipboardManager.class).addPrimaryClipChangedListener(this::onAppClipboardChanged);
        ScreenUpdateHandler.create(frames).requestStartUi();

        launch(launcherIntent(app));
        // Like Simulator.postMotionEvent: an off-screen move so the first frame is drawn.
        input.inject(MotionEvent.obtain(0, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, width, height, 0));
        loop(shell, frames);
    }

    /** Robolectric simulator AppLoader: application state straight from the APK. */
    private void setUpApplication() {
        AndroidManifest manifest = new AndroidManifest(null, null, null, null, "", apk);
        Config config = new Config.Builder()
                .setSdk(Integer.getInteger("apklayer.sdk", 36))
                .setQualifiers(System.getProperty("apklayer.qualifiers", "w480dp-h800dp"))
                .build();
        FixedConfiguration configuration = FixedConfiguration.newBuilder()
                .put(ConscryptMode.Mode.class, ConscryptMode.Mode.OFF)
                .put(LooperMode.Mode.class, LooperMode.Mode.PAUSED)
                // Lazy: the package and its storage are set up first, so the data dirs can be
                // made persistent before Application.onCreate runs (persistDataDirs).
                .put(LazyApplication.LazyLoad.class, LazyApplication.LazyLoad.ON)
                .put(GraphicsMode.Mode.class, GraphicsMode.Mode.NATIVE)
                .put(SQLiteMode.Mode.class, SQLiteMode.Mode.NATIVE)
                .put(Config.class, config)
                .build();
        sandbox.getTestEnvironment().setUpApplicationState("apklayer", configuration, manifest);
        persistDataDirs();
    }

    /**
     * PLAN D20: Robolectric creates the app's data dirs ({@code <package>-dataDir}, {@code
     * userDataDir}, {@code deviceDataDir}) in its per-run temp directory when the (lazy)
     * Application is created, and deletes them at exit. They are pre-created here as links into
     * the persistent per-app tree ({@code apklayer.data}); TempDirectory.createIfNotExists keeps
     * an existing entry, and the exit cleanup does not follow links.
     */
    private static void persistDataDirs() {
        String root = System.getProperty("apklayer.data");
        String pkg = System.getProperty("apklayer.package");
        if (root == null || pkg == null) return; // ephemeral (headless checks)
        Path temp = RuntimeEnvironment.getTempDirectory().getBasePath();
        String[][] links = {{pkg + "-dataDir", "app"}, {"userDataDir", "user"}, {"deviceDataDir", "device"}};
        try {
            for (String[] link : links) {
                Path dir = temp.resolve(link[0]);
                if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) continue;
                Files.createSymbolicLink(dir, Files.createDirectories(Path.of(root, link[1])));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ResolveInfo launcher(Application app) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.getPackageName());
        List<ResolveInfo> found = app.getPackageManager().queryIntentActivities(query, 0);
        if (found.isEmpty()) throw new IllegalStateException("no launcher activity in " + app.getPackageName());
        return found.get(0);
    }

    private static Intent launcherIntent(Application app) {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(app.getPackageName(), launcher(app).activityInfo.name);
    }

    /** What a launcher shows: the launcher activity's label (falls back to the app's). */
    private static String label(Application app) {
        return String.valueOf(launcher(app).loadLabel(app.getPackageManager()));
    }

    /**
     * Install-time metadata (PLAN D19): info.properties plus the launcher icon rendered by the
     * framework's own drawable code (adaptive icons included) as a 256 px PNG.
     */
    private static void describe(Application app, Path dir) {
        try {
            PackageManager pm = app.getPackageManager();
            ResolveInfo launcher = launcher(app);
            PackageInfo pkg = pm.getPackageInfo(app.getPackageName(), 0);
            Properties info = new Properties();
            info.setProperty("package", app.getPackageName());
            info.setProperty("label", label(app));
            info.setProperty("launcher", launcher.activityInfo.name);
            info.setProperty("versionName", String.valueOf(pkg.versionName));
            info.setProperty("versionCode", String.valueOf(pkg.getLongVersionCode()));
            info.setProperty("targetSdk", String.valueOf(app.getApplicationInfo().targetSdkVersion));
            Files.createDirectories(dir);
            try (Writer out = Files.newBufferedWriter(dir.resolve("info.properties"))) {
                info.store(out, "apklayer install metadata");
            }

            int size = 256;
            Drawable icon = launcher.loadIcon(pm);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, size, size);
            icon.draw(new Canvas(bitmap));
            int[] px = new int[size * size];
            bitmap.getPixels(px, 0, size, 0, 0, size, size);
            BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, size, size, px, 0, size);
            ImageIO.write(img, "png", dir.resolve("icon.png").toFile());
            System.out.println("[apklayer] described " + app.getPackageName() + " (" + info.getProperty("label") + ")");
        } catch (IOException | PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /** Simulator.loop(), plus host input, navigation and the verification hooks. */
    private void loop(ShellClient shell, ShellFrameListener frames) {
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
        TreeMap<Long, String> snaps = new TreeMap<>();
        for (String ms : System.getProperty("apklayer.snapms", "").split(",")) {
            if (!ms.isEmpty()) snaps.put(Long.parseLong(ms), ms);
        }
        // runms must be honoured in headless mode too (corpus harness schedules snapshots
        // and taps at absolute times and relies on the run ending on schedule).
        // Headless runs must advance PAUSED clocks by the scheduled deadline (Simulator
        // does this with ShadowSystemClock::advanceTo + idleFor); wall-clock waiting only
        // works with a live window (shell mode), where real time drives the frame clock.
        boolean pausedMode = shell == null;
        long runMs = Long.getLong("apklayer.runms", pausedMode ? 3000 : Long.MAX_VALUE);
        String snapPrefix = System.getProperty("apklayer.snapshot", "/tmp/apklayer-frame");
        // Schedule clock: in headless (PAUSED) mode the sandbox clock is stepped by the loop
        // below, so schedule script/snapshot times on it — a "6 s" run then completes in well
        // under a second of real time. In shell mode real time drives everything.
        long start = pausedMode ? SystemClock.uptimeMillis() : System.nanoTime() / 1_000_000;
        long lastSnapshotAt = -1; // dedupe snapshot times that land in the same loop iteration

        try {
            while (!stack.isEmpty()) {
                long now = System.nanoTime();
                long elapsed = (pausedMode ? SystemClock.uptimeMillis() : System.nanoTime() / 1_000_000) - start;
                // exit when the run time is up (or the window closed) and every scheduled
                // snapshot/script step has fired — the corpus harness relies on the final
                // snapshot at runms actually being taken.
                if (script.isEmpty() && snaps.isEmpty()
                        && (elapsed >= runMs || (shell != null && shell.closed())))
                    break;

                for (int[] ev; shell != null && (ev = shell.poll()) != null; ) input.onHostEvent(ev);
                for (String clip; shell != null && (clip = shell.pollClipboard()) != null; ) hostClipboardIn(clip);
                while (!script.isEmpty() && script.firstKey() <= elapsed) scriptRunner.runStep(script.pollFirstEntry().getValue());
                while (!snaps.isEmpty() && snaps.firstKey() <= elapsed) {
                    long sMs = snaps.firstKey();
                    if (sMs == lastSnapshotAt) { // same instant already captured this iteration
                        snaps.pollFirstEntry();
                        continue;
                    }
                    lastSnapshotAt = sMs;
                    frames.snapshot(snapPrefix + "-" + snaps.pollFirstEntry().getValue() + "ms.png");
                    if (Boolean.getBoolean("apklayer.debugwindows")) HostWindows.dumpWindows();
                }

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
                    ShadowSystemClock.advanceBy(Duration.ofNanos(System.nanoTime() - now));
                    looper.idle();
                }
                followNavigation();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("[apklayer] exit: frames=" + frames.frames() + " depth=" + stack.size());
    }

    /** Builds and fully starts the activity, as the simulator does (Robolectric.setupActivity). */
    private void launch(Intent intent) {
        Class<? extends Activity> cls;
        try {
            cls = Class.forName(intent.getComponent() != null ? intent.getComponent().getClassName()
                    : RuntimeEnvironment.getApplication().getPackageManager()
                            .resolveActivity(intent, 0).activityInfo.name).asSubclass(Activity.class);
        } catch (ClassNotFoundException | NullPointerException e) {
            // Android would crash the app here; on the desktop the app stays on its current screen.
            System.out.println("[apklayer] cannot start " + intent + ": " + e);
            return;
        }
        ActivityController<? extends Activity> previous = stack.peek();
        if (previous != null) previous.pause();
        ActivityController<? extends Activity> c = Robolectric.buildActivity(cls, intent).setup();
        // As in Android, the previous activity is stopped only once an opaque activity covers it;
        // behind a translucent one (dialog-style) it stays paused and visible.
        if (previous != null && !isTranslucent(c.get())) {
            setVisible(previous.stop(), false);
            stopped.add(previous.get());
        }
        stack.push(c);
        // An activity that adds a window in onCreate (e.g. a dialog) otherwise never records its
        // own decor's first draw, and the composed screen would show nothing behind the dialog.
        c.get().getWindow().getDecorView().invalidate();
        System.out.println("[apklayer] started " + c.get().getClass().getName() + " depth=" + stack.size());
    }

    /** Follows the app's startActivity() calls and finish() of the top activity. */
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

    /** The system selection changed (from the shell): make it the app's primary clip. */
    private void hostClipboardIn(String text) {
        if (text.equals(lastClipboard)) return;
        lastClipboard = text;
        System.out.println("[apklayer] clipboard from the system (" + text.length() + " chars)");
        android.content.ClipboardManager cm =
                RuntimeEnvironment.getApplication().getSystemService(android.content.ClipboardManager.class);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("apklayer", text));
    }

    /** The app changed its primary clip: hand the text to the system selection. */
    private void onAppClipboardChanged() {
        android.content.ClipboardManager cm =
                RuntimeEnvironment.getApplication().getSystemService(android.content.ClipboardManager.class);
        android.content.ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(RuntimeEnvironment.getApplication());
        if (text == null || text.length() == 0 || text.toString().equals(lastClipboard)) return;
        lastClipboard = text.toString();
        System.out.println("[apklayer] clipboard to the system (" + text.length() + " chars)");
        if (shell != null) shell.sendClipboard(lastClipboard);
    }

    /** Script step: the app itself copies text. */
    private void hostClipboardCopy(String text) {
        RuntimeEnvironment.getApplication().getSystemService(android.content.ClipboardManager.class)
                .setPrimaryClip(android.content.ClipData.newPlainText("apklayer", text));
    }

    private void hostResize(int w, int h, int scalePct) {
        int dpi = 160 * scalePct / 100;
        int wdp = Math.max(1, w), hdp = Math.max(1, h);
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
