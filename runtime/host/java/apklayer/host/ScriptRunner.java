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

/** Scripted verification steps (-Dapklayer.script): tap, key, wheel, configure, back. */
final class ScriptRunner {
    private final HostInput input;
    /** Steps provided by the host itself: name -> action(argument), e.g. clipin, clipcopy, orient. */
    private final java.util.Map<String, java.util.function.Consumer<String>> hostSteps;

    ScriptRunner(HostInput input, java.util.Map<String, java.util.function.Consumer<String>> hostSteps) {
        this.input = input;
        this.hostSteps = hostSteps;
    }

    void runStep(String step) {
        for (String s : step.split(";")) { // multiple steps scheduled at the same instant
            if (!s.isEmpty()) runOneStep(s);
        }
    }

    void runOneStep(String step) {
        System.out.println("[apklayer] script: " + step);
        long now = SystemClock.uptimeMillis();
        if (step.startsWith("tap:")) {
            String xy = step.substring(4);
            int x, y;
            if (xy.equals("first")) {
                int[] c = HostWindows.firstClickableCenter();
                if (c == null) {
                    System.out.println("[apklayer] script: no clickable view");
                    return;
                }
                x = c[0];
                y = c[1];
            } else {
                String[] p = xy.split(",");
                x = Integer.parseInt(p[0]);
                y = Integer.parseInt(p[1]);
            }
            // DOWN/UP at the same uptimeMillis can be swallowed by tap-vs-scroll tracking;
            // put UP 50 ms in the future, like the shell path does.
            System.out.println("[apklayer] tap injected at " + x + "," + y);
            if (Boolean.getBoolean("apklayer.debugclickables")) HostWindows.dumpClickables();
            input.inject(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0));
            input.inject(MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0));
        } else if (step.startsWith("key:")) { // key:<android keycode>[,unicode]: down+up via the host key path
            String[] p = step.substring(4).split(",");
            int code = Integer.parseInt(p[0]), uni = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            input.onHostEvent(new int[] {12, 0, code, uni, 0});
            input.onHostEvent(new int[] {12, 1, code, uni, 0});
        } else if (step.startsWith("keydown:") || step.startsWith("keyup:")) { // one half of key:, as separate loop turns
            boolean down = step.startsWith("keydown:");
            int code = Integer.parseInt(step.substring(step.indexOf(':') + 1));
            input.onHostEvent(new int[] {12, down ? 0 : 1, code, 0, 0});
        } else if (step.startsWith("touch:")) { // touch:<action 0 down/1 up/2 move/3 cancel>,<finger>,<x>,<y>
            String[] p = step.substring(6).split(",");
            input.onHostEvent(new int[] {16, Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                    p.length > 2 ? Integer.parseInt(p[2]) : 0, p.length > 3 ? Integer.parseInt(p[3]) : 0});
        } else if (step.startsWith("rclick:")) { // rclick:x,y right click = long press
            String[] p = step.substring(7).split(",");
            input.onHostEvent(new int[] {4, Integer.parseInt(p[0]), Integer.parseInt(p[1])});
        } else if (step.startsWith("hover:")) { // hover:x,y mouse move without a button
            String[] p = step.substring(6).split(",");
            input.onHostEvent(new int[] {3, Integer.parseInt(p[0]), Integer.parseInt(p[1])});
        } else if (hostSteps.containsKey(step.substring(0, Math.max(0, step.indexOf(':'))))) {
            hostSteps.get(step.substring(0, step.indexOf(':'))).accept(step.substring(step.indexOf(':') + 1));
        } else if (step.startsWith("state:")) { // state:<flags> (1 active, 2 minimized): window focus/minimize from the shell
            input.onHostEvent(new int[] {15, Integer.parseInt(step.substring(6))});
        } else if (step.startsWith("wheel:")) { // wheel:x,y,vnotches*1000 (+ = up): as the shell sends it
            String[] p = step.substring(6).split(",");
            input.onHostEvent(new int[] {13, Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]), 0});
        } else if (step.startsWith("configure:")) { // configure:w,h[,scale%]: logical size like a compositor resize
            String[] p = step.substring(10).split(",");
            input.onHostEvent(new int[] {14, Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                    p.length > 2 ? Integer.parseInt(p[2]) : 100});
        } else if (step.equals("threads")) { // every Java thread's stack, to see what an idle app waits on
            Thread.getAllStackTraces().forEach((t, st) -> {
                System.out.println("[apklayer] thread \"" + t.getName() + "\" " + t.getState());
                for (StackTraceElement e : st) System.out.println("    at " + e);
            });
        } else if (step.startsWith("field:")) { // field:<class>#<static field>[.<field>...]: an app's state by reflection (diagnostics)
            String[] p = step.substring(6).split("#");
            try {
                Object o = null;
                Class<?> k = Class.forName(p[0], false, org.robolectric.RuntimeEnvironment.getApplication().getClassLoader());
                for (String name : p[1].split("\\.")) {
                    java.lang.reflect.Field f = null;
                    for (Class<?> c = o == null ? k : o.getClass(); f == null && c != null; c = c.getSuperclass())
                        try { f = c.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
                    if (f == null) throw new NoSuchFieldException(name + " in " + (o == null ? k : o.getClass()).getName());
                    f.setAccessible(true);
                    o = f.get(o);
                    System.out.println("[apklayer] field " + name + " = " + (o == null ? null : o.getClass().getName() + ": " + o));
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                System.out.println("[apklayer] field " + step + ": " + e);
            }
        } else if (step.startsWith("immersive:")) { // immersive:0|1: the top window's WindowInsetsController hides (1) or shows (0) the navigation bar
            java.util.List<android.view.View> views = org.robolectric.util.ReflectionHelpers.getField(android.view.WindowManagerGlobal.getInstance(), "mViews");
            android.view.WindowInsetsController c = views.get(views.size() - 1).getWindowInsetsController();
            if (step.endsWith("1")) c.hide(android.view.WindowInsets.Type.navigationBars());
            else c.show(android.view.WindowInsets.Type.navigationBars());
            System.out.println("[apklayer] immersive " + step.substring(10));
        } else if (step.startsWith("fullscreen:")) { // fullscreen:1|0 hide/show(systemBars()), 3|2 the legacy flags (SYSTEM_UI_FLAG_FULLSCREEN.., FLAG_FULLSCREEN) set/cleared
            android.view.WindowManagerGlobal g = android.view.WindowManagerGlobal.getInstance();
            java.util.List<android.view.View> views = org.robolectric.util.ReflectionHelpers.getField(g, "mViews");
            java.util.List<android.view.WindowManager.LayoutParams> params = org.robolectric.util.ReflectionHelpers.getField(g, "mParams");
            android.view.View v = views.get(views.size() - 1);
            int mode = step.charAt(11) - '0';
            if (mode < 2) {
                if (mode == 1) v.getWindowInsetsController().hide(android.view.WindowInsets.Type.systemBars());
                else v.getWindowInsetsController().show(android.view.WindowInsets.Type.systemBars());
            } else {
                v.setSystemUiVisibility(mode == 3 ? android.view.View.SYSTEM_UI_FLAG_FULLSCREEN | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE : 0);
                android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams();
                lp.copyFrom(params.get(params.size() - 1));
                if (mode == 3) lp.flags |= android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN;
                else lp.flags &= ~android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN;
                g.updateViewLayout(v, lp);
            }
            System.out.println("[apklayer] fullscreen " + mode);
        } else if (step.equals("navinset")) { // the top window's navigation bar inset (bottom, px), as its app sees it
            java.util.List<android.view.View> views = org.robolectric.util.ReflectionHelpers.getField(android.view.WindowManagerGlobal.getInstance(), "mViews");
            android.view.WindowInsets wi = views.get(views.size() - 1).getRootWindowInsets();
            System.out.println("[apklayer] navinset " + (wi == null ? -1 : wi.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom));
        } else if (step.equals("back")) {
            input.inject(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0));
            input.inject(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0));
        }
    }
}
