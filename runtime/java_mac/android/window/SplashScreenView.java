package android.window;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
/** The view of the system splash screen handed to the app so it can coordinate a
 *  custom exit animation. Mac.apk shows no separate splash window, so this is an
 *  empty container; remove() detaches it. API 31.
 *  Source: frameworks/base/core/java/android/window/SplashScreenView.java */
public final class SplashScreenView extends FrameLayout {
    private View mIconView;
    public SplashScreenView(Context context) { super(context); }
    public View getIconView() { return mIconView; }
    public java.time.Instant getIconAnimationStart() { return null; }
    public java.time.Duration getIconAnimationDuration() { return null; }
    public void remove() {
        android.view.ViewParent p = getParent();
        if (p instanceof android.view.ViewGroup) ((android.view.ViewGroup) p).removeView(this);
    }
}
