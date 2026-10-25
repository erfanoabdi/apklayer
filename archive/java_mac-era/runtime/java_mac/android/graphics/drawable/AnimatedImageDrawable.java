package android.graphics.drawable;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

/**
 * AOSP-faithful android.graphics.drawable.AnimatedImageDrawable (API 28). On
 * real Android this plays animated WebP/GIF/HEIF decoded via ImageDecoder. Our
 * BitmapFactory only decodes the first frame, so we render that single frame
 * and expose the full animation-control surface as faithful no-ops: start()
 * marks running and immediately reports onAnimationStart/onAnimationEnd to
 * registered callbacks so apps that gate on completion proceed. Mirrors
 * frameworks/base/graphics/java/android/graphics/drawable/AnimatedImageDrawable.java.
 */
public class AnimatedImageDrawable extends Drawable implements Animatable2 {
    public static final int REPEAT_INFINITE = -1;

    private Bitmap mBitmap;
    private final Paint mPaint = new Paint();
    private boolean mRunning;
    private int mRepeatCount = REPEAT_INFINITE;
    private final List<AnimationCallback> mCallbacks = new ArrayList<>();

    public AnimatedImageDrawable() {}

    /** Mac.apk setter so ImageDecoder.decodeDrawable can attach the decoded
     *  first frame (AOSP builds this drawable natively from the decoder). */
    public void macapkSetBitmap(Bitmap bm) { mBitmap = bm; }

    @Override
    public void draw(Canvas canvas) {
        if (canvas == null || mBitmap == null) return;
        int w = getBounds().width();
        int h = getBounds().height();
        if (w <= 0) w = mBitmap.getWidth();
        if (h <= 0) h = mBitmap.getHeight();
        canvas.drawBitmap(mBitmap,
                new Rect(0, 0, mBitmap.getWidth(), mBitmap.getHeight()),
                new Rect(0, 0, w, h), mPaint);
    }

    @Override public int getIntrinsicWidth()  { return mBitmap != null ? mBitmap.getWidth()  : -1; }
    @Override public int getIntrinsicHeight() { return mBitmap != null ? mBitmap.getHeight() : -1; }

    @Override public void setAlpha(int alpha) { mPaint.setAlpha(alpha); invalidateSelf(); }
    @Override public int getAlpha() { return mPaint.getAlpha(); }
    @Override public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter); invalidateSelf();
    }
    @Override public ColorFilter getColorFilter() { return mPaint.getColorFilter(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    public void setRepeatCount(int repeatCount) { mRepeatCount = repeatCount; }
    public int getRepeatCount() { return mRepeatCount; }

    @Override
    public void start() {
        if (mRunning) return;
        mRunning = true;
        for (AnimationCallback cb : new ArrayList<>(mCallbacks)) cb.onAnimationStart(this);
        invalidateSelf();
        // We render a single frame; signal end immediately so listeners that
        // wait for the animation to finish do not hang.
        mRunning = false;
        for (AnimationCallback cb : new ArrayList<>(mCallbacks)) cb.onAnimationEnd(this);
    }

    @Override
    public void stop() {
        if (!mRunning) return;
        mRunning = false;
        for (AnimationCallback cb : new ArrayList<>(mCallbacks)) cb.onAnimationEnd(this);
    }

    @Override
    public boolean isRunning() { return mRunning; }

    @Override
    public void registerAnimationCallback(AnimationCallback callback) {
        if (callback != null && !mCallbacks.contains(callback)) mCallbacks.add(callback);
    }

    @Override
    public boolean unregisterAnimationCallback(AnimationCallback callback) {
        return mCallbacks.remove(callback);
    }

    @Override
    public void clearAnimationCallbacks() {
        mCallbacks.clear();
    }
}
