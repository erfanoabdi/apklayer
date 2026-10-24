// android.graphics.drawable.TransitionDrawable -- LayerDrawable subclass
// that cross-fades between layer 0 and layer 1. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/TransitionDrawable.java
// Glide's DrawableCrossFadeTransition wraps the loaded resource in a
// TransitionDrawable with layer 0 = placeholder and layer 1 = the loaded
// bitmap, then calls startTransition(durationMs). Selectors also use it
// for pressed-state fades.
//
// This impl performs a real time-based cross-fade: draw() computes the
// fraction from the elapsed wall-clock time since startTransition and
// blends layer 0 out / layer 1 in via per-layer alpha. The
// ViewCanvasDriver redraws every frame, so the fade animates without a
// separate timer. When no transition is in flight we paint the resolved
// end state (whichever direction last ran), matching AOSP.
package android.graphics.drawable;

import android.graphics.Canvas;

public class TransitionDrawable extends LayerDrawable {
    private static final int TRANSITION_STARTING = 0;
    private static final int TRANSITION_RUNNING = 1;
    private static final int TRANSITION_NONE = 2;

    private int mTransitionState = TRANSITION_NONE;
    private boolean mReverse = false;
    private boolean mCrossFade = false;
    private long mStartTimeMillis;
    private int mFrom;   // alpha-from for layer 1 (0..255)
    private int mTo;     // alpha-to for layer 1
    private int mDuration;
    private int mOriginalDuration;
    private int mAlpha = 0;   // current layer-1 alpha while running / resolved

    public TransitionDrawable(Drawable[] layers) {
        super(layers);
    }

    /** Begin a forward cross-fade (layer 0 -> layer 1) over durationMs. */
    public void startTransition(int durationMs) {
        mFrom = 0;
        mTo = 255;
        mAlpha = 0;
        mDuration = mOriginalDuration = durationMs;
        mReverse = false;
        mTransitionState = TRANSITION_STARTING;
        invalidateSelf();
    }

    /** Reverse the cross-fade back toward layer 0. */
    public void reverseTransition(int duration) {
        final long time = android.os.SystemClock.uptimeMillis();
        // If a forward run is still in flight, reverse from the current point.
        if (mTransitionState == TRANSITION_RUNNING
                && time - mStartTimeMillis < mOriginalDuration) {
            mFrom = mAlpha;
            mTo = mReverse ? 255 : 0;
            mDuration = (int) (mReverse
                    ? time - mStartTimeMillis
                    : mOriginalDuration - (time - mStartTimeMillis));
            mReverse = !mReverse;
            mTransitionState = TRANSITION_STARTING;
            invalidateSelf();
            return;
        }
        mReverse = true;
        mFrom = 255;
        mTo = 0;
        mDuration = mOriginalDuration = duration;
        mTransitionState = TRANSITION_STARTING;
        invalidateSelf();
    }

    /** Immediately jump to the start (only layer 0 visible). */
    public void resetTransition() {
        mAlpha = 0;
        mTransitionState = TRANSITION_NONE;
        invalidateSelf();
    }

    public void setCrossFadeEnabled(boolean enabled) { mCrossFade = enabled; }
    public boolean isCrossFadeEnabled() { return mCrossFade; }

    @Override public void draw(Canvas canvas) {
        boolean done = true;
        switch (mTransitionState) {
            case TRANSITION_STARTING:
                mStartTimeMillis = android.os.SystemClock.uptimeMillis();
                done = false;
                mTransitionState = TRANSITION_RUNNING;
                break;
            case TRANSITION_RUNNING:
                if (mStartTimeMillis >= 0) {
                    float normalized = (float)
                        (android.os.SystemClock.uptimeMillis() - mStartTimeMillis)
                        / mDuration;
                    done = normalized >= 1.0f;
                    normalized = Math.min(normalized, 1.0f);
                    mAlpha = (int) (mFrom + (mTo - mFrom) * normalized);
                }
                break;
            default:
                // TRANSITION_NONE: keep the resolved mAlpha.
                break;
        }

        final int alpha = mAlpha;
        final boolean crossFade = mCrossFade;
        Drawable d0 = getDrawable(0);
        Drawable d1 = getNumberOfLayers() > 1 ? getDrawable(1) : null;

        if (done) {
            // No more transition; draw the resolved frame.
            if (!crossFade || alpha == 0) {
                if (d0 != null) d0.draw(canvas);
            }
            if (alpha == 0xFF) {
                if (d1 != null) d1.draw(canvas);
            } else if (d1 != null) {
                int restore = d1.getAlpha();
                d1.setAlpha(alpha);
                d1.draw(canvas);
                d1.setAlpha(restore);
            }
            mTransitionState = TRANSITION_NONE;
            return;
        }

        // Running: paint layer 0 (faded out under crossFade) then layer 1.
        if (d0 != null) {
            if (crossFade) {
                int r0 = d0.getAlpha();
                d0.setAlpha(0xFF - alpha);
                d0.draw(canvas);
                d0.setAlpha(r0);
            } else {
                d0.draw(canvas);
            }
        }
        if (alpha > 0 && d1 != null) {
            int r1 = d1.getAlpha();
            d1.setAlpha(alpha);
            d1.draw(canvas);
            d1.setAlpha(r1);
        }
        invalidateSelf();
    }
}
