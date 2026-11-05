package apklayer.host;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A resized window reaches androidx GameActivity games as a new native window (APP_CMD_TERM_WINDOW, then APP_CMD_INIT_WINDOW).
 * GameActivity forwards a size change of its SurfaceView as APP_CMD_WINDOW_RESIZED, which games may ignore: OTR2 reads the window
 * size only at INIT_WINDOW and maps every touch by it, so after a maximize its picture (scaled by SurfaceFlinger) filled the window
 * while a touch at x landed at x x old/new. On Android such a resize crosses the app's size buckets (OTR2: sw600dp, large; not in
 * its configChanges) and relaunches the activity, which gives it a new window; the host resizes in place (HostConfiguration.resize),
 * so it hands GameActivity its own surface again once the size has settled (a drag resize re-announces once, at its end).
 */
final class NativeWindowResize {
    private NativeWindowResize() {}

    private static final long SETTLE_MS = 300;
    private static final Map<Activity, Rect> announced = new WeakHashMap<>();
    private static Handler main;
    private static final Runnable check = NativeWindowResize::check;
    private static List<? extends org.robolectric.android.controller.ActivityController<? extends Activity>> controllers = List.of();

    /** Before AppHost.hostResize applies a new size: remember each GameActivity's current surface size, then check once the size settles. */
    static void resizing(List<? extends org.robolectric.android.controller.ActivityController<? extends Activity>> stack) {
        if (main == null) main = new Handler(Looper.getMainLooper());
        controllers = stack;
        for (var c : stack) {
            Activity a = c.get();
            SurfaceView view = isGameActivity(a) ? surfaceView(a.getWindow().getDecorView()) : null;
            if (view != null && view.getHolder().getSurface().isValid()) announced.putIfAbsent(a, new Rect(view.getHolder().getSurfaceFrame()));
        }
        main.removeCallbacks(check);
        main.postDelayed(check, SETTLE_MS);
    }

    private static void check() {
        for (var c : controllers) {
            Activity a = c.get();
            Rect before = announced.get(a);
            SurfaceView view = before != null ? surfaceView(a.getWindow().getDecorView()) : null;
            if (view == null || a.isFinishing()) continue;
            SurfaceHolder holder = view.getHolder();
            Rect now = new Rect(holder.getSurfaceFrame());
            if (!holder.getSurface().isValid() || now.isEmpty() || (now.width() == before.width() && now.height() == before.height())) continue;
            announced.put(a, now);
            System.out.println("[apklayer] GameActivity window " + before.width() + "x" + before.height() + " -> " + now.width() + "x" + now.height()
                    + ": surface handed over again");
            SurfaceHolder.Callback game = (SurfaceHolder.Callback) a;
            game.surfaceDestroyed(holder);
            game.surfaceCreated(holder);
            game.surfaceChanged(holder, android.graphics.PixelFormat.RGBA_8888, now.width(), now.height());
        }
    }

    /** androidx GameActivity (com.google.androidgamesdk): the activity is its SurfaceView's holder callback. */
    private static boolean isGameActivity(Activity a) {
        if (!(a instanceof SurfaceHolder.Callback)) return false;
        for (Class<?> k = a.getClass(); k != null; k = k.getSuperclass())
            if (k.getName().equals("com.google.androidgamesdk.GameActivity")) return true;
        return false;
    }

    private static SurfaceView surfaceView(View v) {
        if (v instanceof SurfaceView s) return s;
        if (v instanceof ViewGroup g)
            for (int i = 0; i < g.getChildCount(); i++) {
                SurfaceView s = surfaceView(g.getChildAt(i));
                if (s != null) return s;
            }
        return null;
    }
}
