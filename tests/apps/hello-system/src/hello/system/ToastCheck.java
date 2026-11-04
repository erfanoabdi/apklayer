package hello.system;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import java.util.List;

/** A text toast is a window on screen (TYPE_TOAST, attached and laid out) for its duration, then gone. */
final class ToastCheck implements Check {
    @Override public String run(Context context) throws Exception {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, "apklayer toast", Toast.LENGTH_SHORT).show());
        long start = SystemClock.uptimeMillis();
        View shown = null;
        while (shown == null && SystemClock.uptimeMillis() - start < 3000) { shown = toastWindow(); if (shown == null) Thread.sleep(20); }
        Check.require(shown != null, "no toast window within 3 s");
        long seen = SystemClock.uptimeMillis() - start;
        int width = shown.getWidth(), height = shown.getHeight();
        while (toastWindow() != null && SystemClock.uptimeMillis() - start < 6000) Thread.sleep(50);
        long gone = SystemClock.uptimeMillis() - start;
        Check.require(toastWindow() == null && gone >= 1500, "toast window left after " + gone + " ms");
        return "toast window " + width + "x" + height + " after " + seen + " ms, gone after " + gone + " ms";
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
