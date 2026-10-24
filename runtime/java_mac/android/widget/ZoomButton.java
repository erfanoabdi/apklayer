// android.widget.ZoomButton -- deprecated ImageButton that fires repeated
// clicks while held. Was Absent. We keep the repeat-interval surface; the
// long-press auto-repeat is driven by the host's click pipeline. AOSP:
//   frameworks/base/core/java/android/widget/ZoomButton.java
//   @Deprecated public class ZoomButton extends ImageButton implements View.OnLongClickListener
package android.widget;

import android.content.Context;
import android.view.View;

@Deprecated
public class ZoomButton extends ImageButton implements View.OnLongClickListener {
    private long mZoomSpeed = 1000;

    public ZoomButton(Context context) { super(context); }
    public ZoomButton(Context context, android.util.AttributeSet attrs) { super(context, attrs); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public ZoomButton(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public ZoomButton(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setZoomSpeed(long speed) { mZoomSpeed = speed; }

    @Override public boolean onLongClick(View v) { return true; }
}
