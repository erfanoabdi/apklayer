// android.widget.ZoomButtonsController -- deprecated controller that shows a
// floating zoom-controls overlay over a target view. Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/ZoomButtonsController.java
//   @Deprecated public class ZoomButtonsController implements View.OnTouchListener
package android.widget;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

@Deprecated
public class ZoomButtonsController implements View.OnTouchListener {
    public interface OnZoomListener {
        void onVisibilityChanged(boolean visible);
        void onZoom(boolean zoomIn);
    }

    private final View mOwnerView;
    private final ZoomControls mControls;
    private OnZoomListener mCallback;
    private boolean mIsVisible;

    public ZoomButtonsController(View ownerView) {
        mOwnerView = ownerView;
        mControls = new ZoomControls(ownerView.getContext());
    }

    public void setZoomInEnabled(boolean enabled) { mControls.setIsZoomInEnabled(enabled); }
    public void setZoomOutEnabled(boolean enabled) { mControls.setIsZoomOutEnabled(enabled); }
    public void setZoomSpeed(long speed) { mControls.setZoomSpeed(speed); }
    public void setOnZoomListener(OnZoomListener listener) { mCallback = listener; }
    public void setFocusable(boolean focusable) {}

    public ViewGroup getContainer() { return mControls; }
    public View getZoomControls() { return mControls; }

    public void setVisible(boolean visible) {
        if (mIsVisible != visible) {
            mIsVisible = visible;
            if (mCallback != null) mCallback.onVisibilityChanged(visible);
        }
    }
    public boolean isVisible() { return mIsVisible; }

    @Override public boolean onTouch(View v, MotionEvent event) { return false; }

    /** AOSP ZoomButtonsController.java:318 / :326 -- whether the zoom controls
     *  fade themselves out after being shown. AOSP's setter is a no-op when the
     *  value is unchanged, kept. The default is true (AOSP field initialiser). */
    private boolean mAutoDismissControls = true;
    public boolean isAutoDismissed() { return mAutoDismissControls; }
    public void setAutoDismissed(boolean autoDismiss) {
        if (mAutoDismissControls == autoDismiss) return;
        mAutoDismissControls = autoDismiss;
    }
}
