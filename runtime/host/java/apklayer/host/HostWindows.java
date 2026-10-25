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

/** Queries and routing over the app's windows (WindowManagerGlobal): which window/view gets input. */
final class HostWindows {
    private HostWindows() {}

    /** Debug: the window list as WindowManagerGlobal holds it. */
    static void dumpWindows() {
        Object wmg = android.view.WindowManagerGlobal.getInstance();
        List<View> views = org.robolectric.util.ReflectionHelpers.getField(wmg, "mViews");
        for (View v : views) {
            System.out.println("[apklayer]   window " + v.getClass().getSimpleName() + " " + v.getWidth() + "x"
                    + v.getHeight() + " vis=" + v.getVisibility() + " attached=" + v.isAttachedToWindow()
                    + " ctx=" + v.getContext().getClass().getSimpleName());
        }
    }

    /**
     * The window an input event should go to: among the visible, attached windows (containing the
     * point when x >= 0), the highest window type wins (dialogs and popups sit above the activity
     * window), then the most recently added.
     */
    static View topWindow(List<View> views, int x, int y) {
        View best = null;
        int bestType = -1;
        for (View v : views) {
            if (v.getVisibility() != View.VISIBLE || !v.isAttachedToWindow()) continue;
            if (x >= 0) {
                int[] loc = new int[2];
                v.getLocationOnScreen(loc);
                if (x < loc[0] || y < loc[1] || x >= loc[0] + v.getWidth() || y >= loc[1] + v.getHeight()) continue;
            }
            int type = v.getLayoutParams() instanceof android.view.WindowManager.LayoutParams
                    ? ((android.view.WindowManager.LayoutParams) v.getLayoutParams()).type : 0;
            if (type >= bestType) { // >=: later windows win ties
                best = v;
                bestType = type;
            }
        }
        return best;
    }

    /**
     * Wheel events: hit-test the topmost visible window ourselves and offer the event to the view
     * under the pointer and then its ancestors (what Android's generic-pointer routing does).
     * UiAutomation's injector offers them to the first touch-modal root instead, which can be a
     * stopped activity's window, and its dispatch doesn't reach nested scrollers here.
     */
    static void dispatchToTopWindow(MotionEvent e) {
        Object wmg = android.view.WindowManagerGlobal.getInstance();
        List<View> views = org.robolectric.util.ReflectionHelpers.getField(wmg, "mViews");
        View root = topWindow(views, (int) e.getX(), (int) e.getY());
        if (root == null) return;
        View target = viewAt(root, (int) e.getX(), (int) e.getY());
        boolean debug = Boolean.getBoolean("apklayer.debugwheel");
        for (View v = target; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v.onGenericMotionEvent(e)) {
                if (debug) System.out.println("[apklayer] wheel handled by " + v.getClass().getSimpleName());
                return;
            }
        }
        if (debug) System.out.println("[apklayer] wheel at " + e.getX() + "," + e.getY() + ": no view handled it (target "
                + (target == null ? "none" : target.getClass().getSimpleName()) + ")");
    }

    /** Deepest visible view containing the screen point (x, y), topmost child first. */
    static View viewAt(View v, int x, int y) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        if (v.getVisibility() != View.VISIBLE || x < loc[0] || y < loc[1] || x >= loc[0] + v.getWidth()
                || y >= loc[1] + v.getHeight()) return null;
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                View hit = viewAt(g.getChildAt(i), x, y);
                if (hit != null) return hit;
            }
        }
        return v;
    }

    /** True if the focused view of the topmost window takes text input. */
    static boolean focusIsTextEditor() {
        Object wmg = android.view.WindowManagerGlobal.getInstance();
        List<View> views = org.robolectric.util.ReflectionHelpers.getField(wmg, "mViews");
        for (int i = views.size() - 1; i >= 0; i--) {
            View f = views.get(i).findFocus();
            if (f != null) return f.onCheckIsTextEditor();
        }
        return false;
    }

    static boolean isNavigationKey(int code) {
        return code == KeyEvent.KEYCODE_TAB || (code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_CENTER)
                || code == KeyEvent.KEYCODE_PAGE_UP || code == KeyEvent.KEYCODE_PAGE_DOWN
                || code == KeyEvent.KEYCODE_MOVE_HOME || code == KeyEvent.KEYCODE_MOVE_END;
    }

    /** Center of the first clickable view in the topmost window (corpus tap, PLAN P3.2). */
    static int[] firstClickableCenter() {
        Object wmg = android.view.WindowManagerGlobal.getInstance();
        List<View> views = org.robolectric.util.ReflectionHelpers.getField(wmg, "mViews");
        int[] loc = new int[2];
        for (View root : views) {
            View v = findClickable(root);
            if (v != null) {
                v.getLocationOnScreen(loc);
                return new int[] { loc[0] + v.getWidth() / 2, loc[1] + v.getHeight() / 2 };
            }
        }
        return null;
    }

    static View findClickable(View v) {
        if (v.getVisibility() != View.VISIBLE) return null;
        // The real tap target of list screens is the item view, not the clickable
        // AdapterView (which is only clickable for empty-space/long-press semantics).
        // Descend first; prefer the smallest clickable that covers less than half the
        // screen (a row/button), else the largest clickable overall.
        if (v instanceof android.widget.AdapterView<?> a) {
            View child = firstClickableChild(a);
            if (child != null) return child;
        }
        if (v.isClickable() && v.isEnabled() && v.getWidth() > 0 && v.getHeight() > 0) {
            View best = v;
            if (v instanceof android.view.ViewGroup g) {
                View c = firstClickableChild(g);
                if (c != null && (long) c.getWidth() * c.getHeight() < (long) v.getWidth() * v.getHeight() / 2)
                    best = c;
            }
            return best;
        }
        return firstClickableChild(v);
    }

    static View firstClickableChild(View v) {
        if (v instanceof android.view.ViewGroup g) {
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = findClickable(g.getChildAt(i));
                if (c != null) return c;
            }
        }
        return null;
    }

    static void dumpClickables() {
        Object wmg = android.view.WindowManagerGlobal.getInstance();
        List<View> roots = org.robolectric.util.ReflectionHelpers.getField(wmg, "mViews");
        int[] loc = new int[2];
        int n = 0;
        for (View root : roots) {
            java.util.ArrayDeque<View> q = new java.util.ArrayDeque<>();
            q.add(root);
            while (!q.isEmpty() && n < 25) {
                View v = q.poll();
                if (v.getVisibility() != View.VISIBLE) continue;
                if (v.isClickable() && v.getWidth() > 0) {
                    v.getLocationOnScreen(loc);
                    System.out.println("[apklayer]   click " + v.getClass().getSimpleName() + " \""
                            + (v instanceof android.widget.TextView t ? t.getText() : "") + "\" ["
                            + loc[0] + "," + loc[1] + " " + v.getWidth() + "x" + v.getHeight() + "]");
                    n++;
                }
                if (v instanceof android.view.ViewGroup g) {
                    for (int i = 0; i < g.getChildCount(); i++) q.add(g.getChildAt(i));
                }
            }
        }
    }

    private static boolean hoverActive;

    /** Hover (mouse move without a button): offered to the topmost window under the pointer. */
    static void dispatchHover(MotionEvent e) {
        Object wmg = android.view.WindowManagerGlobal.getInstance();
        List<View> views = org.robolectric.util.ReflectionHelpers.getField(wmg, "mViews");
        View root = topWindow(views, (int) e.getX(), (int) e.getY());
        if (root == null) return;
        if (!hoverActive) { // ViewRootImpl starts a hover stream with ENTER
            MotionEvent enter = MotionEvent.obtain(e);
            enter.setAction(MotionEvent.ACTION_HOVER_ENTER);
            root.dispatchGenericMotionEvent(enter);
            hoverActive = true;
        }
        root.dispatchGenericMotionEvent(e);
        if (Boolean.getBoolean("apklayer.debugwheel")) {
            View t = viewAt(root, (int) e.getX(), (int) e.getY());
            System.out.println("[apklayer] hover over " + (t == null ? "none" : t.getClass().getSimpleName() + " hovered=" + t.isHovered()));
        }
    }

    /** Right click: long press (and context menu) on the view under the pointer, or its ancestors. */
    static void longClickAt(int x, int y) {
        Object wmg = android.view.WindowManagerGlobal.getInstance();
        List<View> views = org.robolectric.util.ReflectionHelpers.getField(wmg, "mViews");
        View root = topWindow(views, x, y);
        if (root == null) return;
        for (View v = viewAt(root, x, y); v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v.isLongClickable() && v.performLongClick(x, y)) {
                if (Boolean.getBoolean("apklayer.debugwheel")) System.out.println("[apklayer] long click on " + v.getClass().getSimpleName());
                return;
            }
        }
        if (Boolean.getBoolean("apklayer.debugwheel")) System.out.println("[apklayer] right click at " + x + "," + y + ": no long-clickable view");
    }
}
