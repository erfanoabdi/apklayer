// android.widget.RatingBar -- an AbsSeekBar showing a row of stars the user
// taps to set a rating. Was a bare `extends Object` stub (broke instanceof
// AbsSeekBar/ProgressBar and any rating widget). Now the real AbsSeekBar
// subclass: rating maps onto the ProgressBar progress at a configurable
// step, with touch mapping x -> rating and an OnRatingBarChangeListener.
// AOSP: frameworks/base/core/java/android/widget/RatingBar.java
//   public class RatingBar extends AbsSeekBar
package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class RatingBar extends AbsSeekBar {
    public interface OnRatingBarChangeListener {
        void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser);
    }

    private int mNumStars = 5;
    private float mStepSize = 0.5f;
    private boolean mIsIndicator;
    private OnRatingBarChangeListener mListener;

    public RatingBar(Context context) { super(context); init(); }
    public RatingBar(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public RatingBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init();
    }
    public RatingBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes); init();
    }

    /** AOSP RatingBar(Context, AttributeSet) is
     *  this(context, attrs, com.android.internal.R.attr.ratingBarStyle). */
    @Override int macapkDefaultStyleAttr() { return 0x0101007c; /* android.R.attr.ratingBarStyle */ }

    private void init() {
        // ProgressBar max encodes rating in step units: numStars / stepSize.
        setMax(progressFromStars(mNumStars));
    }

    private int progressFromStars(float stars) {
        return (int) (stars / mStepSize);
    }
    private float starsFromProgress() {
        return getProgress() * mStepSize;
    }

    public void setOnRatingBarChangeListener(OnRatingBarChangeListener listener) {
        mListener = listener;
    }
    public OnRatingBarChangeListener getOnRatingBarChangeListener() { return mListener; }

    public void setNumStars(int numStars) {
        if (numStars <= 0) return;
        mNumStars = numStars;
        setMax(progressFromStars(mNumStars));
        requestLayout();
    }
    public int getNumStars() { return mNumStars; }

    public void setRating(float rating) {
        setProgress(progressFromStars(rating));
    }
    public float getRating() { return starsFromProgress(); }

    public void setStepSize(float stepSize) {
        if (stepSize <= 0) return;
        float oldRating = getRating();
        mStepSize = stepSize;
        setMax(progressFromStars(mNumStars));
        setRating(oldRating);
    }
    public float getStepSize() { return mStepSize; }

    public void setIsIndicator(boolean isIndicator) { mIsIndicator = isIndicator; }
    public boolean isIndicator() { return mIsIndicator; }

    @Override public synchronized void setProgress(int progress) {
        super.setProgress(progress);
        if (mListener != null) {
            mListener.onRatingChanged(this, getRating(), false);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent ev) {
        if (mIsIndicator) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP: {
                int available = getWidth() - getPaddingLeft() - getPaddingRight();
                if (available <= 0) return true;
                float scale = (ev.getX() - getPaddingLeft()) / available;
                if (scale < 0) scale = 0;
                if (scale > 1) scale = 1;
                float rating = scale * mNumStars;
                // Snap to step.
                rating = Math.round(rating / mStepSize) * mStepSize;
                setProgress(progressFromStars(rating));
                if (mListener != null) {
                    mListener.onRatingChanged(this, getRating(), true);
                }
                return true;
            }
        }
        return super.onTouchEvent(ev);
    }
}
