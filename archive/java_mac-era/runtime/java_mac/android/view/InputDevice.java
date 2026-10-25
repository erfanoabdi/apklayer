// android.view.InputDevice -- describes an input device + the canonical
// SOURCE_* class bitmasks. Unity (and any input-aware app) compares an
// event's getSource() against these to decide whether it is a touch, mouse,
// keyboard, etc. The values are load-bearing (Unity routes by them), so they
// must match AOSP exactly
// (frameworks/base/core/java/android/view/InputDevice.java).
//
// ⚠ THE STATICS ARE DELEGATIONS, NOT A SECOND DEVICE TABLE. AOSP defines both
// of them as one-liners onto InputManager (InputDevice.java:482-492), and this
// file must keep it that way. Until 2026-08-09 it did not: getDeviceIds() had a
// hardcoded `return new int[0]` and getDevice(id) manufactured a device for ANY
// id, while android.hardware.input.InputManager -- carrying a comment
// explaining this exact self-contradiction -- held the correct answers. Apps
// call android.view.InputDevice, so the correct half was unreachable and every
// InputDevice capability question Mac.apk answered was 0 against the emulator's
// 1. The device SET, and the reasoning for what is and is not in it, lives in
// android.hardware.input.InputManager. Read it before changing anything here.
package android.view;

public final class InputDevice implements android.os.Parcelable {
    public static final int SOURCE_CLASS_MASK      = 0x000000ff;
    public static final int SOURCE_CLASS_NONE      = 0x00000000;
    public static final int SOURCE_CLASS_BUTTON    = 0x00000001;
    public static final int SOURCE_CLASS_POINTER   = 0x00000002;
    public static final int SOURCE_CLASS_TRACKBALL = 0x00000004;
    public static final int SOURCE_CLASS_POSITION  = 0x00000008;
    public static final int SOURCE_CLASS_JOYSTICK  = 0x00000010;

    public static final int SOURCE_UNKNOWN     = 0x00000000;
    public static final int SOURCE_KEYBOARD    = 0x00000101;
    public static final int SOURCE_DPAD        = 0x00000201;
    public static final int SOURCE_GAMEPAD     = 0x00000401;
    public static final int SOURCE_TOUCHSCREEN = 0x00001002;
    public static final int SOURCE_MOUSE       = 0x00002002;
    public static final int SOURCE_STYLUS      = 0x00004002;
    public static final int SOURCE_TRACKBALL   = 0x00010004;
    public static final int SOURCE_MOUSE_RELATIVE = 0x00020004;
    public static final int SOURCE_TOUCHPAD    = 0x00100008;
    public static final int SOURCE_TOUCH_NAVIGATION = 0x00200000;
    // AOSP InputDevice.SOURCE_ROTARY_ENCODER = 0x00400000 | SOURCE_CLASS_NONE.
    public static final int SOURCE_ROTARY_ENCODER = 0x00400000;
    public static final int SOURCE_JOYSTICK    = 0x01000010;
    public static final int SOURCE_HDMI        = 0x02000001;
    public static final int SOURCE_ANY         = 0xffffff00;

    public static final int KEYBOARD_TYPE_NONE          = 0;
    public static final int KEYBOARD_TYPE_NON_ALPHABETIC = 1;
    public static final int KEYBOARD_TYPE_ALPHABETIC    = 2;

    private final int mId;
    private final int mGeneration;
    private final int mControllerNumber;
    private final String mName;
    private final int mVendorId;
    private final int mProductId;
    private final String mDescriptor;
    private final boolean mIsExternal;
    private final int mSources;
    private final int mKeyboardType;
    private final KeyCharacterMap mKeyCharacterMap;
    private final boolean mHasMicrophone;
    private final boolean mHasButtonUnderPad;
    private final java.util.List<MotionRange> mMotionRanges = new java.util.ArrayList<MotionRange>();
    private android.os.Vibrator mVibrator;
    private android.os.VibratorManager mVibratorManager;
    private android.hardware.lights.LightsManager mLightsManager;

    /** AOSP declares this constructor PRIVATE and calls it from native code
     *  (@UnsupportedAppUsage, InputDevice.java:428-431) as the input reader
     *  discovers devices. Mac.apk has no native input registry: the registry is
     *  android.hardware.input.InputManager, which is in a different package, so
     *  this has to be reachable from there. Widening it is safe in the way that
     *  matters -- the constructor is absent from the Android SDK stubs, so no
     *  app's DEX can reference it; only our own framework can call it.
     *
     *  `hasVibrator` is accepted for signature parity with AOSP and is not
     *  stored: getVibrator()/getVibratorManager() below are already the
     *  null-object AOSP hands back for a device without one. */
    public InputDevice(int id, int generation, int controllerNumber, String name, int vendorId,
            int productId, String descriptor, boolean isExternal, int sources, int keyboardType,
            KeyCharacterMap keyCharacterMap, boolean hasVibrator, boolean hasMicrophone,
            boolean hasButtonUnderPad) {
        mId = id;
        mGeneration = generation;
        mControllerNumber = controllerNumber;
        mName = name;
        mVendorId = vendorId;
        mProductId = productId;
        mDescriptor = descriptor;
        mIsExternal = isExternal;
        mSources = sources;
        mKeyboardType = keyboardType;
        mKeyCharacterMap = keyCharacterMap;
        mHasMicrophone = hasMicrophone;
        mHasButtonUnderPad = hasButtonUnderPad;
    }

    /** Not AOSP API (AOSP has no public no-arg constructor). Kept because it
     *  has been in Mac.apk's framework jar since the Crossy bringup and
     *  removing a public constructor from a shipped jar is a needless
     *  compatibility risk; it now yields the same virtual-keyboard identity the
     *  registry publishes at id -1, so it can no longer contradict it. */
    public InputDevice() {
        this(KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, "Virtual", 0, 0,
             "macapk:virtual-keyboard", false, SOURCE_KEYBOARD | SOURCE_DPAD,
             KEYBOARD_TYPE_ALPHABETIC, KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD),
             false, false, false);
    }

    /** AOSP InputDevice.java:482-484, verbatim behaviour: "The input device or
     *  null if not found." Returning a manufactured device for an unknown id
     *  (what this used to do) breaks the AOSP startup-scan idiom, which probes
     *  ids it has not validated and stops at the first null. */
    public static InputDevice getDevice(int id) {
        return android.hardware.input.InputManager.getInstance().getInputDevice(id);
    }

    /** AOSP InputDevice.java:490-492. */
    public static int[] getDeviceIds() {
        return android.hardware.input.InputManager.getInstance().getInputDeviceIds();
    }

    public int getId() { return mId; }

    /** AOSP: bumped whenever the device's configuration changes. Ours never
     *  does within a run. */
    public int getGeneration() { return mGeneration; }

    public String getName() { return mName; }
    public int getSources() { return mSources; }

    /** AOSP InputDevice.java:669-671: `(mSources & source) == source`. Written
     *  exactly that way on purpose -- the `!= 0` spelling looks equivalent but
     *  answers false for SOURCE_UNKNOWN (0), which AOSP answers true for, and
     *  answers true for a partial match on a composite mask like SOURCE_MOUSE
     *  (0x2002) when only SOURCE_CLASS_POINTER (0x2) is present. */
    public boolean supportsSource(int source) { return (mSources & source) == source; }

    public int getKeyboardType() { return mKeyboardType; }

    /** AOSP InputDevice.java:619-621: `return mId < 0;`. Negative ids are the
     *  platform's own synthetic devices, which is why the virtual keyboard
     *  keeps id -1 and the touchscreen must not. */
    public boolean isVirtual() { return mId < 0; }

    public KeyCharacterMap getKeyCharacterMap() { return mKeyCharacterMap; }

    /** AOSP: "a unique, persistent identifier ... consistent across reboots".
     *  The real one is a hash of the device's vendor/product/name; ours is a
     *  fixed string per device, which is stable for the same reason. */
    public String getDescriptor() { return mDescriptor; }

    /** AOSP: "A value of 0 will be assigned where a vendor id is not
     *  available." Same for the product id. Our devices are synthetic, so 0 is
     *  the correct answer rather than an invented USB id. */
    public int getVendorId() { return mVendorId; }
    public int getProductId() { return mProductId; }

    /** AOSP: "The controller number for a given input device ... 0 if it is
     *  not a game controller or is not associated with a player number." */
    public int getControllerNumber() { return mControllerNumber; }

    /** AOSP: true for a device plugged in / paired by the user rather than
     *  built into the device. Both of ours are built in. */
    public boolean isExternal() { return mIsExternal; }

    /** AOSP @hide: whether the device has a microphone / a button under a
     *  touchpad. Neither is true of anything we advertise. */
    public boolean hasMicrophone() { return mHasMicrophone; }
    public boolean hasButtonUnderPad() { return mHasButtonUnderPad; }

    /** AOSP InputDevice.java:697-699 delegates to
     *  InputManager.getInstance().deviceHasKeys(mId, keys); the reason our
     *  answer is all-false is documented at that method. */
    public boolean[] hasKeys(int... keys) {
        return android.hardware.input.InputManager.getInstance().deviceHasKeys(mId, keys);
    }

    /** AOSP: the axis calibration for this device, or null when the device
     *  does not support that axis (callers null-check, per the AOSP javadoc
     *  and the standard getCenteredAxis() idiom). Which axes each device
     *  carries, and why the touchscreen carries only three of the nine a real
     *  one would, is argued in android.hardware.input.InputManager. */
    public MotionRange getMotionRange(int axis) {
        final int numRanges = mMotionRanges.size();
        for (int i = 0; i < numRanges; i++) {
            final MotionRange range = mMotionRanges.get(i);
            if (range.getAxis() == axis) {
                return range;
            }
        }
        return null;
    }

    public MotionRange getMotionRange(int axis, int source) {
        final int numRanges = mMotionRanges.size();
        for (int i = 0; i < numRanges; i++) {
            final MotionRange range = mMotionRanges.get(i);
            if (range.getAxis() == axis && range.getSource() == source) {
                return range;
            }
        }
        return null;
    }

    /** AOSP returns the live list, not a copy. */
    public java.util.List<MotionRange> getMotionRanges() { return mMotionRanges; }

    // 2026-08-23 conformance sweep: AOSP additionally carries mDeviceBus,
    // mKeyboardLanguageTag, mKeyboardLayoutType, mHasVibrator, mHasSensor,
    // mHasBattery, mHostUsiVersion, mAssociatedDisplayId, mEnabled and a
    // ViewBehavior.mShouldSmoothScroll flag (InputDevice.java L540-575,
    // 1604-1642), none of which this class stores -- mHasVibrator is
    // accepted by the constructor for signature parity but deliberately
    // discarded (see the constructor's own comment). This writes every
    // field this class DOES store, leading with keyCharacterMap the way
    // AOSP's own wire format does, and includes the motionRanges list the
    // same way AOSP loops it (one MotionRange = 5 ints/floats each).
    // mVibrator/mVibratorManager/mLightsManager are lazily-created caches,
    // not identity, and are correctly excluded (AOSP does not parcel its
    // equivalents either -- the receiving side re-derives them on demand).
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeInt(mKeyCharacterMap != null ? 1 : 0);
        if (mKeyCharacterMap != null) mKeyCharacterMap.writeToParcel(out, flags);
        out.writeInt(mId);
        out.writeInt(mGeneration);
        out.writeInt(mControllerNumber);
        out.writeString(mName);
        out.writeInt(mVendorId);
        out.writeInt(mProductId);
        out.writeString(mDescriptor);
        out.writeInt(mIsExternal ? 1 : 0);
        out.writeInt(mSources);
        out.writeInt(mKeyboardType);
        out.writeInt(mHasMicrophone ? 1 : 0);
        out.writeInt(mHasButtonUnderPad ? 1 : 0);
        out.writeInt(mMotionRanges.size());
        for (MotionRange range : mMotionRanges) {
            out.writeInt(range.mAxis);
            out.writeInt(range.mSource);
            out.writeFloat(range.mMin);
            out.writeFloat(range.mMax);
            out.writeFloat(range.mFlat);
            out.writeFloat(range.mFuzz);
            out.writeFloat(range.mResolution);
        }
    }

    public static final android.os.Parcelable.Creator<InputDevice> CREATOR =
            new android.os.Parcelable.Creator<InputDevice>() {
        @Override public InputDevice createFromParcel(android.os.Parcel in) {
            KeyCharacterMap kcm = (in.readInt() != 0)
                    ? KeyCharacterMap.CREATOR.createFromParcel(in) : null;
            int id = in.readInt();
            int generation = in.readInt();
            int controllerNumber = in.readInt();
            String name = in.readString();
            int vendorId = in.readInt();
            int productId = in.readInt();
            String descriptor = in.readString();
            boolean isExternal = in.readInt() != 0;
            int sources = in.readInt();
            int keyboardType = in.readInt();
            boolean hasMicrophone = in.readInt() != 0;
            boolean hasButtonUnderPad = in.readInt() != 0;
            InputDevice device = new InputDevice(id, generation, controllerNumber, name,
                    vendorId, productId, descriptor, isExternal, sources, keyboardType, kcm,
                    false, hasMicrophone, hasButtonUnderPad);
            int numRanges = in.readInt();
            for (int i = 0; i < numRanges; i++) {
                device.addMotionRange(in.readInt(), in.readInt(), in.readFloat(), in.readFloat(),
                        in.readFloat(), in.readFloat(), in.readFloat());
            }
            return device;
        }
        @Override public InputDevice[] newArray(int size) { return new InputDevice[size]; }
    };

    /** AOSP addMotionRange is `@UnsupportedAppUsage public void`, called from
     *  native as the input reader reads a device's axes back. Ours is called by
     *  android.hardware.input.InputManager while it builds the registry, and by
     *  a future host controller bridge (GameController.framework) registering
     *  the real axes it discovers. */
    public void addMotionRange(int axis, int source, float min, float max,
                               float flat, float fuzz, float resolution) {
        mMotionRanges.add(new MotionRange(axis, source, min, max, flat, fuzz, resolution));
    }

    /** AOSP: "Even if the device does not have a vibrator, the result is never
     *  null"; upstream hands back NullVibrator in that case. android.os.Vibrator
     *  already reports hasVibrator() == false and no-ops every vibrate(), so a
     *  plain instance is exactly that null-object.
     *  @deprecated use {@link #getVibratorManager()} */
    @Deprecated
    public android.os.Vibrator getVibrator() {
        synchronized (mMotionRanges) {
            if (mVibrator == null) mVibrator = new android.os.Vibrator();
            return mVibrator;
        }
    }

    /** AOSP: "the result is never null ... use VibratorManager#getVibratorIds
     *  to determine whether any vibrator is present". Ours reports an empty id
     *  array, which is the AOSP-sanctioned way to say "no vibrators". */
    public android.os.VibratorManager getVibratorManager() {
        synchronized (mMotionRanges) {
            if (mVibratorManager == null) mVibratorManager = new android.os.VibratorManager();
            return mVibratorManager;
        }
    }

    /** AOSP InputDevice.java: lazily creates and caches a LightsManager
     *  scoped to this input device (keyboard backlight, controller
     *  player-id LEDs, etc). AOSP delegates to
     *  InputManager.getInstance().getInputDeviceLightsManager(mId); that
     *  method does not exist on our android.hardware.input.InputManager
     *  (out of this change's scope, and there is no peripheral-light
     *  hardware to register regardless -- see
     *  android.hardware.lights.LightsManager for the empty-getLights()
     *  design), so this constructs its own directly. Never null, matching
     *  AOSP's @NonNull contract. */
    public android.hardware.lights.LightsManager getLightsManager() {
        synchronized (mMotionRanges) {
            if (mLightsManager == null) {
                mLightsManager = new android.hardware.lights.LightsManager();
            }
            return mLightsManager;
        }
    }

    /** AOSP InputDevice.MotionRange -- the calibration of one MotionEvent axis
     *  on one source of one device: the value bounds plus the flat (dead-zone)
     *  and fuzz (noise) thresholds an app subtracts before acting on a reading.
     *  AOSP ref: frameworks/base/core/java/android/view/InputDevice.java.
     *  The constructor is private upstream too: only the framework builds
     *  these, from what the device actually reported. */
    public static final class MotionRange {
        private final int mAxis;
        private final int mSource;
        private final float mMin;
        private final float mMax;
        private final float mFlat;
        private final float mFuzz;
        private final float mResolution;

        private MotionRange(int axis, int source, float min, float max, float flat, float fuzz,
                float resolution) {
            mAxis = axis;
            mSource = source;
            mMin = min;
            mMax = max;
            mFlat = flat;
            mFuzz = fuzz;
            mResolution = resolution;
        }

        public int getAxis() { return mAxis; }
        public int getSource() { return mSource; }
        public boolean isFromSource(int source) { return (getSource() & source) == source; }
        public float getMin() { return mMin; }
        public float getMax() { return mMax; }
        public float getRange() { return mMax - mMin; }
        public float getFlat() { return mFlat; }
        public float getFuzz() { return mFuzz; }
        public float getResolution() { return mResolution; }
    }

    /** AOSP InputDevice.isEnabled(): whether the device is currently enabled
     *  for input. Every device Mac.apk synthesises (the touchscreen the Cocoa
     *  bridge feeds, the virtual keyboard) is live for the whole run, so this
     *  is true -- there is no disable path to report. */
    public boolean isEnabled() { return true; }

    /** AOSP InputDevice.getKeyCodeForKeyLocation(int locationKeyCode) (API 33):
     *  maps a US-layout key POSITION to the key code that position produces
     *  under the device's current layout. macOS delivers already-translated key
     *  codes through the Cocoa bridge and exposes no positional layout map to
     *  invert, so the identity mapping is the honest answer -- which is also
     *  exactly what AOSP returns for a device with no key-character-map
     *  override (KeyCharacterMap.getMappedKeyCode falls through to the input
     *  location itself), and what a US-layout device returns for every key. */
    public int getKeyCodeForKeyLocation(int locationKeyCode) { return locationKeyCode; }

    // ⚠ AOSP InputDevice.getSensorManager() (API 31) is NOT implemented, and
    // deliberately so. It must return a SensorManager scoped to THIS input
    // device -- for every device Mac.apk synthesises that means an EMPTY sensor
    // catalog (a touchscreen and a virtual keyboard have no built-in
    // accelerometer). The only SensorManager this tree can hand out is the
    // process-wide one, whose catalog is the host's synthesised motion sensors;
    // returning that would tell an app the keyboard has a gyroscope, which is
    // exactly the kind of comfortable lie that fails later.
    // Expressing the correct answer needs a one-line change in a file this lane
    // does not own: android/hardware/SensorManager.java:172 declares
    // `SensorManager()` package-private, so an InputDevice-scoped subclass
    // cannot be written from android.view. Once that constructor is
    // `protected`, the implementation is a four-method subclass overriding
    // getDefaultSensor(int), getDefaultSensor(int, boolean), getSensorList(int)
    // and getDynamicSensorList(int) to return null / an empty list.
    // RESOLVED 2026-08-24: android/hardware/SensorManager.java's constructor is
    // now `protected` (matching AOSP, where SensorManager is abstract with a
    // protected ctor), so the correct answer is expressible and is implemented
    // below. The reasoning above stands and is why this returns an EMPTY manager
    // rather than the process-wide one.
    private android.hardware.SensorManager mSensorManager;

    /** AOSP InputDevice.getSensorManager() (API 31): a SensorManager scoped to
     *  the sensors built into THIS input device. Every device Mac.apk
     *  synthesises -- a touchscreen, a virtual keyboard -- has none, so the
     *  catalog is empty. AOSP returns an empty-catalog manager for such a
     *  device too; this is the ordinary answer, not a degraded one. */
    public android.hardware.SensorManager getSensorManager() {
        if (mSensorManager == null) mSensorManager = new EmptySensorManager();
        return mSensorManager;
    }

    /** A SensorManager whose device has no built-in sensors. Overrides exactly
     *  the four lookups; everything else inherits the base behaviour, which
     *  operates over this empty catalog. */
    private static final class EmptySensorManager extends android.hardware.SensorManager {
        @Override public android.hardware.Sensor getDefaultSensor(int type) {
            return null;
        }
        @Override public android.hardware.Sensor getDefaultSensor(int type, boolean wakeUp) {
            return null;
        }
        @Override public java.util.List<android.hardware.Sensor> getSensorList(int type) {
            return java.util.Collections.emptyList();
        }
        @Override public java.util.List<android.hardware.Sensor> getDynamicSensorList(int type) {
            return java.util.Collections.emptyList();
        }
    }

    /** AOSP InputDevice.ViewBehavior (API 36): how a View should REACT to this
     *  device's motion axes -- notably whether a scroll from it should be
     *  smoothed. Reached through InputDevice.getViewBehavior(). */
    public static final class ViewBehavior {
        /** @hide -- only InputDevice constructs one. */
        ViewBehavior() {}
        /** AOSP ViewBehavior.shouldSmoothScroll(int axis, int source): should a
         *  View animate scrolls driven by this axis? AOSP answers true only for
         *  a device whose driver reports high-resolution scroll (a precision
         *  mouse wheel or trackpad). Mac.apk delivers scroll through the Cocoa
         *  bridge as discrete MotionEvent axis values with no resolution
         *  metadata, so the answer is false -- the same as for an ordinary
         *  detented mouse wheel on a device. */
        public boolean shouldSmoothScroll(int axis, int source) { return false; }
    }

    /** AOSP InputDevice.getViewBehavior() (API 36). */
    private ViewBehavior mViewBehavior;
    public ViewBehavior getViewBehavior() {
        if (mViewBehavior == null) mViewBehavior = new ViewBehavior();
        return mViewBehavior;
    }
}
