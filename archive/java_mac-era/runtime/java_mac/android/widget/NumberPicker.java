// android.widget.NumberPicker -- a widget to pick a number (or one of a set of
// displayed strings) within [min, max], by dragging/flinging a selector wheel,
// tapping the value above/below the selection, DPAD up/down, or TYPING into
// the middle row.
//
// Ported from AOSP (android14-release)
//   frameworks/base/core/java/android/widget/NumberPicker.java
// in its SELECTOR-WHEEL form, which is the only form a Material/DeviceDefault
// theme ever produces: Widget.Material.NumberPicker sets
//   internalLayout = @layout/number_picker_material   (one CustomEditText)
//   selectionDividerHeight 2dp, selectionDividersDistance 48dp,
//   internalMinWidth 64dp, internalMaxHeight 180dp, solidColor transparent,
//   selectionDivider = number_picker_divider_material (a rect tinted
//   ?colorControlNormal)
// (framework-res values/styles_material.xml:715-724), so AOSP's
// `mHasSelectorWheel = (layoutResId != DEFAULT_LAYOUT_RESOURCE_ID)` is TRUE and
// the increment/decrement ImageButtons of the pre-Holo layout never exist.
// Those style values are applied here as the constants they are, instead of
// being read through R.styleable.NumberPicker (internal attrs our resource
// layer does not expose to framework code); the child tree is built in code
// with the exact shape number_picker_material.xml declares.
//
// WHY THIS EXISTS (2026-09-29, lane text-input): the previous class was a
// value MODEL with no UI ("the spinning visual chrome is not painted"), so
// every DatePicker/TimePicker spinner and every app NumberPicker was an empty
// box. Mastodon's sign-up form asks for a date of birth through an AlertDialog
// holding <DatePicker android:datePickerMode="spinner"> -- the owner reported
// "Cant type in date of birth to create a new account"; there was nothing to
// type into, drag or tap.
//
// Omitted from the AOSP class, deliberately and visibly: the accessibility
// node provider (virtual increment/input/decrement nodes) and the
// virtualButtonPressedDrawable highlight (?selectableItemBackground, which
// needs a themed ripple we do not resolve here). Neither changes a value.
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.NumberKeyListener;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.Locale;

public class NumberPicker extends LinearLayout {
    public interface OnValueChangeListener {
        void onValueChange(NumberPicker picker, int oldVal, int newVal);
    }
    public interface OnScrollListener {
        int SCROLL_STATE_IDLE = 0;
        int SCROLL_STATE_TOUCH_SCROLL = 1;
        int SCROLL_STATE_FLING = 2;
        void onScrollStateChange(NumberPicker view, int scrollState);
    }
    public interface Formatter {
        String format(int value);
    }

    // AOSP NumberPicker.java:117-168 (same names, same values).
    private static final int SELECTOR_WHEEL_ITEM_COUNT = 3;
    private static final long DEFAULT_LONG_PRESS_UPDATE_INTERVAL = 300;
    private static final int SELECTOR_MIDDLE_ITEM_INDEX = SELECTOR_WHEEL_ITEM_COUNT / 2;
    private static final int SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT = 8;
    private static final int SELECTOR_ADJUSTMENT_DURATION_MILLIS = 800;
    private static final int SNAP_SCROLL_DURATION = 300;
    private static final float TOP_AND_BOTTOM_FADING_EDGE_STRENGTH = 0.9f;
    private static final int SIZE_UNSPECIFIED = -1;
    // Widget.Material.NumberPicker (styles_material.xml:715-724), in dp.
    private static final int MATERIAL_SELECTION_DIVIDER_HEIGHT_DP = 2;
    private static final int MATERIAL_SELECTION_DIVIDERS_DISTANCE_DP = 48;
    private static final int MATERIAL_INTERNAL_MIN_WIDTH_DP = 64;
    private static final int MATERIAL_INTERNAL_MAX_HEIGHT_DP = 180;
    // TextAppearance.Material.NumberPicker -> TextAppearance.Material.Body1:
    // @dimen/text_size_body_1_material = 14sp, ?textColorPrimary.
    private static final float MATERIAL_TEXT_SIZE_SP = 14f;

    /** AOSP TwoDigitFormatter (NumberPicker.java:181-217): "%02d" in the
     *  current locale, re-initialised when the locale's zero digit changes. */
    private static class TwoDigitFormatter implements NumberPicker.Formatter {
        final StringBuilder mBuilder = new StringBuilder();
        char mZeroDigit;
        java.util.Formatter mFmt;
        final Object[] mArgs = new Object[1];
        TwoDigitFormatter() { init(Locale.getDefault()); }
        private void init(Locale locale) {
            mFmt = new java.util.Formatter(mBuilder, locale);
            mZeroDigit = getZeroDigit(locale);
        }
        public String format(int value) {
            final Locale currentLocale = Locale.getDefault();
            if (mZeroDigit != getZeroDigit(currentLocale)) init(currentLocale);
            mArgs[0] = value;
            mBuilder.delete(0, mBuilder.length());
            mFmt.format("%02d", mArgs);
            return mFmt.toString();
        }
        private static char getZeroDigit(Locale locale) {
            return java.text.DecimalFormatSymbols.getInstance(locale).getZeroDigit();
        }
    }
    private static final TwoDigitFormatter sTwoDigitFormatter = new TwoDigitFormatter();

    /** @hide AOSP exposes this to DatePicker/TimePicker (UnsupportedAppUsage). */
    public static final Formatter getTwoDigitFormatter() { return sTwoDigitFormatter; }

    private final EditText mInputText;
    private final int mSelectionDividersDistance;
    private final int mMinHeight = SIZE_UNSPECIFIED;
    private final int mMaxHeight;
    private final int mMinWidth;
    private int mMaxWidth = SIZE_UNSPECIFIED;
    private final boolean mComputeMaxWidth = true;
    private final int mTextSize;
    private int mSelectorTextGapHeight;
    private String[] mDisplayedValues;
    private int mMinValue;
    private int mMaxValue;
    private int mValue;
    private OnValueChangeListener mOnValueChangeListener;
    private OnScrollListener mOnScrollListener;
    private Formatter mFormatter;
    private long mLongPressUpdateInterval = DEFAULT_LONG_PRESS_UPDATE_INTERVAL;
    private final SparseArray<String> mSelectorIndexToStringCache = new SparseArray<String>();
    private final int[] mSelectorIndices = new int[SELECTOR_WHEEL_ITEM_COUNT];
    private final Paint mSelectorWheelPaint;
    private int mSelectorElementHeight;
    private int mInitialScrollOffset = Integer.MIN_VALUE;
    private int mCurrentScrollOffset;
    private final Scroller mFlingScroller;
    private final Scroller mAdjustScroller;
    private int mPreviousScrollerY;
    private SetSelectionCommand mSetSelectionCommand;
    private ChangeCurrentByOneFromLongPressCommand mChangeCurrentByOneFromLongPressCommand;
    private BeginSoftInputOnLongPressCommand mBeginSoftInputOnLongPressCommand;
    private float mLastDownEventY;
    private long mLastDownEventTime;
    private float mLastDownOrMoveEventY;
    private VelocityTracker mVelocityTracker;
    private final int mTouchSlop;
    private final int mMinimumFlingVelocity;
    private final int mMaximumFlingVelocity;
    private boolean mWrapSelectorWheel;
    private boolean mWrapSelectorWheelPreferred = true;
    private final int mSolidColor = 0;   // @color/transparent
    private final Drawable mSelectionDivider;
    private int mSelectionDividerHeight;
    private int mScrollState = OnScrollListener.SCROLL_STATE_IDLE;
    private boolean mIgnoreMoveEvents;
    private boolean mPerformClickOnTap;
    private int mTopSelectionDividerTop;
    private int mBottomSelectionDividerBottom;
    private int mLastHandledDownDpadKeyCode = -1;
    private final boolean mHideWheelUntilFocused = false;
    private int mLaidLeft = Integer.MIN_VALUE, mLaidTop, mLaidRight, mLaidBottom;

    public NumberPicker(Context context) { this(context, null); }
    public NumberPicker(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public NumberPicker(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public NumberPicker(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);
        final float density = context.getResources().getDisplayMetrics().density;
        mSelectionDividerHeight = (int) (MATERIAL_SELECTION_DIVIDER_HEIGHT_DP * density);
        mSelectionDividersDistance = (int) (MATERIAL_SELECTION_DIVIDERS_DISTANCE_DP * density);
        mMaxHeight = (int) (MATERIAL_INTERNAL_MAX_HEIGHT_DP * density + 0.5f);
        mMinWidth = (int) (MATERIAL_INTERNAL_MIN_WIDTH_DP * density + 0.5f);

        // number_picker_divider_material: <shape rectangle, solid black,
        // android:tint="?attr/colorControlNormal"> -- i.e. a solid rect of
        // colorControlNormal.
        mSelectionDivider = new ColorDrawable(
            themeColor(context, "colorControlNormal", 0x8A000000));

        setWillNotDraw(false);   // AOSP: setWillNotDraw(!mHasSelectorWheel)

        // number_picker_material.xml: <view class="android.widget.NumberPicker$CustomEditText"
        //   android:id="@+id/numberpicker_input" layout_width="fill_parent"
        //   layout_height="wrap_content" gravity="center" singleLine="true"
        //   background="@null" textAppearance="@style/TextAppearance.Material.NumberPicker"/>
        mInputText = new CustomEditText(context, null);
        final int inputId = internalId(context, "numberpicker_input");
        if (inputId != 0) mInputText.setId(inputId);
        mInputText.setGravity(android.view.Gravity.CENTER);
        mInputText.setSingleLine(true);
        mInputText.setBackground(null);
        mInputText.setTextSize(TypedValue.COMPLEX_UNIT_SP, MATERIAL_TEXT_SIZE_SP);
        mInputText.setTextColor(themeColor(context, "textColorPrimary", 0xDE000000));
        addView(mInputText, new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        mInputText.setOnFocusChangeListener(new OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    mInputText.selectAll();
                } else {
                    mInputText.setSelection(0, 0);
                    validateInputTextView(v);
                }
            }
        });
        mInputText.setFilters(new InputFilter[] { new InputTextFilter() });
        mInputText.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        mInputText.setImeOptions(EditorInfo.IME_ACTION_DONE);

        ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
        mMinimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = configuration.getScaledMaximumFlingVelocity()
                / SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT;
        mTextSize = (int) mInputText.getTextSize();

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(mTextSize);
        paint.setTypeface(mInputText.getTypeface());
        paint.setColor(mInputText.getCurrentTextColor());
        mSelectorWheelPaint = paint;

        mFlingScroller = new Scroller(getContext(), null, true);
        mAdjustScroller = new Scroller(getContext(), new DecelerateInterpolator(2.5f));

        updateInputTextView();

        // AOSP: if (getFocusable() == FOCUSABLE_AUTO) { setFocusable(FOCUSABLE);
        // setFocusableInTouchMode(true); }
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    private static int themeColor(Context context, String attr, int fallback) {
        try {
            android.content.res.Resources.Theme t = context.getTheme();
            if (t != null) {
                int c = t.resolveAttrColor("android:" + attr, 0);
                if (c == 0) c = t.resolveAttrColor(attr, 0);
                if (c != 0) return c;
            }
        } catch (Throwable ignored) { }
        return fallback;
    }

    /** The com.android.internal.R.id value for `name`, so an app that styles the
     *  picker through Resources.getIdentifier(name, "id", "android") still finds
     *  the child. 0 when the framework table does not answer. */
    static int internalId(Context context, String name) {
        try { return context.getResources().getIdentifier(name, "id", "android"); }
        catch (Throwable t) { return 0; }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int msrdWdth = getMeasuredWidth();
        final int msrdHght = getMeasuredHeight();
        final int inptTxtMsrdWdth = mInputText.getMeasuredWidth();
        final int inptTxtMsrdHght = mInputText.getMeasuredHeight();
        final int inptTxtLeft = (msrdWdth - inptTxtMsrdWdth) / 2;
        final int inptTxtTop = (msrdHght - inptTxtMsrdHght) / 2;
        mInputText.layout(inptTxtLeft, inptTxtTop, inptTxtLeft + inptTxtMsrdWdth,
                inptTxtTop + inptTxtMsrdHght);
        // AOSP re-derives the wheel geometry only `if (changed)`, i.e. when
        // setFrame moved this view. Our View.layout calls onLayout(true, ...)
        // on EVERY pass (a documented delta in View.layout), and every typed
        // character requests a layout -- so trusting `changed` re-ran
        // initializeSelectorWheel -> updateInputTextView on each keystroke and
        // put the old value back over the text being typed. Recover AOSP's
        // meaning from the frame itself.
        final boolean frameChanged = left != mLaidLeft || top != mLaidTop
                || right != mLaidRight || bottom != mLaidBottom;
        mLaidLeft = left; mLaidTop = top; mLaidRight = right; mLaidBottom = bottom;
        if (frameChanged || mSelectorElementHeight == 0) {
            initializeSelectorWheel();
            initializeFadingEdges();
            mTopSelectionDividerTop = (getHeight() - mSelectionDividersDistance) / 2
                    - mSelectionDividerHeight;
            mBottomSelectionDividerBottom = mTopSelectionDividerTop + 2 * mSelectionDividerHeight
                    + mSelectionDividersDistance;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int newWidthMeasureSpec = makeMeasureSpec(widthMeasureSpec, mMaxWidth);
        final int newHeightMeasureSpec = makeMeasureSpec(heightMeasureSpec, mMaxHeight);
        super.onMeasure(newWidthMeasureSpec, newHeightMeasureSpec);
        final int widthSize = resolveSizeAndStateRespectingMinSize(mMinWidth, getMeasuredWidth(),
                widthMeasureSpec);
        final int heightSize = resolveSizeAndStateRespectingMinSize(mMinHeight, getMeasuredHeight(),
                heightMeasureSpec);
        setMeasuredDimension(widthSize, heightSize);
    }

    private boolean moveToFinalScrollerPosition(Scroller scroller) {
        scroller.forceFinished(true);
        int amountToScroll = scroller.getFinalY() - scroller.getCurrY();
        if (mSelectorElementHeight == 0) return false;
        int futureScrollOffset = (mCurrentScrollOffset + amountToScroll) % mSelectorElementHeight;
        int overshootAdjustment = mInitialScrollOffset - futureScrollOffset;
        if (overshootAdjustment != 0) {
            if (Math.abs(overshootAdjustment) > mSelectorElementHeight / 2) {
                if (overshootAdjustment > 0) {
                    overshootAdjustment -= mSelectorElementHeight;
                } else {
                    overshootAdjustment += mSelectorElementHeight;
                }
            }
            amountToScroll += overshootAdjustment;
            scrollBy(0, amountToScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                removeAllCallbacks();
                hideSoftInput();
                mLastDownOrMoveEventY = mLastDownEventY = event.getY();
                mLastDownEventTime = event.getEventTime();
                mIgnoreMoveEvents = false;
                mPerformClickOnTap = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (!mFlingScroller.isFinished()) {
                    mFlingScroller.forceFinished(true);
                    mAdjustScroller.forceFinished(true);
                    onScrollerFinished(mFlingScroller);
                    onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                } else if (!mAdjustScroller.isFinished()) {
                    mFlingScroller.forceFinished(true);
                    mAdjustScroller.forceFinished(true);
                    onScrollerFinished(mAdjustScroller);
                } else if (mLastDownEventY < mTopSelectionDividerTop) {
                    postChangeCurrentByOneFromLongPress(
                            false, ViewConfiguration.getLongPressTimeout());
                } else if (mLastDownEventY > mBottomSelectionDividerBottom) {
                    postChangeCurrentByOneFromLongPress(
                            true, ViewConfiguration.getLongPressTimeout());
                } else {
                    mPerformClickOnTap = true;
                    postBeginSoftInputOnLongPressCommand();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                if (mIgnoreMoveEvents) {
                    break;
                }
                float currentMoveY = event.getY();
                if (mScrollState != OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                    int deltaDownY = (int) Math.abs(currentMoveY - mLastDownEventY);
                    if (deltaDownY > mTouchSlop) {
                        removeAllCallbacks();
                        onScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
                    }
                } else {
                    int deltaMoveY = (int) ((currentMoveY - mLastDownOrMoveEventY));
                    scrollBy(0, deltaMoveY);
                    invalidate();
                }
                mLastDownOrMoveEventY = currentMoveY;
            } break;
            case MotionEvent.ACTION_UP: {
                removeBeginSoftInputCommand();
                removeChangeCurrentByOneFromLongPress();
                VelocityTracker velocityTracker = mVelocityTracker;
                velocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
                int initialVelocity = (int) velocityTracker.getYVelocity();
                if (Math.abs(initialVelocity) > mMinimumFlingVelocity) {
                    fling(initialVelocity);
                    onScrollStateChange(OnScrollListener.SCROLL_STATE_FLING);
                } else {
                    int eventY = (int) event.getY();
                    int deltaMoveY = (int) Math.abs(eventY - mLastDownEventY);
                    long deltaTime = event.getEventTime() - mLastDownEventTime;
                    if (deltaMoveY <= mTouchSlop && deltaTime < ViewConfiguration.getTapTimeout()) {
                        if (mPerformClickOnTap) {
                            mPerformClickOnTap = false;
                            performClick();
                        } else if (mSelectorElementHeight > 0) {
                            int selectorIndexOffset = (eventY / mSelectorElementHeight)
                                    - SELECTOR_MIDDLE_ITEM_INDEX;
                            if (selectorIndexOffset > 0) {
                                changeValueByOne(true);
                            } else if (selectorIndexOffset < 0) {
                                changeValueByOne(false);
                            }
                        }
                    } else {
                        ensureScrollWheelAdjusted();
                    }
                    onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
                }
                mVelocityTracker.recycle();
                mVelocityTracker = null;
            } break;
        }
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                removeAllCallbacks();
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                removeAllCallbacks();
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
                switch (event.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        if (mWrapSelectorWheel || ((keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                                ? getValue() < getMaxValue() : getValue() > getMinValue())) {
                            requestFocus();
                            mLastHandledDownDpadKeyCode = keyCode;
                            removeAllCallbacks();
                            if (mFlingScroller.isFinished()) {
                                changeValueByOne(keyCode == KeyEvent.KEYCODE_DPAD_DOWN);
                            }
                            return true;
                        }
                        break;
                    case KeyEvent.ACTION_UP:
                        if (mLastHandledDownDpadKeyCode == keyCode) {
                            mLastHandledDownDpadKeyCode = -1;
                            return true;
                        }
                        break;
                }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void computeScroll() {
        Scroller scroller = mFlingScroller;
        if (scroller.isFinished()) {
            scroller = mAdjustScroller;
            if (scroller.isFinished()) {
                return;
            }
        }
        scroller.computeScrollOffset();
        int currentScrollerY = scroller.getCurrY();
        if (mPreviousScrollerY == 0) {
            mPreviousScrollerY = scroller.getStartY();
        }
        scrollBy(0, currentScrollerY - mPreviousScrollerY);
        mPreviousScrollerY = currentScrollerY;
        if (scroller.isFinished()) {
            onScrollerFinished(scroller);
        } else {
            invalidate();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mInputText.setEnabled(enabled);
    }

    @Override
    public void scrollBy(int x, int y) {
        int[] selectorIndices = mSelectorIndices;
        int startScrollOffset = mCurrentScrollOffset;
        if (!mWrapSelectorWheel && y > 0
                && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] <= mMinValue) {
            mCurrentScrollOffset = mInitialScrollOffset;
            return;
        }
        if (!mWrapSelectorWheel && y < 0
                && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] >= mMaxValue) {
            mCurrentScrollOffset = mInitialScrollOffset;
            return;
        }
        if (mSelectorElementHeight <= 0) return;   // never laid out: no wheel to move
        mCurrentScrollOffset += y;
        while (mCurrentScrollOffset - mInitialScrollOffset > mSelectorTextGapHeight) {
            mCurrentScrollOffset -= mSelectorElementHeight;
            decrementSelectorIndices(selectorIndices);
            setValueInternal(selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX], true);
            if (!mWrapSelectorWheel && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] <= mMinValue) {
                mCurrentScrollOffset = mInitialScrollOffset;
            }
        }
        while (mCurrentScrollOffset - mInitialScrollOffset < -mSelectorTextGapHeight) {
            mCurrentScrollOffset += mSelectorElementHeight;
            incrementSelectorIndices(selectorIndices);
            setValueInternal(selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX], true);
            if (!mWrapSelectorWheel && selectorIndices[SELECTOR_MIDDLE_ITEM_INDEX] >= mMaxValue) {
                mCurrentScrollOffset = mInitialScrollOffset;
            }
        }
        if (startScrollOffset != mCurrentScrollOffset) {
            onScrollChanged(0, mCurrentScrollOffset, 0, startScrollOffset);
        }
    }

    public int getSolidColor() { return mSolidColor; }

    public void setOnValueChangedListener(OnValueChangeListener onValueChangedListener) {
        mOnValueChangeListener = onValueChangedListener;
    }

    public void setOnScrollListener(OnScrollListener onScrollListener) {
        mOnScrollListener = onScrollListener;
    }

    public void setFormatter(Formatter formatter) {
        if (formatter == mFormatter) {
            return;
        }
        mFormatter = formatter;
        initializeSelectorWheelIndices();
        updateInputTextView();
    }

    public void setValue(int value) {
        setValueInternal(value, false);
    }

    @Override
    public boolean performClick() {
        if (!super.performClick()) {
            showSoftInput();
        }
        return true;
    }

    @Override
    public boolean performLongClick() {
        if (!super.performLongClick()) {
            showSoftInput();
            mIgnoreMoveEvents = true;
        }
        return true;
    }

    private void showSoftInput() {
        InputMethodManager inputMethodManager =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            mInputText.setVisibility(View.VISIBLE);
            mInputText.requestFocus();
            inputMethodManager.showSoftInput(mInputText, 0);
        }
    }

    private void hideSoftInput() {
        InputMethodManager inputMethodManager =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null && inputMethodManager.isActive(mInputText)) {
            inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
        }
        // AOSP: the middle value is then drawn by onDraw. View.setFlags clears
        // the focus of a view that becomes INVISIBLE, which is what commits a
        // typed value here (the focus listener -> validateInputTextView).
        mInputText.setVisibility(View.INVISIBLE);
    }

    private void tryComputeMaxWidth() {
        if (!mComputeMaxWidth) {
            return;
        }
        int maxTextWidth = 0;
        if (mDisplayedValues == null) {
            float maxDigitWidth = 0;
            for (int i = 0; i <= 9; i++) {
                final float digitWidth = mSelectorWheelPaint.measureText(formatNumberWithLocale(i));
                if (digitWidth > maxDigitWidth) {
                    maxDigitWidth = digitWidth;
                }
            }
            int numberOfDigits = 0;
            int current = mMaxValue;
            while (current > 0) {
                numberOfDigits++;
                current = current / 10;
            }
            maxTextWidth = (int) (numberOfDigits * maxDigitWidth);
        } else {
            for (String v : mDisplayedValues) {
                final float textWidth = mSelectorWheelPaint.measureText(v);
                if (textWidth > maxTextWidth) {
                    maxTextWidth = (int) textWidth;
                }
            }
        }
        maxTextWidth += mInputText.getPaddingLeft() + mInputText.getPaddingRight();
        if (mMaxWidth != maxTextWidth) {
            if (maxTextWidth > mMinWidth) {
                mMaxWidth = maxTextWidth;
            } else {
                mMaxWidth = mMinWidth;
            }
            invalidate();
        }
    }

    public boolean getWrapSelectorWheel() {
        return mWrapSelectorWheel;
    }

    public void setWrapSelectorWheel(boolean wrapSelectorWheel) {
        mWrapSelectorWheelPreferred = wrapSelectorWheel;
        updateWrapSelectorWheel();
    }

    private void updateWrapSelectorWheel() {
        final boolean wrappingAllowed = (mMaxValue - mMinValue) >= mSelectorIndices.length;
        mWrapSelectorWheel = wrappingAllowed && mWrapSelectorWheelPreferred;
    }

    public void setOnLongPressUpdateInterval(long intervalMillis) {
        mLongPressUpdateInterval = intervalMillis;
    }

    public int getValue() { return mValue; }
    public int getMinValue() { return mMinValue; }

    public void setMinValue(int minValue) {
        if (mMinValue == minValue) {
            return;
        }
        if (minValue < 0) {
            throw new IllegalArgumentException("minValue must be >= 0");
        }
        mMinValue = minValue;
        if (mMinValue > mValue) {
            mValue = mMinValue;
        }
        updateWrapSelectorWheel();
        initializeSelectorWheelIndices();
        updateInputTextView();
        tryComputeMaxWidth();
        invalidate();
    }

    public int getMaxValue() { return mMaxValue; }

    public void setMaxValue(int maxValue) {
        if (mMaxValue == maxValue) {
            return;
        }
        if (maxValue < 0) {
            throw new IllegalArgumentException("maxValue must be >= 0");
        }
        mMaxValue = maxValue;
        if (mMaxValue < mValue) {
            mValue = mMaxValue;
        }
        updateWrapSelectorWheel();
        initializeSelectorWheelIndices();
        updateInputTextView();
        tryComputeMaxWidth();
        invalidate();
    }

    public String[] getDisplayedValues() { return mDisplayedValues; }

    public void setDisplayedValues(String[] displayedValues) {
        if (mDisplayedValues == displayedValues) {
            return;
        }
        mDisplayedValues = displayedValues;
        if (mDisplayedValues != null) {
            mInputText.setRawInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        } else {
            mInputText.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        }
        updateInputTextView();
        initializeSelectorWheelIndices();
        tryComputeMaxWidth();
    }

    /** @hide AOSP @TestApi. */
    public CharSequence getDisplayedValueForCurrentSelection() {
        return mSelectorIndexToStringCache.get(getValue());
    }

    public void setSelectionDividerHeight(int height) {
        mSelectionDividerHeight = height;
        invalidate();
    }

    public int getSelectionDividerHeight() { return mSelectionDividerHeight; }

    protected float getTopFadingEdgeStrength() { return TOP_AND_BOTTOM_FADING_EDGE_STRENGTH; }
    protected float getBottomFadingEdgeStrength() { return TOP_AND_BOTTOM_FADING_EDGE_STRENGTH; }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeAllCallbacks();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final boolean showSelectorWheel = mHideWheelUntilFocused ? hasFocus() : true;
        float x = getWidth() / 2;
        float y = mCurrentScrollOffset;
        int[] selectorIndices = mSelectorIndices;
        for (int i = 0; i < selectorIndices.length; i++) {
            int selectorIndex = selectorIndices[i];
            String scrollSelectorValue = mSelectorIndexToStringCache.get(selectorIndex);
            // Do not draw the middle item if input is visible since the input
            // is shown only if the wheel is static and it covers the middle item.
            if (scrollSelectorValue != null
                    && ((showSelectorWheel && i != SELECTOR_MIDDLE_ITEM_INDEX)
                        || (i == SELECTOR_MIDDLE_ITEM_INDEX
                            && mInputText.getVisibility() != VISIBLE))) {
                canvas.drawText(scrollSelectorValue, x, y, mSelectorWheelPaint);
            }
            y += mSelectorElementHeight;
        }
        if (showSelectorWheel && mSelectionDivider != null) {
            int topOfTopDivider = mTopSelectionDividerTop;
            int bottomOfTopDivider = topOfTopDivider + mSelectionDividerHeight;
            mSelectionDivider.setBounds(0, topOfTopDivider, getWidth(), bottomOfTopDivider);
            mSelectionDivider.draw(canvas);
            int bottomOfBottomDivider = mBottomSelectionDividerBottom;
            int topOfBottomDivider = bottomOfBottomDivider - mSelectionDividerHeight;
            mSelectionDivider.setBounds(0, topOfBottomDivider, getWidth(), bottomOfBottomDivider);
            mSelectionDivider.draw(canvas);
        }
    }

    public void setTextColor(int color) {
        mSelectorWheelPaint.setColor(color);
        mInputText.setTextColor(color);
        invalidate();
    }

    public int getTextColor() { return mSelectorWheelPaint.getColor(); }

    public void setTextSize(float size) {
        mSelectorWheelPaint.setTextSize(size);
        mInputText.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        invalidate();
    }

    public float getTextSize() { return mSelectorWheelPaint.getTextSize(); }

    private int makeMeasureSpec(int measureSpec, int maxSize) {
        if (maxSize == SIZE_UNSPECIFIED) {
            return measureSpec;
        }
        final int size = MeasureSpec.getSize(measureSpec);
        final int mode = MeasureSpec.getMode(measureSpec);
        switch (mode) {
            case MeasureSpec.EXACTLY:
                return measureSpec;
            case MeasureSpec.AT_MOST:
                return MeasureSpec.makeMeasureSpec(Math.min(size, maxSize), MeasureSpec.EXACTLY);
            case MeasureSpec.UNSPECIFIED:
                return MeasureSpec.makeMeasureSpec(maxSize, MeasureSpec.EXACTLY);
            default:
                throw new IllegalArgumentException("Unknown measure mode: " + mode);
        }
    }

    private int resolveSizeAndStateRespectingMinSize(int minSize, int measuredSize,
            int measureSpec) {
        if (minSize != SIZE_UNSPECIFIED) {
            final int desiredWidth = Math.max(minSize, measuredSize);
            return resolveSizeAndState(desiredWidth, measureSpec, 0);
        } else {
            return measuredSize;
        }
    }

    private void initializeSelectorWheelIndices() {
        mSelectorIndexToStringCache.clear();
        int[] selectorIndices = mSelectorIndices;
        int current = getValue();
        for (int i = 0; i < mSelectorIndices.length; i++) {
            int selectorIndex = current + (i - SELECTOR_MIDDLE_ITEM_INDEX);
            if (mWrapSelectorWheel) {
                selectorIndex = getWrappedSelectorIndex(selectorIndex);
            }
            selectorIndices[i] = selectorIndex;
            ensureCachedScrollSelectorValue(selectorIndices[i]);
        }
    }

    private void setValueInternal(int current, boolean notifyChange) {
        if (mValue == current) {
            return;
        }
        if (mWrapSelectorWheel) {
            current = getWrappedSelectorIndex(current);
        } else {
            current = Math.max(current, mMinValue);
            current = Math.min(current, mMaxValue);
        }
        int previous = mValue;
        mValue = current;
        if (mScrollState != OnScrollListener.SCROLL_STATE_FLING) {
            updateInputTextView();
        }
        if (notifyChange) {
            notifyChange(previous, current);
        }
        initializeSelectorWheelIndices();
        invalidate();
    }

    private void changeValueByOne(boolean increment) {
        hideSoftInput();
        if (mSelectorElementHeight <= 0) {
            // Not laid out yet, so there is no wheel to animate: AOSP's
            // no-selector-wheel branch, the value moves directly.
            setValueInternal(increment ? mValue + 1 : mValue - 1, true);
            return;
        }
        if (!moveToFinalScrollerPosition(mFlingScroller)) {
            moveToFinalScrollerPosition(mAdjustScroller);
        }
        mPreviousScrollerY = 0;
        if (increment) {
            mFlingScroller.startScroll(0, 0, 0, -mSelectorElementHeight, SNAP_SCROLL_DURATION);
        } else {
            mFlingScroller.startScroll(0, 0, 0, mSelectorElementHeight, SNAP_SCROLL_DURATION);
        }
        invalidate();
    }

    private void initializeSelectorWheel() {
        initializeSelectorWheelIndices();
        int[] selectorIndices = mSelectorIndices;
        int totalTextHeight = selectorIndices.length * mTextSize;
        float totalTextGapHeight = getHeight() - totalTextHeight;
        float textGapCount = selectorIndices.length;
        mSelectorTextGapHeight = (int) (totalTextGapHeight / textGapCount + 0.5f);
        mSelectorElementHeight = mTextSize + mSelectorTextGapHeight;
        // Ensure that the middle item is positioned the same as the text in
        // mInputText
        int editTextTextPosition = mInputText.getBaseline() + mInputText.getTop();
        mInitialScrollOffset = editTextTextPosition
                - (mSelectorElementHeight * SELECTOR_MIDDLE_ITEM_INDEX);
        mCurrentScrollOffset = mInitialScrollOffset;
        updateInputTextView();
    }

    private void initializeFadingEdges() {
        setVerticalFadingEdgeEnabled(true);
        setFadingEdgeLength((getHeight() - mTextSize) / 2);
    }

    private void onScrollerFinished(Scroller scroller) {
        if (scroller == mFlingScroller) {
            ensureScrollWheelAdjusted();
            updateInputTextView();
            onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE);
        } else {
            if (mScrollState != OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
                updateInputTextView();
            }
        }
    }

    private void onScrollStateChange(int scrollState) {
        if (mScrollState == scrollState) {
            return;
        }
        mScrollState = scrollState;
        if (mOnScrollListener != null) {
            mOnScrollListener.onScrollStateChange(this, scrollState);
        }
    }

    private void fling(int velocityY) {
        mPreviousScrollerY = 0;
        if (velocityY > 0) {
            mFlingScroller.fling(0, 0, 0, velocityY, 0, 0, 0, Integer.MAX_VALUE);
        } else {
            mFlingScroller.fling(0, Integer.MAX_VALUE, 0, velocityY, 0, 0, 0, Integer.MAX_VALUE);
        }
        invalidate();
    }

    private int getWrappedSelectorIndex(int selectorIndex) {
        if (selectorIndex > mMaxValue) {
            return mMinValue + (selectorIndex - mMaxValue) % (mMaxValue - mMinValue) - 1;
        } else if (selectorIndex < mMinValue) {
            return mMaxValue - (mMinValue - selectorIndex) % (mMaxValue - mMinValue) + 1;
        }
        return selectorIndex;
    }

    private void incrementSelectorIndices(int[] selectorIndices) {
        for (int i = 0; i < selectorIndices.length - 1; i++) {
            selectorIndices[i] = selectorIndices[i + 1];
        }
        int nextScrollSelectorIndex = selectorIndices[selectorIndices.length - 2] + 1;
        if (mWrapSelectorWheel && nextScrollSelectorIndex > mMaxValue) {
            nextScrollSelectorIndex = mMinValue;
        }
        selectorIndices[selectorIndices.length - 1] = nextScrollSelectorIndex;
        ensureCachedScrollSelectorValue(nextScrollSelectorIndex);
    }

    private void decrementSelectorIndices(int[] selectorIndices) {
        for (int i = selectorIndices.length - 1; i > 0; i--) {
            selectorIndices[i] = selectorIndices[i - 1];
        }
        int nextScrollSelectorIndex = selectorIndices[1] - 1;
        if (mWrapSelectorWheel && nextScrollSelectorIndex < mMinValue) {
            nextScrollSelectorIndex = mMaxValue;
        }
        selectorIndices[0] = nextScrollSelectorIndex;
        ensureCachedScrollSelectorValue(nextScrollSelectorIndex);
    }

    private void ensureCachedScrollSelectorValue(int selectorIndex) {
        SparseArray<String> cache = mSelectorIndexToStringCache;
        String scrollSelectorValue = cache.get(selectorIndex);
        if (scrollSelectorValue != null) {
            return;
        }
        if (selectorIndex < mMinValue || selectorIndex > mMaxValue) {
            scrollSelectorValue = "";
        } else {
            if (mDisplayedValues != null) {
                int displayedValueIndex = selectorIndex - mMinValue;
                // AOSP indexes unchecked (an app that sets fewer displayed
                // values than max-min+1 gets an AIOOBE there too, but at draw
                // time); guarded so a transient min/max/values update order
                // cannot throw out of a setter.
                scrollSelectorValue = displayedValueIndex < mDisplayedValues.length
                        ? mDisplayedValues[displayedValueIndex] : "";
            } else {
                scrollSelectorValue = formatNumber(selectorIndex);
            }
        }
        cache.put(selectorIndex, scrollSelectorValue);
    }

    private String formatNumber(int value) {
        return (mFormatter != null) ? mFormatter.format(value) : formatNumberWithLocale(value);
    }

    private void validateInputTextView(View v) {
        String str = String.valueOf(((TextView) v).getText());
        if (TextUtils.isEmpty(str)) {
            // Restore to the old value as we don't allow empty values
            updateInputTextView();
        } else {
            // Check the new value and ensure it's in range
            int current = getSelectedPos(str.toString());
            setValueInternal(current, true);
        }
    }

    private boolean updateInputTextView() {
        String text = (mDisplayedValues == null) ? formatNumber(mValue)
                : (mValue - mMinValue >= 0 && mValue - mMinValue < mDisplayedValues.length
                   ? mDisplayedValues[mValue - mMinValue] : null);
        if (!TextUtils.isEmpty(text)) {
            CharSequence beforeText = mInputText.getText();
            if (!text.equals(String.valueOf(beforeText))) {
                mInputText.setText(text);
                return true;
            }
        }
        return false;
    }

    private void notifyChange(int previous, int current) {
        if (mOnValueChangeListener != null) {
            mOnValueChangeListener.onValueChange(this, previous, mValue);
        }
    }

    private void postChangeCurrentByOneFromLongPress(boolean increment, long delayMillis) {
        if (mChangeCurrentByOneFromLongPressCommand == null) {
            mChangeCurrentByOneFromLongPressCommand = new ChangeCurrentByOneFromLongPressCommand();
        } else {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand);
        }
        mChangeCurrentByOneFromLongPressCommand.setStep(increment);
        postDelayed(mChangeCurrentByOneFromLongPressCommand, delayMillis);
    }

    private void removeChangeCurrentByOneFromLongPress() {
        if (mChangeCurrentByOneFromLongPressCommand != null) {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand);
        }
    }

    private void postBeginSoftInputOnLongPressCommand() {
        if (mBeginSoftInputOnLongPressCommand == null) {
            mBeginSoftInputOnLongPressCommand = new BeginSoftInputOnLongPressCommand();
        } else {
            removeCallbacks(mBeginSoftInputOnLongPressCommand);
        }
        postDelayed(mBeginSoftInputOnLongPressCommand, ViewConfiguration.getLongPressTimeout());
    }

    private void removeBeginSoftInputCommand() {
        if (mBeginSoftInputOnLongPressCommand != null) {
            removeCallbacks(mBeginSoftInputOnLongPressCommand);
        }
    }

    private void removeAllCallbacks() {
        if (mChangeCurrentByOneFromLongPressCommand != null) {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand);
        }
        if (mSetSelectionCommand != null) {
            mSetSelectionCommand.cancel();
        }
        if (mBeginSoftInputOnLongPressCommand != null) {
            removeCallbacks(mBeginSoftInputOnLongPressCommand);
        }
    }

    private int getSelectedPos(String value) {
        if (mDisplayedValues == null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Ignore as if it's not a number we don't care
            }
        } else {
            for (int i = 0; i < mDisplayedValues.length; i++) {
                // Don't force the user to type in jan when ja will do
                value = value.toLowerCase();
                if (mDisplayedValues[i].toLowerCase().startsWith(value)) {
                    return mMinValue + i;
                }
            }
            // The user might have typed in a number into the month field i.e.
            // 10 instead of OCT so support that too.
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Ignore as if it's not a number we don't care
            }
        }
        return mMinValue;
    }

    // AOSP NumberPicker.java DIGIT_CHARACTERS: ASCII plus Arabic-Indic,
    // Extended Arabic-Indic, Devanagari, Bengali and Kannada digits.
    private static final char[] DIGIT_CHARACTERS = new char[] {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '٠', '١', '٢', '٣', '٤', '٥', '٦', '٧',
            '٨', '٩',
            '۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷',
            '۸', '۹',
            '०', '१', '२', '३', '४', '५', '६', '७',
            '८', '९',
            '০', '১', '২', '৩', '৪', '৫', '৬', '৭',
            '৮', '৯',
            '೦', '೧', '೨', '೩', '೪', '೫', '೬', '೭',
            '೮', '೯'
    };

    private void postSetSelectionCommand(int selectionStart, int selectionEnd) {
        if (mSetSelectionCommand == null) {
            mSetSelectionCommand = new SetSelectionCommand(mInputText);
        }
        mSetSelectionCommand.post(selectionStart, selectionEnd);
    }

    /** AOSP SetSelectionCommand: select the auto-completed tail AFTER the edit
     *  that produced it has placed its caret, so the next typed character
     *  replaces the tail ("a" -> "A[pr]", then "u" -> "Au" -> "Aug"). */
    private static class SetSelectionCommand implements Runnable {
        private final EditText mInputText;
        private int mSelectionStart;
        private int mSelectionEnd;
        private boolean mPosted;
        SetSelectionCommand(EditText inputText) { mInputText = inputText; }
        void post(int selectionStart, int selectionEnd) {
            mSelectionStart = selectionStart;
            mSelectionEnd = selectionEnd;
            if (!mPosted) {
                mInputText.post(this);
                mPosted = true;
            }
        }
        void cancel() {
            if (mPosted) {
                mInputText.removeCallbacks(this);
                mPosted = false;
            }
        }
        @Override
        public void run() {
            mPosted = false;
            mInputText.setSelection(mSelectionStart, mSelectionEnd);
        }
    }

    /** AOSP InputTextFilter: digits only (when no displayed values) and never
     *  a value above max; with displayed values, the typed prefix completes to
     *  the first matching entry and the completed tail is selected. */
    class InputTextFilter extends NumberKeyListener {
        public int getInputType() {
            return InputType.TYPE_CLASS_TEXT;
        }
        @Override
        protected char[] getAcceptedChars() {
            return DIGIT_CHARACTERS;
        }
        @Override
        public CharSequence filter(
                CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            // We don't know what the output will be, so always cancel any
            // pending set selection command.
            if (mSetSelectionCommand != null) {
                mSetSelectionCommand.cancel();
            }
            if (mDisplayedValues == null) {
                CharSequence filtered = super.filter(source, start, end, dest, dstart, dend);
                if (filtered == null) {
                    filtered = source.subSequence(start, end);
                }
                String result = String.valueOf(dest.subSequence(0, dstart)) + filtered
                        + dest.subSequence(dend, dest.length());
                if ("".equals(result)) {
                    return result;
                }
                int val = getSelectedPos(result);
                /*
                 * Ensure the user can't type in a value greater than the max
                 * allowed. We have to allow less than min as the user might
                 * want to delete some numbers and then type a new number.
                 * And prevent multiple-"0" that exceeds the length of upper
                 * bound number.
                 */
                if (val > mMaxValue || result.length() > String.valueOf(mMaxValue).length()) {
                    return "";
                } else {
                    return filtered;
                }
            } else {
                CharSequence filtered = String.valueOf(source.subSequence(start, end));
                if (TextUtils.isEmpty(filtered)) {
                    return "";
                }
                String result = String.valueOf(dest.subSequence(0, dstart)) + filtered
                        + dest.subSequence(dend, dest.length());
                String str = String.valueOf(result).toLowerCase();
                for (String val : mDisplayedValues) {
                    String valLowerCase = val.toLowerCase();
                    if (valLowerCase.startsWith(str)) {
                        postSetSelectionCommand(result.length(), val.length());
                        return val.subSequence(dstart, val.length());
                    }
                }
                return "";
            }
        }
    }

    private boolean ensureScrollWheelAdjusted() {
        // adjust to the closest value
        int deltaY = mInitialScrollOffset - mCurrentScrollOffset;
        if (deltaY != 0) {
            mPreviousScrollerY = 0;
            if (Math.abs(deltaY) > mSelectorElementHeight / 2) {
                deltaY += (deltaY > 0) ? -mSelectorElementHeight : mSelectorElementHeight;
            }
            mAdjustScroller.startScroll(0, 0, 0, deltaY, SELECTOR_ADJUSTMENT_DURATION_MILLIS);
            invalidate();
            return true;
        }
        return false;
    }

    class ChangeCurrentByOneFromLongPressCommand implements Runnable {
        private boolean mIncrement;
        private void setStep(boolean increment) { mIncrement = increment; }
        @Override
        public void run() {
            changeValueByOne(mIncrement);
            postDelayed(this, mLongPressUpdateInterval);
        }
    }

    class BeginSoftInputOnLongPressCommand implements Runnable {
        @Override
        public void run() { performLongClick(); }
    }

    /** AOSP NumberPicker.CustomEditText: IME_ACTION_DONE clears focus, which
     *  is what commits a typed value (the focus listener validates it). */
    public static class CustomEditText extends EditText {
        public CustomEditText(Context context, AttributeSet attrs) {
            super(context, attrs);
        }
        @Override
        public void onEditorAction(int actionCode) {
            super.onEditorAction(actionCode);
            if (actionCode == EditorInfo.IME_ACTION_DONE) {
                clearFocus();
            }
        }
    }

    private static String formatNumberWithLocale(int value) {
        return String.format(Locale.getDefault(), "%d", value);
    }
}
