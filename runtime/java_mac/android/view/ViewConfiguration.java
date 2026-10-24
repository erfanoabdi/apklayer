// android.view.ViewConfiguration -- system-wide UI tunables (touch
// slop, fling thresholds, scrollbar size, timeouts). Most scrolling
// containers (RecyclerView, NestedScrollView, ViewPager) pull values
// from here during ctor; missing methods cause NoSuchMethodError on
// inflate even though the actual UI work is trivial without
// animations on macOS.
//
// AOSP has TWO constructors and they answer DIFFERENTLY, which is the
// single most important thing about this class:
//
//   ViewConfiguration()          -- @Deprecated, no Context. Assigns the RAW
//                                   mdpi (density=1) dp constants with NO
//                                   density multiply at all
//                                   (ViewConfiguration.java:351-380).
//   ViewConfiguration(Context)   -- reached via get(Context). Multiplies by the
//                                   display density, or resolves a framework
//                                   dimen resource, per field
//                                   (ViewConfiguration.java:394-533).
//
// This file used to have one stateless implementation that density-scaled
// EVERYTHING, so the no-Context instance answered 16/100/24/... where AOSP
// answers 8/50/12/... The framework-answer differential caught it: 11 of the
// 28 VCFG divergences were exactly that one bug (fwprobe battery D3, family
// VCFG, 2026-08-09). Hence the real instance fields below -- do not collapse
// them back into static formulas.
//
// macOS has no configurable Android density, so the Context path reads the
// Context's own Resources/DisplayMetrics (our one fixed host density, 2.0 /
// xhdpi) and falls back to the default metrics when the Context cannot supply
// them, which is what every caller got unconditionally before.
package android.view;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;

public class ViewConfiguration {

    // ---- Base mdpi (density=1) dp / ms constants, verbatim from AOSP
    // (aosp-mirror platform_frameworks_base android-11.0.0_r48
    //  core/java/android/view/ViewConfiguration.java, line numbers inline).
    // AOSP reuses each of these two ways: verbatim as the return value of the
    // deprecated unscaled statics near the bottom of this file AND as the
    // assignment made by the no-Context constructor.
    private static final int SCROLL_BAR_SIZE = 4;                    // :50
    private static final int SCROLL_BAR_FADE_DURATION = 250;         // :55
    private static final int SCROLL_BAR_DEFAULT_DELAY = 300;         // :60
    private static final int FADING_EDGE_LENGTH = 12;                // :65
    private static final int PRESSED_STATE_DURATION = 64;            // :71
    private static final int DEFAULT_LONG_PRESS_TIMEOUT = 400;       // :78
    private static final int KEY_REPEAT_DELAY = 50;                  // :89
    private static final int GLOBAL_ACTIONS_KEY_TIMEOUT = 500;       // :96
    private static final int TAP_TIMEOUT = 100;                      // :121
    private static final int JUMP_TAP_TIMEOUT = 500;                 // :128
    private static final int DOUBLE_TAP_TIMEOUT = 300;               // :135
    private static final int ZOOM_CONTROLS_TIMEOUT = 3000;           // :162
    private static final int EDGE_SLOP = 12;                         // :167
    private static final int TOUCH_SLOP = 8;                         // :180
    private static final int MIN_SCROLLBAR_TOUCH_TARGET = 48;        // :185
    private static final int DOUBLE_TAP_TOUCH_SLOP = TOUCH_SLOP;     // :191
    private static final int PAGING_TOUCH_SLOP = TOUCH_SLOP * 2;     // :205
    private static final int DOUBLE_TAP_SLOP = 100;                  // :210
    private static final int WINDOW_TOUCH_SLOP = 16;                 // :216
    private static final int MINIMUM_FLING_VELOCITY = 50;            // :221
    private static final int MAXIMUM_FLING_VELOCITY = 8000;          // :226
    private static final int MAXIMUM_DRAWING_CACHE_SIZE = 480 * 800 * 4;  // :241 ARGB8888
    private static final float SCROLL_FRICTION = 0.015f;             // :247
    private static final int OVERSCROLL_DISTANCE = 0;                // :252
    private static final int OVERFLING_DISTANCE = 6;                 // :257
    private static final float HORIZONTAL_SCROLL_FACTOR = 64;        // :263
    private static final float VERTICAL_SCROLL_FACTOR = 64;          // :269
    private static final long ACTION_MODE_HIDE_DURATION_DEFAULT = 2000;   // :274
    private static final float AMBIGUOUS_GESTURE_MULTIPLIER = 2f;    // :310

    // API 33/34 additions (not present in the android-11 reference above);
    // the dp bases are core/res/res/values/config.xml's
    // config_viewConfigurationHandwritingSlop / _HandwritingGestureLineMargin.
    private static final int HANDWRITING_SLOP = 2;
    private static final int HANDWRITING_GESTURE_LINE_MARGIN = 16;

    // ---- Framework dimen resources the Context constructor resolves.
    // AOSP reads these with res.getDimensionPixelSize() from the platform
    // resource table; we have no internal R, so the STOCK (no-OEM-overlay)
    // values are pinned here with their source. Verified 2026-08-09 against
    // aosp-mirror android-11.0.0_r48 core/res/res/values/config.xml:
    //   :2238 config_viewConfigurationTouchSlop      8dp
    //   :2242 config_viewConfigurationHoverSlop      4dp
    //   :2234 config_minScrollbarTouchTarget        48dp
    //   :2248 config_viewMinFlingVelocity           50dp
    //   :2251 config_viewMaxFlingVelocity         8000dp
    //   :2263 config_scrollbarSize                   4dp
    //   :2267 config_horizontalScrollFactor         64dp
    //   :2271 config_verticalScrollFactor           64dp
    //   :2353 config_minScalingSpan                 27mm   <-- MILLIMETRES
    //   :2245 config_ambiguousGestureMultiplier      2.0   (a float, not a dimen)
    // The mm one matters: config_minScalingSpan is the only ViewConfiguration
    // input expressed in physical units, so it scales by xdpi rather than by
    // density. 27mm at our 320 xdpi is 340px, which is what the emulator
    // answers too.
    private static final int CONFIG_VIEW_CONFIGURATION_TOUCH_SLOP_DP = 8;
    private static final int CONFIG_VIEW_CONFIGURATION_HOVER_SLOP_DP = 4;
    private static final int CONFIG_MIN_SCROLLBAR_TOUCH_TARGET_DP = MIN_SCROLLBAR_TOUCH_TARGET;
    private static final int CONFIG_VIEW_MIN_FLING_VELOCITY_DP = MINIMUM_FLING_VELOCITY;
    private static final int CONFIG_VIEW_MAX_FLING_VELOCITY_DP = MAXIMUM_FLING_VELOCITY;
    private static final int CONFIG_SCROLLBAR_SIZE_DP = SCROLL_BAR_SIZE;
    private static final int CONFIG_HORIZONTAL_SCROLL_FACTOR_DP = 64;
    private static final int CONFIG_VERTICAL_SCROLL_FACTOR_DP = 64;
    private static final float CONFIG_MIN_SCALING_SPAN_MM = 27f;
    private static final float CONFIG_AMBIGUOUS_GESTURE_MULTIPLIER = 2.0f;

    // ---- Instance state. AOSP's field set (ViewConfiguration.java:312-338),
    // minus the ones no Mac.apk caller can observe (fading marquee, screenshot
    // chord, a11y shortcut).
    private final boolean mConstructedWithContext;
    private final int mEdgeSlop;
    private final int mFadingEdgeLength;
    private final int mMinimumFlingVelocity;
    private final int mMaximumFlingVelocity;
    private final int mScrollbarSize;
    private final int mTouchSlop;
    private final int mMinScalingSpan;
    private final int mHoverSlop;
    private final int mMinScrollbarTouchTarget;
    private final int mDoubleTapTouchSlop;
    private final int mPagingTouchSlop;
    private final int mDoubleTapSlop;
    private final int mWindowTouchSlop;
    private final int mHandwritingSlop;
    private final int mHandwritingGestureLineMargin;
    private final int mMaximumDrawingCacheSize;
    private final int mOverscrollDistance;
    private final int mOverflingDistance;
    private final float mAmbiguousGestureMultiplier;
    private final float mHorizontalScrollFactor;
    private final float mVerticalScrollFactor;

    /** AOSP ViewConfiguration() (:351-380): the RAW constants, no density.
     *  @deprecated Use {@link #get(android.content.Context)} instead. */
    @Deprecated
    public ViewConfiguration() {
        mConstructedWithContext = false;
        mEdgeSlop = EDGE_SLOP;
        mFadingEdgeLength = FADING_EDGE_LENGTH;
        mMinimumFlingVelocity = MINIMUM_FLING_VELOCITY;
        mMaximumFlingVelocity = MAXIMUM_FLING_VELOCITY;
        mScrollbarSize = SCROLL_BAR_SIZE;
        mTouchSlop = TOUCH_SLOP;
        mHoverSlop = TOUCH_SLOP / 2;
        mMinScrollbarTouchTarget = MIN_SCROLLBAR_TOUCH_TARGET;
        mDoubleTapTouchSlop = DOUBLE_TAP_TOUCH_SLOP;
        mPagingTouchSlop = PAGING_TOUCH_SLOP;
        mDoubleTapSlop = DOUBLE_TAP_SLOP;
        mWindowTouchSlop = WINDOW_TOUCH_SLOP;
        mHandwritingSlop = HANDWRITING_SLOP;
        mHandwritingGestureLineMargin = HANDWRITING_GESTURE_LINE_MARGIN;
        mAmbiguousGestureMultiplier = AMBIGUOUS_GESTURE_MULTIPLIER;
        mMaximumDrawingCacheSize = MAXIMUM_DRAWING_CACHE_SIZE;
        mOverscrollDistance = OVERSCROLL_DISTANCE;
        mOverflingDistance = OVERFLING_DISTANCE;
        mHorizontalScrollFactor = HORIZONTAL_SCROLL_FACTOR;
        mVerticalScrollFactor = VERTICAL_SCROLL_FACTOR;
        // AOSP: "Getter throws if mConstructedWithContext is false so doesn't
        // matter what this value is."
        mMinScalingSpan = 0;
    }

    /** AOSP ViewConfiguration(Context) (:394-533). Two different scalings live
     *  here and mixing them up is the classic bug:
     *
     *    sizeAndDensity  -- density, or density*1.5 on an xlarge screen. Used
     *                       by the fields AOSP computes ARITHMETICALLY
     *                       (edge slop, fading edge, double-tap slop, window
     *                       touch slop, overscroll, overfling).
     *    density         -- plain. Used by the fields AOSP resolves from a dp
     *                       RESOURCE (touch slop, hover slop, scrollbar size,
     *                       fling velocities, scroll factors), because a
     *                       resource never sees the screen-size bucket.
     *
     *  Private, like AOSP's: get(Context) is the entry point. */
    private ViewConfiguration(Context context) {
        mConstructedWithContext = true;

        // AOSP reads context.getResources().getDisplayMetrics()/getConfiguration()
        // unconditionally. Ours is defensive because ViewConfiguration.get() is
        // called from widget constructors during very early inflate, where a
        // Context may not have Resources attached yet; before this file had a
        // Context path at all every caller silently got the default metrics, so
        // the fallback is a strict improvement rather than a new failure mode.
        DisplayMetrics metrics = null;
        Configuration config = null;
        try {
            Resources res = (context != null) ? context.getResources() : null;
            if (res != null) {
                metrics = res.getDisplayMetrics();
                config = res.getConfiguration();
            }
        } catch (Throwable ignored) { }
        if (metrics == null) {
            metrics = new DisplayMetrics();
            metrics.setToDefaults();
        }

        final float density = (metrics.density > 0f) ? metrics.density : 1f;
        boolean xlarge = false;
        try {
            if (config != null) {
                xlarge = config.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_XLARGE);
            }
        } catch (Throwable ignored) { }
        final float sizeAndDensity = xlarge ? density * 1.5f : density;

        mEdgeSlop = (int) (sizeAndDensity * EDGE_SLOP + 0.5f);
        mFadingEdgeLength = (int) (sizeAndDensity * FADING_EDGE_LENGTH + 0.5f);
        mDoubleTapSlop = (int) (sizeAndDensity * DOUBLE_TAP_SLOP + 0.5f);
        mWindowTouchSlop = (int) (sizeAndDensity * WINDOW_TOUCH_SLOP + 0.5f);
        mOverscrollDistance = (int) (sizeAndDensity * OVERSCROLL_DISTANCE + 0.5f);
        mOverflingDistance = (int) (sizeAndDensity * OVERFLING_DISTANCE + 0.5f);

        mScrollbarSize = dimenPx(CONFIG_SCROLLBAR_SIZE_DP * density);
        mTouchSlop = dimenPx(CONFIG_VIEW_CONFIGURATION_TOUCH_SLOP_DP * density);
        mHoverSlop = dimenPx(CONFIG_VIEW_CONFIGURATION_HOVER_SLOP_DP * density);
        mMinScrollbarTouchTarget = dimenPx(CONFIG_MIN_SCROLLBAR_TOUCH_TARGET_DP * density);
        mMinimumFlingVelocity = dimenPx(CONFIG_VIEW_MIN_FLING_VELOCITY_DP * density);
        mMaximumFlingVelocity = dimenPx(CONFIG_VIEW_MAX_FLING_VELOCITY_DP * density);
        mHorizontalScrollFactor = dimenPx(CONFIG_HORIZONTAL_SCROLL_FACTOR_DP * density);
        mVerticalScrollFactor = dimenPx(CONFIG_VERTICAL_SCROLL_FACTOR_DP * density);
        mHandwritingSlop = dimenPx(HANDWRITING_SLOP * density);
        mHandwritingGestureLineMargin = dimenPx(HANDWRITING_GESTURE_LINE_MARGIN * density);

        // AOSP: mPagingTouchSlop = mTouchSlop * 2 and mDoubleTapTouchSlop =
        // mTouchSlop -- both derived from the ALREADY-resolved touch slop, not
        // from a separately scaled base constant.
        mPagingTouchSlop = mTouchSlop * 2;
        mDoubleTapTouchSlop = mTouchSlop;

        // COMPLEX_UNIT_MM: TypedValue.applyDimension multiplies by xdpi/25.4.
        final float xdpi = (metrics.xdpi > 0f) ? metrics.xdpi : metrics.densityDpi;
        mMinScalingSpan = dimenPx(CONFIG_MIN_SCALING_SPAN_MM * xdpi * (1.0f / 25.4f));

        mAmbiguousGestureMultiplier =
                Math.max(1.0f, CONFIG_AMBIGUOUS_GESTURE_MULTIPLIER);

        // AOSP: "Size of the screen in bytes, in ARGB_8888 format", from the
        // window manager's maximum window bounds. Our window metrics ARE the
        // display metrics (one window, no multi-window), so read them from
        // there rather than adding a WindowManager dependency to a class that
        // widget constructors touch during early inflate.
        // ⚠ v1.0.939: the REAL display, not the app area. AOSP sizes this from
        // the window manager's MAXIMUM window bounds, which is the physical
        // display -- and since this version getMetrics() reports the smaller
        // app area, reading it here would shrink the cache by a nav strip.
        // Measured: reference 5804672 = 4 * 808 * 1796 (real); the app area
        // would give 4 * 808 * 1700 = 5494400, which is what we used to answer.
        final int w = android.view.Display.macapkRealWidthPx() > 0
                ? android.view.Display.macapkRealWidthPx()  : metrics.widthPixels;
        final int h = android.view.Display.macapkRealHeightPx() > 0
                ? android.view.Display.macapkRealHeightPx() : metrics.heightPixels;
        mMaximumDrawingCacheSize =
                (w > 0 && h > 0) ? (4 * w * h) : MAXIMUM_DRAWING_CACHE_SIZE;
    }

    /** AOSP TypedValue.complexToDimensionPixelSize's rounding, which is what
     *  Resources.getDimensionPixelSize() applies to every dimen the Context
     *  constructor reads: round to nearest, then never round a non-zero
     *  dimension away to nothing. */
    private static int dimenPx(float value) {
        final int res = (int) ((value >= 0) ? (value + 0.5f) : (value - 0.5f));
        if (res != 0) return res;
        if (value == 0) return 0;
        if (value > 0) return 1;
        return -1;
    }

    /** AOSP caches per-density in a static SparseArray. We deliberately do not:
     *  our density never changes but our window size DOES (orientation flip),
     *  and mMaximumDrawingCacheSize is derived from the window size -- a
     *  density-keyed cache would pin the first window's size forever. Building
     *  a fresh instance is cheap and always current. */
    public static ViewConfiguration get(Context ctx) { return new ViewConfiguration(ctx); }

    // ---- Touch slop family
    public int getScaledTouchSlop()                    { return mTouchSlop; }
    public int getScaledPagingTouchSlop()              { return mPagingTouchSlop; }
    public int getScaledDoubleTapSlop()                { return mDoubleTapSlop; }
    public int getScaledDoubleTapTouchSlop()           { return mDoubleTapTouchSlop; }
    public int getScaledEdgeSlop()                     { return mEdgeSlop; }
    public int getScaledWindowTouchSlop()              { return mWindowTouchSlop; }
    public int getScaledHoverSlop()                    { return mHoverSlop; }
    public int getScaledHandwritingSlop()              { return mHandwritingSlop; }
    public int getScaledHandwritingGestureLineMargin() { return mHandwritingGestureLineMargin; }
    public int getScaledMinScrollbarTouchTarget()      { return mMinScrollbarTouchTarget; }

    /** AOSP (:1052-1057): this is the ONE getter that refuses to answer on a
     *  no-Context instance, because the value comes from a physical-unit
     *  (27mm) resource that needs the display's real dpi. Throwing is the
     *  contract, not a failure -- ScaleGestureDetector catches it. */
    public int getScaledMinimumScalingSpan() {
        if (!mConstructedWithContext) {
            throw new IllegalStateException("Min scaling span cannot be determined when this "
                    + "method is called on a ViewConfiguration that was instantiated using a "
                    + "constructor with no Context parameter");
        }
        return mMinScalingSpan;
    }

    // ---- Fling velocity bounds (px/sec)
    public int getScaledMinimumFlingVelocity()       { return mMinimumFlingVelocity; }
    public int getScaledMaximumFlingVelocity()       { return mMaximumFlingVelocity; }
    public int getScaledMinimumScrollWheelVelocity() { return 50; }
    // API 34 (ViewConfiguration.java lines ~1231/1250): per-input-device/axis
    // fling threshold, falling back to a rotary-encoder-specific value for
    // SOURCE_ROTARY_ENCODER. We advertise no rotary encoder (see
    // android.hardware.input.InputManager's device registry), so both
    // overloads collapse to the global value the 0-arg forms return -- the
    // correct answer for the non-rotary path, which is the only one we have.
    public int getScaledMinimumFlingVelocity(int inputDeviceId, int axis, int source) {
        return getScaledMinimumFlingVelocity();
    }
    public int getScaledMaximumFlingVelocity(int inputDeviceId, int axis, int source) {
        return getScaledMaximumFlingVelocity();
    }

    // ---- Overscroll / scrollbar / fading edge sizes
    public int getScaledOverscrollDistance()   { return mOverscrollDistance; }
    public int getScaledOverflingDistance()    { return mOverflingDistance; }
    public int getScaledScrollBarSize()        { return mScrollbarSize; }
    public int getScaledFadingEdgeLength()     { return mFadingEdgeLength; }
    public int getScaledMaximumDrawingCacheSize() { return mMaximumDrawingCacheSize; }

    // ---- API 24+ scroll-factor accessors. RecyclerView's ScrollListener +
    // ViewFlinger fetch these to translate a MotionEvent.ACTION_SCROLL axis
    // value into scroll pixels. AOSP resolves config_{horizontal,vertical}
    // ScrollFactor = 64dp, i.e. 128px at our density -- NOT the bare 64 this
    // file used to return, which under-scrolled every wheel/trackpad event by
    // exactly 2x.
    public float getScaledHorizontalScrollFactor() { return mHorizontalScrollFactor; }
    public float getScaledVerticalScrollFactor()   { return mVerticalScrollFactor; }

    /** AOSP (:1012): the multiplier applied to gesture thresholds when a
     *  MotionEvent is CLASSIFICATION_AMBIGUOUS_GESTURE. */
    public float getScaledAmbiguousGestureMultiplier() { return mAmbiguousGestureMultiplier; }

    // ---- Timeouts (ms), density-independent statics
    // AOSP master: DEFAULT_LONG_PRESS_TIMEOUT = 400, and
    // DEFAULT_KEY_REPEAT_TIMEOUT_MS = DEFAULT_LONG_PRESS_TIMEOUT (both 400).
    public static int getLongPressTimeout()      { return DEFAULT_LONG_PRESS_TIMEOUT; }
    public static int getTapTimeout()            { return TAP_TIMEOUT; }
    public static int getDoubleTapTimeout()      { return DOUBLE_TAP_TIMEOUT; }
    public static int getKeyRepeatTimeout()      { return DEFAULT_LONG_PRESS_TIMEOUT; }
    public static int getKeyRepeatDelay()        { return KEY_REPEAT_DELAY; }
    /** AOSP (:564): SCROLL_BAR_DEFAULT_DELAY, which is 300 -- NOT the 250 of
     *  getScrollBarFadeDuration(). This file had the two swapped. */
    public static int getScrollDefaultDelay()    { return SCROLL_BAR_DEFAULT_DELAY; }
    public static int getScrollBarFadeDuration() { return SCROLL_BAR_FADE_DURATION; }
    public static int getScrollBarDefaultDelayBeforeFade() { return SCROLL_BAR_DEFAULT_DELAY; }
    // Not deprecated in AOSP (unlike most of its neighbors below).
    public static long getZoomControlsTimeout()  { return ZOOM_CONTROLS_TIMEOUT; }
    public static long getDeviceGlobalActionKeyTimeout() { return GLOBAL_ACTIONS_KEY_TIMEOUT; }
    /** AOSP (:983): the default duration for ActionMode.hide(long). */
    public static long getDefaultActionModeHideDuration() {
        return ACTION_MODE_HIDE_DURATION_DEFAULT;
    }
    // AOSP coefficient of friction for fling deceleration (SCROLL_FRICTION =
    // 0.015). Compose's scroll/fling (LazyColumn -- Now-in-Android's For You feed)
    // reads it in the measure pass; a missing method threw NoSuchMethodError there
    // so the lazy list never laid out. v1.0.429.
    public static float getScrollFriction()      { return SCROLL_FRICTION; }

    // Pre-density-aware deprecated static accessors (aosp-mirror lines
    // ~654-1074). AOSP keeps these public, returning the RAW unscaled
    // constant, for source/binary compat with pre-API-11 callers; some
    // androidx internals still call them defensively instead of the
    // getScaledX() instance methods above, which return the real
    // density-aware values. @Deprecated is applied to match AOSP exactly
    // (getPressedStateDuration, getJumpTapTimeout and getZoomControlsTimeout
    // above are NOT deprecated in AOSP; the rest are).
    @Deprecated public static int getScrollBarSize()          { return SCROLL_BAR_SIZE; }
    @Deprecated public static int getFadingEdgeLength()       { return FADING_EDGE_LENGTH; }
    public static int getPressedStateDuration()                { return PRESSED_STATE_DURATION; }
    @Deprecated public static int getTouchSlop()               { return TOUCH_SLOP; }
    @Deprecated public static int getEdgeSlop()                { return EDGE_SLOP; }
    @Deprecated public static int getWindowTouchSlop()         { return WINDOW_TOUCH_SLOP; }
    @Deprecated public static int getMinimumFlingVelocity()    { return MINIMUM_FLING_VELOCITY; }
    @Deprecated public static int getMaximumFlingVelocity()    { return MAXIMUM_FLING_VELOCITY; }
    @Deprecated public static int getMaximumDrawingCacheSize() { return MAXIMUM_DRAWING_CACHE_SIZE; }
    public static int getJumpTapTimeout()                       { return JUMP_TAP_TIMEOUT; }
    /** AOSP (:999), @Deprecated in favour of the scaled instance form. */
    @Deprecated public static float getAmbiguousGestureMultiplier() {
        return AMBIGUOUS_GESTURE_MULTIPLIER;
    }
    // Distinct from getDeviceGlobalActionKeyTimeout() above: AOSP's
    // getDeviceGlobalActionKeyTimeout is @hide/@TestApi (an app can't call
    // it); this is the public-but-@Deprecated form apps actually reference.
    @Deprecated public static long getGlobalActionKeyTimeout() { return GLOBAL_ACTIONS_KEY_TIMEOUT; }

    public boolean hasPermanentMenuKey() { return false; }
    public boolean shouldShowMenuShortcutsWhenKeyboardPresent() { return false; }

    /** AOSP ViewConfiguration.getMultiPressTimeout(): the maximum gap between
     *  two taps that still counts as a multi-press, MULTI_PRESS_TIMEOUT = 300ms
     *  (frameworks/base core/java/android/view/ViewConfiguration.java). AOSP
     *  reads DOUBLE_TAP_TIMEOUT for it, so the two constants are the same 300. */
    public static int getMultiPressTimeout() { return 300; }
}
