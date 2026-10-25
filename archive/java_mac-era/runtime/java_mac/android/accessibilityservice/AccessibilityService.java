package android.accessibilityservice;

import android.content.Intent;
import android.graphics.ColorSpace;
import android.graphics.Region;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.IBinder;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;

/**
 * Base class for an accessibility service.
 *
 * WHAT MAC.APK CAN AND CANNOT DO HERE. Mac.apk runs exactly ONE app, and an
 * accessibility service is by definition a service the SYSTEM binds so it can
 * observe OTHER apps. There is no system here to bind it and no other app to
 * observe, so a guest that declares one is never started: onAccessibilityEvent
 * never fires, getWindows() sees nothing, and performGlobalAction has no
 * navigation bar to act on.
 *
 * That is not the same as "the class does not matter". Everything below has to
 * exist and has to LINK, because:
 *   - an app that ships an accessibility service still loads the class when its
 *     own settings screen reflects over it, and a NoSuchMethodError inside a
 *     coroutine is silent (see docs / the Aurora getArchivedPackage regression);
 *   - the accessors are declared final in AOSP, so a subclass cannot supply
 *     them itself;
 *   - the controller objects handed out here are the ones a service touches in
 *     onServiceConnected, which some apps call directly from a test or a
 *     "check my setup" button.
 *
 * So every accessor returns a REAL, non-null controller in the disconnected
 * state AOSP itself reports (button unavailable, no fingerprint hardware,
 * STATE_CLEAR, no windows). Nothing here fabricates a bound service.
 *
 * Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt
 * (signature format 2.0), package android.accessibilityservice, class
 * AccessibilityService. See the report for the full list.
 *
 * Source: frameworks/base/core/java/android/accessibilityservice/AccessibilityService.java
 */
public abstract class AccessibilityService extends android.app.Service {

    /** The Intent action a service must declare in its <intent-filter>. */
    public static final String SERVICE_INTERFACE =
            "android.accessibilityservice.AccessibilityService";
    /** The <meta-data> name whose resource holds the AccessibilityServiceInfo. */
    public static final String SERVICE_META_DATA = "android.accessibilityservice";

    // ---- performGlobalAction ids -------------------------------------------
    public static final int GLOBAL_ACTION_BACK = 1;
    public static final int GLOBAL_ACTION_HOME = 2;
    public static final int GLOBAL_ACTION_RECENTS = 3;
    public static final int GLOBAL_ACTION_NOTIFICATIONS = 4;
    public static final int GLOBAL_ACTION_QUICK_SETTINGS = 5;
    public static final int GLOBAL_ACTION_POWER_DIALOG = 6;
    public static final int GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN = 7;
    public static final int GLOBAL_ACTION_LOCK_SCREEN = 8;
    public static final int GLOBAL_ACTION_TAKE_SCREENSHOT = 9;
    public static final int GLOBAL_ACTION_KEYCODE_HEADSETHOOK = 10;
    public static final int GLOBAL_ACTION_ACCESSIBILITY_BUTTON = 11;
    public static final int GLOBAL_ACTION_ACCESSIBILITY_BUTTON_CHOOSER = 12;
    public static final int GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT = 13;
    public static final int GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS = 14;
    public static final int GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE = 15;
    public static final int GLOBAL_ACTION_DPAD_UP = 16;
    public static final int GLOBAL_ACTION_DPAD_DOWN = 17;
    public static final int GLOBAL_ACTION_DPAD_LEFT = 18;
    public static final int GLOBAL_ACTION_DPAD_RIGHT = 19;
    public static final int GLOBAL_ACTION_DPAD_CENTER = 20;
    public static final int GLOBAL_ACTION_MENU = 21;
    public static final int GLOBAL_ACTION_MEDIA_PLAY_PAUSE = 22;

    // ---- onGesture ids ------------------------------------------------------
    public static final int GESTURE_UNKNOWN = 0;
    public static final int GESTURE_SWIPE_UP = 1;
    public static final int GESTURE_SWIPE_DOWN = 2;
    public static final int GESTURE_SWIPE_LEFT = 3;
    public static final int GESTURE_SWIPE_RIGHT = 4;
    public static final int GESTURE_SWIPE_LEFT_AND_RIGHT = 5;
    public static final int GESTURE_SWIPE_RIGHT_AND_LEFT = 6;
    public static final int GESTURE_SWIPE_UP_AND_DOWN = 7;
    public static final int GESTURE_SWIPE_DOWN_AND_UP = 8;
    public static final int GESTURE_SWIPE_LEFT_AND_UP = 9;
    public static final int GESTURE_SWIPE_LEFT_AND_DOWN = 10;
    public static final int GESTURE_SWIPE_RIGHT_AND_UP = 11;
    public static final int GESTURE_SWIPE_RIGHT_AND_DOWN = 12;
    public static final int GESTURE_SWIPE_UP_AND_LEFT = 13;
    public static final int GESTURE_SWIPE_UP_AND_RIGHT = 14;
    public static final int GESTURE_SWIPE_DOWN_AND_LEFT = 15;
    public static final int GESTURE_SWIPE_DOWN_AND_RIGHT = 16;
    public static final int GESTURE_DOUBLE_TAP = 17;
    public static final int GESTURE_DOUBLE_TAP_AND_HOLD = 18;
    public static final int GESTURE_2_FINGER_SINGLE_TAP = 19;
    public static final int GESTURE_2_FINGER_DOUBLE_TAP = 20;
    public static final int GESTURE_2_FINGER_TRIPLE_TAP = 21;
    public static final int GESTURE_3_FINGER_SINGLE_TAP = 22;
    public static final int GESTURE_3_FINGER_DOUBLE_TAP = 23;
    public static final int GESTURE_3_FINGER_TRIPLE_TAP = 24;
    public static final int GESTURE_2_FINGER_SWIPE_UP = 25;
    public static final int GESTURE_2_FINGER_SWIPE_DOWN = 26;
    public static final int GESTURE_2_FINGER_SWIPE_LEFT = 27;
    public static final int GESTURE_2_FINGER_SWIPE_RIGHT = 28;
    public static final int GESTURE_3_FINGER_SWIPE_UP = 29;
    public static final int GESTURE_3_FINGER_SWIPE_DOWN = 30;
    public static final int GESTURE_3_FINGER_SWIPE_LEFT = 31;
    public static final int GESTURE_3_FINGER_SWIPE_RIGHT = 32;
    public static final int GESTURE_4_FINGER_SWIPE_UP = 33;
    public static final int GESTURE_4_FINGER_SWIPE_DOWN = 34;
    public static final int GESTURE_4_FINGER_SWIPE_LEFT = 35;
    public static final int GESTURE_4_FINGER_SWIPE_RIGHT = 36;
    public static final int GESTURE_4_FINGER_SINGLE_TAP = 37;
    public static final int GESTURE_4_FINGER_DOUBLE_TAP = 38;
    public static final int GESTURE_4_FINGER_TRIPLE_TAP = 39;
    public static final int GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD = 40;
    public static final int GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD = 41;
    public static final int GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD = 42;
    public static final int GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD = 43;
    public static final int GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD = 44;
    public static final int GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD = 45;

    // ---- takeScreenshot failure codes --------------------------------------
    public static final int ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR = 1;
    public static final int ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS = 2;
    public static final int ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT = 3;
    public static final int ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY = 4;
    public static final int ERROR_TAKE_SCREENSHOT_INVALID_WINDOW = 5;
    public static final int ERROR_TAKE_SCREENSHOT_SECURE_WINDOW = 6;

    // ---- attachAccessibilityOverlay* result codes (API 35) ------------------
    public static final int OVERLAY_RESULT_SUCCESS = 0;
    public static final int OVERLAY_RESULT_INTERNAL_ERROR = 1;
    public static final int OVERLAY_RESULT_INVALID = 2;

    // ---- SoftKeyboardController show modes ---------------------------------
    public static final int SHOW_MODE_AUTO = 0;
    public static final int SHOW_MODE_HIDDEN = 1;
    public static final int SHOW_MODE_IGNORE_HARD_KEYBOARD = 2;

    // Lazily-created controllers. AOSP creates each on first request and hands
    // back the SAME instance thereafter -- a service that registers a callback
    // in onServiceConnected and unregisters it in onDestroy must get the same
    // object both times, or the unregister silently does nothing.
    private AccessibilityServiceInfo mInfo = new AccessibilityServiceInfo();
    private AccessibilityButtonController mButtonController;
    private final SparseArray<AccessibilityButtonController> mButtonControllers =
            new SparseArray<AccessibilityButtonController>();
    private FingerprintGestureController mFingerprintController;
    private MagnificationController mMagnificationController;
    private SoftKeyboardController mSoftKeyboardController;
    private final SparseArray<TouchInteractionController> mTouchControllers =
            new SparseArray<TouchInteractionController>();
    private InputMethod mInputMethod;
    private boolean mCacheEnabled = true;

    public AccessibilityService() { }

    // ---- the two callbacks every service must implement ---------------------
    public abstract void onAccessibilityEvent(AccessibilityEvent event);

    public abstract void onInterrupt();

    protected void onServiceConnected() { }

    /**
     * AOSP declares this final. Returning null matches android.app.Service's own
     * default (Service.onBind returns null in this tree), so nothing that the
     * superclass would have assigned is lost.
     */
    @Override public final IBinder onBind(Intent intent) { return null; }

    // ---- global actions -----------------------------------------------------
    /** No navigation bar, no recents, no shade: nothing to act on. AOSP also
     *  returns false when the action could not be performed. */
    public final boolean performGlobalAction(int action) { return false; }

    /**
     * AOSP: `public final void disableSelf()`. A boolean return is a different
     * method descriptor, so an app compiled against the SDK could never link to
     * a boolean variant.
     */
    public final void disableSelf() { }

    // ---- window / node access ----------------------------------------------
    public AccessibilityNodeInfo getRootInActiveWindow() { return null; }

    /** API 33 per-display overload. No display hosts a bound service. */
    public AccessibilityNodeInfo getRootInActiveWindow(int displayId) { return null; }

    /** AOSP returns an empty list when the service did not request
     *  FLAG_RETRIEVE_INTERACTIVE_WINDOWS or is not connected. */
    public List<AccessibilityWindowInfo> getWindows() {
        return Collections.emptyList();
    }

    /** API 30. Keyed by display id; empty because no display reports windows to
     *  an unbound service. */
    public final SparseArray<List<AccessibilityWindowInfo>> getWindowsOnAllDisplays() {
        return new SparseArray<List<AccessibilityWindowInfo>>();
    }

    /** AOSP returns null when no node holds the requested focus. */
    public AccessibilityNodeInfo findFocus(int focus) { return null; }

    /** API 31. AOSP returns the platform's global actions; with no system
     *  navigation surface there are none, and an empty list is the same shape
     *  an app iterates. */
    public final List<AccessibilityNodeInfo.AccessibilityAction> getSystemActions() {
        return new ArrayList<AccessibilityNodeInfo.AccessibilityAction>();
    }

    /** API 31 notification that the system action list changed. Empty in AOSP
     *  too; a service overrides it. */
    public void onSystemActionsChanged() { }

    // ---- the node cache (API 31) -------------------------------------------
    // AOSP keeps a client-side AccessibilityCache. We retrieve no nodes, so the
    // cache is always empty -- but the ENABLE flag is real state, because
    // setCacheEnabled/isCacheEnabled is a round-trip an app can observe.
    public boolean setCacheEnabled(boolean enabled) {
        mCacheEnabled = enabled;
        return true;
    }

    public boolean isCacheEnabled() { return mCacheEnabled; }

    /** AOSP returns false when there was nothing cached to clear. */
    public boolean clearCache() { return false; }

    public boolean clearCachedSubtree(AccessibilityNodeInfo node) { return false; }

    public boolean isNodeInCache(AccessibilityNodeInfo node) { return false; }

    // ---- service info -------------------------------------------------------
    public final AccessibilityServiceInfo getServiceInfo() { return mInfo; }

    /**
     * AOSP stores the info and pushes it to the system. We keep it so the
     * getter round-trips, which is the only half an unbound service can observe.
     * ⚠ This ASSIGNS state deliberately -- an earlier shape of this file
     * discarded the argument, which made setServiceInfo/getServiceInfo
     * disagree.
     */
    public final void setServiceInfo(AccessibilityServiceInfo info) {
        if (info != null) mInfo = info;
    }

    // ---- input callbacks ----------------------------------------------------
    /** @deprecated superseded by onGesture(AccessibilityGestureEvent) at API 30. */
    @Deprecated
    protected boolean onGesture(int gestureId) { return false; }

    public boolean onGesture(AccessibilityGestureEvent gestureEvent) {
        // AOSP's default routes to the deprecated int overload so a service that
        // only overrode the old one keeps working. Mirror that, or overriding
        // the deprecated method would silently stop having any effect.
        return onGesture(gestureEvent == null ? GESTURE_UNKNOWN
                : gestureEvent.getGestureId());
    }

    protected boolean onKeyEvent(KeyEvent event) { return false; }

    /** API 34. Empty in AOSP; a service overrides it. */
    public void onMotionEvent(MotionEvent event) { }

    // ---- gesture injection --------------------------------------------------
    /**
     * AOSP dispatches the gesture and later calls the callback. Mac.apk cannot
     * inject on behalf of a service, and AOSP itself returns false (without
     * ever calling the callback) when the gesture could not be dispatched, so
     * a service's failure path runs rather than a callback that never arrives.
     */
    public final boolean dispatchGesture(GestureDescription gesture,
            GestureResultCallback callback, Handler handler) {
        return false;
    }

    // ---- controllers --------------------------------------------------------
    public final AccessibilityButtonController getAccessibilityButtonController() {
        synchronized (mButtonControllers) {
            if (mButtonController == null) {
                mButtonController = new AccessibilityButtonController();
            }
            return mButtonController;
        }
    }

    public final AccessibilityButtonController getAccessibilityButtonController(int displayId) {
        synchronized (mButtonControllers) {
            AccessibilityButtonController c = mButtonControllers.get(displayId);
            if (c == null) {
                c = new AccessibilityButtonController();
                mButtonControllers.put(displayId, c);
            }
            return c;
        }
    }

    public final FingerprintGestureController getFingerprintGestureController() {
        synchronized (mButtonControllers) {
            if (mFingerprintController == null) {
                mFingerprintController = new FingerprintGestureController();
            }
            return mFingerprintController;
        }
    }

    public final MagnificationController getMagnificationController() {
        synchronized (mButtonControllers) {
            if (mMagnificationController == null) {
                mMagnificationController = new MagnificationController();
            }
            return mMagnificationController;
        }
    }

    public final SoftKeyboardController getSoftKeyboardController() {
        synchronized (mButtonControllers) {
            if (mSoftKeyboardController == null) {
                mSoftKeyboardController = new SoftKeyboardController();
            }
            return mSoftKeyboardController;
        }
    }

    public final TouchInteractionController getTouchInteractionController(int displayId) {
        synchronized (mTouchControllers) {
            TouchInteractionController c = mTouchControllers.get(displayId);
            if (c == null) {
                c = new TouchInteractionController(displayId);
                mTouchControllers.put(displayId, c);
            }
            return c;
        }
    }

    /** API 35. AOSP hands back the platform's implementation; ours reports every
     *  connect attempt as "not found", which is an outcome AOSP produces for a
     *  device that is not a Braille display. */
    public final BrailleDisplayController getBrailleDisplayController() {
        return new DisconnectedBrailleDisplayController();
    }

    /** API 33. Null until the service returns one from onCreateInputMethod and
     *  the platform starts input; that never happens here, and AOSP documents
     *  the method @Nullable for exactly this state. */
    public final InputMethod getInputMethod() { return mInputMethod; }

    /** API 33. AOSP's default builds the plain InputMethod; a service overrides
     *  this to supply its own subclass. */
    public InputMethod onCreateInputMethod() { return new InputMethod(this); }

    // ---- appearance / passthrough knobs -------------------------------------
    /** API 30. There is no accessibility focus indicator to restyle. */
    public void setAccessibilityFocusAppearance(int strokeWidth, int color) { }

    /** API 33. AOSP scales window animations globally; a guest cannot be
     *  allowed to restyle the host, and 0f/1f would be a lie either way, so
     *  this is a no-op with nothing to assign. */
    public void setAnimationScale(float scale) { }

    public void setGestureDetectionPassthroughRegion(int displayId, Region region) { }

    public void setTouchExplorationPassthroughRegion(int displayId, Region region) { }

    // ---- screenshots --------------------------------------------------------
    /**
     * AOSP grabs the display and hands back a HardwareBuffer. Mac.apk will not
     * hand a guest a screenshot of the host display, and there is no bound
     * service to grant the capability to, so we report the failure AOSP reports
     * when the service lacks it: ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS.
     * The callback IS invoked (on the caller's executor), because a callback
     * that never fires hangs a service that awaits it.
     */
    public void takeScreenshot(int displayId, Executor executor,
            TakeScreenshotCallback callback) {
        failScreenshot(executor, callback, ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS);
    }

    /** API 34 window variant; same reasoning as takeScreenshot. */
    public void takeScreenshotOfWindow(int accessibilityWindowId, Executor executor,
            TakeScreenshotCallback callback) {
        failScreenshot(executor, callback, ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS);
    }

    private static void failScreenshot(Executor executor,
            final TakeScreenshotCallback callback, final int error) {
        if (callback == null) return;
        Runnable r = new Runnable() {
            @Override public void run() { callback.onFailure(error); }
        };
        if (executor != null) executor.execute(r); else r.run();
    }

    // ---- accessibility overlays (API 34/35) ---------------------------------
    /** No SurfaceControl hierarchy is exposed to a guest service, so there is
     *  nothing to reparent an overlay under. */
    public void attachAccessibilityOverlayToDisplay(int displayId, SurfaceControl sc) { }

    public final void attachAccessibilityOverlayToDisplay(int displayId, SurfaceControl sc,
            Executor executor, IntConsumer callback) {
        reportOverlay(executor, callback);
    }

    public void attachAccessibilityOverlayToWindow(int accessibilityWindowId,
            SurfaceControl sc) { }

    public final void attachAccessibilityOverlayToWindow(int accessibilityWindowId,
            SurfaceControl sc, Executor executor, IntConsumer callback) {
        reportOverlay(executor, callback);
    }

    private static void reportOverlay(Executor executor, final IntConsumer callback) {
        if (callback == null) return;
        Runnable r = new Runnable() {
            @Override public void run() { callback.accept(OVERLAY_RESULT_INTERNAL_ERROR); }
        };
        if (executor != null) executor.execute(r); else r.run();
    }

    // =========================================================================
    // Nested types
    // =========================================================================

    /** Empty bodies mirror AOSP: a service overrides only the outcome it cares
     *  about. Neither is ever called here (dispatchGesture returns false). */
    public abstract static class GestureResultCallback {
        public GestureResultCallback() { }

        public void onCompleted(GestureDescription gestureDescription) { }

        public void onCancelled(GestureDescription gestureDescription) { }
    }

    /**
     * Magnification is a system-wide effect; Mac.apk hosts one app window and
     * does not magnify it on a guest's say-so. Every mutator therefore returns
     * false -- the same answer AOSP gives when the service is not connected or
     * lacks CAPABILITY_CAN_CONTROL_MAGNIFICATION -- rather than reporting a
     * success that did not happen.
     */
    public static final class MagnificationController {

        private final List<OnMagnificationChangedListener> mListeners =
                new ArrayList<OnMagnificationChangedListener>();

        MagnificationController() { }

        public void addListener(OnMagnificationChangedListener listener) {
            addListener(listener, null);
        }

        public void addListener(OnMagnificationChangedListener listener, Handler handler) {
            if (listener == null) throw new IllegalArgumentException("listener cannot be null");
            synchronized (mListeners) {
                if (!mListeners.contains(listener)) mListeners.add(listener);
            }
        }

        public boolean removeListener(OnMagnificationChangedListener listener) {
            synchronized (mListeners) { return mListeners.remove(listener); }
        }

        /** @deprecated use getMagnificationConfig(). Unmagnified is scale 1. */
        @Deprecated public float getScale() { return 1.0f; }

        /** @deprecated AOSP returns the magnification centre; unmagnified that
         *  is the centre of the (empty) magnification region, i.e. 0. */
        @Deprecated public float getCenterX() { return 0.0f; }

        @Deprecated public float getCenterY() { return 0.0f; }

        /** @deprecated use getCurrentMagnificationRegion(). */
        @Deprecated public Region getMagnificationRegion() { return new Region(); }

        /** API 33. Empty region: nothing is magnified. */
        public Region getCurrentMagnificationRegion() { return new Region(); }

        /** API 33. AOSP returns null when magnification is not active. */
        public MagnificationConfig getMagnificationConfig() { return null; }

        public boolean setMagnificationConfig(MagnificationConfig config, boolean animate) {
            return false;
        }

        @Deprecated public boolean setScale(float scale, boolean animate) { return false; }

        @Deprecated public boolean setCenter(float centerX, float centerY, boolean animate) {
            return false;
        }

        public boolean reset(boolean animate) { return false; }

        /** API 33. Nothing is magnified, so there is nothing to reset. */
        public boolean resetCurrentMagnification(boolean animate) { return false; }

        public interface OnMagnificationChangedListener {
            /** @deprecated superseded by the MagnificationConfig overload. */
            @Deprecated
            void onMagnificationChanged(MagnificationController controller, Region region,
                    float scale, float centerX, float centerY);

            /** AOSP gives this a default that forwards to the deprecated form,
             *  so a listener written against either one still receives the
             *  callback. Mirror the forwarding exactly. */
            default void onMagnificationChanged(MagnificationController controller,
                    Region region, MagnificationConfig config) {
                onMagnificationChanged(controller, region,
                        config == null ? 1.0f : config.getScale(),
                        config == null ? 0.0f : config.getCenterX(),
                        config == null ? 0.0f : config.getCenterY());
            }
        }
    }

    /**
     * API 30 screenshot payload. Only the platform constructs one, and Mac.apk
     * never succeeds at a screenshot, so no instance is produced -- but the type
     * is named in TakeScreenshotCallback.onSuccess and must resolve.
     */
    public static final class ScreenshotResult {
        private final HardwareBuffer mHardwareBuffer;
        private final ColorSpace mColorSpace;
        private final long mTimestamp;

        ScreenshotResult(HardwareBuffer hardwareBuffer, ColorSpace colorSpace,
                long timestamp) {
            mHardwareBuffer = hardwareBuffer;
            mColorSpace = colorSpace;
            mTimestamp = timestamp;
        }

        public HardwareBuffer getHardwareBuffer() { return mHardwareBuffer; }

        public ColorSpace getColorSpace() { return mColorSpace; }

        public long getTimestamp() { return mTimestamp; }
    }

    public interface TakeScreenshotCallback {
        void onSuccess(ScreenshotResult screenshot);

        void onFailure(int errorCode);
    }

    /**
     * Controls whether the soft keyboard is shown while a service is active.
     * Mac.apk shows no soft keyboard at all (the host has a hardware keyboard),
     * so SHOW_MODE_AUTO is the standing answer and setShowMode declines --
     * AOSP also returns false when the service is not connected.
     */
    public static final class SoftKeyboardController {

        public static final int ENABLE_IME_SUCCESS = 0;
        public static final int ENABLE_IME_FAIL_BY_ADMIN = 1;
        public static final int ENABLE_IME_FAIL_UNKNOWN = 2;

        private final List<OnShowModeChangedListener> mListeners =
                new ArrayList<OnShowModeChangedListener>();

        SoftKeyboardController() { }

        public void addOnShowModeChangedListener(OnShowModeChangedListener listener) {
            addOnShowModeChangedListener(listener, null);
        }

        public void addOnShowModeChangedListener(OnShowModeChangedListener listener,
                Handler handler) {
            if (listener == null) throw new IllegalArgumentException("listener cannot be null");
            synchronized (mListeners) {
                if (!mListeners.contains(listener)) mListeners.add(listener);
            }
        }

        public boolean removeOnShowModeChangedListener(OnShowModeChangedListener listener) {
            synchronized (mListeners) { return mListeners.remove(listener); }
        }

        public int getShowMode() { return SHOW_MODE_AUTO; }

        public boolean setShowMode(int showMode) { return false; }

        /** API 30. Enabling an IME on the user's behalf is a system-settings
         *  change; report the unknown failure AOSP uses rather than claiming a
         *  success. AOSP declares `throws SecurityException`. */
        public int setInputMethodEnabled(String imeId, boolean enabled)
                throws SecurityException {
            return ENABLE_IME_FAIL_UNKNOWN;
        }

        /** API 30. There is no IME to switch to. */
        public boolean switchToInputMethod(String imeId) { return false; }

        public interface OnShowModeChangedListener {
            void onShowModeChanged(SoftKeyboardController controller, int showMode);
        }
    }

    /**
     * The BrailleDisplayController handed out by getBrailleDisplayController().
     * Every connect reports FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND on the
     * caller's executor, which is the outcome AOSP produces when the named
     * device is not a Braille display -- so the service's own error path runs.
     */
    private static final class DisconnectedBrailleDisplayController
            implements BrailleDisplayController {

        @Override public void connect(android.bluetooth.BluetoothDevice device,
                BrailleDisplayCallback callback) {
            fail(null, callback);
        }

        @Override public void connect(android.bluetooth.BluetoothDevice device,
                Executor executor, BrailleDisplayCallback callback) {
            fail(executor, callback);
        }

        @Override public void connect(android.hardware.usb.UsbDevice device,
                BrailleDisplayCallback callback) {
            fail(null, callback);
        }

        @Override public void connect(android.hardware.usb.UsbDevice device,
                Executor executor, BrailleDisplayCallback callback) {
            fail(executor, callback);
        }

        @Override public boolean isConnected() { return false; }

        @Override public void write(byte[] buffer) throws java.io.IOException {
            // AOSP throws IOException when no display is connected. Silently
            // succeeding would tell the service its braille output landed.
            throw new java.io.IOException("No Braille display connected");
        }

        @Override public void disconnect() { }

        private static void fail(Executor executor, final BrailleDisplayCallback callback) {
            if (callback == null) return;
            Runnable r = new Runnable() {
                @Override public void run() {
                    callback.onConnectionFailed(
                            BrailleDisplayCallback.FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND);
                }
            };
            if (executor != null) executor.execute(r); else r.run();
        }
    }
}
