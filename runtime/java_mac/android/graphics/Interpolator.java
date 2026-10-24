package android.graphics;

import android.os.SystemClock;

/**
 * AOSP-faithful legacy android.graphics.Interpolator (NOT the view-animation
 * one). A native keyframe interpolator that maps a time to a vector of values.
 * We reimplement the keyframe math in plain Java: linear interpolation between
 * the bracketing keyframes, with optional repeat/mirror and start/end freeze.
 * Mirrors frameworks/base/graphics/java/android/graphics/Interpolator.java.
 */
public class Interpolator {

    public enum Result {
        NORMAL,
        FREEZE_START,
        FREEZE_END
    }

    private int mValueCount;
    private int mFrameCount;
    private int[] mTimes;       // msec per keyframe
    private float[][] mValues;  // [frame][value]
    private float[][] mBlends;  // optional cubic blend per keyframe (stored only)
    private long mStartTime;
    private float mRepeatCount = 1f;
    private boolean mMirror = false;

    public Interpolator(int valueCount) {
        this(valueCount, 2);
    }

    public Interpolator(int valueCount, int frameCount) {
        reset(valueCount, frameCount);
    }

    public void reset(int valueCount) {
        reset(valueCount, 2);
    }

    public void reset(int valueCount, int frameCount) {
        mValueCount = valueCount;
        mFrameCount = frameCount;
        mTimes = new int[frameCount];
        mValues = new float[frameCount][valueCount];
        mBlends = new float[frameCount][];
        mStartTime = SystemClock.uptimeMillis();
    }

    public final int getKeyFrameCount() { return mFrameCount; }
    public final int getValueCount() { return mValueCount; }

    public void setKeyFrame(int index, int msec, float[] values) {
        setKeyFrame(index, msec, values, null);
    }

    public void setKeyFrame(int index, int msec, float[] values, float[] blend) {
        if (index < 0 || index >= mFrameCount) {
            throw new IndexOutOfBoundsException("index=" + index);
        }
        if (values == null || values.length < mValueCount) {
            throw new IllegalArgumentException("values too short");
        }
        mTimes[index] = msec;
        System.arraycopy(values, 0, mValues[index], 0, mValueCount);
        if (blend != null) mBlends[index] = blend.clone();
    }

    public void setRepeatMirror(float repeatCount, boolean mirror) {
        if (repeatCount >= 0) {
            mRepeatCount = repeatCount;
            mMirror = mirror;
        }
    }

    public Result timeToValues(float[] values) {
        return timeToValues((int) (SystemClock.uptimeMillis() - mStartTime), values);
    }

    public Result timeToValues(int msec, float[] values) {
        if (values != null && values.length < mValueCount) {
            throw new IllegalArgumentException("values too short");
        }
        if (mFrameCount == 0) {
            return Result.FREEZE_START;
        }
        int first = mTimes[0];
        int last = mTimes[mFrameCount - 1];
        int span = last - first;
        Result result = Result.NORMAL;

        int t = msec;
        if (span > 0 && mRepeatCount != 1f) {
            int rel = t - first;
            int totalSpan = (int) (span * mRepeatCount);
            if (rel < 0) {
                copyFrame(0, values);
                return Result.FREEZE_START;
            }
            if (rel >= totalSpan) {
                copyFrame(mFrameCount - 1, values);
                return Result.FREEZE_END;
            }
            int cycle = rel / span;
            int within = rel % span;
            if (mMirror && (cycle & 1) == 1) within = span - within;
            t = first + within;
        }

        if (t <= first) {
            copyFrame(0, values);
            return (t < first) ? Result.FREEZE_START : Result.NORMAL;
        }
        if (t >= last) {
            copyFrame(mFrameCount - 1, values);
            return (t > last) ? Result.FREEZE_END : Result.NORMAL;
        }
        // Find bracketing keyframes and linearly interpolate.
        for (int i = 1; i < mFrameCount; i++) {
            if (t <= mTimes[i]) {
                int t0 = mTimes[i - 1], t1 = mTimes[i];
                float frac = (t1 == t0) ? 0f : (float) (t - t0) / (t1 - t0);
                if (values != null) {
                    for (int v = 0; v < mValueCount; v++) {
                        values[v] = mValues[i - 1][v]
                                + frac * (mValues[i][v] - mValues[i - 1][v]);
                    }
                }
                break;
            }
        }
        return result;
    }

    private void copyFrame(int frame, float[] values) {
        if (values != null) {
            System.arraycopy(mValues[frame], 0, values, 0, mValueCount);
        }
    }
}
