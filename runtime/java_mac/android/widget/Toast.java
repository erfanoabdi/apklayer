// android.widget.Toast -- a short transient on-screen message. Previously
// show() only logged; now it renders a real overlay: a rounded dark TextView
// added to the current activity's decor (the render root) at the configured
// gravity, removed automatically after the duration via a main-thread
// Handler. Falls back to logging if no activity/decor is available (e.g.
// called from a Service before any window exists). AOSP:
//   frameworks/base/core/java/android/widget/Toast.java
//   public class Toast
package android.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import com.macapk.loader.NativeLog;

public class Toast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG  = 1;

    private static final long SHORT_MS = 2000;
    private static final long LONG_MS  = 3500;

    private final Context mContext;
    private CharSequence mText;
    private int mDuration;
    private View mView;
    private View mShownView;
    private int mGravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
    private int mX, mY;
    private float mHorizontalMargin, mVerticalMargin;
    private Handler mHandler;

    public Toast(Context c) {
        // AOSP android14 Toast.java:161/172/183-189: Toast(Context) ->
        // this(context, null), whose constructor calls getLooper(null):
        //     return checkNotNull(Looper.myLooper(),
        //             "Can't toast on a thread that has not called Looper.prepare()");
        // i.e. a NullPointerException on a thread without a Looper -- notably the
        // GLSurfaceView GLThread, so a toast an engine raises from its renderer is
        // never shown on a device. makeText() builds through here. Only the
        // renderer thread is held to it (MACAPK_GL_THREAD_NO_LOOPER;
        // Handler.macapkImplicitLooper explains the scope).
        // docs/gl-thread-has-no-looper_2026-10-01.md
        if (android.os.Looper.macapkOnGlRendererThread() && android.os.Looper.myLooper() == null) {
            android.os.Handler.macapkNoteGlThreadNoLooper("Toast", "a NullPointerException");
            throw new NullPointerException("Can't toast on a thread that has not called Looper.prepare()");
        }
        mContext = c;
    }

    public static Toast makeText(Context c, CharSequence text, int duration) {
        Toast t = new Toast(c);
        t.mText = text;
        t.mDuration = duration;
        t.mView = t.buildDefaultView(text);
        return t;
    }

    public static Toast makeText(Context c, int resId, int duration) {
        CharSequence text;
        try { text = c.getString(resId); }
        catch (Throwable e) { text = "(toast resId=" + resId + ")"; }
        return makeText(c, text, duration);
    }

    private View buildDefaultView(CharSequence text) {
        TextView tv = new TextView(mContext);
        tv.setText(text);
        tv.setTextColor(0xFFFFFFFF);
        try { tv.setTextSize(14f); } catch (Throwable ignored) {}
        tv.setGravity(Gravity.CENTER);
        try { tv.setBackgroundColor(0xCC333333); } catch (Throwable ignored) {}
        tv.setPadding(48, 24, 48, 24);
        return tv;
    }

    public void show() {
        // Mirror to the log either way (useful for headless verification).
        NativeLog.i("Mac.apk.Toast", String.valueOf(mText));

        final ViewGroup decor = currentDecor();
        if (decor == null || mView == null) return;

        ensureHandler();
        Runnable showR = new Runnable() {
            @Override public void run() {
                try {
                    if (mShownView != null) removeFrom(decor, mShownView);
                    mShownView = mView;
                    ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    decor.addView(mShownView, lp);
                    decor.requestLayout();
                    decor.invalidate();
                } catch (Throwable t) {
                    NativeLog.i("Mac.apk.Toast", "overlay add failed: " + t);
                }
            }
        };
        Runnable hideR = new Runnable() {
            @Override public void run() { cancel(); }
        };
        mHandler.post(showR);
        mHandler.postDelayed(hideR, mDuration == LENGTH_LONG ? LONG_MS : SHORT_MS);
    }

    public void cancel() {
        if (mShownView == null) return;
        final View v = mShownView;
        mShownView = null;
        Runnable r = new Runnable() {
            @Override public void run() {
                ViewGroup parent = (v.getParent() instanceof ViewGroup)
                        ? (ViewGroup) v.getParent() : null;
                if (parent != null) removeFrom(parent, v);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else { ensureHandler(); mHandler.post(r); }
    }

    private void removeFrom(ViewGroup parent, View v) {
        try {
            parent.removeView(v);
            parent.invalidate();
        } catch (Throwable ignored) {}
    }

    private void ensureHandler() {
        if (mHandler == null) mHandler = new Handler(Looper.getMainLooper());
    }

    private ViewGroup currentDecor() {
        try {
            android.app.Activity a = com.macapk.loader.ActivityHost.peek();
            if (a == null) return null;
            View decor = a.getWindow() != null
                    ? a.getWindow().getDecorView() : null;
            return decor instanceof ViewGroup ? (ViewGroup) decor : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public CharSequence getText() { return mText; }
    public void setText(CharSequence t) {
        mText = t;
        if (mView instanceof TextView) ((TextView) mView).setText(t);
        else mView = buildDefaultView(t);
    }
    public void setText(int resId) {
        try { setText(mContext.getString(resId)); }
        catch (Throwable e) { setText("(toast resId=" + resId + ")"); }
    }
    public int  getDuration() { return mDuration; }
    public void setDuration(int d) { mDuration = d; }
    public void setView(View v) { mView = v; }
    public View getView() { return mView; }
    public void setGravity(int g, int xOff, int yOff) { mGravity = g; mX = xOff; mY = yOff; }
    public int  getGravity() { return mGravity; }
    public int  getXOffset() { return mX; }
    public int  getYOffset() { return mY; }
    public void setMargin(float h, float v) { mHorizontalMargin = h; mVerticalMargin = v; }
    public float getHorizontalMargin() { return mHorizontalMargin; }
    public float getVerticalMargin() { return mVerticalMargin; }

    // KIND: an ABSTRACT CLASS with empty bodies, not an interface. AOSP:
    //     public abstract static class Callback {
    //         public void onToastShown() {}
    //         public void onToastHidden() {}
    //     }
    // (frameworks/base/core/java/android/widget/Toast.java:759, android14-release),
    // confirmed against the platform jar: `javap -p 'android.widget.Toast$Callback'`
    // -> `public abstract class ... { public Toast$Callback(); ... }`.
    // As an interface it broke both ways for a guest: `extends Toast.Callback`
    // fails to link (IncompatibleClassChangeError, superclass is an interface),
    // and a subclass overriding only ONE of the two methods would have been
    // rejected as abstract, when AOSP's default-empty bodies make that legal.
    public abstract static class Callback {
        public void onToastShown() {}
        public void onToastHidden() {}
    }
    public void addCallback(Callback callback) {}
    public void removeCallback(Callback callback) {}
}
