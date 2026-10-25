// android.gesture.GestureOverlayView -- a transparent overlay that captures a
// finger gesture, draws the ink as it is being drawn, decides when the stroke
// has become "a gesture" rather than a scroll, and hands the finished Gesture to
// its listeners. Mirrors AOSP
// frameworks/base/core/java/android/gesture/GestureOverlayView.java
// declaration-for-declaration and body-for-body (aosp-mirror android16-release,
// fetched 2026-08-24).
//
// History: this used to be a 66-line stub with a comment saying "we never
// synthesize gestures, so the listener simply never fires (correct -- there are
// no ad gestures on this platform)". That reasoning was true for the one SDK
// that motivated it and wrong as framework behaviour: Mac.apk delivers real
// MotionEvents, so an app whose whole UI is gesture-driven (a launcher, a
// note-taking app, an accessibility shortcut) had a dead input surface with no
// error to point at. It now captures, draws and dispatches for real.
//
// ONE DELIBERATE DIVERGENCE, in mechanism only:
//
//   AOSP reads its twelve XML attributes through
//   context.obtainStyledAttributes(attrs, R.styleable.GestureOverlayView, ...),
//   i.e. through com.android.internal.R, which does not exist here -- and
//   Mac.apk's obtainStyledAttributes returns an EMPTY TypedArray, so that call
//   would silently yield the hardcoded defaults for every app. Instead we read
//   the same twelve attributes by NAME off the AttributeSet, which Mac.apk's
//   DomXmlResourceParser answers namespace-correctly. Same attribute names, same
//   defaults, same resulting field values; only the lookup path differs. This is
//   generic (no app is named anywhere) and it is the difference between an
//   app's `android:gestureColor="#ff0000"` being honoured and being dropped.
package android.gesture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import java.util.ArrayList;

public class GestureOverlayView extends FrameLayout {
    public static final int GESTURE_STROKE_TYPE_SINGLE = 0;
    public static final int GESTURE_STROKE_TYPE_MULTIPLE = 1;

    public static final int ORIENTATION_HORIZONTAL = 0;
    public static final int ORIENTATION_VERTICAL = 1;

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final int FADE_ANIMATION_RATE = 16;
    private static final boolean GESTURE_RENDERING_ANTIALIAS = true;
    private static final boolean DITHER_FLAG = true;

    private final Paint mGesturePaint = new Paint();

    private long mFadeDuration = 150;
    private long mFadeOffset = 420;
    private long mFadingStart;
    private boolean mFadingHasStarted;
    private boolean mFadeEnabled = true;

    private int mCurrentColor;
    private int mCertainGestureColor = 0xFFFFFF00;
    private int mUncertainGestureColor = 0x48FFFF00;
    private float mGestureStrokeWidth = 12.0f;
    private int mInvalidateExtraBorder = 10;

    private int mGestureStrokeType = GESTURE_STROKE_TYPE_SINGLE;
    private float mGestureStrokeLengthThreshold = 50.0f;
    private float mGestureStrokeSquarenessTreshold = 0.275f;
    private float mGestureStrokeAngleThreshold = 40.0f;

    private int mOrientation = ORIENTATION_VERTICAL;

    private final Rect mInvalidRect = new Rect();
    private final Path mPath = new Path();
    private boolean mGestureVisible = true;

    private float mX;
    private float mY;

    private float mCurveEndX;
    private float mCurveEndY;

    private float mTotalLength;
    private boolean mIsGesturing = false;
    private boolean mPreviousWasGesturing = false;
    private boolean mInterceptEvents = true;
    private boolean mIsListeningForGestures;
    private boolean mResetGesture;

    // current gesture
    private Gesture mCurrentGesture;
    private final ArrayList<GesturePoint> mStrokeBuffer = new ArrayList<GesturePoint>(100);

    private final ArrayList<OnGestureListener> mOnGestureListeners =
            new ArrayList<OnGestureListener>();
    private final ArrayList<OnGesturePerformedListener> mOnGesturePerformedListeners =
            new ArrayList<OnGesturePerformedListener>();
    private final ArrayList<OnGesturingListener> mOnGesturingListeners =
            new ArrayList<OnGesturingListener>();

    private boolean mHandleGestureActions;

    // fading out effect
    private boolean mIsFadingOut = false;
    private float mFadingAlpha = 1.0f;
    private final AccelerateDecelerateInterpolator mInterpolator =
            new AccelerateDecelerateInterpolator();

    private final FadeOutRunnable mFadingOut = new FadeOutRunnable();

    public GestureOverlayView(Context context) {
        super(context);
        init();
    }

    public GestureOverlayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public GestureOverlayView(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        // See the file header: AOSP's R.styleable path cannot work here, so the
        // SAME attribute names are read off the AttributeSet by name.
        mGestureStrokeWidth = attrFloat(attrs, "gestureStrokeWidth", mGestureStrokeWidth);
        mInvalidateExtraBorder = Math.max(1, ((int) mGestureStrokeWidth) - 1);
        mCertainGestureColor = attrColor(attrs, "gestureColor", mCertainGestureColor);
        mUncertainGestureColor =
                attrColor(attrs, "uncertainGestureColor", mUncertainGestureColor);
        mFadeDuration = attrInt(attrs, "fadeDuration", (int) mFadeDuration);
        mFadeOffset = attrInt(attrs, "fadeOffset", (int) mFadeOffset);
        mGestureStrokeType = attrEnum(attrs, "gestureStrokeType", mGestureStrokeType,
                "single", GESTURE_STROKE_TYPE_SINGLE,
                "multiple", GESTURE_STROKE_TYPE_MULTIPLE);
        mGestureStrokeLengthThreshold = attrFloat(attrs, "gestureStrokeLengthThreshold",
                mGestureStrokeLengthThreshold);
        mGestureStrokeAngleThreshold = attrFloat(attrs, "gestureStrokeAngleThreshold",
                mGestureStrokeAngleThreshold);
        mGestureStrokeSquarenessTreshold = attrFloat(attrs, "gestureStrokeSquarenessThreshold",
                mGestureStrokeSquarenessTreshold);
        mInterceptEvents = attrBool(attrs, "eventsInterceptionEnabled", mInterceptEvents);
        mFadeEnabled = attrBool(attrs, "fadeEnabled", mFadeEnabled);
        mOrientation = attrEnum(attrs, "orientation", mOrientation,
                "horizontal", ORIENTATION_HORIZONTAL,
                "vertical", ORIENTATION_VERTICAL);

        init();
    }

    // ---- AttributeSet readers (see the divergence note in the file header) --
    //
    // Every one of these returns the caller's default when the attribute is
    // absent OR unparseable, which is what an empty TypedArray would have done,
    // so a malformed layout degrades exactly as far as it did before and no
    // further.

    private static String attrRaw(AttributeSet attrs, String name) {
        if (attrs == null) return null;
        try {
            return attrs.getAttributeValue(ANDROID_NS, name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static float attrFloat(AttributeSet attrs, String name, float def) {
        String v = attrRaw(attrs, name);
        if (v == null) return def;
        try {
            return Float.parseFloat(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int attrInt(AttributeSet attrs, String name, int def) {
        String v = attrRaw(attrs, name);
        if (v == null) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean attrBool(AttributeSet attrs, String name, boolean def) {
        String v = attrRaw(attrs, name);
        if (v == null) return def;
        v = v.trim();
        if ("true".equalsIgnoreCase(v)) return true;
        if ("false".equalsIgnoreCase(v)) return false;
        return def;
    }

    private static int attrColor(AttributeSet attrs, String name, int def) {
        String v = attrRaw(attrs, name);
        if (v == null) return def;
        v = v.trim();
        try {
            if (v.startsWith("#")) return Color.parseColor(v);
        } catch (Throwable t) {
            return def;
        }
        return def;
    }

    /** AOSP declares these two as XML enums; aapt would have handed
     *  obtainStyledAttributes the integer. Off a raw AttributeSet the value can
     *  arrive as either the enum NAME or the already-resolved integer, so accept
     *  both. */
    private static int attrEnum(AttributeSet attrs, String name, int def,
            String name0, int value0, String name1, int value1) {
        String v = attrRaw(attrs, name);
        if (v == null) return def;
        v = v.trim();
        if (name0.equalsIgnoreCase(v)) return value0;
        if (name1.equalsIgnoreCase(v)) return value1;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void init() {
        setWillNotDraw(false);

        final Paint gesturePaint = mGesturePaint;
        gesturePaint.setAntiAlias(GESTURE_RENDERING_ANTIALIAS);
        gesturePaint.setColor(mCertainGestureColor);
        gesturePaint.setStyle(Paint.Style.STROKE);
        gesturePaint.setStrokeJoin(Paint.Join.ROUND);
        gesturePaint.setStrokeCap(Paint.Cap.ROUND);
        gesturePaint.setStrokeWidth(mGestureStrokeWidth);
        gesturePaint.setDither(DITHER_FLAG);

        mCurrentColor = mCertainGestureColor;
        setPaintAlpha(255);
    }

    public ArrayList<GesturePoint> getCurrentStroke() {
        return mStrokeBuffer;
    }

    public int getOrientation() {
        return mOrientation;
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
    }

    public void setGestureColor(int color) {
        mCertainGestureColor = color;
    }

    public void setUncertainGestureColor(int color) {
        mUncertainGestureColor = color;
    }

    public int getUncertainGestureColor() {
        return mUncertainGestureColor;
    }

    public int getGestureColor() {
        return mCertainGestureColor;
    }

    public float getGestureStrokeWidth() {
        return mGestureStrokeWidth;
    }

    public void setGestureStrokeWidth(float gestureStrokeWidth) {
        mGestureStrokeWidth = gestureStrokeWidth;
        mInvalidateExtraBorder = Math.max(1, ((int) gestureStrokeWidth) - 1);
        mGesturePaint.setStrokeWidth(gestureStrokeWidth);
    }

    public int getGestureStrokeType() {
        return mGestureStrokeType;
    }

    public void setGestureStrokeType(int gestureStrokeType) {
        mGestureStrokeType = gestureStrokeType;
    }

    public float getGestureStrokeLengthThreshold() {
        return mGestureStrokeLengthThreshold;
    }

    public void setGestureStrokeLengthThreshold(float gestureStrokeLengthThreshold) {
        mGestureStrokeLengthThreshold = gestureStrokeLengthThreshold;
    }

    public float getGestureStrokeSquarenessTreshold() {
        return mGestureStrokeSquarenessTreshold;
    }

    public void setGestureStrokeSquarenessTreshold(float gestureStrokeSquarenessTreshold) {
        mGestureStrokeSquarenessTreshold = gestureStrokeSquarenessTreshold;
    }

    public float getGestureStrokeAngleThreshold() {
        return mGestureStrokeAngleThreshold;
    }

    public void setGestureStrokeAngleThreshold(float gestureStrokeAngleThreshold) {
        mGestureStrokeAngleThreshold = gestureStrokeAngleThreshold;
    }

    public boolean isEventsInterceptionEnabled() {
        return mInterceptEvents;
    }

    public void setEventsInterceptionEnabled(boolean enabled) {
        mInterceptEvents = enabled;
    }

    public boolean isFadeEnabled() {
        return mFadeEnabled;
    }

    public void setFadeEnabled(boolean fadeEnabled) {
        mFadeEnabled = fadeEnabled;
    }

    public Gesture getGesture() {
        return mCurrentGesture;
    }

    public void setGesture(Gesture gesture) {
        if (mCurrentGesture != null) {
            clear(false);
        }

        setCurrentColor(mCertainGestureColor);
        mCurrentGesture = gesture;

        final Path path = mCurrentGesture.toPath();
        final RectF bounds = new RectF();
        path.computeBounds(bounds, true);

        mPath.rewind();
        mPath.addPath(path, -bounds.left + (getWidth() - bounds.width()) / 2.0f,
                -bounds.top + (getHeight() - bounds.height()) / 2.0f);

        mResetGesture = true;

        invalidate();
    }

    public Path getGesturePath() {
        return mPath;
    }

    public Path getGesturePath(Path path) {
        path.set(mPath);
        return path;
    }

    public boolean isGestureVisible() {
        return mGestureVisible;
    }

    public void setGestureVisible(boolean visible) {
        mGestureVisible = visible;
    }

    public long getFadeOffset() {
        return mFadeOffset;
    }

    public void setFadeOffset(long fadeOffset) {
        mFadeOffset = fadeOffset;
    }

    public void addOnGestureListener(OnGestureListener listener) {
        mOnGestureListeners.add(listener);
    }

    public void removeOnGestureListener(OnGestureListener listener) {
        mOnGestureListeners.remove(listener);
    }

    public void removeAllOnGestureListeners() {
        mOnGestureListeners.clear();
    }

    public void addOnGesturePerformedListener(OnGesturePerformedListener listener) {
        mOnGesturePerformedListeners.add(listener);
        if (mOnGesturePerformedListeners.size() > 0) {
            mHandleGestureActions = true;
        }
    }

    public void removeOnGesturePerformedListener(OnGesturePerformedListener listener) {
        mOnGesturePerformedListeners.remove(listener);
        if (mOnGesturePerformedListeners.size() <= 0) {
            mHandleGestureActions = false;
        }
    }

    public void removeAllOnGesturePerformedListeners() {
        mOnGesturePerformedListeners.clear();
        mHandleGestureActions = false;
    }

    public void addOnGesturingListener(OnGesturingListener listener) {
        mOnGesturingListeners.add(listener);
    }

    public void removeOnGesturingListener(OnGesturingListener listener) {
        mOnGesturingListeners.remove(listener);
    }

    public void removeAllOnGesturingListeners() {
        mOnGesturingListeners.clear();
    }

    public boolean isGesturing() {
        return mIsGesturing;
    }

    private void setCurrentColor(int color) {
        mCurrentColor = color;
        if (mFadingHasStarted) {
            setPaintAlpha((int) (255 * mFadingAlpha));
        } else {
            setPaintAlpha(255);
        }
        invalidate();
    }

    /** AOSP @hide. */
    public Paint getGesturePaint() {
        return mGesturePaint;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        if (mCurrentGesture != null && mGestureVisible) {
            canvas.drawPath(mPath, mGesturePaint);
        }
    }

    private void setPaintAlpha(int alpha) {
        alpha += alpha >> 7;
        final int baseAlpha = mCurrentColor >>> 24;
        final int useAlpha = baseAlpha * alpha >> 8;
        mGesturePaint.setColor((mCurrentColor << 8 >>> 8) | (useAlpha << 24));
    }

    public void clear(boolean animated) {
        clear(animated, false, true);
    }

    private void clear(boolean animated, boolean fireActionPerformed, boolean immediate) {
        setPaintAlpha(255);
        removeCallbacks(mFadingOut);
        mResetGesture = false;
        mFadingOut.fireActionPerformed = fireActionPerformed;
        mFadingOut.resetMultipleStrokes = false;

        if (animated && mCurrentGesture != null) {
            mFadingAlpha = 1.0f;
            mIsFadingOut = true;
            mFadingHasStarted = false;
            mFadingStart = AnimationUtils.currentAnimationTimeMillis() + mFadeOffset;

            postDelayed(mFadingOut, mFadeOffset);
        } else {
            mFadingAlpha = 1.0f;
            mIsFadingOut = false;
            mFadingHasStarted = false;

            if (immediate) {
                mCurrentGesture = null;
                mPath.rewind();
                invalidate();
            } else if (fireActionPerformed) {
                postDelayed(mFadingOut, mFadeOffset);
            } else if (mGestureStrokeType == GESTURE_STROKE_TYPE_MULTIPLE) {
                mFadingOut.resetMultipleStrokes = true;
                postDelayed(mFadingOut, mFadeOffset);
            } else {
                mCurrentGesture = null;
                mPath.rewind();
                invalidate();
            }
        }
    }

    public void cancelClearAnimation() {
        setPaintAlpha(255);
        mIsFadingOut = false;
        mFadingHasStarted = false;
        removeCallbacks(mFadingOut);
        mPath.rewind();
        mCurrentGesture = null;
    }

    public void cancelGesture() {
        mIsListeningForGestures = false;

        // add the stroke to the current gesture
        mCurrentGesture.addStroke(new GestureStroke(mStrokeBuffer));

        // pass the event to handlers
        final long now = SystemClock.uptimeMillis();
        final MotionEvent event = MotionEvent.obtain(now, now,
                MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);

        final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
        int count = listeners.size();
        for (int i = 0; i < count; i++) {
            listeners.get(i).onGestureCancelled(this, event);
        }

        event.recycle();

        clear(false);
        mIsGesturing = false;
        mPreviousWasGesturing = false;
        mStrokeBuffer.clear();

        final ArrayList<OnGesturingListener> otherListeners = mOnGesturingListeners;
        count = otherListeners.size();
        for (int i = 0; i < count; i++) {
            otherListeners.get(i).onGesturingEnded(this);
        }
    }

    /** AOSP declares this `protected`; Mac.apk's android.view.View declares it
     *  `public` (runtime/java/android/view/View.java:6355), and Java forbids
     *  narrowing an override, so it is public here. Observably identical -- a
     *  wider modifier only ADDS callers, and nothing in this class relies on it
     *  being unreachable. The divergence is in View, not here. */
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelClearAnimation();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (isEnabled()) {
            final boolean cancelDispatch = (mIsGesturing || (mCurrentGesture != null &&
                    mCurrentGesture.getStrokesCount() > 0 && mPreviousWasGesturing)) &&
                    mInterceptEvents;

            processEvent(event);

            if (cancelDispatch) {
                event.setAction(MotionEvent.ACTION_CANCEL);
            }

            super.dispatchTouchEvent(event);

            return true;
        }

        return super.dispatchTouchEvent(event);
    }

    private boolean processEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDown(event);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mIsListeningForGestures) {
                    Rect rect = touchMove(event);
                    if (rect != null) {
                        invalidate(rect);
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsListeningForGestures) {
                    touchUp(event, false);
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsListeningForGestures) {
                    touchUp(event, true);
                    invalidate();
                    return true;
                }
        }

        return false;
    }

    private void touchDown(MotionEvent event) {
        mIsListeningForGestures = true;

        float x = event.getX();
        float y = event.getY();

        mX = x;
        mY = y;

        mTotalLength = 0;
        mIsGesturing = false;

        if (mGestureStrokeType == GESTURE_STROKE_TYPE_SINGLE || mResetGesture) {
            if (mHandleGestureActions) setCurrentColor(mUncertainGestureColor);
            mResetGesture = false;
            mCurrentGesture = null;
            mPath.rewind();
        } else if (mCurrentGesture == null || mCurrentGesture.getStrokesCount() == 0) {
            if (mHandleGestureActions) setCurrentColor(mUncertainGestureColor);
        }

        // if there is fading out going on, stop it.
        if (mFadingHasStarted) {
            cancelClearAnimation();
        } else if (mIsFadingOut) {
            setPaintAlpha(255);
            mIsFadingOut = false;
            mFadingHasStarted = false;
            removeCallbacks(mFadingOut);
        }

        if (mCurrentGesture == null) {
            mCurrentGesture = new Gesture();
        }

        mStrokeBuffer.add(new GesturePoint(x, y, event.getEventTime()));
        mPath.moveTo(x, y);

        final int border = mInvalidateExtraBorder;
        mInvalidRect.set((int) x - border, (int) y - border, (int) x + border, (int) y + border);

        mCurveEndX = x;
        mCurveEndY = y;

        // pass the event to handlers
        final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
        final int count = listeners.size();
        for (int i = 0; i < count; i++) {
            listeners.get(i).onGestureStarted(this, event);
        }
    }

    private Rect touchMove(MotionEvent event) {
        Rect areaToRefresh = null;

        final float x = event.getX();
        final float y = event.getY();

        final float previousX = mX;
        final float previousY = mY;

        final float dx = Math.abs(x - previousX);
        final float dy = Math.abs(y - previousY);

        if (dx >= GestureStroke.TOUCH_TOLERANCE || dy >= GestureStroke.TOUCH_TOLERANCE) {
            areaToRefresh = mInvalidRect;

            // start with the curve end
            final int border = mInvalidateExtraBorder;
            areaToRefresh.set((int) mCurveEndX - border, (int) mCurveEndY - border,
                    (int) mCurveEndX + border, (int) mCurveEndY + border);

            float cX = mCurveEndX = (x + previousX) / 2;
            float cY = mCurveEndY = (y + previousY) / 2;

            mPath.quadTo(previousX, previousY, cX, cY);

            // union with the control point of the new curve
            areaToRefresh.union((int) previousX - border, (int) previousY - border,
                    (int) previousX + border, (int) previousY + border);

            // union with the end point of the new curve
            areaToRefresh.union((int) cX - border, (int) cY - border,
                    (int) cX + border, (int) cY + border);

            mX = x;
            mY = y;

            mStrokeBuffer.add(new GesturePoint(x, y, event.getEventTime()));

            if (mHandleGestureActions && !mIsGesturing) {
                mTotalLength += (float) Math.hypot(dx, dy);

                if (mTotalLength > mGestureStrokeLengthThreshold) {
                    final OrientedBoundingBox box =
                            GestureUtils.computeOrientedBoundingBox(mStrokeBuffer);

                    float angle = Math.abs(box.orientation);
                    if (angle > 90) {
                        angle = 180 - angle;
                    }

                    if (box.squareness > mGestureStrokeSquarenessTreshold ||
                            (mOrientation == ORIENTATION_VERTICAL ?
                                    angle < mGestureStrokeAngleThreshold :
                                    angle > mGestureStrokeAngleThreshold)) {

                        mIsGesturing = true;
                        setCurrentColor(mCertainGestureColor);

                        final ArrayList<OnGesturingListener> listeners = mOnGesturingListeners;
                        int count = listeners.size();
                        for (int i = 0; i < count; i++) {
                            listeners.get(i).onGesturingStarted(this);
                        }
                    }
                }
            }

            // pass the event to handlers
            final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
            final int count = listeners.size();
            for (int i = 0; i < count; i++) {
                listeners.get(i).onGesture(this, event);
            }
        }

        return areaToRefresh;
    }

    private void touchUp(MotionEvent event, boolean cancel) {
        mIsListeningForGestures = false;

        // A gesture wasn't started or was cancelled
        if (mCurrentGesture != null) {
            // add the stroke to the current gesture
            mCurrentGesture.addStroke(new GestureStroke(mStrokeBuffer));

            if (!cancel) {
                // pass the event to handlers
                final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
                int count = listeners.size();
                for (int i = 0; i < count; i++) {
                    listeners.get(i).onGestureEnded(this, event);
                }

                clear(mHandleGestureActions && mFadeEnabled, mHandleGestureActions && mIsGesturing,
                        false);
            } else {
                cancelGesture(event);

            }
        } else {
            cancelGesture(event);
        }

        mStrokeBuffer.clear();
        mPreviousWasGesturing = mIsGesturing;
        mIsGesturing = false;

        final ArrayList<OnGesturingListener> listeners = mOnGesturingListeners;
        int count = listeners.size();
        for (int i = 0; i < count; i++) {
            listeners.get(i).onGesturingEnded(this);
        }
    }

    private void cancelGesture(MotionEvent event) {
        // pass the event to handlers
        final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
        final int count = listeners.size();
        for (int i = 0; i < count; i++) {
            listeners.get(i).onGestureCancelled(this, event);
        }

        clear(false);
    }

    private void fireOnGesturePerformed() {
        final ArrayList<OnGesturePerformedListener> actionListeners = mOnGesturePerformedListeners;
        final int count = actionListeners.size();
        for (int i = 0; i < count; i++) {
            actionListeners.get(i).onGesturePerformed(GestureOverlayView.this, mCurrentGesture);
        }
    }

    private class FadeOutRunnable implements Runnable {
        boolean fireActionPerformed;
        boolean resetMultipleStrokes;

        public void run() {
            if (mIsFadingOut) {
                final long now = AnimationUtils.currentAnimationTimeMillis();
                final long duration = now - mFadingStart;

                if (duration > mFadeDuration) {
                    if (fireActionPerformed) {
                        fireOnGesturePerformed();
                    }

                    mPreviousWasGesturing = false;
                    mIsFadingOut = false;
                    mFadingHasStarted = false;
                    mPath.rewind();
                    mCurrentGesture = null;
                    setPaintAlpha(255);
                } else {
                    mFadingHasStarted = true;
                    float interpolatedTime = Math.max(0.0f,
                            Math.min(1.0f, duration / (float) mFadeDuration));
                    mFadingAlpha = 1.0f - mInterpolator.getInterpolation(interpolatedTime);
                    setPaintAlpha((int) (255 * mFadingAlpha));
                    postDelayed(this, FADE_ANIMATION_RATE);
                }
            } else if (resetMultipleStrokes) {
                mResetGesture = true;
            } else {
                fireOnGesturePerformed();

                mFadingHasStarted = false;
                mPath.rewind();
                mCurrentGesture = null;
                mPreviousWasGesturing = false;
                setPaintAlpha(255);
            }

            invalidate();
        }
    }

    public static interface OnGesturingListener {
        void onGesturingStarted(GestureOverlayView overlay);

        void onGesturingEnded(GestureOverlayView overlay);
    }

    public static interface OnGestureListener {
        void onGestureStarted(GestureOverlayView overlay, MotionEvent event);

        void onGesture(GestureOverlayView overlay, MotionEvent event);

        void onGestureEnded(GestureOverlayView overlay, MotionEvent event);

        void onGestureCancelled(GestureOverlayView overlay, MotionEvent event);
    }

    public static interface OnGesturePerformedListener {
        void onGesturePerformed(GestureOverlayView overlay, Gesture gesture);
    }
}
