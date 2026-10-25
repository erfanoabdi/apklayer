package apklayer.rob;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * apklayer driver on the Robolectric method: unmodified framework (android-all-instrumented)
 * + shadows; the app is the dex2jvm-converted uibench classes; the manifest/resources come
 * from the real APK (binary resources mode, AGP's test_config.properties contract: the real
 * PackageParser reads the APK, so package/themes are unmodified). Frames: real View tree -> Canvas -> Bitmap ->
 * presented on the host Wayland window (driver-side, outside the sandbox).
 * Graphics mode comes from -Drobolectric.graphicsMode (LEGACY|NATIVE). Input from the host
 * window drives the top activity; startActivity()/finish() are followed with a back stack.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 27, qualifiers = "w480dp-h800dp")
public final class RunUiBench {

    private static final int W = 480, H = 800;
    private static final int KEY_ESC = 1, KEY_BACKSPACE = 14; // evdev codes

    private final Deque<ActivityController<?>> stack = new ArrayDeque<>();

    @Test(timeout = 0)
    public void runUi() throws Exception {
        launch(new Intent().setClassName("com.android.test.uibench",
                "com.android.test.uibench.MainActivity"));

        HostWindow win = HostWindow.get(W, H);
        if (win == null) System.out.println("[apklayer-rob] no host window: headless");

        // Scripted input "frame:tap:x,y+frame:back+..." and snapshots "frame,frame,..." — for
        // headless verification; they go through the same dispatch path as real input.
        Map<Integer, String> script = new HashMap<>();
        for (String step : System.getProperty("apklayer.script", "").split("[+;]")) {
            if (!step.isEmpty()) script.put(Integer.parseInt(step.split(":", 2)[0]), step.split(":", 2)[1]);
        }
        Set<Integer> snaps = new HashSet<>();
        for (String f : System.getProperty("apklayer.snapframes", "30").split(",")) snaps.add(Integer.parseInt(f));
        int lastFrame = Integer.getInteger("apklayer.frames", win != null ? Integer.MAX_VALUE : 30);

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int[] px = new int[W * H];
        int[] ev = new int[4];
        long downTime = 0;

        int frames = 0;
        while (!stack.isEmpty() && frames < lastFrame && (win == null || !win.shouldClose())) {
            Activity act = stack.peek().get();
            while (win != null && win.nextEvent(ev)) downTime = dispatch(act, ev, downTime);
            if (act != stack.peek().get()) continue;
            String step = script.remove(frames);
            if (step != null) {
                System.out.println("[apklayer-rob] script @" + frames + ": " + step);
                if (step.startsWith("tap:")) {
                    String[] xy = step.substring(4).split(",");
                    int x = Integer.parseInt(xy[0]), y = Integer.parseInt(xy[1]);
                    downTime = dispatch(act, new int[] {0, x, y, 0}, downTime);
                    downTime = dispatch(act, new int[] {1, x, y, 0}, downTime);
                } else if (step.equals("back")) {
                    dispatch(act, new int[] {100, KEY_ESC, 1, 0}, downTime);
                }
            }
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS);
            followNavigation(act);
            if (stack.isEmpty()) break;

            canvas.drawColor(Color.BLACK);
            for (HostSurface hs : windows(stack.peek().get())) {
                if ((hs.lp.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
                    canvas.drawColor(Color.argb((int) (hs.lp.dimAmount * 255), 0, 0, 0));
                }
                canvas.save();
                canvas.translate(hs.frame.left, hs.frame.top);
                hs.view.draw(canvas);
                canvas.restore();
            }
            bmp.getPixels(px, 0, W, 0, 0, W, H);
            if (win != null) win.present(px, W, H);
            frames++;
            if (snaps.contains(frames)) {
                dumpPng(px, W, H, System.getProperty("apklayer.snapshot", "/tmp/apklayer-frame")
                        + "-" + frames + ".png");
            }
            if (win != null) Thread.sleep(16);
        }
        System.out.println("[apklayer-rob] DONE frames=" + frames);
    }

    /** Builds and fully starts the activity named by the intent, pausing the current one. */
    private void launch(Intent intent) throws Exception {
        String cls = intent.getComponent() != null ? intent.getComponent().getClassName()
                : RuntimeEnvironment.getApplication().getPackageManager()
                        .resolveActivity(intent, 0).activityInfo.name;
        if (!stack.isEmpty()) stack.peek().pause().stop();
        @SuppressWarnings({"unchecked", "rawtypes"})
        ActivityController<?> c = Robolectric.buildActivity(
                (Class) getClass().getClassLoader().loadClass(cls), intent);
        c.setup();
        stack.push(c);
        View decor = c.get().getWindow().getDecorView();
        System.out.println("[apklayer-rob] started " + cls + " decor "
                + decor.getWidth() + "x" + decor.getHeight() + " depth=" + stack.size());
    }

    /** Follows startActivity() and finish() of the top activity. */
    private void followNavigation(Activity act) throws Exception {
        Intent next = ((ShadowActivity) Shadow.extract(act)).getNextStartedActivity();
        if (next != null) {
            launch(next);
        } else if (act.isFinishing()) {
            stack.pop().pause().stop().destroy();
            System.out.println("[apklayer-rob] finished " + act.getClass().getName()
                    + " depth=" + stack.size());
            if (!stack.isEmpty()) stack.peek().restart().resume().visible();
        }
    }

    /** A window to compose: its root view, screen frame and layout params. */
    private static final class HostSurface {
        final View view; final Rect frame; final WindowManager.LayoutParams lp;
        HostSurface(View view, Rect frame, WindowManager.LayoutParams lp) {
            this.view = view; this.frame = frame; this.lp = lp;
        }
    }

    /**
     * Windows of the top activity in z-order, from the real WindowManagerGlobal: its decor plus
     * any dialog/popup/menu windows, placed with the framework's own Gravity.apply.
     */
    private static List<HostSurface> windows(Activity top) {
        Object wmg = WindowManagerGlobal.getInstance();
        List<View> views = ReflectionHelpers.getField(wmg, "mViews");
        List<WindowManager.LayoutParams> params = ReflectionHelpers.getField(wmg, "mParams");
        View decor = top.getWindow().getDecorView();
        List<HostSurface> out = new ArrayList<>();
        out.add(new HostSurface(decor, new Rect(0, 0, W, H), top.getWindow().getAttributes()));
        for (int i = 0; i < views.size(); i++) {
            View v = views.get(i);
            WindowManager.LayoutParams lp = params.get(i);
            if (v == decor || lp.type == WindowManager.LayoutParams.TYPE_BASE_APPLICATION
                    || v.getVisibility() != View.VISIBLE || !v.isAttachedToWindow()) continue;
            Rect frame = new Rect();
            Gravity.apply(lp.gravity == 0 ? Gravity.TOP | Gravity.START : lp.gravity,
                    v.getWidth(), v.getHeight(), new Rect(0, 0, W, H), lp.x, lp.y, frame);
            out.add(new HostSurface(v, frame, lp));
        }
        return out;
    }

    /**
     * Input goes to the topmost window (as InputDispatcher would): pointer events become
     * MotionEvents in its coordinates; Esc/Backspace is Back. Returns the gesture downTime.
     */
    private static long dispatch(Activity act, int[] ev, long downTime) {
        List<HostSurface> ws = windows(act);
        HostSurface target = ws.get(ws.size() - 1);
        long now = SystemClock.uptimeMillis();
        if (ev[0] <= 2) {
            if (ev[0] == MotionEvent.ACTION_DOWN) downTime = now;
            MotionEvent me = MotionEvent.obtain(downTime, now, ev[0],
                    ev[1] - target.frame.left, ev[2] - target.frame.top, 0);
            target.view.dispatchTouchEvent(me);
            me.recycle();
        } else if (ev[0] == 100 && (ev[1] == KEY_ESC || ev[1] == KEY_BACKSPACE)) {
            int action = ev[2] == 1 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
            target.view.dispatchKeyEvent(new KeyEvent(now, now, action, KeyEvent.KEYCODE_BACK, 0));
            if (action == KeyEvent.ACTION_DOWN) {
                target.view.dispatchKeyEvent(
                        new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0));
            }
        }
        return downTime;
    }

    /** Debug snapshot of a presented frame (what the Wayland window shows). */
    private static void dumpPng(int[] px, int w, int h, String path) throws Exception {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, px, 0, w);
        javax.imageio.ImageIO.write(img, "png", new java.io.File(path));
        System.out.println("[apklayer-rob] snapshot -> " + path);
    }
}
