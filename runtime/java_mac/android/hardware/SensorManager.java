// android.hardware.SensorManager -- mirrors AOSP's public surface.
//
// Mac.apk has no sensor hardware. Two halves of the contract:
//
//  (1) The static math utilities (getRotationMatrix, getOrientation,
//      remapCoordinateSystem, getInclination, getAltitude,
//      getAngleChange, getRotationMatrixFromVector,
//      getQuaternionFromVector) are PURE FUNCTIONS -- they compute on
//      caller-supplied float arrays, no hardware involved. These are
//      copied verbatim from AOSP frameworks/base SensorManager.java so
//      tilt/AR games that feed us their own data get the correct
//      result. (URL in repo CLAUDE.md reading list.)
//
//  (2) The instance side hands back real Sensor objects and actually
//      delivers events. With no hardware we synthesize a quiescent
//      "device lying flat, not moving" stream: the accelerometer (and
//      gravity) report (0, 0, g) [STALE since v1.0.1042: they report the
//      1g UPRIGHT pose, turned into device axes by the display rotation --
//      see POSE_ACCEL_* and macapkFillPose]; the magnetometer reports a nominal
//      earth field; gyroscope/linear-accel report zero; light reports
//      a typical indoor lux; proximity reports far; rotation vector
//      reports identity. A single daemon timer ticks all registered
//      listeners at their requested-ish rate. This keeps tilt-control
//      games alive (they read a stable gravity vector) instead of
//      seeing "no sensors" and disabling input. No per-APK code -- the
//      synthesized stream is the honest "flat, still" device state.
//      Only sensors a Mac can actually stand behind are synthesized:
//      there is no pedometer on this hardware, so TYPE_STEP_COUNTER /
//      TYPE_STEP_DETECTOR are absent from the catalog exactly as they
//      are on an Android device with no step-counter HAL (feature
//      detection via getDefaultSensor(...) != null must see null, not
//      a counter frozen at zero forever).
//
//      The timer thread only SYNTHESIZES; it never calls back into the
//      app directly. AOSP resolves one delivery Looper per listener
//      (SystemSensorManager.registerListenerImpl: `Looper looper =
//      (handler != null) ? handler.getLooper() : mMainLooper;`), so the
//      callbacks land on the supplied Handler's thread, or the MAIN
//      thread when none was supplied. Apps depend on that: a tilt
//      handler mutates view/game state inside onSensorChanged with no
//      synchronization because the platform guarantees serialization
//      with the main thread.
package android.hardware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SensorManager {
    public static final int SENSOR_DELAY_FASTEST = 0;
    public static final int SENSOR_DELAY_GAME = 1;
    public static final int SENSOR_DELAY_UI = 2;
    public static final int SENSOR_DELAY_NORMAL = 3;

    public static final int SENSOR_STATUS_NO_CONTACT = -1;
    public static final int SENSOR_STATUS_UNRELIABLE = 0;
    public static final int SENSOR_STATUS_ACCURACY_LOW = 1;
    public static final int SENSOR_STATUS_ACCURACY_MEDIUM = 2;
    public static final int SENSOR_STATUS_ACCURACY_HIGH = 3;

    // Legacy SensorListener bit constants (deprecated API, still queried).
    public static final int SENSOR_ORIENTATION = 1 << 0;
    public static final int SENSOR_ACCELEROMETER = 1 << 1;
    public static final int SENSOR_TEMPERATURE = 1 << 2;
    public static final int SENSOR_MAGNETIC_FIELD = 1 << 3;
    public static final int SENSOR_LIGHT = 1 << 4;
    public static final int SENSOR_PROXIMITY = 1 << 5;
    public static final int SENSOR_TRICORDER = 1 << 6;
    public static final int SENSOR_ORIENTATION_RAW = 1 << 7;
    public static final int SENSOR_ALL = 0x7F;
    public static final int SENSOR_MIN = SENSOR_ORIENTATION;
    public static final int SENSOR_MAX = ((SENSOR_ALL + 1) >> 1);

    public static final int DATA_X = 0;
    public static final int DATA_Y = 1;
    public static final int DATA_Z = 2;
    public static final int RAW_DATA_INDEX = 3;
    public static final int RAW_DATA_X = 3;
    public static final int RAW_DATA_Y = 4;
    public static final int RAW_DATA_Z = 5;

    // Physical constants (verbatim AOSP).
    public static final float STANDARD_GRAVITY = 9.80665f;
    public static final float GRAVITY_SUN = 275.0f;
    public static final float GRAVITY_MERCURY = 3.70f;
    public static final float GRAVITY_VENUS = 8.87f;
    public static final float GRAVITY_EARTH = 9.80665f;
    public static final float GRAVITY_MOON = 1.6f;
    public static final float GRAVITY_MARS = 3.71f;
    public static final float GRAVITY_JUPITER = 23.12f;
    public static final float GRAVITY_SATURN = 8.96f;
    public static final float GRAVITY_URANUS = 8.69f;
    public static final float GRAVITY_NEPTUNE = 11.0f;
    public static final float GRAVITY_PLUTO = 0.6f;
    public static final float GRAVITY_DEATH_STAR_I = 0.000000353036145f;
    public static final float GRAVITY_THE_ISLAND = 4.815162342f;

    public static final float MAGNETIC_FIELD_EARTH_MAX = 60.0f;
    public static final float MAGNETIC_FIELD_EARTH_MIN = 30.0f;
    public static final float PRESSURE_STANDARD_ATMOSPHERE = 1013.25f;

    public static final float LIGHT_SUNLIGHT_MAX = 120000.0f;
    public static final float LIGHT_SUNLIGHT = 110000.0f;
    public static final float LIGHT_SHADE = 20000.0f;
    public static final float LIGHT_OVERCAST = 10000.0f;
    public static final float LIGHT_SUNRISE = 400.0f;
    public static final float LIGHT_CLOUDY = 100.0f;
    public static final float LIGHT_FULLMOON = 0.25f;
    public static final float LIGHT_NO_MOON = 0.001f;

    // Matches AOSP remapCoordinateSystem's sTempMatrix scratch buffer.
    private static final float[] sTempMatrix = new float[16];

    // --- synthesized sensor catalog -------------------------------------
    private final List<Sensor> mSensors = new ArrayList<>();

    // ---- WHAT THIS DEVICE HONESTLY HAS, AND WHAT IT DOES NOT ---------------
    //
    // A Mac has none of the motion or environment sensors an Android phone has.
    // Probed on Mac14,7 (MacBook Pro 13" M2), 2026-08-23: zero instances of every
    // accelerometer/motion IOKit service name, and no ambient-light service
    // (AppleLMUController / AppleSPUVD6287 / AppleALS all absent).
    //
    // ⚠ There IS an undocumented motion stream. Six AppleSPUHIDDevice instances
    // exist, two with MaxInputReportSize=22, on VENDOR-DEFINED usage page 0xFF00
    // (not the HID Sensors page 0x20), which is why searching for service NAMES
    // finds nothing and concludes "no sensor" -- a wrong answer this file's own
    // history is evidence for. `debug/spuhid_probe.swift` dumps them, and
    // IOHIDManagerOpen succeeds with NO TCC prompt. In a 25s listen those two
    // devices emitted nothing while telemetry devices chattered, so the stream is
    // event-driven or needs a feature report to enable. NOT cracked.
    //
    // OWNER DECISIONS, 2026-08-23, recorded verbatim because they are judgement
    // calls and not derivable from AOSP:
    //   * MAGNETIC_FIELD, PROXIMITY, PRESSURE, AMBIENT_TEMPERATURE and
    //     RELATIVE_HUMIDITY are GONE from the catalog. getDefaultSensor returns
    //     null and getSensorList omits them -- the answer AOSP documents for a
    //     device without the hardware, and the one apps already handle. These
    //     were the worst offenders: a weather or altimeter app would render our
    //     fabricated 1013.25 hPa / 22 C / 45% to the user AS A READING.
    //   * LIGHT is also gone. The owner asked for it to be wired to the host's
    //     real ambient-light sensor (rung 1 of CLAUDE.md's decision ladder, like
    //     pmset for battery and ioreg for brightness) -- but the probe above
    //     found no reachable ALS service on this machine, so there is nothing to
    //     wire yet. Absent is the truthful answer until there is; see
    //     docs/future_implementations.md.
    //   * GYROSCOPE STAYS, reporting 0,0,0. For a laptop sitting on a desk that
    //     is not a fabrication, it is the correct reading.
    //   * The MOTION family (ACCELEROMETER, GRAVITY, LINEAR_ACCELERATION,
    //     ROTATION_VECTOR, GAME_ROTATION_VECTOR, ORIENTATION) stays PRESENT and
    //     reports A DEVICE AT REST: the 1g gravity vector on ACCELEROMETER and
    //     GRAVITY, and genuine zeros on LINEAR_ACCELERATION and GYROSCOPE, which
    //     at rest are not fabrications at all.
    //     ⚠ The owner asked for the accelerometer to be zeroed too, and v1.0.1041
    //     did that. It was reverted at v1.0.1042 because (0,0,0) is FREE FALL, a
    //     less plausible fabrication than gravity, and because it measurably broke
    //     an app (mastodon 91.14 -> 83.74). MACAPK_SENSOR_ZERO_MOTION=1 restores
    //     the literal zeros. Full reasoning at the RESTORED note in makeEvent.
    //     Advertising a pose at all is knowingly a fabrication -- CLAUDE.md's decision
    //     ladder calls advertising a capability we cannot honour the forbidden
    //     fourth rung -- and it is kept deliberately, because the alternative
    //     (returning null) makes a tilt-steered game REFUSE TO START rather than
    //     merely be unplayable, and because the real fix is already designed:
    //     a KEYBOARD-DRIVEN accelerometer, which turns these values into genuine
    //     user input the way KeyBindProfiles already turns keys into real touch
    //     events. LANDED 2026-10-02 as Controls -> Tilt Control
    //     (com.macapk.loader.TiltControl + macapkTiltEvent below,
    //     docs/tilt-control_2026-10-02.md): with it on, these values ARE user
    //     input. With it off, this comment remains the honesty the owner asked for.
    //
    // Do not "improve" the zeros back into plausible values. A plausible lie is
    // strictly worse than an obvious one: it is what let the fabricated pressure
    // and humidity readings sit here unnoticed.
    /** protected, matching AOSP (SensorManager is abstract upstream with a
     *  protected ctor). Made non-package-private 2026-08-24 so an
     *  InputDevice-scoped SensorManager can be subclassed from android.view:
     *  InputDevice.getSensorManager() must report an EMPTY catalog, because a
     *  touchscreen or a virtual keyboard has no built-in accelerometer, and
     *  handing back this process-wide instance would tell an app the keyboard
     *  has a gyroscope. */
    protected SensorManager() {
        // Synthesize the "device still" sensor set. Continuous sensors carry a
        // 10000us (100Hz) minDelay like a typical phone.
        mSensors.add(new Sensor(Sensor.TYPE_ACCELEROMETER, "Mac.apk Accelerometer",
            Sensor.STRING_TYPE_ACCELEROMETER, 2f * STANDARD_GRAVITY, 0.01f, 0.1f,
            10000, Sensor.REPORTING_MODE_CONTINUOUS));
        mSensors.add(new Sensor(Sensor.TYPE_GYROSCOPE, "Mac.apk Gyroscope",
            Sensor.STRING_TYPE_GYROSCOPE, 8.7f, 0.001f, 0.1f,
            10000, Sensor.REPORTING_MODE_CONTINUOUS));
        mSensors.add(new Sensor(Sensor.TYPE_GRAVITY, "Mac.apk Gravity",
            Sensor.STRING_TYPE_GRAVITY, 2f * STANDARD_GRAVITY, 0.01f, 0.1f,
            10000, Sensor.REPORTING_MODE_CONTINUOUS));
        mSensors.add(new Sensor(Sensor.TYPE_LINEAR_ACCELERATION, "Mac.apk Linear Acceleration",
            Sensor.STRING_TYPE_LINEAR_ACCELERATION, 2f * STANDARD_GRAVITY, 0.01f, 0.1f,
            10000, Sensor.REPORTING_MODE_CONTINUOUS));
        mSensors.add(new Sensor(Sensor.TYPE_ROTATION_VECTOR, "Mac.apk Rotation Vector",
            Sensor.STRING_TYPE_ROTATION_VECTOR, 1f, 0.0001f, 0.1f,
            10000, Sensor.REPORTING_MODE_CONTINUOUS));
        mSensors.add(new Sensor(Sensor.TYPE_GAME_ROTATION_VECTOR, "Mac.apk Game Rotation Vector",
            Sensor.STRING_TYPE_GAME_ROTATION_VECTOR, 1f, 0.0001f, 0.1f,
            10000, Sensor.REPORTING_MODE_CONTINUOUS));
        mSensors.add(new Sensor(Sensor.TYPE_ORIENTATION, "Mac.apk Orientation",
            Sensor.STRING_TYPE_ORIENTATION, 360f, 0.1f, 0.1f,
            10000, Sensor.REPORTING_MODE_CONTINUOUS));
        // No TYPE_STEP_COUNTER / TYPE_STEP_DETECTOR: a Mac has no pedometer.
        // getDefaultSensor(TYPE_STEP_COUNTER) is THE feature-detection idiom
        // ("non-null means this device counts steps"), and an Android device
        // without a step-counter HAL returns null for it. Advertising one that
        // can only ever report 0 steps is silently wrong -- indistinguishable
        // from "you did not walk" -- where null is the honest, well-understood
        // answer every fitness app already handles.

        // Guard: MACAPK_CATALOG_TYPES is what PackageManager advertises, and the
        // adds above are what apps actually get. If those two ever disagree we
        // are back to advertising a sensor that returns null -- the exact bug
        // this arrangement exists to prevent -- so say so LOUDLY rather than
        // letting it sit unnoticed for months as the last one did.
        if (mSensors.size() != MACAPK_CATALOG_TYPES.length) {
            com.macapk.loader.NativeLog.e("SensorManager",
                "catalog/advertised MISMATCH: " + mSensors.size() + " sensors built vs "
                + MACAPK_CATALOG_TYPES.length + " advertised -- hasSystemFeature will lie");
        } else {
            for (int type : MACAPK_CATALOG_TYPES) {
                boolean found = false;
                for (Sensor sn : mSensors) if (sn.getType() == type) { found = true; break; }
                if (!found) com.macapk.loader.NativeLog.e("SensorManager",
                    "advertised sensor type " + type + " is NOT in the built catalog"
                    + " -- getDefaultSensor(" + type + ") will return null after"
                    + " hasSystemFeature said yes");
            }
        }
    }

    /** Loader-side factory; the constructor is package-private in real Android too. */
    public static SensorManager macapkNew() { return new SensorManager(); }

    // ---- the NDK side: ASensorManager / ASensorEventQueue -------------------
    //
    // A native game (Unity's Input.acceleration, Gameloft's engines, any
    // NativeActivity) reads sensors through libandroid's ASensor* API, not this
    // class. On a device both are views of ONE sensor service; here the bionic
    // shim (runtime/translator/src/bionic_shim_sensors.cpp) mirrors THIS
    // catalog and asks THIS class for every event's values, so the NDK reports
    // exactly what a Java listener would -- the at-rest pose, or the Tilt
    // Control pose while it is on. docs/tilt-control_2026-10-02.md.
    private static SensorManager sNative;
    private static synchronized SensorManager macapkNativeInstance() {
        if (sNative == null) sNative = new SensorManager();
        return sNative;
    }

    /** The catalog for the shim: {type, minDelayUs, reportingMode} per sensor,
     *  in catalog order (the order getSensorList(TYPE_ALL) returns). */
    public static int[] macapkNativeCatalog() {
        List<Sensor> l = macapkNativeInstance().mSensors;
        int[] out = new int[l.size() * 3];
        for (int i = 0; i < l.size(); i++) {
            Sensor s = l.get(i);
            out[i * 3] = s.getType();
            out[i * 3 + 1] = s.getMinDelay();
            out[i * 3 + 2] = s.getReportingMode();
        }
        return out;
    }

    /** {maxRange, resolution, power} of the sensor at catalog index i. */
    public static float[] macapkNativeFloats(int i) {
        Sensor s = macapkNativeInstance().mSensors.get(i);
        return new float[] { s.getMaximumRange(), s.getResolution(), s.getPower() };
    }

    /** Name (which == 0) or string type (which == 1) of catalog index i. */
    public static String macapkNativeString(int i, int which) {
        Sensor s = macapkNativeInstance().mSensors.get(i);
        return which == 0 ? s.getName() : s.getStringType();
    }

    /** One event for `type`, exactly what makeEvent gives a Java listener now:
     *  the values into `out` (returns how many; 0 when this device has no such
     *  sensor) and the event timestamp (elapsedRealtimeNanos base) in ts[0]. */
    public static int macapkNativeSample(int type, float[] out, long[] ts) {
        SensorManager sm = macapkNativeInstance();
        Sensor s = sm.getDefaultSensor(type);
        if (s == null) return 0;
        SensorEvent ev = sm.makeEvent(s, System.nanoTime());
        if (ev == null) return 0;
        int n = Math.min(out.length, ev.values.length);
        System.arraycopy(ev.values, 0, out, 0, n);
        if (ts != null && ts.length > 0) ts[0] = ev.timestamp;
        return n;
    }

    public Sensor getDefaultSensor(int type) {
        for (Sensor s : mSensors) if (s.getType() == type) return s;
        return null;
    }

    public Sensor getDefaultSensor(int type, boolean wakeUp) {
        // Mac.apk synthesizes only non-wakeup sensors.
        if (wakeUp) return null;
        return getDefaultSensor(type);
    }

    public List<Sensor> getSensorList(int type) {
        if (type == Sensor.TYPE_ALL) {
            return Collections.unmodifiableList(new ArrayList<>(mSensors));
        }
        List<Sensor> out = new ArrayList<>();
        for (Sensor s : mSensors) if (s.getType() == type) out.add(s);
        return Collections.unmodifiableList(out);
    }

    public List<Sensor> getDynamicSensorList(int type) { return Collections.emptyList(); }

    /** The `android.hardware.sensor.*` PackageManager features this device may
     *  honestly claim, DERIVED from the catalog above rather than hand-listed.
     *
     *  Why derived: MacApkPackageManager used to keep its own copy of this list,
     *  and on 2026-08-23 the catalog lost MAGNETIC_FIELD / LIGHT / PROXIMITY /
     *  PRESSURE / AMBIENT_TEMPERATURE / RELATIVE_HUMIDITY while that copy kept
     *  claiming all six. hasSystemFeature("...sensor.compass") answered true and
     *  getDefaultSensor(TYPE_MAGNETIC_FIELD) then returned null -- an app takes
     *  its has-compass branch and NPEs, which is the forbidden fourth rung
     *  (advertising a capability we cannot honour) reintroduced by two commits
     *  that each obeyed the coherence rule against a different snapshot of the
     *  other file. Same reasoning, and same cure, as getLegacySensorMask below:
     *  one answer, computed from the catalog, cannot drift from it.
     *
     *  Static because PackageManager answers before any Context-scoped
     *  SensorManager exists; it reads the same table the instance ctor does. */
    public static java.util.Set<String> macapkSensorFeatures() {
        java.util.LinkedHashSet<String> f = new java.util.LinkedHashSet<>();
        for (int type : MACAPK_CATALOG_TYPES) {
            String name = macapkFeatureForType(type);
            if (name != null) f.add(name);
        }
        return f;
    }

    /** AOSP PackageManager FEATURE_SENSOR_* <-> Sensor.TYPE_* mapping. Only the
     *  types that HAVE a feature string appear; TYPE_GRAVITY,
     *  TYPE_LINEAR_ACCELERATION, TYPE_ROTATION_VECTOR, TYPE_GAME_ROTATION_VECTOR
     *  and TYPE_ORIENTATION have none upstream, so they contribute nothing. */
    private static String macapkFeatureForType(int type) {
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:       return "android.hardware.sensor.accelerometer";
            case Sensor.TYPE_GYROSCOPE:           return "android.hardware.sensor.gyroscope";
            case Sensor.TYPE_MAGNETIC_FIELD:      return "android.hardware.sensor.compass";
            case Sensor.TYPE_LIGHT:               return "android.hardware.sensor.light";
            case Sensor.TYPE_PROXIMITY:           return "android.hardware.sensor.proximity";
            case Sensor.TYPE_PRESSURE:            return "android.hardware.sensor.barometer";
            case Sensor.TYPE_AMBIENT_TEMPERATURE: return "android.hardware.sensor.ambient_temperature";
            case Sensor.TYPE_RELATIVE_HUMIDITY:   return "android.hardware.sensor.relative_humidity";
            case Sensor.TYPE_HEART_RATE:          return "android.hardware.sensor.heartrate";
            case Sensor.TYPE_STEP_COUNTER:        return "android.hardware.sensor.stepcounter";
            case Sensor.TYPE_STEP_DETECTOR:       return "android.hardware.sensor.stepdetector";
            default: return null;
        }
    }

    /** The one place the catalog's membership is written down. The instance
     *  constructor builds its Sensor objects from these same types, so adding or
     *  removing a sensor moves BOTH the catalog and the advertised features. */
    static final int[] MACAPK_CATALOG_TYPES = {
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_GRAVITY,
        Sensor.TYPE_LINEAR_ACCELERATION,
        Sensor.TYPE_ROTATION_VECTOR,
        Sensor.TYPE_GAME_ROTATION_VECTOR,
        Sensor.TYPE_ORIENTATION,
    };

    // ---- the ORIGINAL (API 1) SensorListener API -------------------------
    //
    // Deprecated upstream since API 3 and still shipped by a great many older
    // apps. It is NOT a thin alias for the modern API -- different sensor
    // identifiers, negated axes, an extra yaw filter -- so it is bridged for
    // real through LegacySensorManager rather than stubbed. See that file's
    // header for what would break if it were not.
    //
    // AOSP lazily creates the bridge (getLegacySensorManager()); same here, so
    // an app that never touches the legacy API never allocates it.
    private LegacySensorManager mLegacySensorManager;

    private LegacySensorManager getLegacySensorManager() {
        synchronized (mSensors) {
            if (mLegacySensorManager == null) {
                mLegacySensorManager = new LegacySensorManager(this);
            }
            return mLegacySensorManager;
        }
    }

    /** AOSP SensorManager.java:405. A bitmask of SENSOR_* legacy bits, derived
     *  from the sensors this device actually reports -- so it stays consistent
     *  with getSensorList() by construction rather than being a second,
     *  hand-maintained answer that could drift from it. */
    @Deprecated
    public int getSensors() {
        return getLegacySensorManager().getSensors();
    }

    @Deprecated
    public boolean registerListener(SensorListener listener, int sensors) {
        return registerListener(listener, sensors, SENSOR_DELAY_NORMAL);
    }

    @Deprecated
    public boolean registerListener(SensorListener listener, int sensors, int rate) {
        return getLegacySensorManager().registerListener(listener, sensors, rate);
    }

    /** AOSP :642 -- note the mask: SENSOR_ALL does NOT include
     *  SENSOR_ORIENTATION_RAW (it is 0x7F, bit 7 is RAW), so unregistering
     *  "everything" has to name RAW explicitly or a raw-orientation
     *  registration would survive. */
    @Deprecated
    public void unregisterListener(SensorListener listener) {
        unregisterListener(listener, SENSOR_ALL | SENSOR_ORIENTATION_RAW);
    }

    @Deprecated
    public void unregisterListener(SensorListener listener, int sensors) {
        getLegacySensorManager().unregisterListener(listener, sensors);
    }

    // ---- dynamic sensors -------------------------------------------------
    //
    // "Dynamic" means hot-pluggable: a sensor that appears after boot, e.g. one
    // inside a plugged-in USB peripheral or a connected game controller. A Mac
    // exposes no such HAL through this runtime, so the catalog never changes
    // and a registered callback is never invoked -- which is precisely what
    // isDynamicSensorDiscoverySupported() reporting false MEANS, and an app is
    // documented to check that first.

    /** AOSP SensorManager.java:1062 derives this from the presence of a
     *  TYPE_DYNAMIC_SENSOR_META sensor rather than from a flag. Mirrored, so
     *  the day a dynamic-sensor source exists the answer follows the catalog
     *  automatically instead of needing this line changed too. */
    public boolean isDynamicSensorDiscoverySupported() {
        return getSensorList(Sensor.TYPE_DYNAMIC_SENSOR_META).size() > 0;
    }

    public void registerDynamicSensorCallback(DynamicSensorCallback callback) {
        registerDynamicSensorCallback(callback, null);
    }

    /** The callback is held rather than dropped so that unregister() can be
     *  symmetric and so an app that registers twice does not accumulate. It is
     *  never invoked: nothing here can connect or disconnect a sensor. */
    public void registerDynamicSensorCallback(DynamicSensorCallback callback,
            android.os.Handler handler) {
        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null");
        }
        mDynamicSensorCallbacks.addIfAbsent(callback);
    }

    public void unregisterDynamicSensorCallback(DynamicSensorCallback callback) {
        if (callback == null) return;
        mDynamicSensorCallbacks.remove(callback);
    }

    private final CopyOnWriteArrayList<DynamicSensorCallback> mDynamicSensorCallbacks =
            new CopyOnWriteArrayList<>();

    /** AOSP SensorManager.DynamicSensorCallback. */
    public abstract static class DynamicSensorCallback {
        public void onDynamicSensorConnected(Sensor sensor) { }
        public void onDynamicSensorDisconnected(Sensor sensor) { }
    }

    // ---- direct channels -------------------------------------------------
    //
    // A direct channel is shared memory a sensor HAL writes samples into with
    // no Binder hop. There is no HAL here -- the values this class reports are
    // synthesised in Java -- so there is nothing on the other end of a buffer
    // to write into it.
    //
    // null is AOSP's own documented failure return ("null on failure"), so an
    // app already branches on it and falls back to registerListener, which
    // DOES work. Returning a live-looking SensorDirectChannel that no producer
    // ever writes to would be the worse failure: the app would poll a buffer
    // that never changes and conclude the device is perfectly still, with no
    // error anywhere to explain it.
    //
    // The argument checks are AOSP's (SensorManager.java:943/:965 dereference
    // the argument immediately, and both parameters are @NonNull).

    public SensorDirectChannel createDirectChannel(android.os.MemoryFile mem) {
        if (mem == null) {
            throw new NullPointerException("MemoryFile cannot be null");
        }
        return null;
    }

    public SensorDirectChannel createDirectChannel(HardwareBuffer mem) {
        if (mem == null) {
            throw new NullPointerException("HardwareBuffer cannot be null");
        }
        return null;
    }

    // --- listener registration + synthetic delivery ----------------------
    private static final class Registration {
        final SensorEventListener listener;
        final Sensor sensor;
        final int rateUs;
        /** The thread callbacks run on. AOSP keeps one Looper per listener,
         *  owned by that listener's SensorEventQueue; we keep a Handler bound
         *  to the same Looper and post through it. */
        final android.os.Handler handler;
        long lastDeliveryNanos;
        /** Last accuracy handed to onAccuracyChanged. AOSP tracks this in a
         *  SparseIntArray whose absent-key read is 0, and fires the callback
         *  only when the incoming accuracy differs -- so it lands once, on the
         *  delivery thread, just before the first onSensorChanged. */
        int lastAccuracy;
        /** At most one event in flight per registration. The real event queue
         *  is a fixed-size ring buffer: when the consumer thread falls behind,
         *  the HAL drops samples rather than growing an unbounded backlog that
         *  would later replay as one burst. Dropping matches that. */
        final java.util.concurrent.atomic.AtomicBoolean inFlight =
            new java.util.concurrent.atomic.AtomicBoolean();
        Registration(SensorEventListener l, Sensor s, int rateUs, android.os.Handler h) {
            this.listener = l; this.sensor = s; this.rateUs = rateUs; this.handler = h;
        }
    }

    private final CopyOnWriteArrayList<Registration> mRegs = new CopyOnWriteArrayList<>();
    private volatile java.util.Timer mTimer;

    public boolean registerListener(SensorEventListener l, Sensor s, int rate) {
        return registerListener(l, s, rate, null);
    }

    public boolean registerListener(SensorEventListener l, Sensor s, int rate, android.os.Handler h) {
        if (l == null || s == null) return false;
        // AOSP SystemSensorManager.registerListenerImpl:
        //   Looper looper = (handler != null) ? handler.getLooper() : mMainLooper;
        // i.e. the supplied Handler's thread, else the app's MAIN thread --
        // never "whichever thread happened to call registerListener" and never
        // an internal worker. mMainLooper is always non-null there; the null
        // guard below covers a Handler built on a thread with no Looper, which
        // would otherwise leave us with nowhere to dispatch.
        android.os.Looper looper = (h != null) ? h.getLooper() : null;
        if (looper == null) looper = android.os.Looper.getMainLooper();
        mRegs.add(new Registration(l, s, samplingPeriodUs(rate), new android.os.Handler(looper)));
        // No onAccuracyChanged here: AOSP does not fire one at registration.
        // It arrives on the delivery thread immediately before the first event
        // (see deliverTick), which is what listeners are written against.
        ensureTimer();
        return true;
    }

    public boolean registerListener(SensorEventListener l, Sensor s, int rate, int maxReportLatencyUs) {
        return registerListener(l, s, rate, (android.os.Handler) null);
    }

    public boolean registerListener(SensorEventListener l, Sensor s, int rate,
                                    int maxReportLatencyUs, android.os.Handler h) {
        return registerListener(l, s, rate, h);
    }

    public void unregisterListener(SensorEventListener l) {
        mRegs.removeIf(r -> r.listener == l);
    }

    public void unregisterListener(SensorEventListener l, Sensor s) {
        mRegs.removeIf(r -> r.listener == l && r.sensor == s);
    }

    // AOSP accepts SENSOR_DELAY_* tokens OR a microsecond period. Map the
    // named rates to typical microsecond periods; pass through raw us values.
    private static int samplingPeriodUs(int rate) {
        switch (rate) {
            case SENSOR_DELAY_FASTEST: return 0;
            case SENSOR_DELAY_GAME:    return 20000;
            case SENSOR_DELAY_UI:      return 66667;
            case SENSOR_DELAY_NORMAL:  return 200000;
            default:                   return rate; // already a us period
        }
    }

    private synchronized void ensureTimer() {
        if (mTimer != null) return;
        java.util.Timer t = new java.util.Timer("macapk-sensor", true);
        // Tick at 60Hz; per-registration rate gating decides who fires.
        t.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override public void run() { deliverTick(); }
        }, 0L, 16L);
        mTimer = t;
    }

    private void deliverTick() {
        long now = System.nanoTime();
        for (final Registration r : mRegs) {
            long periodNs = Math.max(r.rateUs, 16000) * 1000L;
            if (now - r.lastDeliveryNanos < periodNs) continue;
            // Consumer still holds the previous sample: drop this one rather
            // than queue it (fixed-ring-buffer semantics, see Registration).
            if (!r.inFlight.compareAndSet(false, true)) continue;
            r.lastDeliveryNanos = now;
            final SensorEvent ev = makeEvent(r.sensor, now);
            if (ev == null) { r.inFlight.set(false); continue; }
            // Synthesis happens on this timer thread; DELIVERY happens on the
            // registration's Looper, per AOSP's one-Looper-per-listener rule.
            // A fresh SensorEvent per tick is what makes that safe -- nothing
            // is mutated after the post.
            r.handler.post(() -> {
                try {
                    // AOSP ignores events still queued for a listener that has
                    // since unregistered (SensorEventQueue.dispatchSensorEvent
                    // returns early once the listener's entry is gone).
                    if (!mRegs.contains(r)) return;
                    if (ev.accuracy >= 0 && r.lastAccuracy != ev.accuracy) {
                        r.lastAccuracy = ev.accuracy;
                        r.listener.onAccuracyChanged(ev.sensor, ev.accuracy);
                    }
                    r.listener.onSensorChanged(ev);
                } finally {
                    // Exceptions from app callbacks propagate to the looper,
                    // which logs them (Android would let them reach the
                    // uncaught handler); swallowing here would hide app bugs.
                    r.inFlight.set(false);
                }
            });
        }
    }

    // The POSE the synthetic device is in: HELD UPRIGHT in portrait, facing the
    // user, tilted back about 5 degrees. NOT lying on a table.
    //
    // WHY NOT (0, 0, +g). A Mac.apk window is something the user is looking AT,
    // head on, so the honest analogue of the host's physical situation is a phone
    // held up in front of you. (0, 0, +g) is the face-up-on-a-desk pose, and it is
    // DEGENERATE: both pitch and roll come out exactly zero, so every app that
    // derives an attitude from gravity is told the screen is horizontal and gets
    // the MIDPOINT of whatever range it drives.
    //
    // Measured consequence, mastodon 2.13.1's splash. Its parallax art
    // (org.joinmastodon.android.ui.InterpolatingMotionEffect) positions each of 5
    // layers with setTranslationY(lerp(minY, maxY, (roll+1)/2)) where
    // roll = atan2(y, hypot(x,z))/PI*2. With roll == 0 every layer parks at its
    // midpoint; the reference AVD reports roll == +0.947 and every layer parks
    // near the bottom of its range. That difference, times the splash's own
    // 808/720 container scale, is 10.6px for the cloud layer and 31.9px for the
    // hill layers, which is the whole of the "splash art sits 38px too high"
    // divergence less the 6px our 48px status bar owes the emulator's 60px one.
    //
    // The numbers are the reference AVD's own default accelerometer output, and
    // they were RECOVERED FROM THE RENDERED PIXELS rather than assumed: solving
    // the parallax for the observed per-layer offsets on three layers with three
    // DIFFERENT ranges gives one consistent roll of 0.945 to 0.947 of full scale,
    // i.e. gravity 85.2 degrees off the device Y axis. A forward model of the
    // drawable independently predicted the horizontal extents to 1px, and x is
    // unchanged on both sides because pitch stays 0.
    //
    // The invariant that must hold: an accelerometer at rest reports exactly 1g in
    // SOME direction. |(0, 9.7726, 0.8159)| == STANDARD_GRAVITY, and TYPE_GRAVITY
    // shares the vector by definition.
    //
    // ⚠ TYPE_ORIENTATION and TYPE_ROTATION_VECTOR below still synthesise the FLAT
    // pose and are now inconsistent with this. getRotationMatrix + getOrientation
    // derive from accelerometer + magnetometer so those stay coherent; only the
    // two standalone synths drift. Left alone deliberately -- a quaternion is a
    // second guess-risk and this change is meant to be one falsifiable thing.
    // SETTLED 2026-08-24 -- the 1g pose is the OWNER-APPROVED default. Do not
    // re-litigate this; it has now been argued in three places.
    //
    // History: the owner first asked to "fabricate the accelerometer to zero and
    // just be honest in our documentation" (v1.0.1041). That was implemented, cost
    // mastodon 91.14 -> 83.74, and was reverted at v1.0.1042 with the reasoning in
    // makeEvent. On review the owner approved gravity as the default, verbatim:
    // "I approve of fabricating it to show the gravity. Make it default. Especially
    // since it's technically true of what it's experiencing." That last clause is
    // the actual justification and it is a better one than ours: a Mac really is
    // sitting in a 1g field, so this is the ONE synthesized value on this device
    // that is not a fabrication at all -- only its DIRECTION is chosen.
    //
    // MACAPK_SENSOR_ZERO_MOTION=1 still forces the zeros. Kept deliberately: it is
    // a one-env-var bisect for "is this app's layout driven by the accelerometer",
    // which is exactly the question that took a 178-class jar bisect to answer.
    private static final boolean ZERO_MOTION =
        "1".equals(System.getenv("MACAPK_SENSOR_ZERO_MOTION"));

    private static final float POSE_ACCEL_X = 0f;
    private static final float POSE_ACCEL_Y = 9.7726f;
    private static final float POSE_ACCEL_Z = 0.8159f;

    // ---- the pose follows the DISPLAY ROTATION (wave 5, 2026-09-26) ---------
    //
    // The pose above is SCREEN-relative: "the screen stands upright in front of
    // the user". Sensor axes are not. AOSP SensorEvent.java (android14-release,
    // class javadoc :33-35): "The coordinate-system is defined relative to the
    // screen of the phone in its default orientation. The axes are not swapped
    // when the device's screen orientation changes."
    //
    // We report Surface.ROTATION_90 for every landscape window (Display.
    // getRotation, the committed display), and AOSP Display.java:970-975 defines
    // what that claims physically: "if the device is rotated 90 degrees
    // counter-clockwise, to compensate rendering will be rotated by 90 degrees
    // clockwise and thus the returned value here will be ROTATION_90". Turn a
    // phone 90 degrees counter-clockwise and its +X axis points UP, so a device
    // at rest in that pose reads (+g_up, 0, z) -- not (0, +g_up, z). Emitting the
    // portrait vector next to ROTATION_90 described a device lying on its side in
    // a window that says it is upright; no physical device reports that pair.
    //
    // Measured consequence (docs/fleet-widget-apps-mastodon-fossify-pianoli_
    // 2026-09-26.md, M4): Mastodon's splash parallax (ui.w.onSensorChanged)
    // remaps by getRotation() the textbook way, so on a landscape window it
    // derived a full sideways tilt and translated its art layers (+126,+126)px
    // away from the tablet emulator's. Every tilt-steered landscape game reads
    // the same fabricated lateral tilt.
    //
    // So rotate the SCREEN-relative pose into DEVICE axes by the current
    // rotation. After this, the canonical screen remap every app applies
    //   ROTATION_90:  screenX = -values[1], screenY = values[0]
    // hands back (0, 9.7726) in every orientation, which is the invariant: the
    // user is looking at an upright screen whichever way the window is shaped.
    // ROTATION_0 is untouched by construction, so the portrait phone fleet --
    // every pair the pose above was fitted against -- cannot move.
    //
    // The Z component is unchanged: a rotation about the screen normal leaves the
    // 5-degree lean-back where it was. TYPE_ORIENTATION and the ROTATION_VECTOR
    // family need nothing: they synthesise the flat pose, and turning a flat
    // device about its screen normal changes only the azimuth, which with no
    // magnetometer in the catalog is an arbitrary reference anyway.
    //
    // The LEGACY SensorListener API needed no change of its own: LegacySensorManager
    // already remaps by the same rotation (AOSP mapSensorDataToWindow), so with a
    // device-axes vector here it now reports the SAME screen-relative gravity in
    // landscape as in portrait, where before it reported a sideways device too.
    //
    // Bisect gate: MACAPK_SENSOR_POSE_ROTATE=0 emits the portrait vector for
    // every rotation, exactly as before. Offline gate: debug/w5sensorsjobs_gate.sh.
    private static final boolean POSE_ROTATE =
        !"0".equals(System.getenv("MACAPK_SENSOR_POSE_ROTATE"));

    /** The display rotation the pose is expressed against. Asks the SAME object
     *  an app asks (android.view.Display.getRotation, which reads the committed
     *  display), so the two can never disagree. Read per event, not cached: the
     *  window can change shape (Cmd+R, setRequestedOrientation) while a listener
     *  is registered. Headless or before the display is known this throws out of
     *  the native canvas read, and ROTATION_0 is AOSP's own answer for a caller
     *  that cannot reach the window service (LegacySensorManager does the same). */
    private static int macapkPoseRotation() {
        try {
            android.view.Display d = sRotationDisplay;
            if (d == null) sRotationDisplay = d = new android.view.Display();
            return d.getRotation();
        } catch (Throwable t) {
            return android.view.Surface.ROTATION_0;
        }
    }
    /** Stateless: Display.getRotation reads static committed-display state, so
     *  one instance answers for every thread. A race creating two is harmless. */
    private static volatile android.view.Display sRotationDisplay;

    /** Fill values[0..2] with the at-rest gravity reaction for the current
     *  rotation. The screen-relative pose is (POSE_ACCEL_X, POSE_ACCEL_Y); the
     *  device axes are that vector turned by the rotation (see the block above).
     *  0f - v rather than -v keeps a 0 component +0.0: an app's atan2 would
     *  otherwise see -0.0, which is not what a device reports at rest. */
    private static void macapkFillPose(float[] values) {
        float sx = POSE_ACCEL_X, sy = POSE_ACCEL_Y;
        float dx = sx, dy = sy;
        if (POSE_ROTATE) {
            switch (macapkPoseRotation()) {
                case android.view.Surface.ROTATION_90:  dx = sy;      dy = 0f - sx; break;
                case android.view.Surface.ROTATION_180: dx = 0f - sx; dy = 0f - sy; break;
                case android.view.Surface.ROTATION_270: dx = 0f - sy; dy = sx;      break;
                default: break;   // ROTATION_0: the pose as fitted
            }
        }
        values[0] = dx;
        values[1] = dy;
        values[2] = POSE_ACCEL_Z;
    }

    // Build a "still" event for the given sensor type, for a device at rest in the
    // POSE above. Android's sensor frame is defined relative to the screen in its
    // natural orientation: +X right, +Y up, +Z out of the screen (AOSP SensorEvent
    // docs), and an accelerometer reports the reaction to gravity, so an upright
    // phone reads a large +Y.
    private SensorEvent makeEvent(Sensor s, long nowNanos) {
        // Tilt Control ON: the motion family is derived from the user-driven
        // orientation (macapkTiltEvent). OFF, or a type it does not own: null,
        // and the at-rest synthesis below runs exactly as before.
        SensorEvent ev = com.macapk.loader.TiltControl.isEnabled()
                ? macapkTiltEvent(s.getType(), nowNanos) : null;
        if (ev == null) switch (s.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_GRAVITY:
                ev = SensorEvent.macapkNew(3);
                // RESTORED 2026-08-23 (v1.0.1042) after a measured regression.
                //
                // v1.0.1041 replaced this vector with all-zero, under the owner's
                // "fabricate the accelerometer to zero and just be honest in our
                // documentation". That change cost mastodon 91.14% -> 83.74% on the
                // Canvas pair set, bisected to these three lines and nothing else
                // (framework-jar A/B, four classes, then this case). It is restored
                // because BOTH of the reasons given for zeroing it are wrong:
                //
                //  1. The deleted comment claimed POSE_ACCEL_* "reads as a device
                //     lying flat". It does not. The block 40 lines above says in its
                //     first sentence "HELD UPRIGHT in portrait ... NOT lying on a
                //     table", and explains at length WHY NOT (0, 0, +g) -- the
                //     face-up pose is the DEGENERATE one. The pose was deleted on a
                //     misreading of the comment that sat directly above it.
                //  2. Zero is not the honest answer, it is a DIFFERENT and less
                //     plausible fabrication: an accelerometer reading (0,0,0) is a
                //     device in FREE FALL. v1.0.1041's own comment said so. There is
                //     no configuration of a Mac on a desk in which this reading is
                //     true, whereas "a screen standing upright in Earth's gravity"
                //     is simply what the host physically is.
                //
                // And the pose is not a guess: POSE_ACCEL_* was RECOVERED FROM
                // RENDERED PIXELS (see the derivation above it) and matches the
                // reference AVD's own default output. Zeroing it drove
                // roll = atan2(y, hypot(x,z)) to 0, which parks every layer of
                // mastodon's splash parallax at its range midpoint instead of the
                // AVD's roll=+0.947 -- the uniform 12px shift that was measured.
                //
                // The honesty the owner asked for belongs in the class header and in
                // docs/, not in a value that apps compute geometry from. TYPE_
                // LINEAR_ACCELERATION and TYPE_GYROSCOPE below stay ZERO, and that
                // IS honest: a device at rest has no linear acceleration and no
                // rotation rate. Only the gravity vector must be 1g.
                //
                // INVARIANT: |(x,y,z)| == STANDARD_GRAVITY. Do not zero this again
                // without re-running debug/recap_mac.sh on mastodon first.
                //
                // The vector is expressed in DEVICE axes for the current display
                // rotation (macapkFillPose; gate MACAPK_SENSOR_POSE_ROTATE), so the
                // invariant holds and ROTATION_0 still emits exactly the fitted pose.
                if (ZERO_MOTION) {
                    ev.values[0] = 0f;
                    ev.values[1] = 0f;
                    ev.values[2] = 0f;
                } else {
                    macapkFillPose(ev.values);
                }
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
            case Sensor.TYPE_GYROSCOPE:
                ev = SensorEvent.macapkNew(3);
                ev.values[0] = 0f; ev.values[1] = 0f; ev.values[2] = 0f;
                break;
            case Sensor.TYPE_ORIENTATION:
                // azimuth/pitch/roll degrees for a flat, north-facing device.
                ev = SensorEvent.macapkNew(3);
                ev.values[0] = 0f; ev.values[1] = 0f; ev.values[2] = 0f;
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
            case Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR:
                // Identity quaternion (x=y=z=0, w=1) plus heading-accuracy.
                ev = SensorEvent.macapkNew(5);
                ev.values[0] = 0f; ev.values[1] = 0f; ev.values[2] = 0f;
                ev.values[3] = 1f; ev.values[4] = 0f;
                break;
            default:
                return null; // sensors we deliberately keep quiescent
        }
        ev.sensor = s;
        ev.accuracy = SENSOR_STATUS_ACCURACY_HIGH;
        // AOSP frameworks/base/core/java/android/hardware/SensorEvent.java :834-836:
        // "the time in nanoseconds ... in the same time base as
        // SystemClock#elapsedRealtimeNanos()". Under MACAPK_GUEST_CLOCK_NANOTIME
        // (default ON) that is CLOCK_BOOTTIME, not System.nanoTime() -- stamping
        // with nowNanos (System.nanoTime(), used above only for the 60 Hz rate
        // gate) would leave `elapsedRealtimeNanos() - event.timestamp` off by the
        // host uptime, same shape of bug this lane fixed elsewhere. Under =0 keep
        // nowNanos unchanged (byte-identical old path; elapsedRealtimeNanos() was
        // itself process-relative there, so the two already agreed).
        ev.timestamp = android.os.SystemClock.AOSP_CLOCKS
                ? android.os.SystemClock.elapsedRealtimeNanos() : nowNanos;
        return ev;
    }

    // ---- Tilt Control: ONE orientation, every motion sensor derived from it ----
    //
    // When the user turns on Controls -> Tilt Control (com.macapk.loader.
    // TiltControl), the arrow keys -- or any other transducer feeding
    // TiltControl.setTarget / setPose -- tilt the virtual device, and every
    // motion sensor is computed from that ONE pose so they can never disagree:
    //
    //   T = Rz(roll) * Rx(pitch), applied in SCREEN axes to the resting pose
    //       (roll > 0 = right edge down, pitch > 0 = top edge away from the user;
    //       docs/tilt-control_2026-10-02.md derives the signs).
    //   ACCELEROMETER = GRAVITY = T * (POSE_ACCEL_X, POSE_ACCEL_Y, POSE_ACCEL_Z),
    //       so |v| stays STANDARD_GRAVITY and roll = pitch = 0 is bit-for-bit the
    //       at-rest vector (cos 0 = 1, sin 0 = 0 exactly).
    //   LINEAR_ACCELERATION = accelerometer - gravity = 0, AOSP
    //       LinearAccelerationSensor.cpp's definition. A slewing tilt is a pure
    //       rotation about the device, so this is the honest value, not a stub.
    //   ROTATION_VECTOR / GAME_ROTATION_VECTOR = the unit quaternion of R, the
    //       device->world matrix whose rows are the world East / North / Up axes
    //       in device coordinates (the layout SensorManager.getRotationMatrix
    //       produces), chosen so the AOSP-verbatim getRotationMatrixFromVector
    //       below gives back R. World North is the direction the BACK of the
    //       device faces at rest (horizontal): there is no magnetometer, so the
    //       heading reference is arbitrary, as it is for GAME_ROTATION_VECTOR.
    //   ORIENTATION = AOSP sensorservice OrientationSensor.cpp (android14-release)
    //       verbatim on the same matrix: azimuth = atan2(-R[1][0], R[0][0]),
    //       pitch = atan2(-R[2][1], R[2][2]), roll = asin(R[2][0]), degrees,
    //       azimuth wrapped to [0, 360). Fusion's mat33 is column-major and maps
    //       world -> device (GravitySensor.cpp reads gravity as R[2]), so its
    //       R[a][b] is element a*3+b of our row-major device->world matrix.
    //   GYROSCOPE = the body angular velocity of the slew, zero once settled. With
    //       the device rotated by Rx(-pitch) then Rz(-roll) in its own frame,
    //       omega = (-pitch' cos roll, -pitch' sin roll, -roll') in screen axes.
    //
    // Screen axes become DEVICE axes by the display rotation, the same turn
    // macapkFillPose applies (gate MACAPK_SENSOR_POSE_ROTATE).
    // Gate: debug/tilt_gate.sh.
    private static SensorEvent macapkTiltEvent(int type, long nowNanos) {
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_GRAVITY:
            case Sensor.TYPE_LINEAR_ACCELERATION:
            case Sensor.TYPE_GYROSCOPE:
            case Sensor.TYPE_ORIENTATION:
            case Sensor.TYPE_ROTATION_VECTOR:
            case Sensor.TYPE_GAME_ROTATION_VECTOR:
                break;
            default:
                return null;
        }
        float[] st = new float[4];
        com.macapk.loader.TiltControl.sample(nowNanos, st);
        double roll = Math.toRadians(st[0]), pitch = Math.toRadians(st[1]);
        double cr = Math.cos(roll), sr = Math.sin(roll);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        int rot = POSE_ROTATE ? macapkPoseRotation() : android.view.Surface.ROTATION_0;
        SensorEvent ev;
        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:
            case Sensor.TYPE_GRAVITY: {
                ev = SensorEvent.macapkNew(3);
                macapkTilt(cr, sr, cp, sp, POSE_ACCEL_X, POSE_ACCEL_Y, POSE_ACCEL_Z, rot, ev.values, 0);
                break;
            }
            case Sensor.TYPE_LINEAR_ACCELERATION:
                ev = SensorEvent.macapkNew(3);
                ev.values[0] = 0f; ev.values[1] = 0f; ev.values[2] = 0f;
                break;
            case Sensor.TYPE_GYROSCOPE: {
                ev = SensorEvent.macapkNew(3);
                double rd = Math.toRadians(st[2]), pd = Math.toRadians(st[3]);
                // A zero rate must read +0.0, as the at-rest gyro does.
                macapkToDevice(0.0 - pd * cr, 0.0 - pd * sr, 0.0 - rd, rot, ev.values, 0);
                break;
            }
            default: {
                float[] r = new float[9];
                macapkTiltRotationMatrix(cr, sr, cp, sp, rot, r);
                if (type == Sensor.TYPE_ORIENTATION) {
                    ev = SensorEvent.macapkNew(3);
                    final float rad2deg = (float) (180 / Math.PI);
                    float az = (float) Math.atan2(-r[3], r[0]) * rad2deg;
                    if (az < 0) az += 360;
                    ev.values[0] = az;
                    ev.values[1] = (float) Math.atan2(-r[7], r[8]) * rad2deg;
                    ev.values[2] = (float) Math.asin(r[6]) * rad2deg;
                } else {
                    ev = SensorEvent.macapkNew(5);
                    macapkQuaternionFromMatrix(r, ev.values);
                    ev.values[4] = 0f;   // heading accuracy: as the at-rest stream reports
                }
                break;
            }
        }
        return ev;   // makeEvent fills sensor / accuracy / timestamp
    }

    /** out[off..off+2] = T * (x, y, z) in DEVICE axes, T = Rz(roll) * Rx(pitch). */
    private static void macapkTilt(double cr, double sr, double cp, double sp,
                                   double x, double y, double z, int rot, float[] out, int off) {
        // Rx(pitch): y' = y cp - z sp, z' = y sp + z cp
        double y1 = y * cp - z * sp, z1 = y * sp + z * cp;
        // Rz(roll): x' = x cr - y sr, y' = x sr + y cr
        double x2 = x * cr - y1 * sr, y2 = x * sr + y1 * cr;
        macapkToDevice(x2, y2, z1, rot, out, off);
    }

    /** Screen axes -> device axes for the display rotation, macapkFillPose's
     *  turn (0f - v keeps a zero component +0.0). */
    private static void macapkToDevice(double sx, double sy, double sz, int rot, float[] out, int off) {
        float fx = (float) sx, fy = (float) sy;
        float dx = fx, dy = fy;
        switch (rot) {
            case android.view.Surface.ROTATION_90:  dx = fy;      dy = 0f - fx; break;
            case android.view.Surface.ROTATION_180: dx = 0f - fx; dy = 0f - fy; break;
            case android.view.Surface.ROTATION_270: dx = 0f - fy; dy = fx;      break;
            default: break;
        }
        out[off] = dx;
        out[off + 1] = dy;
        out[off + 2] = (float) sz;
    }

    /** Row-major device->world matrix for the tilted pose: rows are the world
     *  East, North and Up axes expressed in device coordinates, the layout
     *  getRotationMatrix fills. Up is the at-rest gravity reaction, North the
     *  horizontal direction the back of the screen faces at rest, East = N x U. */
    private static void macapkTiltRotationMatrix(double cr, double sr, double cp, double sp,
                                                 int rot, float[] r) {
        double gx = POSE_ACCEL_X, gy = POSE_ACCEL_Y, gz = POSE_ACCEL_Z;
        double gn = Math.sqrt(gx * gx + gy * gy + gz * gz);
        double ux = gx / gn, uy = gy / gn, uz = gz / gn;
        // North: the back of the device, (0, 0, -1), minus its Up component.
        double d = -uz;
        double nx = 0 - d * ux, ny = 0 - d * uy, nz = -1 - d * uz;
        double nn = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= nn; ny /= nn; nz /= nn;
        // East = North x Up.
        double ex = ny * uz - nz * uy, ey = nz * ux - nx * uz, ez = nx * uy - ny * ux;
        macapkTilt(cr, sr, cp, sp, ex, ey, ez, rot, r, 0);
        macapkTilt(cr, sr, cp, sp, nx, ny, nz, rot, r, 3);
        macapkTilt(cr, sr, cp, sp, ux, uy, uz, rot, r, 6);
    }

    /** The inverse of getRotationMatrixFromVector (below, AOSP verbatim): the
     *  unit quaternion (x, y, z, w) whose matrix is r, with w >= 0 (the rotation
     *  vector's values[3] is cos(theta/2)). Shepperd's method, branching on the
     *  largest diagonal term so no square root is taken of a small number. */
    private static void macapkQuaternionFromMatrix(float[] r, float[] q) {
        double m00 = r[0], m01 = r[1], m02 = r[2];
        double m10 = r[3], m11 = r[4], m12 = r[5];
        double m20 = r[6], m21 = r[7], m22 = r[8];
        double tr = m00 + m11 + m22, x, y, z, w;
        if (tr > 0) {
            double s = Math.sqrt(tr + 1.0) * 2;
            w = 0.25 * s; x = (m21 - m12) / s; y = (m02 - m20) / s; z = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2;
            w = (m21 - m12) / s; x = 0.25 * s; y = (m01 + m10) / s; z = (m02 + m20) / s;
        } else if (m11 > m22) {
            double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2;
            w = (m02 - m20) / s; x = (m01 + m10) / s; y = 0.25 * s; z = (m12 + m21) / s;
        } else {
            double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2;
            w = (m10 - m01) / s; x = (m02 + m20) / s; y = (m12 + m21) / s; z = 0.25 * s;
        }
        double n = Math.sqrt(x * x + y * y + z * z + w * w);
        if (w < 0) n = -n;
        q[0] = (float) (x / n); q[1] = (float) (y / n); q[2] = (float) (z / n); q[3] = (float) (w / n);
    }

    // --- TriggerEventListener (one-shot sensors) -------------------------
    public boolean requestTriggerSensor(TriggerEventListener listener, Sensor sensor) {
        // Mac.apk's flat-and-still device never satisfies a significant-motion
        // trigger, so the request is accepted but never fires. Honest no-op.
        return listener != null && sensor != null;
    }

    public boolean cancelTriggerSensor(TriggerEventListener listener, Sensor sensor) {
        return listener != null && sensor != null;
    }

    /** AOSP: onFlushCompleted "is called after all the events in the batch have
     *  been delivered"; returns "false if no sensor is previously registered for
     *  this listener". We returned true for a never-registered listener and NEVER
     *  fired the callback, so an app using the standard batch-drain idiom (flush,
     *  then block until onFlushCompleted) hung.
     *
     *  Our FIFO is size 0, so there is nothing to drain and the flush completes
     *  immediately -- the same reasoning LocationManager.requestFlush already
     *  uses one package over. */
    public boolean flush(SensorEventListener listener) {
        if (listener == null) return false;
        boolean any = false;
        final boolean l2able = listener instanceof SensorEventListener2;
        // mRegs is copy-on-write, so iterating it needs no lock and a concurrent
        // unregister cannot break this walk.
        for (final Registration reg : mRegs) {
            if (reg.listener != listener) continue;
            any = true;
            if (!l2able) continue;
            final SensorEventListener2 l2 = (SensorEventListener2) listener;
            final Sensor sn = reg.sensor;
            Runnable task = new Runnable() { @Override public void run() {
                try { l2.onFlushCompleted(sn); } catch (Throwable ignored) { }
            } };
            // Deliver on the handler this listener registered with, matching the
            // event path above -- not on whatever thread called flush().
            try { reg.handler.post(task); } catch (Throwable t) { task.run(); }
        }
        return any;   // AOSP: false when nothing was registered for this listener
    }

    // === Static math utilities (verbatim AOSP frameworks/base) ===========

    public static boolean getRotationMatrix(float[] R, float[] I,
            float[] gravity, float[] geomagnetic) {
        float Ax = gravity[0];
        float Ay = gravity[1];
        float Az = gravity[2];

        final float normsqA = (Ax * Ax + Ay * Ay + Az * Az);
        final float g = 9.81f;
        final float freeFallGravitySquared = 0.01f * g * g;
        if (normsqA < freeFallGravitySquared) {
            // gravity less than 10% of normal value
            return false;
        }

        final float Ex = geomagnetic[0];
        final float Ey = geomagnetic[1];
        final float Ez = geomagnetic[2];
        float Hx = Ey * Az - Ez * Ay;
        float Hy = Ez * Ax - Ex * Az;
        float Hz = Ex * Ay - Ey * Ax;
        final float normH = (float) Math.sqrt(Hx * Hx + Hy * Hy + Hz * Hz);

        if (normH < 0.1f) {
            // device is close to free fall (or in space?), or close to
            // magnetic north pole. Typical values are  > 100.
            return false;
        }
        final float invH = 1.0f / normH;
        Hx *= invH;
        Hy *= invH;
        Hz *= invH;
        final float invA = 1.0f / (float) Math.sqrt(Ax * Ax + Ay * Ay + Az * Az);
        Ax *= invA;
        Ay *= invA;
        Az *= invA;
        final float Mx = Ay * Hz - Az * Hy;
        final float My = Az * Hx - Ax * Hz;
        final float Mz = Ax * Hy - Ay * Hx;
        if (R != null) {
            if (R.length == 9) {
                R[0] = Hx;     R[1] = Hy;     R[2] = Hz;
                R[3] = Mx;     R[4] = My;     R[5] = Mz;
                R[6] = Ax;     R[7] = Ay;     R[8] = Az;
            } else if (R.length == 16) {
                R[0]  = Hx;    R[1]  = Hy;    R[2]  = Hz;   R[3]  = 0;
                R[4]  = Mx;    R[5]  = My;    R[6]  = Mz;   R[7]  = 0;
                R[8]  = Ax;    R[9]  = Ay;    R[10] = Az;   R[11] = 0;
                R[12] = 0;     R[13] = 0;     R[14] = 0;    R[15] = 1;
            }
        }
        if (I != null) {
            // compute the inclination matrix by projecting the geomagnetic
            // vector onto the Z (gravity) and X (horizontal component
            // of geomagnetic vector) axes.
            final float invE = 1.0f / (float) Math.sqrt(Ex * Ex + Ey * Ey + Ez * Ez);
            final float c = (Ex * Mx + Ey * My + Ez * Mz) * invE;
            final float s = (Ex * Ax + Ey * Ay + Ez * Az) * invE;
            if (I.length == 9) {
                I[0] = 1;     I[1] = 0;     I[2] = 0;
                I[3] = 0;     I[4] = c;     I[5] = s;
                I[6] = 0;     I[7] = -s;     I[8] = c;
            } else if (I.length == 16) {
                I[0] = 1;     I[1] = 0;     I[2] = 0;
                I[4] = 0;     I[5] = c;     I[6] = s;
                I[8] = 0;     I[9] = -s;     I[10] = c;
                I[3] = I[7] = I[11] = I[12] = I[13] = I[14] = 0;
                I[15] = 1;
            }
        }
        return true;
    }

    public static float getInclination(float[] I) {
        if (I.length == 9) {
            return (float) Math.atan2(I[5], I[4]);
        } else {
            return (float) Math.atan2(I[6], I[5]);
        }
    }

    public static boolean remapCoordinateSystem(float[] inR, int X, int Y, float[] outR) {
        if (inR == outR) {
            final float[] temp = sTempMatrix;
            synchronized (temp) {
                // we don't expect to have a lot of contention
                if (remapCoordinateSystemImpl(inR, X, Y, temp)) {
                    final int size = outR.length;
                    for (int i = 0; i < size; i++) {
                        outR[i] = temp[i];
                    }
                    return true;
                }
            }
        }
        return remapCoordinateSystemImpl(inR, X, Y, outR);
    }

    private static boolean remapCoordinateSystemImpl(float[] inR, int X, int Y, float[] outR) {
        final int length = outR.length;
        if (inR.length != length) {
            return false;   // invalid parameter
        }
        if ((X & 0x7C) != 0 || (Y & 0x7C) != 0) {
            return false;   // invalid parameter
        }
        if (((X & 0x3) == 0) || ((Y & 0x3) == 0)) {
            return false;   // no axis specified
        }
        if ((X & 0x3) == (Y & 0x3)) {
            return false;   // same axis specified
        }

        // Z is "the other" axis, its sign is either +/- sign(X)*sign(Y)
        // this can be calculated by exclusive-or'ing X and Y; except for
        // the sign inversion (+/-) which is calculated below.
        int Z = X ^ Y;

        // extract the axis (remove the sign), offset in the range 0 to 2.
        final int x = (X & 0x3) - 1;
        final int y = (Y & 0x3) - 1;
        final int z = (Z & 0x3) - 1;

        // compute the sign of Z (whether it needs to be inverted)
        final int axis_y = (z + 1) % 3;
        final int axis_z = (z + 2) % 3;
        if (((x ^ axis_y) | (y ^ axis_z)) != 0) {
            Z ^= 0x80;
        }

        final boolean sx = (X >= 0x80);
        final boolean sy = (Y >= 0x80);
        final boolean sz = (Z >= 0x80);

        // Perform R * r, in avoiding actual muls and adds.
        final int rowLength = ((length == 16) ? 4 : 3);
        for (int j = 0; j < 3; j++) {
            final int offset = j * rowLength;
            for (int i = 0; i < 3; i++) {
                if (x == i)   outR[offset + i] = sx ? -inR[offset + 0] : inR[offset + 0];
                if (y == i)   outR[offset + i] = sy ? -inR[offset + 1] : inR[offset + 1];
                if (z == i)   outR[offset + i] = sz ? -inR[offset + 2] : inR[offset + 2];
            }
        }
        if (length == 16) {
            outR[3] = outR[7] = outR[11] = outR[12] = outR[13] = outR[14] = 0;
            outR[15] = 1;
        }
        return true;
    }

    public static float[] getOrientation(float[] R, float[] values) {
        /*
         * 4x4 (length=16) case:
         *   /  R[ 0]   R[ 1]   R[ 2]   0  \
         *   |  R[ 4]   R[ 5]   R[ 6]   0  |
         *   |  R[ 8]   R[ 9]   R[10]   0  |
         *   \      0       0       0   1  /
         *
         * 3x3 (length=9) case:
         *   /  R[ 0]   R[ 1]   R[ 2]  \
         *   |  R[ 3]   R[ 4]   R[ 5]  |
         *   \  R[ 6]   R[ 7]   R[ 8]  /
         */
        if (R.length == 9) {
            values[0] = (float) Math.atan2(R[1], R[4]);
            values[1] = (float) Math.asin(-R[7]);
            values[2] = (float) Math.atan2(-R[6], R[8]);
        } else {
            values[0] = (float) Math.atan2(R[1], R[5]);
            values[1] = (float) Math.asin(-R[9]);
            values[2] = (float) Math.atan2(-R[8], R[10]);
        }

        return values;
    }

    public static float getAltitude(float p0, float p) {
        final float coef = 1.0f / 5.255f;
        return 44330.0f * (1.0f - (float) Math.pow(p / p0, coef));
    }

    public static void getAngleChange(float[] angleChange, float[] R, float[] prevR) {
        float rd1 = 0, rd4 = 0, rd6 = 0, rd7 = 0, rd8 = 0;
        float ri0 = 0, ri1 = 0, ri2 = 0, ri3 = 0, ri4 = 0, ri5 = 0, ri6 = 0, ri7 = 0, ri8 = 0;
        float pri0 = 0, pri1 = 0, pri2 = 0, pri3 = 0, pri4 = 0;
        float pri5 = 0, pri6 = 0, pri7 = 0, pri8 = 0;

        if (R.length == 9) {
            ri0 = R[0];
            ri1 = R[1];
            ri2 = R[2];
            ri3 = R[3];
            ri4 = R[4];
            ri5 = R[5];
            ri6 = R[6];
            ri7 = R[7];
            ri8 = R[8];
        } else if (R.length == 16) {
            ri0 = R[0];
            ri1 = R[1];
            ri2 = R[2];
            ri3 = R[4];
            ri4 = R[5];
            ri5 = R[6];
            ri6 = R[8];
            ri7 = R[9];
            ri8 = R[10];
        }

        if (prevR.length == 9) {
            pri0 = prevR[0];
            pri1 = prevR[1];
            pri2 = prevR[2];
            pri3 = prevR[3];
            pri4 = prevR[4];
            pri5 = prevR[5];
            pri6 = prevR[6];
            pri7 = prevR[7];
            pri8 = prevR[8];
        } else if (prevR.length == 16) {
            pri0 = prevR[0];
            pri1 = prevR[1];
            pri2 = prevR[2];
            pri3 = prevR[4];
            pri4 = prevR[5];
            pri5 = prevR[6];
            pri6 = prevR[8];
            pri7 = prevR[9];
            pri8 = prevR[10];
        }

        // calculate the parts of the rotation difference matrix we need
        // rd[i][j] = pri[0][i] * ri[0][j] + pri[1][i] * ri[1][j] + pri[2][i] * ri[2][j];

        rd1 = pri0 * ri1 + pri3 * ri4 + pri6 * ri7; // rd[0][1]
        rd4 = pri1 * ri1 + pri4 * ri4 + pri7 * ri7; // rd[1][1]
        rd6 = pri2 * ri0 + pri5 * ri3 + pri8 * ri6; // rd[2][0]
        rd7 = pri2 * ri1 + pri5 * ri4 + pri8 * ri7; // rd[2][1]
        rd8 = pri2 * ri2 + pri5 * ri5 + pri8 * ri8; // rd[2][2]

        angleChange[0] = (float) Math.atan2(rd1, rd4);
        angleChange[1] = (float) Math.asin(-rd7);
        angleChange[2] = (float) Math.atan2(-rd6, rd8);
    }

    public static void getRotationMatrixFromVector(float[] R, float[] rotationVector) {

        float q0;
        float q1 = rotationVector[0];
        float q2 = rotationVector[1];
        float q3 = rotationVector[2];

        if (rotationVector.length >= 4) {
            q0 = rotationVector[3];
        } else {
            q0 = 1 - q1 * q1 - q2 * q2 - q3 * q3;
            q0 = (q0 > 0) ? (float) Math.sqrt(q0) : 0;
        }

        float sq_q1 = 2 * q1 * q1;
        float sq_q2 = 2 * q2 * q2;
        float sq_q3 = 2 * q3 * q3;
        float q1_q2 = 2 * q1 * q2;
        float q3_q0 = 2 * q3 * q0;
        float q1_q3 = 2 * q1 * q3;
        float q2_q0 = 2 * q2 * q0;
        float q2_q3 = 2 * q2 * q3;
        float q1_q0 = 2 * q1 * q0;

        if (R.length == 9) {
            R[0] = 1 - sq_q2 - sq_q3;
            R[1] = q1_q2 - q3_q0;
            R[2] = q1_q3 + q2_q0;

            R[3] = q1_q2 + q3_q0;
            R[4] = 1 - sq_q1 - sq_q3;
            R[5] = q2_q3 - q1_q0;

            R[6] = q1_q3 - q2_q0;
            R[7] = q2_q3 + q1_q0;
            R[8] = 1 - sq_q1 - sq_q2;
        } else if (R.length == 16) {
            R[0] = 1 - sq_q2 - sq_q3;
            R[1] = q1_q2 - q3_q0;
            R[2] = q1_q3 + q2_q0;
            R[3] = 0.0f;

            R[4] = q1_q2 + q3_q0;
            R[5] = 1 - sq_q1 - sq_q3;
            R[6] = q2_q3 - q1_q0;
            R[7] = 0.0f;

            R[8] = q1_q3 - q2_q0;
            R[9] = q2_q3 + q1_q0;
            R[10] = 1 - sq_q1 - sq_q2;
            R[11] = 0.0f;

            R[12] = R[13] = R[14] = 0.0f;
            R[15] = 1.0f;
        }
    }

    public static void getQuaternionFromVector(float[] Q, float[] rv) {
        if (rv.length >= 4) {
            Q[0] = rv[3];
        } else {
            Q[0] = 1 - rv[0] * rv[0] - rv[1] * rv[1] - rv[2] * rv[2];
            Q[0] = (Q[0] > 0) ? (float) Math.sqrt(Q[0]) : 0;
        }
        Q[1] = rv[0];
        Q[2] = rv[1];
        Q[3] = rv[2];
    }
}
