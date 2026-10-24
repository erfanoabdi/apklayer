// android.view.ScaleGestureDetector -- pinch-to-zoom detector. Mirrors AOSP
// frameworks/base/core/java/android/view/ScaleGestureDetector.java.
//
// Was entirely absent. Maps, image viewers, PDF readers, camera UIs and many
// Compose/Custom-view zoom gestures feed MotionEvents into one of these and read
// getScaleFactor() each onScale callback. Without the class those views
// NoClassDefFoundError'd at load (the listener type appears in field/method
// signatures), and even when loadable they could not zoom.
//
// Detection model (AOSP): track the span = average distance of all pointers from
// their focus (centroid). On the first frame with >= 2 pointers past the
// configured minimum span, fire onScaleBegin. Each subsequent multi-pointer
// frame computes scaleFactor = currentSpan / previousSpan and fires onScale; on
// the listener returning true the span baseline resets. When the gesture ends
// (down to < 2 pointers or up/cancel) fire onScaleEnd.
package android.view;

import android.content.Context;

public class ScaleGestureDetector {
    public interface OnScaleGestureListener {
        boolean onScale(ScaleGestureDetector detector);
        boolean onScaleBegin(ScaleGestureDetector detector);
        void onScaleEnd(ScaleGestureDetector detector);
    }

    public static class SimpleOnScaleGestureListener implements OnScaleGestureListener {
        public boolean onScale(ScaleGestureDetector detector) { return false; }
        public boolean onScaleBegin(ScaleGestureDetector detector) { return true; }
        public void onScaleEnd(ScaleGestureDetector detector) {}
    }

    private final OnScaleGestureListener mListener;

    private float mFocusX, mFocusY;
    private boolean mQuickScaleEnabled;
    private boolean mStylusScaleEnabled = true;

    private float mCurrSpan, mPrevSpan, mInitialSpan;
    private float mCurrSpanX, mCurrSpanY, mPrevSpanX, mPrevSpanY;
    private long mCurrTime, mPrevTime;
    private boolean mInProgress;
    private int mSpanSlop;
    private int mMinSpan;

    // AOSP default minimum span before a scale begins (in px). 27 dp scaled is
    // the platform default; scaled by the device density.
    private static final int SPAN_SLOP_DP = 8;
    private static final int MIN_SPAN_DP = 27;
    private static final float DENSITY = android.util.DisplayMetrics.macapkDeviceDensity();

    public ScaleGestureDetector(Context context, OnScaleGestureListener listener) {
        this(context, listener, null);
    }

    public ScaleGestureDetector(Context context, OnScaleGestureListener listener,
            android.os.Handler handler) {
        mListener = listener;
        mSpanSlop = (int) (SPAN_SLOP_DP * DENSITY) * 2;
        mMinSpan = (int) (MIN_SPAN_DP * DENSITY);
        // AOSP ScaleGestureDetector.java:232-236 (android16-release):
        //     // Quick scale is enabled by default after JB_MR2
        //     final int targetSdkVersion = context.getApplicationInfo().targetSdkVersion;
        //     setQuickScaleEnabled(targetSdkVersion > Build.VERSION_CODES.JELLY_BEAN_MR2);
        //     // Stylus scale is enabled by default after LOLLIPOP_MR1
        //     setStylusScaleEnabled(targetSdkVersion > Build.VERSION_CODES.LOLLIPOP_MR1);
        // Below JB-MR2 a double-tap-drag must NOT zoom, because an app of that
        // era owns the double-tap gesture itself.
        //
        // ⚠ These two fields are currently STORE-ONLY -- onTouchEvent below
        // never reads either, so no gesture behaviour changes here in either
        // direction. What does change is the answer isQuickScaleEnabled()
        // gives: it was a hardcoded false for every app, where a device says
        // true for anything above 18. Wiring the real gesture is separate work;
        // when it lands it must read these fields rather than re-deriving.
        final int targetSdkVersion = macapkTargetSdk(context);
        setQuickScaleEnabled(targetSdkVersion > android.os.Build.VERSION_CODES.JELLY_BEAN_MR2);
        setStylusScaleEnabled(targetSdkVersion > android.os.Build.VERSION_CODES.LOLLIPOP_MR1);
    }

    /** The app's OWN declared targetSdkVersion, or the MODERN answer
     *  (Integer.MAX_VALUE) when it cannot be read yet. Never latched, never
     *  guessed backwards -- see android.app.Application.macapkAppTargetSdk. */
    private static int macapkTargetSdk(Context context) {
        try {
            android.content.pm.ApplicationInfo ai =
                    (context != null) ? context.getApplicationInfo() : null;
            if (ai != null && android.app.Application.macapkTargetSdkKnown(ai.targetSdkVersion)) return ai.targetSdkVersion;
        } catch (Throwable ignored) { }
        return Integer.MAX_VALUE;
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (event == null || mListener == null) return false;
        mCurrTime = event.getEventTime();
        final int action = event.getActionMasked();

        final boolean streamComplete = action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL;

        if (action == MotionEvent.ACTION_DOWN || streamComplete) {
            // End any in-progress scale at the start/end of a complete stream.
            if (mInProgress) {
                mListener.onScaleEnd(this);
                mInProgress = false;
                mInitialSpan = 0;
            }
            if (streamComplete) return true;
        }

        final boolean configChanged = action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_POINTER_DOWN;

        final boolean pointerUp = action == MotionEvent.ACTION_POINTER_UP;
        final int skipIndex = pointerUp ? event.getActionIndex() : -1;

        // Determine focus = centroid of active pointers.
        final int count = event.getPointerCount();
        final int div = pointerUp ? count - 1 : count;
        float sumX = 0, sumY = 0;
        for (int i = 0; i < count; i++) {
            if (skipIndex == i) continue;
            sumX += event.getX(i);
            sumY += event.getY(i);
        }
        final float focusX = div > 0 ? sumX / div : event.getX();
        final float focusY = div > 0 ? sumY / div : event.getY();

        // Average deviation from focus -> span (separately per axis and combined).
        float devSumX = 0, devSumY = 0;
        for (int i = 0; i < count; i++) {
            if (skipIndex == i) continue;
            devSumX += Math.abs(event.getX(i) - focusX);
            devSumY += Math.abs(event.getY(i) - focusY);
        }
        final float devX = div > 0 ? devSumX / div : 0;
        final float devY = div > 0 ? devSumY / div : 0;

        final float spanX = devX * 2;
        final float spanY = devY * 2;
        final float span = (float) Math.hypot(spanX, spanY);

        // Less than 2 pointers, or span collapsed -> not a scale anymore.
        final boolean wasInProgress = mInProgress;
        mFocusX = focusX;
        mFocusY = focusY;
        if (mInProgress && (span < mMinSpan || configChanged)) {
            if (span < mMinSpan) {
                mListener.onScaleEnd(this);
                mInProgress = false;
                mInitialSpan = span;
            }
        }
        if (configChanged) {
            mPrevSpanX = mCurrSpanX = spanX;
            mPrevSpanY = mCurrSpanY = spanY;
            mInitialSpan = mPrevSpan = mCurrSpan = span;
        }

        final int minSpan = mMinSpan;
        if (!mInProgress && span >= minSpan
                && (wasInProgress || Math.abs(span - mInitialSpan) > mSpanSlop)
                && count >= 2) {
            mPrevSpanX = mCurrSpanX = spanX;
            mPrevSpanY = mCurrSpanY = spanY;
            mPrevSpan = mCurrSpan = span;
            mPrevTime = mCurrTime;
            mInProgress = mListener.onScaleBegin(this);
        }

        // Handle motion: report scale factor.
        if (action == MotionEvent.ACTION_MOVE) {
            mCurrSpanX = spanX;
            mCurrSpanY = spanY;
            mCurrSpan = span;
            boolean updatePrev = true;
            if (mInProgress) {
                updatePrev = mListener.onScale(this);
            }
            if (updatePrev) {
                mPrevSpanX = mCurrSpanX;
                mPrevSpanY = mCurrSpanY;
                mPrevSpan = mCurrSpan;
                mPrevTime = mCurrTime;
            }
        }

        return true;
    }

    public boolean isInProgress() { return mInProgress; }
    public float getFocusX()      { return mFocusX; }
    public float getFocusY()      { return mFocusY; }
    public float getCurrentSpan() { return mCurrSpan; }
    public float getCurrentSpanX(){ return mCurrSpanX; }
    public float getCurrentSpanY(){ return mCurrSpanY; }
    public float getPreviousSpan(){ return mPrevSpan; }
    public float getPreviousSpanX(){ return mPrevSpanX; }
    public float getPreviousSpanY(){ return mPrevSpanY; }

    public float getScaleFactor() {
        return mPrevSpan > 0 ? mCurrSpan / mPrevSpan : 1f;
    }

    public long getTimeDelta()   { return mCurrTime - mPrevTime; }
    public long getEventTime()   { return mCurrTime; }

    public void setQuickScaleEnabled(boolean scales) { mQuickScaleEnabled = scales; }
    public boolean isQuickScaleEnabled() { return mQuickScaleEnabled; }
    public void setStylusScaleEnabled(boolean scales) { mStylusScaleEnabled = scales; }
    public boolean isStylusScaleEnabled() { return mStylusScaleEnabled; }
}
