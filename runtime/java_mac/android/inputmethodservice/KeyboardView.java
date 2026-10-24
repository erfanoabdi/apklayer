package android.inputmethodservice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.Arrays;
import java.util.List;

import android.inputmethodservice.Keyboard.Key;

/** A view that renders a {@link Keyboard} and reports taps, long-presses,
 *  repeats and swipes through {@link OnKeyboardActionListener}.
 *
 *  Deprecated at API 29, but still shipped and still used -- and unlike the
 *  rest of android.inputmethodservice, THIS class works fully on a single-app
 *  host, because it is just a View. An app that embeds a KeyboardView as an
 *  in-app keypad gets real behaviour here; only an app that IS an input method
 *  is blocked, and it is blocked upstream of this class (nothing binds the
 *  service). So this is implemented, not stubbed.
 *
 *  THREE DELIBERATE DEVIATIONS, each because the platform resource AOSP reaches
 *  for does not exist in this tree, and each landing on a state AOSP itself has:
 *
 *  1. ATTRIBUTES BY NAME. AOSP reads keyBackground / keyTextSize /
 *     labelTextSize / keyTextColor / verticalCorrection / keyPreviewLayout /
 *     popupLayout / shadowColor / shadowRadius through
 *     com.android.internal.R.styleable.KeyboardView. There is no
 *     com.android.internal.R here, so they are read from the same AttributeSet
 *     by their public android-namespace names (AOSP attrs.xml,
 *     declare-styleable KeyboardView). Consequence worth knowing: values set
 *     INLINE in the app's layout XML are honoured; values inherited from the
 *     platform's keyboardViewStyle are not, because that style is a platform
 *     resource this tree does not ship. Where AOSP would have inherited
 *     @drawable/btn_keyboard_key, mKeyBackground stays null and onDraw paints a
 *     plain key face instead of throwing.
 *
 *  2. NO OFFSCREEN BUFFER. AOSP renders into an ARGB_8888 Bitmap and blits it,
 *     with a dirty-rect fast path for a single invalidated key. That is a
 *     rendering OPTIMISATION, not an observable behaviour: the same keys land
 *     at the same pixels either way, so onDraw paints straight onto the
 *     supplied Canvas. invalidateKey() therefore just invalidates the key's
 *     rectangle rather than redrawing the buffer first.
 *
 *  3. NO PREVIEW POPUP AND NO MINI-KEYBOARD. Both need a real PopupWindow, and
 *     both are driven by resource ids (keyPreviewLayout / popupLayout) that
 *     only the platform style supplies. AOSP's own behaviour when those ids are
 *     0 is `mShowPreview = false` and, for a long press, `return false` -- which
 *     is exactly the path taken here. onPressed/onReleased state and the
 *     invalidate that draws the pressed key still happen, so a key still looks
 *     pressed.
 *
 *  API 3. Source: frameworks/base
 *  core/java/android/inputmethodservice/KeyboardView.java (android16-release). */
@Deprecated
public class KeyboardView extends View implements View.OnClickListener {

    /** Listener for virtual keyboard events. */
    @Deprecated
    public interface OnKeyboardActionListener {
        /** Called when the user presses a key, before onKey. */
        void onPress(int primaryCode);
        /** Called when the user releases a key. */
        void onRelease(int primaryCode);
        /** Send a key press to the listener.
         *  @param primaryCode the code of the key pressed
         *  @param keyCodes the codes for all the possible alternative keys with
         *         the primary code being the first, ordered by increasing
         *         distance from the touch point. */
        void onKey(int primaryCode, int[] keyCodes);
        /** Sends a sequence of characters to the listener. */
        void onText(CharSequence text);
        void swipeLeft();
        void swipeRight();
        void swipeDown();
        void swipeUp();
    }

    private static final boolean DEBUG = false;

    private static final int NOT_A_KEY = -1;
    private static final int[] KEY_DELETE = { Keyboard.KEYCODE_DELETE };

    private static final int MSG_REPEAT = 3;
    private static final int MSG_LONGPRESS = 4;

    private static final int DEBOUNCE_TIME = 70;
    private static final int REPEAT_INTERVAL = 50;   // ~20 keys per second
    private static final int REPEAT_START_DELAY = 400;
    private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static final int MULTITAP_INTERVAL = 800; // milliseconds
    private static final int MAX_NEARBY_KEYS = 12;

    private Keyboard mKeyboard;
    private Key[] mKeys;
    private OnKeyboardActionListener mKeyboardActionListener;

    private int mCurrentKeyIndex = NOT_A_KEY;

    private int mLabelTextSize;
    private int mKeyTextSize;
    private int mKeyTextColor = 0xFF000000;
    private float mShadowRadius;
    private int mShadowColor;
    private float mBackgroundDimAmount = 0.5f;

    private int mPreviewOffset;
    private int mPreviewHeight;
    private int mPopupLayout;
    private boolean mShowPreview = true;

    private int mVerticalCorrection;
    private int mProximityThreshold;

    private int mLastX;
    private int mLastY;
    private int mStartX;
    private int mStartY;

    private boolean mProximityCorrectOn;
    private Paint mPaint;
    private Rect mPadding;

    private long mDownTime;
    private long mLastMoveTime;
    private int mLastKey;
    private int mLastCodeX;
    private int mLastCodeY;
    private int mCurrentKey = NOT_A_KEY;
    private int mDownKey = NOT_A_KEY;
    private long mLastKeyTime;
    private long mCurrentKeyTime;
    private int[] mKeyIndices = new int[MAX_NEARBY_KEYS];

    private GestureDetector mGestureDetector;
    private int mRepeatKeyIndex = NOT_A_KEY;
    private boolean mAbortKey;
    private boolean mMiniKeyboardOnScreen;
    private View mPopupParent;
    private int mMiniKeyboardOffsetX;
    private int mMiniKeyboardOffsetY;

    private boolean mPossiblePoly;
    private final SwipeTracker mSwipeTracker = new SwipeTracker();
    private int mSwipeThreshold;
    private boolean mDisambiguateSwipe;

    private int mOldPointerCount = 1;
    private float mOldPointerX;
    private float mOldPointerY;

    private Drawable mKeyBackground;

    private int[] mDistances = new int[MAX_NEARBY_KEYS];

    private int mLastSentIndex;
    private int mTapCount;
    private long mLastTapTime;
    private boolean mInMultiTap;

    private Rect mDirtyRect = new Rect();

    private Handler mHandler;

    public KeyboardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public KeyboardView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);

        // Deviation 1 (see the class comment): read the KeyboardView styleable's
        // attributes by their public android-namespace names off the same
        // AttributeSet, since com.android.internal.R.styleable.KeyboardView does
        // not exist here. Defaults are AOSP's own per-attribute defaults from
        // the switch in its constructor.
        if (attrs != null) {
            mKeyBackground = readDrawableAttr(attrs, "keyBackground");
            mVerticalCorrection = readDimenAttr(attrs, "verticalCorrection", 0);
            mPreviewOffset = readDimenAttr(attrs, "keyPreviewOffset", 0);
            mPreviewHeight = readDimenAttr(attrs, "keyPreviewHeight", 80);
            mKeyTextSize = readDimenAttr(attrs, "keyTextSize", 18);
            mKeyTextColor = readColorAttr(attrs, "keyTextColor", 0xFF000000);
            mLabelTextSize = readDimenAttr(attrs, "labelTextSize", 14);
            mPopupLayout = attrs.getAttributeResourceValue(
                    Keyboard.ANDROID_NS, "popupLayout", 0);
            mShadowColor = readColorAttr(attrs, "shadowColor", 0);
            mShadowRadius = attrs.getAttributeFloatValue(
                    Keyboard.ANDROID_NS, "shadowRadius", 0f);
        } else {
            mKeyTextSize = 18;
            mLabelTextSize = 14;
            mPreviewHeight = 80;
        }

        // AOSP inflates keyPreviewLayout here and, when it is 0, sets
        // mShowPreview = false. There is no platform preview layout to inflate
        // (deviation 3), so that is unconditionally the branch taken.
        mShowPreview = false;

        mPopupParent = this;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setTextSize(mKeyTextSize);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setAlpha(255);

        mPadding = new Rect(0, 0, 0, 0);
        if (mKeyBackground != null) {
            mKeyBackground.getPadding(mPadding);
        }

        // AOSP: 500 dp/s is a swipe, and config_swipeDisambiguation is `true`
        // in core/res/res/values/config.xml (android16-release, line 1969), so
        // the SwipeTracker path below is live rather than dead code.
        mSwipeThreshold = (int) (500 * getResources().getDisplayMetrics().density);
        mDisambiguateSwipe = true;

        mHandler = new Handler() {
            @Override public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_REPEAT:
                        if (repeatKey()) {
                            Message repeat = obtainMessage(MSG_REPEAT);
                            sendMessageDelayed(repeat, REPEAT_INTERVAL);
                        }
                        break;
                    case MSG_LONGPRESS:
                        openPopupIfRequired((MotionEvent) msg.obj);
                        break;
                    default:
                        break;
                }
            }
        };

        resetMultiTap();
        initGestureDetector();
    }

    private void initGestureDetector() {
        if (mGestureDetector == null) {
            mGestureDetector = new GestureDetector(getContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onFling(MotionEvent me1, MotionEvent me2,
                        float velocityX, float velocityY) {
                    if (mPossiblePoly) return false;
                    final float absX = Math.abs(velocityX);
                    final float absY = Math.abs(velocityY);
                    float deltaX = me2.getX() - me1.getX();
                    float deltaY = me2.getY() - me1.getY();
                    int travelX = getWidth() / 2;   // Half the keyboard width
                    int travelY = getHeight() / 2;  // Half the keyboard height
                    mSwipeTracker.computeCurrentVelocity(1000);
                    final float endingVelocityX = mSwipeTracker.getXVelocity();
                    final float endingVelocityY = mSwipeTracker.getYVelocity();
                    boolean sendDownKey = false;
                    if (velocityX > mSwipeThreshold && absY < absX && deltaX > travelX) {
                        if (mDisambiguateSwipe && endingVelocityX < velocityX / 4) {
                            sendDownKey = true;
                        } else {
                            swipeRight();
                            return true;
                        }
                    } else if (velocityX < -mSwipeThreshold && absY < absX && deltaX < -travelX) {
                        if (mDisambiguateSwipe && endingVelocityX > velocityX / 4) {
                            sendDownKey = true;
                        } else {
                            swipeLeft();
                            return true;
                        }
                    } else if (velocityY < -mSwipeThreshold && absX < absY && deltaY < -travelY) {
                        if (mDisambiguateSwipe && endingVelocityY > velocityY / 4) {
                            sendDownKey = true;
                        } else {
                            swipeUp();
                            return true;
                        }
                    } else if (velocityY > mSwipeThreshold && absX < absY / 2 && deltaY > travelY) {
                        if (mDisambiguateSwipe && endingVelocityY < velocityY / 4) {
                            sendDownKey = true;
                        } else {
                            swipeDown();
                            return true;
                        }
                    }
                    if (sendDownKey) {
                        detectAndSendKey(mDownKey, mStartX, mStartY, me1.getEventTime());
                    }
                    return false;
                }
            });
            mGestureDetector.setIsLongpressEnabled(false);
        }
    }

    public void setOnKeyboardActionListener(OnKeyboardActionListener listener) {
        mKeyboardActionListener = listener;
    }

    /** Returns the OnKeyboardActionListener object. */
    protected OnKeyboardActionListener getOnKeyboardActionListener() {
        return mKeyboardActionListener;
    }

    /** Attaches a keyboard to this view. The keyboard can be switched at any
     *  time and the view will re-layout itself to accommodate it. */
    public void setKeyboard(Keyboard keyboard) {
        if (mKeyboard != null) {
            showPreview(NOT_A_KEY);
        }
        removeMessages();
        mKeyboard = keyboard;
        List<Key> keys = mKeyboard.getKeys();
        mKeys = keys.toArray(new Key[keys.size()]);
        requestLayout();
        invalidateAllKeys();
        computeProximityThreshold(keyboard);
        mAbortKey = true; // Until the next ACTION_DOWN
    }

    public Keyboard getKeyboard() {
        return mKeyboard;
    }

    /** Sets the state of the shift key of the keyboard, if any.
     *  @return true if the shift key state changed, false if there was no shift
     *          key on the keyboard or there was no change. */
    public boolean setShifted(boolean shifted) {
        if (mKeyboard != null) {
            if (mKeyboard.setShifted(shifted)) {
                invalidateAllKeys();
                return true;
            }
        }
        return false;
    }

    public boolean isShifted() {
        if (mKeyboard != null) {
            return mKeyboard.isShifted();
        }
        return false;
    }

    /** Enables or disables the key feedback popup. See deviation 3: with no
     *  platform preview layout there is nothing to pop up, so this stores the
     *  flag (which isPreviewEnabled() reports back) without producing a popup --
     *  the same state AOSP is in when keyPreviewLayout is 0. */
    public void setPreviewEnabled(boolean previewEnabled) {
        mShowPreview = previewEnabled;
    }

    public boolean isPreviewEnabled() {
        return mShowPreview;
    }

    public void setVerticalCorrection(int verticalOffset) {
        // AOSP's body is empty too -- the field is set from the attribute only.
    }

    public void setPopupParent(View v) {
        mPopupParent = v;
    }

    public void setPopupOffset(int x, int y) {
        mMiniKeyboardOffsetX = x;
        mMiniKeyboardOffsetY = y;
    }

    /** When enabled, calls to onKey will include key codes for adjacent keys.
     *  When disabled, only the primary key code will be reported. */
    public void setProximityCorrectionEnabled(boolean enabled) {
        mProximityCorrectOn = enabled;
    }

    public boolean isProximityCorrectionEnabled() {
        return mProximityCorrectOn;
    }

    /** AOSP: the close button of the mini keyboard. */
    @Override public void onClick(View v) {
        dismissPopupKeyboard();
    }

    @Override public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mKeyboard == null) {
            setMeasuredDimension(getPaddingLeft() + getPaddingRight(),
                    getPaddingTop() + getPaddingBottom());
        } else {
            int width = mKeyboard.getMinWidth() + getPaddingLeft() + getPaddingRight();
            if (MeasureSpec.getSize(widthMeasureSpec) < width + 10) {
                width = MeasureSpec.getSize(widthMeasureSpec);
            }
            setMeasuredDimension(width,
                    mKeyboard.getHeight() + getPaddingTop() + getPaddingBottom());
        }
    }

    @Override public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mKeyboard != null) {
            mKeyboard.resize(w, h);
        }
    }

    /** Deviation 2: AOSP draws into an offscreen Bitmap and blits it here. The
     *  key loop below is AOSP's onBufferDraw() verbatim apart from the target
     *  Canvas, so the pixels are the same. */
    @Override public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mKeyboard == null || mKeys == null) return;

        final Paint paint = mPaint;
        final Drawable keyBackground = mKeyBackground;
        final Rect padding = mPadding;
        final int kbdPaddingLeft = getPaddingLeft();
        final int kbdPaddingTop = getPaddingTop();
        final Key[] keys = mKeys;

        paint.setColor(mKeyTextColor);

        final int keyCount = keys.length;
        for (int i = 0; i < keyCount; i++) {
            final Key key = keys[i];
            int[] drawableState = key.getCurrentDrawableState();

            String label = key.label == null ? null : adjustCase(key.label).toString();

            canvas.translate(key.x + kbdPaddingLeft, key.y + kbdPaddingTop);
            if (keyBackground != null) {
                keyBackground.setState(drawableState);
                final Rect bounds = keyBackground.getBounds();
                if (key.width != bounds.right || key.height != bounds.bottom) {
                    keyBackground.setBounds(0, 0, key.width, key.height);
                }
                keyBackground.draw(canvas);
            } else {
                // Deviation 1: the platform's btn_keyboard_key state-list
                // drawable is not available. Paint a flat key face so the
                // keyboard is visible and the pressed state still reads,
                // rather than NPE-ing on a null background.
                int face = key.pressed ? 0xFFB0B0B0 : (key.on ? 0xFFC8C8C8 : 0xFFE0E0E0);
                int saved = paint.getColor();
                paint.setColor(face);
                canvas.drawRect(0, 0, key.width, key.height, paint);
                paint.setColor(saved);
            }

            if (label != null) {
                // For characters, use large font. For labels like "Done", use
                // small font.
                if (label.length() > 1 && key.codes != null && key.codes.length < 2) {
                    paint.setTextSize(mLabelTextSize);
                    paint.setTypeface(Typeface.DEFAULT_BOLD);
                } else {
                    paint.setTextSize(mKeyTextSize);
                    paint.setTypeface(Typeface.DEFAULT);
                }
                paint.setShadowLayer(mShadowRadius, 0, 0, mShadowColor);
                canvas.drawText(label,
                        (key.width - padding.left - padding.right) / 2 + padding.left,
                        (key.height - padding.top - padding.bottom) / 2
                                + (paint.getTextSize() - paint.descent()) / 2 + padding.top,
                        paint);
                paint.setShadowLayer(0, 0, 0, 0);
            } else if (key.icon != null) {
                final int drawableX = (key.width - padding.left - padding.right
                        - key.icon.getIntrinsicWidth()) / 2 + padding.left;
                final int drawableY = (key.height - padding.top - padding.bottom
                        - key.icon.getIntrinsicHeight()) / 2 + padding.top;
                canvas.translate(drawableX, drawableY);
                key.icon.setBounds(0, 0,
                        key.icon.getIntrinsicWidth(), key.icon.getIntrinsicHeight());
                key.icon.draw(canvas);
                canvas.translate(-drawableX, -drawableY);
            }
            canvas.translate(-key.x - kbdPaddingLeft, -key.y - kbdPaddingTop);
        }

        if (mMiniKeyboardOnScreen) {
            paint.setColor((int) (mBackgroundDimAmount * 0xFF) << 24);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
        mDirtyRect.setEmpty();
    }

    /** AOSP: fills allKeys with the codes of every key within
     *  mProximityThreshold of (x,y), nearest first, and returns the index of
     *  the key actually under the point (falling back to the closest). */
    private int getKeyIndices(int x, int y, int[] allKeys) {
        final Key[] keys = mKeys;
        int primaryIndex = NOT_A_KEY;
        int closestKey = NOT_A_KEY;
        int closestKeyDist = mProximityThreshold + 1;
        Arrays.fill(mDistances, Integer.MAX_VALUE);
        int[] nearestKeyIndices = mKeyboard.getNearestKeys(x, y);
        final int keyCount = nearestKeyIndices.length;
        for (int i = 0; i < keyCount; i++) {
            final Key key = keys[nearestKeyIndices[i]];
            int dist = 0;
            boolean isInside = key.isInside(x, y);
            if (isInside) {
                primaryIndex = nearestKeyIndices[i];
            }
            if (key.codes == null || key.codes.length == 0) {
                continue;
            }
            if (((mProximityCorrectOn
                    && (dist = key.squaredDistanceFrom(x, y)) < mProximityThreshold)
                    || isInside)
                    && key.codes[0] > 32) {
                // Find insertion point
                final int nCodes = key.codes.length;
                if (dist < closestKeyDist) {
                    closestKeyDist = dist;
                    closestKey = nearestKeyIndices[i];
                }
                if (allKeys == null) continue;
                for (int j = 0; j < mDistances.length; j++) {
                    if (mDistances[j] > dist) {
                        // Make space at position j
                        System.arraycopy(mDistances, j, mDistances, j + nCodes,
                                mDistances.length - j - nCodes);
                        System.arraycopy(allKeys, j, allKeys, j + nCodes,
                                allKeys.length - j - nCodes);
                        for (int c = 0; c < nCodes; c++) {
                            allKeys[j + c] = key.codes[c];
                            mDistances[j + c] = dist;
                        }
                        break;
                    }
                }
            }
        }
        if (primaryIndex == NOT_A_KEY) {
            primaryIndex = closestKey;
        }
        return primaryIndex;
    }

    private void detectAndSendKey(int index, int x, int y, long eventTime) {
        if (index != NOT_A_KEY && mKeys != null && index < mKeys.length) {
            final Key key = mKeys[index];
            final OnKeyboardActionListener l = mKeyboardActionListener;
            if (key.text != null) {
                if (l != null) {
                    l.onText(key.text);
                    l.onRelease(NOT_A_KEY);
                }
            } else {
                if (key.codes == null || key.codes.length == 0) {
                    return;
                }
                int code = key.codes[0];
                int[] codes = new int[MAX_NEARBY_KEYS];
                Arrays.fill(codes, NOT_A_KEY);
                getKeyIndices(x, y, codes);
                // Multi-tap
                if (mInMultiTap) {
                    if (mTapCount != -1) {
                        if (l != null) l.onKey(Keyboard.KEYCODE_DELETE, KEY_DELETE);
                    } else {
                        mTapCount = 0;
                    }
                    code = key.codes[Math.max(0, Math.min(mTapCount, key.codes.length - 1))];
                }
                if (l != null) {
                    l.onKey(code, codes);
                    l.onRelease(code);
                }
            }
            mLastSentIndex = index;
            mLastTapTime = eventTime;
        }
    }

    /** AOSP's adjustCase: a short lowercase label is uppercased while the
     *  keyboard is shifted, so a layout does not have to declare both cases. */
    private CharSequence adjustCase(CharSequence label) {
        if (mKeyboard.isShifted() && label != null && label.length() < 3
                && Character.isLowerCase(label.charAt(0))) {
            label = label.toString().toUpperCase();
        }
        return label;
    }

    /** Requests a redraw of the entire keyboard. */
    public void invalidateAllKeys() {
        mDirtyRect.union(0, 0, getWidth(), getHeight());
        invalidate();
    }

    /** Invalidates a key so that it will be redrawn on the next repaint. */
    public void invalidateKey(int keyIndex) {
        if (mKeys == null) return;
        if (keyIndex < 0 || keyIndex >= mKeys.length) {
            return;
        }
        final Key key = mKeys[keyIndex];
        mDirtyRect.union(key.x + getPaddingLeft(), key.y + getPaddingTop(),
                key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
        invalidate(key.x + getPaddingLeft(), key.y + getPaddingTop(),
                key.x + key.width + getPaddingLeft(), key.y + key.height + getPaddingTop());
    }

    /** Called when a key is long pressed. By default this opens the mini
     *  keyboard associated with the key.
     *
     *  Deviation 3: the mini keyboard needs both a platform popupLayout and a
     *  PopupWindow that actually presents. When popupResId or mPopupLayout is 0
     *  AOSP returns false here, which is the branch this host always takes. */
    protected boolean onLongPress(Key popupKey) {
        int popupKeyboardId = popupKey.popupResId;
        if (popupKeyboardId != 0 && mPopupLayout != 0) {
            // Nothing can present the popup on this host; report unhandled so
            // the caller keeps treating the gesture as a plain press.
            return false;
        }
        return false;
    }

    private void openPopupIfRequired(MotionEvent me) {
        if (mCurrentKey < 0 || mKeys == null || mCurrentKey >= mKeys.length) {
            return;
        }
        Key popupKey = mKeys[mCurrentKey];
        boolean result = onLongPress(popupKey);
        if (result) {
            mAbortKey = true;
            showPreview(NOT_A_KEY);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent me) {
        // Convert multi-pointer up/down events to single up/down, exactly as
        // AOSP does: this view is single-touch and a second finger must not be
        // read as a move of the first.
        final int pointerCount = me.getPointerCount();
        final int action = me.getAction();
        boolean result = false;
        final long now = me.getEventTime();

        if (pointerCount != mOldPointerCount) {
            if (pointerCount == 1) {
                // Send a down event for the latest pointer
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN,
                        me.getX(), me.getY(), me.getMetaState());
                result = onModifiedTouchEvent(down, false);
                down.recycle();
                // If it is an up action, then deliver the up as well.
                if (action == MotionEvent.ACTION_UP) {
                    result = onModifiedTouchEvent(me, true);
                }
            } else {
                // Send an up event for the last pointer
                MotionEvent up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP,
                        mOldPointerX, mOldPointerY, me.getMetaState());
                result = onModifiedTouchEvent(up, true);
                up.recycle();
            }
        } else {
            if (pointerCount == 1) {
                result = onModifiedTouchEvent(me, false);
                mOldPointerX = me.getX();
                mOldPointerY = me.getY();
            } else {
                // Don't do anything when 2 pointers are down and moving.
                result = true;
            }
        }
        mOldPointerCount = pointerCount;

        return result;
    }

    private boolean onModifiedTouchEvent(MotionEvent me, boolean possiblePoly) {
        if (mKeyboard == null || mKeys == null) {
            return false;
        }
        int touchX = (int) me.getX() - getPaddingLeft();
        int touchY = (int) me.getY() - getPaddingTop();
        if (touchY >= -mVerticalCorrection) {
            touchY += mVerticalCorrection;
        }
        final int action = me.getAction();
        final long eventTime = me.getEventTime();
        int keyIndex = getKeyIndices(touchX, touchY, null);
        mPossiblePoly = possiblePoly;

        // Track the last few movements to look for spurious swipes.
        if (action == MotionEvent.ACTION_DOWN) mSwipeTracker.clear();
        mSwipeTracker.addMovement(me);

        // Ignore all motion events until a DOWN.
        if (mAbortKey
                && action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_CANCEL) {
            return true;
        }

        if (mGestureDetector.onTouchEvent(me)) {
            showPreview(NOT_A_KEY);
            mHandler.removeMessages(MSG_REPEAT);
            mHandler.removeMessages(MSG_LONGPRESS);
            return true;
        }

        // Needs to be called after the gesture detector gets a turn, as it may
        // have displayed the mini keyboard.
        if (mMiniKeyboardOnScreen && action != MotionEvent.ACTION_CANCEL) {
            return true;
        }

        final OnKeyboardActionListener l = mKeyboardActionListener;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mAbortKey = false;
                mStartX = touchX;
                mStartY = touchY;
                mLastCodeX = touchX;
                mLastCodeY = touchY;
                mLastKeyTime = 0;
                mCurrentKeyTime = 0;
                mLastKey = NOT_A_KEY;
                mCurrentKey = keyIndex;
                mDownKey = keyIndex;
                mDownTime = me.getEventTime();
                mLastMoveTime = mDownTime;
                checkMultiTap(eventTime, keyIndex);
                if (l != null) {
                    l.onPress(keyIndex != NOT_A_KEY && mKeys[keyIndex].codes != null
                            && mKeys[keyIndex].codes.length > 0
                            ? mKeys[keyIndex].codes[0] : 0);
                }
                if (mCurrentKey >= 0 && mKeys[mCurrentKey].repeatable) {
                    mRepeatKeyIndex = mCurrentKey;
                    Message msg = mHandler.obtainMessage(MSG_REPEAT);
                    mHandler.sendMessageDelayed(msg, REPEAT_START_DELAY);
                    repeatKey();
                    // Delivering the key could have caused an abort.
                    if (mAbortKey) {
                        mRepeatKeyIndex = NOT_A_KEY;
                        break;
                    }
                }
                if (mCurrentKey != NOT_A_KEY) {
                    Message msg = mHandler.obtainMessage(MSG_LONGPRESS, me);
                    mHandler.sendMessageDelayed(msg, LONGPRESS_TIMEOUT);
                }
                showPreview(keyIndex);
                break;

            case MotionEvent.ACTION_MOVE:
                boolean continueLongPress = false;
                if (keyIndex != NOT_A_KEY) {
                    if (mCurrentKey == NOT_A_KEY) {
                        mCurrentKey = keyIndex;
                        mCurrentKeyTime = eventTime - mDownTime;
                    } else {
                        if (keyIndex == mCurrentKey) {
                            mCurrentKeyTime += eventTime - mLastMoveTime;
                            continueLongPress = true;
                        } else if (mRepeatKeyIndex == NOT_A_KEY) {
                            resetMultiTap();
                            mLastKey = mCurrentKey;
                            mLastCodeX = mLastX;
                            mLastCodeY = mLastY;
                            mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime;
                            mCurrentKey = keyIndex;
                            mCurrentKeyTime = 0;
                        }
                    }
                }
                if (!continueLongPress) {
                    // Cancel old long press
                    mHandler.removeMessages(MSG_LONGPRESS);
                    // Start new long press if the key has changed
                    if (keyIndex != NOT_A_KEY) {
                        Message msg = mHandler.obtainMessage(MSG_LONGPRESS, me);
                        mHandler.sendMessageDelayed(msg, LONGPRESS_TIMEOUT);
                    }
                }
                showPreview(mCurrentKey);
                mLastMoveTime = eventTime;
                break;

            case MotionEvent.ACTION_UP:
                removeMessages();
                if (keyIndex == mCurrentKey) {
                    mCurrentKeyTime += eventTime - mLastMoveTime;
                } else {
                    resetMultiTap();
                    mLastKey = mCurrentKey;
                    mLastKeyTime = mCurrentKeyTime + eventTime - mLastMoveTime;
                    mCurrentKey = keyIndex;
                    mCurrentKeyTime = 0;
                }
                if (mCurrentKeyTime < mLastKeyTime && mCurrentKeyTime < DEBOUNCE_TIME
                        && mLastKey != NOT_A_KEY) {
                    mCurrentKey = mLastKey;
                    touchX = mLastCodeX;
                    touchY = mLastCodeY;
                }
                showPreview(NOT_A_KEY);
                Arrays.fill(mKeyIndices, NOT_A_KEY);
                // If we're not on a repeating key (which sends on a DOWN event)
                if (mRepeatKeyIndex == NOT_A_KEY && !mMiniKeyboardOnScreen && !mAbortKey) {
                    detectAndSendKey(mCurrentKey, touchX, touchY, eventTime);
                }
                invalidateKey(keyIndex);
                mRepeatKeyIndex = NOT_A_KEY;
                break;

            case MotionEvent.ACTION_CANCEL:
                removeMessages();
                dismissPopupKeyboard();
                mAbortKey = true;
                showPreview(NOT_A_KEY);
                invalidateKey(mCurrentKey);
                break;

            default:
                break;
        }
        mLastX = touchX;
        mLastY = touchY;
        return true;
    }

    /** AOSP: re-send the held key and report whether the repeat should
     *  continue. */
    private boolean repeatKey() {
        if (mKeys == null || mRepeatKeyIndex < 0 || mRepeatKeyIndex >= mKeys.length) {
            return false;
        }
        Key key = mKeys[mRepeatKeyIndex];
        detectAndSendKey(mCurrentKey, key.x, key.y, mLastTapTime);
        return true;
    }

    /** AOSP's showPreview() does two separate jobs: it maintains the pressed/
     *  released state of the outgoing and incoming keys (and invalidates them),
     *  and it drives the preview popup. Only the second needs a PopupWindow, so
     *  the first is kept intact and the second is the branch AOSP takes when
     *  there is no preview layout. */
    private void showPreview(int keyIndex) {
        int oldKeyIndex = mCurrentKeyIndex;
        mCurrentKeyIndex = keyIndex;
        final Key[] keys = mKeys;
        if (keys == null) return;
        if (oldKeyIndex != mCurrentKeyIndex) {
            if (oldKeyIndex != NOT_A_KEY && keys.length > oldKeyIndex) {
                Key oldKey = keys[oldKeyIndex];
                oldKey.onReleased(mCurrentKeyIndex == NOT_A_KEY);
                invalidateKey(oldKeyIndex);
            }
            if (mCurrentKeyIndex != NOT_A_KEY && keys.length > mCurrentKeyIndex) {
                Key newKey = keys[mCurrentKeyIndex];
                newKey.onPressed();
                invalidateKey(mCurrentKeyIndex);
            }
        }
    }

    private void computeProximityThreshold(Keyboard keyboard) {
        if (keyboard == null) return;
        final Key[] keys = mKeys;
        if (keys == null) return;
        int length = keys.length;
        int dimensionSum = 0;
        for (int i = 0; i < length; i++) {
            Key key = keys[i];
            dimensionSum += Math.min(key.width, key.height) + key.gap;
        }
        if (dimensionSum < 0 || length == 0) return;
        mProximityThreshold = (int) (dimensionSum * 1.4f / length);
        mProximityThreshold *= mProximityThreshold; // Square it
    }

    protected void swipeRight() {
        if (mKeyboardActionListener != null) mKeyboardActionListener.swipeRight();
    }

    protected void swipeLeft() {
        if (mKeyboardActionListener != null) mKeyboardActionListener.swipeLeft();
    }

    protected void swipeUp() {
        if (mKeyboardActionListener != null) mKeyboardActionListener.swipeUp();
    }

    protected void swipeDown() {
        if (mKeyboardActionListener != null) mKeyboardActionListener.swipeDown();
    }

    /** AOSP dismisses the preview and mini keyboard and drops the offscreen
     *  buffer. Neither popup exists here (deviation 3) and there is no buffer
     *  (deviation 2), so what remains is cancelling the pending repeat and
     *  long-press messages -- which is the part that actually matters, since a
     *  live message would fire against a detached view. */
    public void closing() {
        removeMessages();
        dismissPopupKeyboard();
    }

    private void removeMessages() {
        if (mHandler != null) {
            mHandler.removeMessages(MSG_REPEAT);
            mHandler.removeMessages(MSG_LONGPRESS);
        }
    }

    @Override public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        closing();
    }

    private void dismissPopupKeyboard() {
        if (mMiniKeyboardOnScreen) {
            mMiniKeyboardOnScreen = false;
            invalidateAllKeys();
        }
    }

    /** AOSP: true if the back key was consumed to dismiss the mini keyboard. */
    public boolean handleBack() {
        if (mMiniKeyboardOnScreen) {
            dismissPopupKeyboard();
            return true;
        }
        return false;
    }

    private void resetMultiTap() {
        mLastSentIndex = NOT_A_KEY;
        mTapCount = 0;
        mLastTapTime = -1;
        mInMultiTap = false;
    }

    private void checkMultiTap(long eventTime, int keyIndex) {
        if (keyIndex == NOT_A_KEY) return;
        Key key = mKeys[keyIndex];
        if (key.codes != null && key.codes.length > 1) {
            mInMultiTap = true;
            if (eventTime < mLastTapTime + MULTITAP_INTERVAL && keyIndex == mLastSentIndex) {
                mTapCount = (mTapCount + 1) % key.codes.length;
                return;
            } else {
                mTapCount = -1;
                return;
            }
        }
        if (eventTime > mLastTapTime + MULTITAP_INTERVAL || keyIndex != mLastSentIndex) {
            resetMultiTap();
        }
    }

    // -----------------------------------------------------------------------
    // Attribute readers -- see deviation 1.
    // -----------------------------------------------------------------------

    private Drawable readDrawableAttr(AttributeSet attrs, String name) {
        int id = attrs.getAttributeResourceValue(Keyboard.ANDROID_NS, name, 0);
        if (id == 0 || getResources() == null) return null;
        try {
            return getResources().getDrawable(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private int readDimenAttr(AttributeSet attrs, String name, int defValue) {
        // Keyboard.getDimensionOrFraction wants an XmlResourceParser; the
        // KeyboardView attributes are plain dimensions with no fraction form
        // (AOSP declares them format="dimension"), so the base is irrelevant.
        String v = attrs.getAttributeValue(Keyboard.ANDROID_NS, name);
        if (v == null) return defValue;
        if (!v.isEmpty() && (v.charAt(0) == '@' || v.charAt(0) == '?')) {
            int id = attrs.getAttributeResourceValue(Keyboard.ANDROID_NS, name, 0);
            if (id == 0 || getResources() == null) return defValue;
            try {
                return getResources().getDimensionPixelSize(id);
            } catch (RuntimeException e) {
                return defValue;
            }
        }
        return Keyboard.parseDimensionValue(getResources(), v, defValue);
    }

    private int readColorAttr(AttributeSet attrs, String name, int defValue) {
        String v = attrs.getAttributeValue(Keyboard.ANDROID_NS, name);
        if (v == null) return defValue;
        v = v.trim();
        if (v.startsWith("#")) {
            try {
                return android.graphics.Color.parseColor(v);
            } catch (RuntimeException e) {
                return defValue;
            }
        }
        int id = attrs.getAttributeResourceValue(Keyboard.ANDROID_NS, name, 0);
        if (id == 0 || getResources() == null) return defValue;
        try {
            return getResources().getColor(id);
        } catch (RuntimeException e) {
            return defValue;
        }
    }

    /** AOSP's private SwipeTracker: a four-sample velocity estimator used to
     *  tell a real swipe from the tail of a tap. Reproduced verbatim because
     *  config_swipeDisambiguation is true, so it is on the live path. */
    private static class SwipeTracker {
        static final int NUM_PAST = 4;
        static final int LONGEST_PAST_TIME = 200;

        final float[] mPastX = new float[NUM_PAST];
        final float[] mPastY = new float[NUM_PAST];
        final long[] mPastTime = new long[NUM_PAST];

        float mYVelocity;
        float mXVelocity;

        public void clear() {
            mPastTime[0] = 0;
        }

        public void addMovement(MotionEvent ev) {
            long time = ev.getEventTime();
            final int N = ev.getHistorySize();
            for (int i = 0; i < N; i++) {
                addPoint(ev.getHistoricalX(i), ev.getHistoricalY(i),
                        ev.getHistoricalEventTime(i));
            }
            addPoint(ev.getX(), ev.getY(), time);
        }

        private void addPoint(float x, float y, long time) {
            int drop = -1;
            int i;
            final long[] pastTime = mPastTime;
            for (i = 0; i < NUM_PAST; i++) {
                if (pastTime[i] == 0) {
                    break;
                } else if (pastTime[i] < time - LONGEST_PAST_TIME) {
                    drop = i;
                }
            }
            if (i == NUM_PAST && drop < 0) {
                drop = 0;
            }
            if (drop == i) drop--;
            final float[] pastX = mPastX;
            final float[] pastY = mPastY;
            if (drop >= 0) {
                final int start = drop + 1;
                final int count = NUM_PAST - drop - 1;
                System.arraycopy(pastX, start, pastX, 0, count);
                System.arraycopy(pastY, start, pastY, 0, count);
                System.arraycopy(pastTime, start, pastTime, 0, count);
                i -= (drop + 1);
            }
            pastX[i] = x;
            pastY[i] = y;
            pastTime[i] = time;
            i++;
            if (i < NUM_PAST) {
                pastTime[i] = 0;
            }
        }

        public void computeCurrentVelocity(int units) {
            computeCurrentVelocity(units, Float.MAX_VALUE);
        }

        public void computeCurrentVelocity(int units, float maxVelocity) {
            final float[] pastX = mPastX;
            final float[] pastY = mPastY;
            final long[] pastTime = mPastTime;

            final float oldestX = pastX[0];
            final float oldestY = pastY[0];
            final long oldestTime = pastTime[0];
            float accumX = 0;
            float accumY = 0;
            int N = 0;
            while (N < NUM_PAST) {
                if (pastTime[N] == 0) {
                    break;
                }
                N++;
            }
            for (int i = 1; i < N; i++) {
                final int dur = (int) (pastTime[i] - oldestTime);
                if (dur == 0) continue;
                float dist = pastX[i] - oldestX;
                float vel = (dist / dur) * units;
                if (accumX == 0) accumX = vel;
                else accumX = (accumX + vel) * .5f;

                dist = pastY[i] - oldestY;
                vel = (dist / dur) * units;
                if (accumY == 0) accumY = vel;
                else accumY = (accumY + vel) * .5f;
            }
            mXVelocity = accumX < 0.0f ? Math.max(accumX, -maxVelocity)
                    : Math.min(accumX, maxVelocity);
            mYVelocity = accumY < 0.0f ? Math.max(accumY, -maxVelocity)
                    : Math.min(accumY, maxVelocity);
        }

        public float getXVelocity() {
            return mXVelocity;
        }

        public float getYVelocity() {
            return mYVelocity;
        }
    }
}
