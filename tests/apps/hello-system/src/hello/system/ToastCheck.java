package hello.system;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import java.util.List;

/**
 * A text toast is a window on screen (TYPE_TOAST, attached and laid out) for its duration, then gone. Toasts queue as in NotificationManagerService:
 * a second one shows when the first is hidden, a cancelled queued one never shows; Toast.Callback hears each shown and hidden.
 */
final class ToastCheck implements Check {
    @Override public String run(Context context) throws Exception {
        List<String> events = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        new Handler(Looper.getMainLooper()).post(() -> {
            for (String name : new String[] {"first", "second", "cancelled"}) {
                Toast toast = Toast.makeText(context, "apklayer toast " + name, Toast.LENGTH_SHORT);
                toast.addCallback(new Toast.Callback() {
                    @Override public void onToastShown() { events.add(name + " shown"); }
                    @Override public void onToastHidden() { events.add(name + " hidden"); }
                });
                toast.show();
                if (name.equals("cancelled")) toast.cancel();
            }
        });
        long start = SystemClock.uptimeMillis();
        View shown = null;
        while (shown == null && SystemClock.uptimeMillis() - start < 3000) { shown = toastWindow(); if (shown == null) Thread.sleep(20); }
        Check.require(shown != null, "no toast window within 3 s");
        long seen = SystemClock.uptimeMillis() - start;
        int width = shown.getWidth(), height = shown.getHeight();
        Object root = View.class.getMethod("getViewRootImpl").invoke(shown);
        java.lang.reflect.Field surface = root.getClass().getDeclaredField("mSurface");
        surface.setAccessible(true);
        Check.require(((android.view.Surface) surface.get(root)).isValid(), "the toast window has no surface");  // a real layer, not only a laid-out view
        while (toastWindow() != null && SystemClock.uptimeMillis() - start < 6000) Thread.sleep(50);
        long gone = SystemClock.uptimeMillis() - start;
        Check.require(gone >= 1500, "toast window left after " + gone + " ms");
        while (events.size() < 4 && SystemClock.uptimeMillis() - start < 9000) Thread.sleep(50);
        Thread.sleep(300);  // a cancelled toast that showed anyway would be reported by now
        Check.require(toastWindow() == null && events.equals(List.of("first shown", "first hidden", "second shown", "second hidden")), "toast queue and callbacks: " + events);
        return "toast window " + width + "x" + height + " after " + seen + " ms, next toast after " + gone + " ms; " + events;
    }

    /** The attached TYPE_TOAST window of this process, as WindowManagerGlobal holds its windows. */
    private static View toastWindow() throws Exception {
        Object global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null);
        java.lang.reflect.Field views = global.getClass().getDeclaredField("mViews"), params = global.getClass().getDeclaredField("mParams");
        views.setAccessible(true);
        params.setAccessible(true);
        {  // a snapshot: a race with addView only delays the next look
            List<?> v = new java.util.ArrayList<>((List<?>) views.get(global)), p = new java.util.ArrayList<>((List<?>) params.get(global));
            for (int i = 0; i < v.size(); i++)
                if (((WindowManager.LayoutParams) p.get(i)).type == WindowManager.LayoutParams.TYPE_TOAST && ((View) v.get(i)).isAttachedToWindow() && ((View) v.get(i)).getWidth() > 0)
                    return (View) v.get(i);
        }
        return null;
    }
}
