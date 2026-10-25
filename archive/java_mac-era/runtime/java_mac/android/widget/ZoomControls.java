// android.widget.ZoomControls -- deprecated LinearLayout with zoom-in/out
// buttons. Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/ZoomControls.java
//   @Deprecated public class ZoomControls extends LinearLayout
package android.widget;

import android.content.Context;
import android.view.View;

@Deprecated
public class ZoomControls extends LinearLayout {
    public ZoomControls(Context context) { super(context); setOrientation(HORIZONTAL); }
    public ZoomControls(Context context, android.util.AttributeSet attrs) {
        super(context, attrs); setOrientation(HORIZONTAL);
    }

    public void setOnZoomInClickListener(View.OnClickListener listener) {}
    public void setOnZoomOutClickListener(View.OnClickListener listener) {}
    public void setZoomSpeed(long speed) {}
    public void show() { setVisibility(View.VISIBLE); }
    public void hide() { setVisibility(View.GONE); }
    public void setIsZoomInEnabled(boolean isEnabled) {}
    public void setIsZoomOutEnabled(boolean isEnabled) {}
    public boolean hasFocus() { return super.hasFocus(); }
}
