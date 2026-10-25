package apklayer.host;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.app.Activity;
import android.app.Application;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
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
 * {@code apklayer.snapshot} (PNG path prefix).
 */
public final class AppHost implements Runnable {
    private static final int KEY_ESC = 1, KEY_BACKSPACE = 14; // evdev codes from the shell

    private final AndroidSandbox sandbox;
    private final Path apk;
    private final Deque<ActivityController<? extends Activity>> stack = new ArrayDeque<>();
    private final Set<Activity> stopped = new HashSet<>();
    private Handler main;
    private UiAutomation uiAutomation;
    private long downTime;

    public AppHost(AndroidSandbox sandbox, Path apk) {
        this.sandbox = sandbox;
        this.apk = apk;
    }

    @Override
    public void run() {
        Thread.currentThread().setContextClassLoader(sandbox.getRobolectricClassLoader());
        setUpApplication();
        Application app = RuntimeEnvironment.getApplication();

        // Simulator.start(): real draws and animations, a paused Choreographer driven by the loop.
        ShadowView.setUseRealViewAnimations(true);
        ShadowView.setUseRealDrawTraversals(true);
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(15));
        ShadowLog.setCaptureLogsEnabled(false);

        Display display = app.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        int width = display.getWidth(), height = display.getHeight();
        main = new Handler(Looper.getMainLooper());
        uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        HostWindow window = HostWindow.get(width, height);
        if (window == null) System.out.println("[apklayer] no host window: headless");
        ShellFrameListener frames = new ShellFrameListener(window);
        ScreenUpdateHandler.create(frames).requestStartUi();

        launch(launcherIntent(app));
        // Like Simulator.postMotionEvent: an off-screen move so the first frame is drawn.
        inject(MotionEvent.obtain(0, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, width, height, 0));
        loop(window, frames);
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
                .put(LazyApplication.LazyLoad.class, LazyApplication.LazyLoad.OFF)
                .put(GraphicsMode.Mode.class, GraphicsMode.Mode.NATIVE)
                .put(SQLiteMode.Mode.class, SQLiteMode.Mode.NATIVE)
                .put(Config.class, config)
                .build();
        sandbox.getTestEnvironment().setUpApplicationState("apklayer", configuration, manifest);
    }

    private static Intent launcherIntent(Application app) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = app.getPackageManager().queryIntentActivities(query, 0);
        if (found.isEmpty()) throw new IllegalStateException("no launcher activity in " + app.getPackageName());
        return query.setClassName(app.getPackageName(), found.get(0).activityInfo.name);
    }

    /** Simulator.loop(), plus host input, navigation and the verification hooks. */
    private void loop(HostWindow window, ShellFrameListener frames) {
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
            if (!step.isEmpty()) script.put(Long.parseLong(step.split(":", 2)[0]), step.split(":", 2)[1]);
        }
        TreeMap<Long, String> snaps = new TreeMap<>();
        for (String ms : System.getProperty("apklayer.snapms", "").split(",")) {
            if (!ms.isEmpty()) snaps.put(Long.parseLong(ms), ms);
        }
        long runMs = Long.getLong("apklayer.runms", window == null ? 3000 : Long.MAX_VALUE);
        String snapPrefix = System.getProperty("apklayer.snapshot", "/tmp/apklayer-frame");
        long start = System.nanoTime();
        int[] ev = new int[4];

        try {
            while (!stack.isEmpty()) {
                long now = System.nanoTime();
                long elapsed = (now - start) / 1_000_000;
                if (elapsed >= runMs || (window != null && window.shouldClose())) break;

                while (window != null && window.nextEvent(ev)) onHostEvent(ev);
                while (!script.isEmpty() && script.firstKey() <= elapsed) runStep(script.pollFirstEntry().getValue());
                while (!snaps.isEmpty() && snaps.firstKey() <= elapsed) {
                    frames.snapshot(snapPrefix + "-" + snaps.pollFirstEntry().getValue() + "ms.png");
                    if (Boolean.getBoolean("apklayer.debugwindows")) dumpWindows();
                }

                long nextTask = looper.getNextScheduledTaskTime().toMillis();
                long nextVsync = ShadowChoreographer.getNextVsyncTime();
                long timeout = nextTask == 0 || nextVsync == 0 ? max(nextTask, nextVsync) : min(nextTask, nextVsync);
                // Bounded so host input and the scripted steps are serviced while idle.
                long wait = timeout == 0 ? 8 : max(1, min(8, timeout - SystemClock.uptimeMillis()));
                looper.poll(wait);
                ShadowSystemClock.advanceBy(Duration.ofNanos(System.nanoTime() - now));
                looper.idle();
                followNavigation();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("[apklayer] exit: frames=" + frames.frames() + " depth=" + stack.size());
    }

    /** Builds and fully starts the activity, as the simulator does (Robolectric.setupActivity). */
    private void launch(Intent intent) {
        ActivityController<? extends Activity> previous = stack.peek();
        if (previous != null) previous.pause();
        Class<? extends Activity> cls;
        try {
            cls = Class.forName(intent.getComponent() != null ? intent.getComponent().getClassName()
                    : RuntimeEnvironment.getApplication().getPackageManager()
                            .resolveActivity(intent, 0).activityInfo.name).asSubclass(Activity.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
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

    /** Debug: the window list as WindowManagerGlobal holds it. */
    private static void dumpWindows() {
        Object wmg = android.view.WindowManagerGlobal.getInstance();
        List<View> views = org.robolectric.util.ReflectionHelpers.getField(wmg, "mViews");
        for (View v : views) {
            System.out.println("[apklayer]   window " + v.getClass().getSimpleName() + " " + v.getWidth() + "x"
                    + v.getHeight() + " vis=" + v.getVisibility() + " attached=" + v.isAttachedToWindow()
                    + " ctx=" + v.getContext().getClass().getSimpleName());
        }
    }

    /** Host input {type, a, b, 0} (bridge/shell/present.c): pointer -> MotionEvent, Esc/Backspace -> Back. */
    private void onHostEvent(int[] ev) {
        long now = SystemClock.uptimeMillis();
        if (ev[0] <= 2) {
            if (ev[0] == MotionEvent.ACTION_DOWN) downTime = now;
            inject(MotionEvent.obtain(downTime, now, ev[0], ev[1], ev[2], 0));
        } else if (ev[0] == 100 && (ev[1] == KEY_ESC || ev[1] == KEY_BACKSPACE)) {
            inject(new KeyEvent(now, now, ev[2] == 1 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_BACK, 0));
        }
    }

    private void runStep(String step) {
        System.out.println("[apklayer] script: " + step);
        long now = SystemClock.uptimeMillis();
        if (step.startsWith("tap:")) {
            String[] xy = step.substring(4).split(",");
            int x = Integer.parseInt(xy[0]), y = Integer.parseInt(xy[1]);
            inject(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
            inject(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0));
        } else if (step.equals("back")) {
            inject(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0));
            inject(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0));
        }
    }

    /** Input goes through UiAutomation, which routes it to the right window (like the simulator). */
    private void inject(InputEvent event) {
        main.post(() -> uiAutomation.injectInputEvent(event, true));
    }
}
