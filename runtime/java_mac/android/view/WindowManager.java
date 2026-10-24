package android.view;
import android.content.Context;
import android.os.IBinder;
import java.util.function.Consumer;
// AOSP: `public interface WindowManager extends ViewManager`
// (frameworks/base/core/java/android/view/WindowManager.java). The `extends`
// was missing because ViewManager was mistakenly shipped as a CLASS until
// 2026-07-29; without it, handing a WindowManager to a ViewManager-typed
// parameter is a ClassCastException at runtime. addView/updateViewLayout/
// removeView below ARE ViewManager's three methods -- kept declared here so the
// file still reads as the full window API surface.
public interface WindowManager extends ViewManager {

    /** AOSP WindowManager.registerTrustedPresentationListener (API 34): report
     *  when a window meets the given presentation thresholds. Mac.apk composites
     *  through one Cocoa window and has no per-window trusted-presentation signal
     *  from the host, so we cannot honestly say a window met them -- and this API
     *  exists precisely so an app can gate a SENSITIVE action on the answer.
     *  Reporting `true` would be the dangerous direction: it would tell an app its
     *  content was verifiably visible when we did not verify it. So the listener is
     *  accepted and never invoked, which is the same as a window that never
     *  satisfies the thresholds. AOSP declares these `default`, so an implementor
     *  that does nothing is an anticipated case. Referenced by YouTube. */
    default void registerTrustedPresentationListener(android.os.IBinder window,
            android.window.TrustedPresentationThresholds thresholds,
            java.util.concurrent.Executor executor,
            java.util.function.Consumer<Boolean> listener) {
        if (window == null)     throw new NullPointerException("window must not be null");
        if (thresholds == null) throw new NullPointerException("thresholds must not be null");
        if (executor == null)   throw new NullPointerException("executor must not be null");
        if (listener == null)   throw new NullPointerException("listener must not be null");
    }

    // (unregisterTrustedPresentationListener is declared further down -- the
    // android.view lane added it already. It could not add the REGISTER half
    // because android.window.TrustedPresentationThresholds did not exist yet.)
    Display getDefaultDisplay();
    void addView(View view, ViewGroup.LayoutParams params);
    void updateViewLayout(View view, ViewGroup.LayoutParams params);
    void removeView(View view);
    void removeViewImmediate(View view);

    // API 30 window-size APIs. The Jetpack WindowManager library + Compose's
    // window-size-class path call these to drive responsive layout; a missing
    // method AbstractMethodError'd at the call site. Default impls build a
    // WindowMetrics from the default display size with empty insets, which is the
    // correct shape for our single fixed-size NSWindow. Implementations may
    // override with the real surface bounds.
    /** ⚠ v1.0.939: the REAL display, not Display.getWidth/getHeight.
     *
     *  AOSP's javadoc is explicit that these bounds INCLUDE system decor:
     *  getCurrentWindowMetrics returns "the window size and its insets", where
     *  the bounds "are not limited by the app's window area" and for a
     *  fullscreen activity equal the whole display; getMaximumWindowMetrics is
     *  "the maximum metrics... the largest possible area the window could
     *  occupy". Neither is the decor-subtracted app area.
     *
     *  Display.getWidth/getHeight became the APP size in this same version
     *  (the decor ladder), so reading them here started reporting 808x1700
     *  where the reference says 808x1796 -- and `wm.maximumBoundsEqualsRealSize`
     *  is a probe row precisely because AOSP guarantees that identity. */
    default WindowMetrics getCurrentWindowMetrics() {
        int w = Display.macapkRealWidthPx();
        int h = Display.macapkRealHeightPx();
        if (w <= 0 || h <= 0) {
            Display d = getDefaultDisplay();
            w = (d != null) ? d.getWidth() : 0;
            h = (d != null) ? d.getHeight() : 0;
        }
        return new WindowMetrics(new android.graphics.Rect(0, 0, w, h), macapkMetricsInsets());
    }
    /** The WindowInsets a WindowMetrics carries.
     *
     *  A bare `new WindowInsets.Builder().build()` is NOT the right shape:
     *  AOSP's WindowManagerImpl.computeWindowInsets goes through
     *  InsetsState.calculateInsets, which populates both type maps and always
     *  passes a DisplayCutout (NO_CUTOUT when there is no notch). The
     *  observable consequence is that a real window's metrics insets report
     *  isConsumed()==false, while an empty Builder produces a fully CONSUMED
     *  bag -- and "consumed" is what stops inset dispatch, so handing an app a
     *  consumed bag from getCurrentWindowMetrics() is a meaningfully different
     *  answer, not a cosmetic one.
     *
     *  ⚠ v1.0.939: the VALUES are now the REAL bar geometry. They were zero,
     *  with the comment here reasoning that this interface cannot reach the
     *  loader's StatusBarDriver -- but Display already does exactly that
     *  (macapkStatusDecorPx / macapkNavDecorPx), so the constraint was never
     *  real, and zero was simply a wrong answer: we PAINT a status bar and we
     *  RESERVE a navigation strip, then told any app that asked that neither
     *  exists. That is the one-question-two-answers shape, and it is the shape
     *  an app uses to decide how much padding to add.
     *
     *  The visible-vs-maximum split is the whole reason both APIs exist:
     *
     *    getInsets(statusBars())                  -> 0 for a FLAG_FULLSCREEN
     *                                                window (the bar is hidden)
     *    getInsetsIgnoringVisibility(statusBars()) -> always the 24dp dimension
     *
     *  so the current map is fed from StatusBarDriver.heightPx (flag-gated) and
     *  the max map from heightPxRaw (the dimension), and setVisible follows the
     *  same test. AOSP's InsetsState.calculateInsets does this with a real
     *  per-source visibility bit; ours has one source per bar.
     *
     *  setSystemWindowInsets is deliberately how the current map is written:
     *  assignCompatInsets SPLITS the Rect into statusBars = (0,top,0,0) and
     *  navigationBars = (left,0,right,bottom), which is precisely the split the
     *  reference reports, and it keeps the compat getters (which several probe
     *  contract rows assert equal the typed getters) consistent by
     *  construction. */
    static WindowInsets macapkMetricsInsets() {
        // ⚠ v1.0.997: the navigation inset is not always on the BOTTOM.
        // AOSP's DisplayPolicy.navigationBarPosition moves the bar to the
        // display's right edge in landscape on a phone-class display, and the
        // comment just above already describes the split assignCompatInsets
        // performs -- navigationBars = (left, 0, right, bottom). We simply
        // always wrote 0 into the horizontal terms, which told a landscape app
        // to pad the wrong edge. StatusBarDriver.navBarInset{Bottom,Left,Right}Px
        // is the one predicate; nothing here re-derives it.
        int sb = 0, navB = 0, navL = 0, navR = 0;
        int sbMax = 0, navBMax = 0, navLMax = 0, navRMax = 0;
        try {
            sbMax   = com.macapk.loader.StatusBarDriver.heightPxRaw(null);
            navBMax = com.macapk.loader.StatusBarDriver.navBarInsetBottomPx(null);
            navLMax = com.macapk.loader.StatusBarDriver.navBarInsetLeftPx(null);
            navRMax = com.macapk.loader.StatusBarDriver.navBarInsetRightPx(null);
            sb      = com.macapk.loader.StatusBarDriver.heightPx(null);
            navB    = navBMax;
            navL    = navLMax;
            navR    = navRMax;
        } catch (Throwable ignored) {}
        final int nav = navB + navL + navR;
        return new WindowInsets.Builder()
                .setSystemWindowInsets(android.graphics.Insets.of(navL, sb, navR, navB))
                .setStableInsets(android.graphics.Insets.of(navLMax, sbMax, navRMax, navBMax))
                .setVisible(WindowInsets.Type.statusBars(), sb > 0)
                .setVisible(WindowInsets.Type.navigationBars(), nav > 0)
                .setDisplayCutout(null)   // -> NO_CUTOUT: present but empty, NOT consumed
                .build();
    }
    default WindowMetrics getMaximumWindowMetrics() {
        return getCurrentWindowMetrics();
    }

    /** AOSP WindowManager.isCrossWindowBlurEnabled / add|removeCrossWindow-
     *  BlurEnabledListener (API 31). Whether cross-window blur (LayoutParams.
     *  setBlurBehindRadius / Window.setBackgroundBlurRadius, both below) is
     *  currently in effect, and a listener for when that answer changes at
     *  runtime (GPU limitations, battery saver, multimedia tunneling, etc.
     *  can force it off on a real device).
     *  core/java/android/view/WindowManager.java (android13-release,
     *  :915-985): all three are DEFAULT methods on the real interface, with
     *  empty/false bodies -- WindowManagerImpl overrides them to talk to the
     *  real compositor:
     *      default boolean isCrossWindowBlurEnabled() { return false; }
     *      default void addCrossWindowBlurEnabledListener(Consumer<Boolean> l) {}
     *      default void removeCrossWindowBlurEnabledListener(Consumer<Boolean> l) {}
     *  Mac.apk has no compositor blur, so "blur is never enabled" is the
     *  honest, AOSP-shaped answer -- it is a real value real devices report
     *  too (a GPU-limited or battery-saver device answers false), not a
     *  stand-in for "not implemented". The one piece of REAL behaviour this
     *  adds beyond AOSP's bare default bodies: the javadoc on
     *  addCrossWindowBlurEnabledListener (:933-934) documents "If the
     *  listener is added successfully, it will be called immediately with
     *  the current cross-window blur enabled state" -- that immediate
     *  callback is part of the observable contract apps rely on to
     *  initialize their own blur-dependent state, so it is honoured here
     *  even though the literal interface default body does not show it (the
     *  real implementation lives in WindowManagerImpl, which this interface
     *  has no equivalent of). The state never changes afterward, so nothing
     *  is stored and remove is a safe no-op.
     *
     *  Were entirely absent, so EVERY window's content view threw
     *  NoSuchMethodError the moment it attached. Confirmed via Telegram's
     *  LaunchActivity$6 (a View.OnAttachStateChangeListener installed on the
     *  content view) -- onViewAttachedToWindow calls
     *  addCrossWindowBlurEnabledListener via a $$ExternalSyntheticApiModel-
     *  Outline0 wrapper, onViewDetachedFromWindow calls
     *  removeCrossWindowBlurEnabledListener via ...Outline1. */
    default boolean isCrossWindowBlurEnabled() { return false; }
    default void addCrossWindowBlurEnabledListener(Consumer<Boolean> listener) {
        if (listener != null) listener.accept(isCrossWindowBlurEnabled());
    }
    default void removeCrossWindowBlurEnabledListener(Consumer<Boolean> listener) {}

    /** AOSP WindowManager.addCrossWindowBlurEnabledListener(Executor, Consumer)
     *  (API 31): same contract as the single-argument form above, but the
     *  immediate current-state callback and every later one are delivered on the
     *  supplied Executor. */
    default void addCrossWindowBlurEnabledListener(java.util.concurrent.Executor executor,
            Consumer<Boolean> listener) {
        if (executor != null && listener != null) {
            executor.execute(() -> listener.accept(isCrossWindowBlurEnabled()));
        }
    }

    /** AOSP WindowManager.addProposedRotationListener(Executor, IntConsumer)
     *  (API 33): notifies when the window manager PROPOSES a new rotation for
     *  this context, and (per its javadoc) "the listener will be called
     *  immediately with the current proposed rotation". Mac.apk's window has one
     *  fixed orientation for the life of the process (the launcher picks it
     *  before the runtime starts and there is no sensor to change it), so the
     *  proposal is the current rotation and it never changes -- which is exactly
     *  what a device with rotation locked reports. */
    default void addProposedRotationListener(java.util.concurrent.Executor executor,
            java.util.function.IntConsumer listener) {
        if (executor != null && listener != null) {
            final int rotation = getDefaultDisplay() != null
                    ? getDefaultDisplay().getRotation() : Surface.ROTATION_0;
            executor.execute(() -> listener.accept(rotation));
        }
    }
    /** AOSP WindowManager.removeProposedRotationListener(IntConsumer). Nothing
     *  is stored (the proposal never changes), so removal is a no-op. */
    default void removeProposedRotationListener(java.util.function.IntConsumer listener) {}

    /** AOSP WindowManager.SCREEN_RECORDING_STATE_* (API 35). */
    int SCREEN_RECORDING_STATE_NOT_VISIBLE = 0;
    int SCREEN_RECORDING_STATE_VISIBLE = 1;

    /** AOSP WindowManager.addScreenRecordingCallback(Executor, Consumer) (API 35):
     *  returns the CURRENT screen-recording state and calls back on change.
     *  macOS screen recording is a system-level capture Mac.apk is not told
     *  about, so the honest answer is NOT_VISIBLE -- the same answer a device
     *  gives when nothing is recording. Returned directly (AOSP returns the
     *  current state from the call, not through the callback). */
    default int addScreenRecordingCallback(java.util.concurrent.Executor executor,
            Consumer<Integer> callback) {
        return SCREEN_RECORDING_STATE_NOT_VISIBLE;
    }
    /** AOSP WindowManager.removeScreenRecordingCallback(Consumer). */
    default void removeScreenRecordingCallback(Consumer<Integer> callback) {}

    /** AOSP WindowManager.unregisterSurfaceControlInputReceiver(SurfaceControl)
     *  (API 35). Nothing is ever registered here (see
     *  SurfaceControlInputReceiver's own header), so this is the no-op AOSP
     *  performs for an unknown SurfaceControl. */
    default void unregisterSurfaceControlInputReceiver(SurfaceControl surfaceControl) {}

    /** AOSP WindowManager.unregisterTrustedPresentationListener(Consumer)
     *  (API 35). The matching register* overload needs
     *  android.window.TrustedPresentationThresholds, which this tree does not
     *  have yet -- see the android.view lane report. */
    default void unregisterTrustedPresentationListener(Consumer<Boolean> listener) {}


    class LayoutParams extends ViewGroup.LayoutParams implements android.os.Parcelable {
        public int x, y;
        public int gravity;
        public float horizontalWeight;
        public float verticalWeight;
        public int type;
        public int flags;
        public int softInputMode;
        public int format;
        public int windowAnimations;
        public float alpha = 1f;
        public IBinder token;
        public CharSequence title;
        // AOSP LayoutParams direct fields. Apps write these on the object handed
        // back by Window.getAttributes(), so a missing field is a NoSuchFieldError
        // at the assignment (`params.dimAmount = 0.5f` is the standard
        // FLAG_DIM_BEHIND dialog/overlay idiom). Defaults are AOSP's.
        /** Fraction of the dim applied behind the window when FLAG_DIM_BEHIND is
         *  set: 1.0 fully opaque, 0.0 no dim. */
        public float dimAmount = 1.0f;
        /** AOSP LayoutParams.setBlurBehindRadius / getBlurBehindRadius
         *  (API 31). Blurs the WHOLE SCREEN behind the window (as opposed to
         *  Window.setBackgroundBlurRadius above, which blurs only within the
         *  window's own bounds); requires FLAG_BLUR_BEHIND to be set. Verbatim
         *  shape, core/java/android/view/WindowManager.java (android13-release,
         *  :3512, :3887-3898):
         *      private int mBlurBehindRadius = 0;
         *      public void setBlurBehindRadius(@IntRange(from = 0) int r) {
         *          mBlurBehindRadius = r;
         *      }
         *      public int getBlurBehindRadius() { return mBlurBehindRadius; }
         *  Note AOSP's setter does NOT clamp despite the @IntRange hint (that
         *  is a lint annotation only, not an enforced range) -- mirrored
         *  verbatim, unlike Window.setBackgroundBlurRadius's PhoneWindow
         *  clamp, which is a genuinely different method on a genuinely
         *  different concrete class. Mac.apk has no compositor blur, so this
         *  stores the value and reports it back through the getter rather
         *  than rendering it.
         *
         *  Was entirely absent, so any window requesting blur-behind threw
         *  NoSuchMethodError. Confirmed via Telegram's
         *  AlertDialog$$ExternalSyntheticApiModelOutline1.m(LayoutParams,
         *  int) -> invokevirtual LayoutParams.setBlurBehindRadius:(I)V. */
        private int mBlurBehindRadius = 0;
        public void setBlurBehindRadius(int blurBehindRadius) { mBlurBehindRadius = blurBehindRadius; }
        public int getBlurBehindRadius() { return mBlurBehindRadius; }
        /** Horizontal/vertical margin of the container, as a fraction of the
         *  container's width/height, applied on top of gravity. */
        public float horizontalMargin;
        public float verticalMargin;
        /** Value for screenBrightness/buttonBrightness meaning "use the user's
         *  preferred brightness" (i.e. do not override). */
        public static final float BRIGHTNESS_OVERRIDE_NONE = -1.0f;
        public static final float BRIGHTNESS_OVERRIDE_OFF  = 0.0f;
        public static final float BRIGHTNESS_OVERRIDE_FULL = 1.0f;
        public float screenBrightness = BRIGHTNESS_OVERRIDE_NONE;
        public float buttonBrightness = BRIGHTNESS_OVERRIDE_NONE;
        public static final int ROTATION_ANIMATION_ROTATE    = 0;
        public static final int ROTATION_ANIMATION_CROSSFADE = 1;
        public static final int ROTATION_ANIMATION_JUMPCUT   = 2;
        public static final int ROTATION_ANIMATION_SEAMLESS  = 3;
        public int rotationAnimation = ROTATION_ANIMATION_ROTATE;
        /** Name of the package owning this window. AOSP leaves it null until the
         *  window is attached; ViewRootImpl-adjacent app code both reads and
         *  writes it. */
        public String packageName = null;
        public int privateFlags;
        public static final int PRIVATE_FLAG_FIT_INSETS_CONTROLLED = 1 << 28;
        // Android P+ display-cutout (notch) handling. Apps set this in
        // onCreate to opt into drawing under the cutout. Mac.apk has
        // no notch concept; we accept the field for API compatibility.
        // Constants mirror android.view.WindowManager.LayoutParams.*.
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT       = 0;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES   = 1;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER         = 2;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS        = 3;
        public int layoutInDisplayCutoutMode;
        // Preferred refresh-rate mode (API 23+). libGDX (Unciv) writes this in its
        // window setup; a missing field threw NoSuchFieldError every frame.
        public int preferredDisplayModeId;
        public float preferredRefreshRate;
        /** AOSP WindowManager.java:2519. The orientation the window requests,
         *  defaulting to "whatever the activity/manifest says"
         *  (ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED = -1). Absent here, so
         *  `getWindow().getAttributes().screenOrientation` -- the standard way
         *  a game forces landscape from code -- was a NoSuchFieldError at the
         *  read, not just at the write. */
        public int screenOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        /** AOSP WindowManager.java:2556 / :2565. The window's own copy of the
         *  View.SYSTEM_UI_FLAG_* word (and the subtree's union of it). Absent
         *  here, so the pre-API-30 way of asking for immersive / edge-to-edge
         *  through the LayoutParams threw NoSuchFieldError. Stored only; the
         *  decor-fits signal Mac.apk acts on comes through
         *  View.setSystemUiVisibility (see Window.macapkSetDecorFits). */
        public int systemUiVisibility;
        public int subtreeSystemUiVisibility;
        /** AOSP WindowManager.java:2837 + :3063-3073. The requested colour
         *  mode, read back through Window.getColorMode(). */
        private int mColorMode = android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT;
        public void setColorMode(int colorMode) { mColorMode = colorMode; }
        public int getColorMode() { return mColorMode; }
        // Window flags (android.view.WindowManager.LayoutParams.FLAG_*). Unity's
        // native code reads FLAG_KEEP_SCREEN_ON via bitter.jnibridge during startup
        // (Screen.sleepTimeout); a missing field threw NoSuchFieldError in the posted
        // runnable. Values mirror AOSP frameworks/base WindowManager.LayoutParams.
        public static final int FLAG_ALLOW_LOCK_WHILE_SCREEN_ON = 0x00000001;
        public static final int FLAG_DIM_BEHIND                 = 0x00000002;
        public static final int FLAG_BLUR_BEHIND                = 0x00000004;
        public static final int FLAG_NOT_FOCUSABLE              = 0x00000008;
        public static final int FLAG_NOT_TOUCHABLE              = 0x00000010;
        public static final int FLAG_NOT_TOUCH_MODAL            = 0x00000020;
        public static final int FLAG_TOUCHABLE_WHEN_WAKING      = 0x00000040;
        public static final int FLAG_KEEP_SCREEN_ON             = 0x00000080;
        public static final int FLAG_LAYOUT_IN_SCREEN           = 0x00000100;
        public static final int FLAG_LAYOUT_NO_LIMITS           = 0x00000200;
        public static final int FLAG_FULLSCREEN                 = 0x00000400;
        public static final int FLAG_FORCE_NOT_FULLSCREEN       = 0x00000800;
        public static final int FLAG_DITHER                     = 0x00001000;
        public static final int FLAG_SECURE                     = 0x00002000;
        public static final int FLAG_SCALED                     = 0x00004000;
        public static final int FLAG_IGNORE_CHEEK_PRESSES       = 0x00008000;
        public static final int FLAG_LAYOUT_INSET_DECOR         = 0x00010000;
        public static final int FLAG_ALT_FOCUSABLE_IM           = 0x00020000;
        public static final int FLAG_WATCH_OUTSIDE_TOUCH        = 0x00040000;
        public static final int FLAG_SHOW_WHEN_LOCKED           = 0x00080000;
        public static final int FLAG_SHOW_WALLPAPER             = 0x00100000;
        public static final int FLAG_TURN_SCREEN_ON             = 0x00200000;
        public static final int FLAG_DISMISS_KEYGUARD           = 0x00400000;
        public static final int FLAG_SPLIT_TOUCH                = 0x00800000;
        public static final int FLAG_HARDWARE_ACCELERATED       = 0x01000000;
        public static final int FLAG_LAYOUT_IN_OVERSCAN         = 0x02000000;
        public static final int FLAG_TRANSLUCENT_STATUS         = 0x04000000;
        public static final int FLAG_TRANSLUCENT_NAVIGATION     = 0x08000000;
        public static final int FLAG_LOCAL_FOCUS_MODE           = 0x10000000;
        public static final int FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = 0x80000000;
        // Common window types + soft-input modes (TYPE_* / SOFT_INPUT_*).
        public static final int TYPE_BASE_APPLICATION          = 1;
        public static final int TYPE_APPLICATION               = 2;
        // AOSP: TYPE_APPLICATION_PANEL = FIRST_SUB_WINDOW = 1000 (1001 is
        // TYPE_APPLICATION_MEDIA). frameworks/base/core/java/android/view/WindowManager.java
        // (android-11.0.0_r48)
        public static final int FIRST_SUB_WINDOW               = 1000;
        public static final int TYPE_APPLICATION_PANEL         = FIRST_SUB_WINDOW;
        public static final int SOFT_INPUT_ADJUST_RESIZE       = 0x10;
        public static final int SOFT_INPUT_ADJUST_PAN          = 0x20;
        public static final int SOFT_INPUT_ADJUST_NOTHING      = 0x30;
        public static final int SOFT_INPUT_STATE_HIDDEN        = 0x02;
        public static final int SOFT_INPUT_STATE_ALWAYS_HIDDEN = 0x03;
        // The rest of AOSP's soft-input constants (WindowManager.java,
        // android16-release); NativeActivity.onCreate passes
        // SOFT_INPUT_STATE_UNSPECIFIED | SOFT_INPUT_ADJUST_RESIZE.
        public static final int SOFT_INPUT_MASK_STATE           = 0x0f;
        public static final int SOFT_INPUT_STATE_UNSPECIFIED    = 0;
        public static final int SOFT_INPUT_STATE_UNCHANGED      = 1;
        public static final int SOFT_INPUT_STATE_VISIBLE        = 4;
        public static final int SOFT_INPUT_STATE_ALWAYS_VISIBLE = 5;
        public static final int SOFT_INPUT_MASK_ADJUST          = 0xf0;
        public static final int SOFT_INPUT_ADJUST_UNSPECIFIED   = 0x00;
        public static final int SOFT_INPUT_IS_FORWARD_NAVIGATION = 0x100;
        // AOSP LayoutParams constructors (WindowManager.java :2971-2990,
        // android-11.0.0_r48):
        //     public LayoutParams() {
        //         super(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        //         type = TYPE_APPLICATION;
        //         format = PixelFormat.OPAQUE;
        //     }
        // Ours seeded nothing, so a default-constructed LayoutParams -- which
        // is what every Window in this tree hands back from getAttributes() --
        // reported width=0 height=0 type=0 format=0 where a real one reports
        // -1 / -1 / TYPE_APPLICATION / OPAQUE. Type 0 is not even a legal
        // window type, and a 0x0 window is the degenerate case
        // WindowOverlayStack.measureAndLayout falls back on.
        public LayoutParams() {
            super(MATCH_PARENT, MATCH_PARENT);
            type = TYPE_APPLICATION;
            format = android.graphics.PixelFormat.OPAQUE;
        }

        /** AOSP LayoutParams(int _type) -- WindowManager.java:4503,
         *  android14-release:
         *      super(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
         *      type = _type;
         *      format = PixelFormat.OPAQUE;
         *  Absent here until the 2026-08-27 fleet census turned it up as a
         *  referenced-but-missing method (`WindowManager$LayoutParams.<init>(I)V`,
         *  org.joinmastodon.android). */
        public LayoutParams(int _type) {
            super(MATCH_PARENT, MATCH_PARENT);
            type = _type;
            format = android.graphics.PixelFormat.OPAQUE;
        }

        /** AOSP LayoutParams(int _type, int _flags) -- WindowManager.java:4509.
         *
         *  ⚠ THE ORDERING TRAP, AND WE HAD IT BACKWARDS. This slot used to hold
         *  `LayoutParams(int w, int h) { super(w, h); type = TYPE_APPLICATION; }`,
         *  which is not a constructor AOSP has at all: WindowManager.LayoutParams
         *  declares exactly seven, and NONE of them is (width, height) --
         *      LayoutParams()
         *      LayoutParams(int _type)
         *      LayoutParams(int _type, int _flags)
         *      LayoutParams(int _type, int _flags, int _format)
         *      LayoutParams(int w, int h, int _type, int _flags, int _format)
         *      LayoutParams(int w, int h, int xpos, int ypos, int _type, int _flags, int _format)
         *      LayoutParams(Parcel in)
         *  Width and height only appear once there are five or more arguments.
         *
         *  The consequence was silent, not a crash: `new WindowManager.LayoutParams(
         *  TYPE_APPLICATION_PANEL, FLAG_NOT_FOCUSABLE)` -- an extremely common
         *  idiom for dialogs, popups and overlays -- produced a window whose
         *  width was the TYPE constant, whose height was the FLAGS bitmask, and
         *  whose type/flags were the defaults. The 3-arg ctor below already
         *  carried a comment warning about exactly this trap; the 2-arg one was
         *  left wrong beside it.
         *
         *  No in-tree caller used the (w, h) reading (checked: Window.java:424,
         *  WindowOverlayStack.java:184 and WindowManager.java:816 all use the
         *  no-arg or Parcel form), so nothing in the framework changes meaning. */
        public LayoutParams(int _type, int _flags) {
            super(MATCH_PARENT, MATCH_PARENT);
            type = _type;
            flags = _flags;
            format = android.graphics.PixelFormat.OPAQUE;
        }
        public LayoutParams(int w, int h, int type, int flags, int format) { super(w, h); this.type = type; this.flags = flags; this.format = format; }
        public LayoutParams(int w, int h, int xpos, int ypos, int _type, int _flags, int _format) {
            super(w, h);
            x = xpos;
            y = ypos;
            type = _type;
            flags = _flags;
            format = _format;
        }

        // AOSP holds the title in a private mTitle and exposes it only through
        // setTitle/getTitle; our `title` field predates that and in-tree code
        // reads it, so setTitle writes through to the same storage. The
        // load-bearing part of AOSP's body is the null -> "" coercion (callers
        // rely on getTitle() never returning null) and routing through
        // TextUtils.stringOrSpannedString so a Spanned title keeps its spans.
        public final void setTitle(CharSequence title) {
            if (null == title) title = "";
            this.title = android.text.TextUtils.stringOrSpannedString(title);
        }
        public final CharSequence getTitle() { return title != null ? title : ""; }

        // ---- AOSP API-36 surface completion (android.view lane) -------------
        // Every member below is declared on WindowManager.LayoutParams by AOSP's
        // core/api/current.txt and was absent here. Bodies mirror
        // frameworks/base core/java/android/view/WindowManager.java
        // (android16-release); the ones the compositor would consume are stored
        // and read back, because macOS has no equivalent knob to forward them to
        // and reporting a value the app did not set would be worse than
        // reporting its own.

        /** AOSP LayoutParams(int _type, int _flags, int _format)
         *  (WindowManager.java, the 3-arg ctor): MATCH_PARENT x MATCH_PARENT.
         *  NOTE the ordering trap -- AOSP's 3-arg ctor is (type, flags, format),
         *  NOT (width, height, type). */
        public LayoutParams(int _type, int _flags, int _format) {
            super(MATCH_PARENT, MATCH_PARENT);
            type = _type;
            flags = _flags;
            format = _format;
        }

        /** AOSP LayoutParams.memoryType, deprecated since API 5 and ignored by
         *  the platform ever since; kept because an app that names the field
         *  still gets a NoSuchFieldError without it. */
        @Deprecated
        public int memoryType;

        /** AOSP LayoutParams.preferMinimalPostProcessing (API 30): asks the
         *  display to minimise post-processing latency (game/VR mode). macOS
         *  exposes no such display control, so it round-trips only -- matching a
         *  device whose display does not support it, where
         *  Display.isMinimalPostProcessingSupported() is false. */
        public boolean preferMinimalPostProcessing;

        /** AOSP LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION (WindowManager.java:3448). */
        public static final int PRIVATE_FLAG_NO_MOVE_ANIMATION = 1 << 6;
        /** AOSP LayoutParams.setCanPlayMoveAnimation:4925 / canPlayMoveAnimation:4940
         *  -- an inverted PRIVATE_FLAG_NO_MOVE_ANIMATION bit, default "can play". */
        public void setCanPlayMoveAnimation(boolean enable) {
            if (enable) privateFlags &= ~PRIVATE_FLAG_NO_MOVE_ANIMATION;
            else        privateFlags |= PRIVATE_FLAG_NO_MOVE_ANIMATION;
        }
        public boolean canPlayMoveAnimation() {
            return (privateFlags & PRIVATE_FLAG_NO_MOVE_ANIMATION) == 0;
        }

        /** AOSP LayoutParams.setWallpaperTouchEventsEnabled:4903 /
         *  areWallpaperTouchEventsEnabled:4915 -- a plain boolean field, default
         *  true in AOSP. */
        private boolean mWallpaperTouchEventsEnabled = true;
        public void setWallpaperTouchEventsEnabled(boolean enable) {
            mWallpaperTouchEventsEnabled = enable;
        }
        public boolean areWallpaperTouchEventsEnabled() { return mWallpaperTouchEventsEnabled; }

        /** AOSP LayoutParams.DISPLAY_FLAG_DISABLE_HDR_CONVERSION (:4262) and the
         *  setHdrConversionEnabled:5088 / isHdrConversionEnabled:5079 pair that
         *  toggles it. Default enabled, as on AOSP. */
        private int mDisplayFlags;
        private static final int DISPLAY_FLAG_DISABLE_HDR_CONVERSION = 1 << 0;
        public void setHdrConversionEnabled(boolean enabled) {
            if (!enabled) mDisplayFlags |= DISPLAY_FLAG_DISABLE_HDR_CONVERSION;
            else          mDisplayFlags &= ~DISPLAY_FLAG_DISABLE_HDR_CONVERSION;
        }
        public boolean isHdrConversionEnabled() {
            return (mDisplayFlags & DISPLAY_FLAG_DISABLE_HDR_CONVERSION) == 0;
        }

        /** AOSP LayoutParams.setDesiredHdrHeadroom / getDesiredHdrHeadroom:5150
         *  (API 35): how much HDR headroom the window wants, 0 meaning "let the
         *  system decide". AOSP's setter validates and stores; the value reaches
         *  the display pipeline. We have no HDR pipeline to feed, so it stores
         *  and reads back -- the same as on an SDR display, where the request is
         *  accepted and has no effect. The range check is AOSP's own. */
        private float mDesiredHdrHeadroom;
        public void setDesiredHdrHeadroom(float desiredHeadroom) {
            if (!Float.isFinite(desiredHeadroom)) {
                throw new IllegalArgumentException("desiredHeadroom must be finite: "
                        + desiredHeadroom);
            }
            if (desiredHeadroom < 0 || desiredHeadroom > 10000.0f) {
                throw new IllegalArgumentException("desiredHeadroom must be in the range "
                        + "[0, 10000.0f], received: " + desiredHeadroom);
            }
            mDesiredHdrHeadroom = desiredHeadroom;
        }
        public float getDesiredHdrHeadroom() { return mDesiredHdrHeadroom; }

        /** AOSP LayoutParams.setFrameRateBoostOnTouchEnabled /
         *  getFrameRateBoostOnTouchEnabled (API 35): whether the platform may
         *  raise the refresh rate while the user touches this window. AOSP's
         *  default is true. Mac.apk presents through CAMetalLayer at the
         *  display's own rate with no per-window boost to enable, so this is a
         *  round-trip -- the same observable behaviour as a fixed-refresh-rate
         *  device, which accepts the request and cannot act on it. */
        private boolean mFrameRateBoostOnTouch = true;
        public void setFrameRateBoostOnTouchEnabled(boolean enabled) {
            mFrameRateBoostOnTouch = enabled;
        }
        public boolean getFrameRateBoostOnTouchEnabled() { return mFrameRateBoostOnTouch; }

        /** AOSP LayoutParams.setFrameRatePowerSavingsBalanced /
         *  isFrameRatePowerSavingsBalanced (API 35). AOSP's default is true. */
        private boolean mFrameRatePowerSavingsBalanced = true;
        public void setFrameRatePowerSavingsBalanced(boolean enabled) {
            mFrameRatePowerSavingsBalanced = enabled;
        }
        public boolean isFrameRatePowerSavingsBalanced() {
            return mFrameRatePowerSavingsBalanced;
        }

        /** AOSP LayoutParams.debug(String output): a human-readable dump used by
         *  ViewDebug / dumpsys. AOSP prefixes each line with the caller's tag. */
        public String debug(String output) {
            output += "Contents of " + this + ":";
            output += "\n     ViewGroup.LayoutParams={ width=" + width + ", height=" + height + " }";
            output += "\n     WindowManager.LayoutParams={ gravity=" + Integer.toHexString(gravity)
                    + " type=" + type + " flags=" + Integer.toHexString(flags)
                    + " format=" + format + " softInputMode="
                    + Integer.toHexString(softInputMode) + " }";
            return output;
        }


        // AOSP change-mask bits returned by copyFrom. Callers (ViewRootImpl-
        // adjacent app code) test individual bits to decide what to re-do, so
        // the values have to be the platform ones, not an ad-hoc enumeration.
        public static final int LAYOUT_CHANGED                  = 1 << 0;
        public static final int TYPE_CHANGED                    = 1 << 1;
        public static final int FLAGS_CHANGED                   = 1 << 2;
        public static final int FORMAT_CHANGED                  = 1 << 3;
        public static final int ANIMATION_CHANGED               = 1 << 4;
        public static final int DIM_AMOUNT_CHANGED              = 1 << 5;
        public static final int TITLE_CHANGED                   = 1 << 6;
        public static final int ALPHA_CHANGED                   = 1 << 7;
        public static final int MEMORY_TYPE_CHANGED             = 1 << 8;
        public static final int SOFT_INPUT_MODE_CHANGED         = 1 << 9;
        public static final int SCREEN_ORIENTATION_CHANGED      = 1 << 10;
        public static final int SCREEN_BRIGHTNESS_CHANGED       = 1 << 11;
        public static final int ROTATION_ANIMATION_CHANGED      = 1 << 12;
        public static final int BUTTON_BRIGHTNESS_CHANGED       = 1 << 13;
        public static final int SYSTEM_UI_VISIBILITY_CHANGED    = 1 << 16;
        public static final int PRIVATE_FLAGS_CHANGED           = 1 << 17;
        public static final int PREFERRED_REFRESH_RATE_CHANGED  = 1 << 21;
        public static final int PREFERRED_DISPLAY_MODE_ID       = 1 << 23;
        public static final int COLOR_MODE_CHANGED              = 1 << 26;
        /** AOSP WindowManager.java (android13-release, :4160): the platform
         *  bit for a changed setBlurBehindRadius value, wired into copyFrom
         *  below alongside every other tracked field. */
        public static final int BLUR_BEHIND_RADIUS_CHANGED      = 1 << 29;

        /** AOSP copyFrom: overwrite this object with o's values and return a
         *  bitmask naming what actually changed. Only the fields this class
         *  models are diffed; every one uses AOSP's own change bit. Note the
         *  title rule -- AOSP only copies a NON-null incoming title, so a
         *  default-constructed LayoutParams never clears an existing title. */
        public final int copyFrom(LayoutParams o) {
            int changes = 0;
            if (width != o.width)   { width = o.width;   changes |= LAYOUT_CHANGED; }
            if (height != o.height) { height = o.height; changes |= LAYOUT_CHANGED; }
            if (x != o.x)           { x = o.x;           changes |= LAYOUT_CHANGED; }
            if (y != o.y)           { y = o.y;           changes |= LAYOUT_CHANGED; }
            if (horizontalWeight != o.horizontalWeight) {
                horizontalWeight = o.horizontalWeight; changes |= LAYOUT_CHANGED;
            }
            if (verticalWeight != o.verticalWeight) {
                verticalWeight = o.verticalWeight; changes |= LAYOUT_CHANGED;
            }
            if (horizontalMargin != o.horizontalMargin) {
                horizontalMargin = o.horizontalMargin; changes |= LAYOUT_CHANGED;
            }
            if (verticalMargin != o.verticalMargin) {
                verticalMargin = o.verticalMargin; changes |= LAYOUT_CHANGED;
            }
            if (type != o.type)   { type = o.type;   changes |= TYPE_CHANGED; }
            if (flags != o.flags) { flags = o.flags; changes |= FLAGS_CHANGED; }
            if (privateFlags != o.privateFlags) {
                privateFlags = o.privateFlags; changes |= PRIVATE_FLAGS_CHANGED;
            }
            if (softInputMode != o.softInputMode) {
                softInputMode = o.softInputMode; changes |= SOFT_INPUT_MODE_CHANGED;
            }
            if (layoutInDisplayCutoutMode != o.layoutInDisplayCutoutMode) {
                layoutInDisplayCutoutMode = o.layoutInDisplayCutoutMode;
                changes |= LAYOUT_CHANGED;
            }
            if (gravity != o.gravity) { gravity = o.gravity; changes |= LAYOUT_CHANGED; }
            if (format != o.format)   { format = o.format;   changes |= FORMAT_CHANGED; }
            if (windowAnimations != o.windowAnimations) {
                windowAnimations = o.windowAnimations; changes |= ANIMATION_CHANGED;
            }
            if (token == null) { token = o.token; }
            if (packageName == null) { packageName = o.packageName; }
            if (!java.util.Objects.equals(title, o.title) && o.title != null) {
                title = o.title; changes |= TITLE_CHANGED;
            }
            if (alpha != o.alpha)         { alpha = o.alpha;         changes |= ALPHA_CHANGED; }
            if (dimAmount != o.dimAmount) { dimAmount = o.dimAmount; changes |= DIM_AMOUNT_CHANGED; }
            if (screenBrightness != o.screenBrightness) {
                screenBrightness = o.screenBrightness; changes |= SCREEN_BRIGHTNESS_CHANGED;
            }
            if (buttonBrightness != o.buttonBrightness) {
                buttonBrightness = o.buttonBrightness; changes |= BUTTON_BRIGHTNESS_CHANGED;
            }
            if (rotationAnimation != o.rotationAnimation) {
                rotationAnimation = o.rotationAnimation; changes |= ROTATION_ANIMATION_CHANGED;
            }
            if (preferredRefreshRate != o.preferredRefreshRate) {
                preferredRefreshRate = o.preferredRefreshRate;
                changes |= PREFERRED_REFRESH_RATE_CHANGED;
            }
            if (preferredDisplayModeId != o.preferredDisplayModeId) {
                preferredDisplayModeId = o.preferredDisplayModeId;
                changes |= PREFERRED_DISPLAY_MODE_ID;
            }
            if (mFitInsetsTypes != o.mFitInsetsTypes) {
                mFitInsetsTypes = o.mFitInsetsTypes; changes |= LAYOUT_CHANGED;
            }
            if (mFitInsetsSides != o.mFitInsetsSides) {
                mFitInsetsSides = o.mFitInsetsSides; changes |= LAYOUT_CHANGED;
            }
            if (mFitInsetsIgnoringVisibility != o.mFitInsetsIgnoringVisibility) {
                mFitInsetsIgnoringVisibility = o.mFitInsetsIgnoringVisibility;
                changes |= LAYOUT_CHANGED;
            }
            if (screenOrientation != o.screenOrientation) {
                screenOrientation = o.screenOrientation;
                changes |= SCREEN_ORIENTATION_CHANGED;
            }
            if (systemUiVisibility != o.systemUiVisibility
                    || subtreeSystemUiVisibility != o.subtreeSystemUiVisibility) {
                systemUiVisibility = o.systemUiVisibility;
                subtreeSystemUiVisibility = o.subtreeSystemUiVisibility;
                changes |= SYSTEM_UI_VISIBILITY_CHANGED;
            }
            if (mColorMode != o.mColorMode) {
                mColorMode = o.mColorMode;
                changes |= COLOR_MODE_CHANGED;
            }
            if (mBlurBehindRadius != o.mBlurBehindRadius) {
                mBlurBehindRadius = o.mBlurBehindRadius;
                changes |= BLUR_BEHIND_RADIUS_CHANGED;
            }
            return changes;
        }

        // AOSP's initial fit-insets state is "avoid all the system bars on every
        // side" (WindowManager.java :2881 / :2901). The sides literal is gone
        // now that WindowInsets carries the real Side class.
        //
        // NOTE for anyone comparing against a device: a REAL activity answers 0
        // for both of these after its decor is installed, because
        // PhoneWindow.generateLayout (:2384-2385) calls setFitInsetsSides(0) /
        // setFitInsetsTypes(0) for a non-floating window -- the window frame
        // then spans the whole display and the decor applies the insets itself.
        // That is a generateLayout behaviour we do not have, NOT a wrong
        // default: the values below are exactly AOSP's pre-install defaults and
        // must stay that way.
        private int mFitInsetsTypes = WindowInsets.Type.systemBars();
        private int mFitInsetsSides = WindowInsets.Side.all();
        private boolean mFitInsetsIgnoringVisibility = false;

        public void setFitInsetsTypes(int types) {
            mFitInsetsTypes = types;
            privateFlags |= PRIVATE_FLAG_FIT_INSETS_CONTROLLED;
        }
        public void setFitInsetsSides(int sides) {
            mFitInsetsSides = sides;
            privateFlags |= PRIVATE_FLAG_FIT_INSETS_CONTROLLED;
        }
        public void setFitInsetsIgnoringVisibility(boolean ignore) {
            mFitInsetsIgnoringVisibility = ignore;
            privateFlags |= PRIVATE_FLAG_FIT_INSETS_CONTROLLED;
        }
        public int getFitInsetsTypes() { return mFitInsetsTypes; }
        public int getFitInsetsSides() { return mFitInsetsSides; }
        public boolean isFitInsetsIgnoringVisibility() { return mFitInsetsIgnoringVisibility; }

        /** AOSP: a window can host the IME unless it is both non-focusable and
         *  alt-focusable-IM; the two flag tests are what the framework itself
         *  uses to decide IME eligibility. */
        public static boolean mayUseInputMethod(int flags) {
            return (flags & FLAG_NOT_FOCUSABLE) != FLAG_NOT_FOCUSABLE
                    && (flags & FLAG_ALT_FOCUSABLE_IM) != FLAG_ALT_FOCUSABLE_IM;
        }

        // 2026-08-23 conformance sweep: AOSP's writeToParcel carries ~45
        // fields (WindowManager.java L5302-5361), including several this
        // class does not model (mWindowContextToken, preferredMin/Max
        // DisplayRefreshRate, surfaceInsets, accessibilityIdOfAnchor,
        // inputFeatures, providedInsets, paramsForRotation, ...). This
        // writes the subset this class DOES store, in AOSP's own leading
        // order wherever the field name matches, so the wire shape is a
        // faithful PREFIX of AOSP's rather than an arbitrary reordering.
        // horizontalWeight/verticalWeight (also inherited from
        // ViewGroup.LayoutParams) are DELIBERATELY absent below, matching
        // AOSP's own writeToParcel exactly -- those two fields only matter
        // when a LayoutParams is used as a ViewGroup child's layout params,
        // never for a top-level window, so AOSP itself never parcels them.
        // width/height come from the ViewGroup.LayoutParams superclass,
        // exactly as AOSP's own class (also a ViewGroup.LayoutParams
        // subclass) writes its inherited fields first.
        @Override public int describeContents() { return 0; }

        @Override public void writeToParcel(android.os.Parcel out, int parcelableFlags) {
            out.writeInt(width);
            out.writeInt(height);
            out.writeInt(x);
            out.writeInt(y);
            out.writeInt(type);
            out.writeInt(flags);
            out.writeInt(privateFlags);
            out.writeInt(softInputMode);
            out.writeInt(layoutInDisplayCutoutMode);
            out.writeInt(gravity);
            out.writeFloat(horizontalMargin);
            out.writeFloat(verticalMargin);
            out.writeInt(format);
            out.writeInt(windowAnimations);
            out.writeFloat(alpha);
            out.writeFloat(dimAmount);
            out.writeFloat(screenBrightness);
            out.writeFloat(buttonBrightness);
            out.writeInt(rotationAnimation);
            out.writeStrongBinder(token);
            out.writeString(packageName);
            out.writeCharSequence(title);
            out.writeInt(screenOrientation);
            out.writeFloat(preferredRefreshRate);
            out.writeInt(preferredDisplayModeId);
            out.writeInt(systemUiVisibility);
            out.writeInt(subtreeSystemUiVisibility);
            out.writeInt(mColorMode);
            out.writeInt(mFitInsetsTypes);
            out.writeInt(mFitInsetsSides);
            out.writeBoolean(mFitInsetsIgnoringVisibility);
            out.writeInt(mBlurBehindRadius);
        }

        public LayoutParams(android.os.Parcel in) {
            width = in.readInt();
            height = in.readInt();
            x = in.readInt();
            y = in.readInt();
            type = in.readInt();
            flags = in.readInt();
            privateFlags = in.readInt();
            softInputMode = in.readInt();
            layoutInDisplayCutoutMode = in.readInt();
            gravity = in.readInt();
            horizontalMargin = in.readFloat();
            verticalMargin = in.readFloat();
            format = in.readInt();
            windowAnimations = in.readInt();
            alpha = in.readFloat();
            dimAmount = in.readFloat();
            screenBrightness = in.readFloat();
            buttonBrightness = in.readFloat();
            rotationAnimation = in.readInt();
            token = in.readStrongBinder();
            packageName = in.readString();
            title = in.readCharSequence();
            screenOrientation = in.readInt();
            preferredRefreshRate = in.readFloat();
            preferredDisplayModeId = in.readInt();
            systemUiVisibility = in.readInt();
            subtreeSystemUiVisibility = in.readInt();
            mColorMode = in.readInt();
            mFitInsetsTypes = in.readInt();
            mFitInsetsSides = in.readInt();
            mFitInsetsIgnoringVisibility = in.readBoolean();
            mBlurBehindRadius = in.readInt();
        }

        public static final android.os.Parcelable.Creator<LayoutParams> CREATOR =
                new android.os.Parcelable.Creator<LayoutParams>() {
            @Override public LayoutParams createFromParcel(android.os.Parcel in) {
                return new LayoutParams(in);
            }
            @Override public LayoutParams[] newArray(int size) { return new LayoutParams[size]; }
        };
    }
    class BadTokenException extends RuntimeException {
        public BadTokenException() { super(); }
        public BadTokenException(String msg) { super(msg); }
    }
    // Thrown by addView when the target display is invalid/removed. Was absent;
    // apps that catch it around addView referenced the type. Mirrors
    // android.view.WindowManager.InvalidDisplayException.
    class InvalidDisplayException extends RuntimeException {
        public InvalidDisplayException() { super(); }
        public InvalidDisplayException(String name) { super(name); }
    }
}
