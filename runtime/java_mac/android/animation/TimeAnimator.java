// android.animation.TimeAnimator -- a ValueAnimator that does not interpolate a
// value; instead it calls back every frame with the total elapsed time and the
// delta since the last frame. Used as a generic per-frame ticker (game loops,
// custom physics). Was absent. Mirrors
// frameworks/base/core/java/android/animation/TimeAnimator.java.
//
// We drive the TimeListener off the inherited update path: an infinite-duration
// ValueAnimator emits a frame callback each tick (on the real-looper path) from
// which we compute total/delta wall-clock time. On the shipped snap-to-end path
// the animator completes immediately, so we emit a single terminal tick (total =
// 0, delta = 0); apps that need a real frame loop run on the real-looper path.
package android.animation;

public class TimeAnimator extends ValueAnimator {

    public interface TimeListener {
        void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime);
    }

    private TimeListener mListener;
    private long mStartWallMs = -1;
    private long mPrevWallMs = -1;

    public TimeAnimator() {
        // A TimeAnimator runs until cancelled; AOSP gives it an infinite duration
        // and no value to interpolate.
        setRepeatCount(INFINITE);
        setFloatValues(0f, 1f);
        addUpdateListener(new AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator animation) {
                if (mListener == null) return;
                long now = System.currentTimeMillis();
                if (mStartWallMs < 0) { mStartWallMs = now; mPrevWallMs = now; }
                long total = now - mStartWallMs;
                long delta = now - mPrevWallMs;
                mPrevWallMs = now;
                mListener.onTimeUpdate(TimeAnimator.this, total, delta);
            }
        });
    }

    public void setTimeListener(TimeListener listener) { mListener = listener; }

    @Override
    public void start() {
        mStartWallMs = -1;
        mPrevWallMs = -1;
        super.start();
    }
}
