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
 * Host input (PLAN P2): translates what apklayer-shell sends (pointer, xkb keys, wheel, configure)
 * into Android events for the sandbox, and injects them into the topmost window.
 */
final class HostInput {
    /** A compositor resize or scale change: logical size in dp, scale in percent. */
    interface ConfigureHandler {
        void configure(int w, int h, int scalePct);
    }

    private static final int KEY_ESC = 1, KEY_BACKSPACE = 14; // evdev codes from the shell
    private final Handler main;
    private final UiAutomation uiAutomation;
    private final ConfigureHandler onConfigure;
    private long downTime;
    private boolean backspaceIsBack; // the current Backspace press was turned into Back

    HostInput(Handler main, UiAutomation uiAutomation, ConfigureHandler onConfigure) {
        this.main = main;
        this.uiAutomation = uiAutomation;
        this.onConfigure = onConfigure;
    }

    /** Shell input {type, a, b, 0} (bridge/shell/shell.c): pointer -> MotionEvent, Esc/Backspace -> Back. */
    void onHostEvent(int[] ev) {
        long now = SystemClock.uptimeMillis();
        if (ev[0] <= 2) {
            if (ev[0] == MotionEvent.ACTION_DOWN) downTime = now;
            inject(MotionEvent.obtain(downTime, now, ev[0], ev[1], ev[2], 0));
        } else if (ev[0] == 12) { // key (xkb): {12, action, android keycode, unicode, meta}
            hostKey(now, ev[1], ev[2], ev[3], ev[4]);
        } else if (ev[0] == 13) { // wheel: {13, x, y, vscroll*1000, hscroll*1000}
            MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
            pp.id = 0;
            pp.toolType = MotionEvent.TOOL_TYPE_MOUSE;
            MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
            pc.x = ev[1];
            pc.y = ev[2];
            pc.setAxisValue(MotionEvent.AXIS_VSCROLL, ev[3] / 1000f);
            pc.setAxisValue(MotionEvent.AXIS_HSCROLL, ev[4] / 1000f);
            MotionEvent scroll = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1,
                    new MotionEvent.PointerProperties[] {pp}, new MotionEvent.PointerCoords[] {pc},
                    0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_MOUSE, 0);
            main.post(() -> HostWindows.dispatchToTopWindow(scroll));
        } else if (ev[0] == 14) { // configure: {14, logical w, logical h, scale*100}
            onConfigure.configure(ev[1], ev[2], ev[3]);
        } else if (ev[0] == 100 && (ev[1] == KEY_ESC || ev[1] == KEY_BACKSPACE)) {
            // no xkb keymap in the shell: raw evdev {100, code, pressed}
            inject(new KeyEvent(now, now, ev[2] == 1 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_BACK, 0));
        }
    }

    /** A key from the shell: action 0 = down / 1 = up, Android keycode, UTF-32 char (0 = none). */
    void hostKey(long now, int action, int code, int unicode, int meta) {
        if (Boolean.getBoolean("apklayer.debugkeys"))
            System.out.println("[apklayer] key action=" + action + " code=" + code + " uni=" + unicode + " meta=" + meta);
        int act = action == 0 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
        if (code == KeyEvent.KEYCODE_DEL) {
            // Desktop Backspace is also "go back" when nothing is being edited.
            if (act == KeyEvent.ACTION_DOWN && !backspaceIsBack) backspaceIsBack = !HostWindows.focusIsTextEditor();
            if (backspaceIsBack) {
                inject(new KeyEvent(now, now, act, KeyEvent.KEYCODE_BACK, 0));
                if (act == KeyEvent.ACTION_UP) backspaceIsBack = false;
                return;
            }
        }
        if (code == 0) { // no Android keycode (non-US characters, symbols): type the character
            if (act == KeyEvent.ACTION_DOWN && unicode > 0)
                inject(new KeyEvent(now, new String(Character.toChars(unicode)), 0, 0));
            return;
        }
        injectKey(new KeyEvent(now, now, act, code, 0, meta), HostWindows.isNavigationKey(code));
    }

    /**
     * Keys go through the topmost visible window's ViewRootImpl input pipeline, like a real input
     * channel: focus navigation (Tab, arrows), key shortcuts and the IME run there, whereas
     * UiAutomation's injector only calls decor.dispatchKeyEvent.
     */
    void injectKey(KeyEvent event, boolean navigation) {
        main.post(() -> {
            Object wmg = android.view.WindowManagerGlobal.getInstance();
            List<View> views = org.robolectric.util.ReflectionHelpers.getField(wmg, "mViews");
            View root = HostWindows.topWindow(views, -1, -1);
            android.view.ViewRootImpl impl = root == null ? null : root.getViewRootImpl();
            if (impl != null) {
                // A real window manager tells the root it has input focus; here nobody does
                // (and it is lost on relayout), and keyboard navigation leaves touch mode.
                if (!root.hasWindowFocus()) impl.windowFocusChanged(true);
                if (navigation && root.isInTouchMode()) {
                    org.robolectric.util.ReflectionHelpers.callInstanceMethod(impl, "ensureTouchModeLocally",
                            org.robolectric.util.ReflectionHelpers.ClassParameter.from(boolean.class, false));
                }
                impl.dispatchInputEvent(event);
                return;
            }
            uiAutomation.injectInputEvent(event, true);
        });
    }

    /** Input goes through UiAutomation, which routes it to the right window (like the simulator). */
    void inject(InputEvent event) {
        main.post(() -> uiAutomation.injectInputEvent(event, true));
    }

    /**
     * Inject an input event and wait until the main looper has finished dispatching it
     * (queue idle, no delayed work) — for scripted steps that must take effect before the
     * next snapshot. Returns after a bounded timeout if the app keeps the looper busy.
     */
    void injectSync(InputEvent event) {
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        main.post(() -> {
            uiAutomation.injectInputEvent(event, true);
            done.countDown();
        });
        long deadline = System.nanoTime() + 1_000_000_000L; // 1 s
        try {
            while (!done.await(2, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                if (System.nanoTime() > deadline) break;
                Thread.sleep(1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Let the main run loop (which owns the Choreographer frame clock) dispatch this
        // event and draw the result: the loop polls the looper and advances the vsync
        // clock itself, and the scripted step returns to it. Wait until the looper has
        // drained or a bounded timeout, so the next snapshot sees the resulting state.
        long drainDeadline = System.nanoTime() + 500_000_000L; // 500 ms
        ShadowPausedLooper rl = Shadow.extract(Looper.getMainLooper());
        try {
            while (rl.getNextScheduledTaskTime().toMillis() != 0) {
                if (System.nanoTime() > drainDeadline) break;
                Thread.sleep(2);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
