// android.hardware.Sensor -- describes a single hardware sensor.
// Mirrors the AOSP public type-constant and getter surface. Mac.apk
// has no real sensor hardware, so SensorManager synthesizes a small
// set of "device flat / no motion" sensors (accelerometer reading the
// gravity vector, etc.) and hands out Sensor instances describing
// them. The constructor stays package-private (as in AOSP) -- only
// SensorManager mints them.
package android.hardware;

public final class Sensor {
    // Full AOSP TYPE_* set (raw github aosp-mirror Sensor.java).
    public static final int TYPE_ACCELEROMETER = 1;
    public static final int TYPE_MAGNETIC_FIELD = 2;
    public static final int TYPE_ORIENTATION = 3;
    public static final int TYPE_GYROSCOPE = 4;
    public static final int TYPE_LIGHT = 5;
    public static final int TYPE_PRESSURE = 6;
    public static final int TYPE_TEMPERATURE = 7;
    public static final int TYPE_PROXIMITY = 8;
    public static final int TYPE_GRAVITY = 9;
    public static final int TYPE_LINEAR_ACCELERATION = 10;
    public static final int TYPE_ROTATION_VECTOR = 11;
    public static final int TYPE_RELATIVE_HUMIDITY = 12;
    public static final int TYPE_AMBIENT_TEMPERATURE = 13;
    public static final int TYPE_MAGNETIC_FIELD_UNCALIBRATED = 14;
    public static final int TYPE_GAME_ROTATION_VECTOR = 15;
    public static final int TYPE_GYROSCOPE_UNCALIBRATED = 16;
    public static final int TYPE_SIGNIFICANT_MOTION = 17;
    public static final int TYPE_STEP_DETECTOR = 18;
    public static final int TYPE_STEP_COUNTER = 19;
    public static final int TYPE_GEOMAGNETIC_ROTATION_VECTOR = 20;
    public static final int TYPE_HEART_RATE = 21;
    public static final int TYPE_TILT_DETECTOR = 22;
    public static final int TYPE_WAKE_GESTURE = 23;
    public static final int TYPE_GLANCE_GESTURE = 24;
    public static final int TYPE_PICK_UP_GESTURE = 25;
    public static final int TYPE_WRIST_TILT_GESTURE = 26;
    public static final int TYPE_DEVICE_ORIENTATION = 27;
    public static final int TYPE_POSE_6DOF = 28;
    public static final int TYPE_STATIONARY_DETECT = 29;
    public static final int TYPE_MOTION_DETECT = 30;
    public static final int TYPE_HEART_BEAT = 31;
    public static final int TYPE_DYNAMIC_SENSOR_META = 32;
    public static final int TYPE_LOW_LATENCY_OFFBODY_DETECT = 34;
    public static final int TYPE_ACCELEROMETER_UNCALIBRATED = 35;
    public static final int TYPE_HINGE_ANGLE = 36;
    public static final int TYPE_HEAD_TRACKER = 37;
    public static final int TYPE_ACCELEROMETER_LIMITED_AXES = 38;
    public static final int TYPE_GYROSCOPE_LIMITED_AXES = 39;
    public static final int TYPE_ACCELEROMETER_LIMITED_AXES_UNCALIBRATED = 40;
    public static final int TYPE_GYROSCOPE_LIMITED_AXES_UNCALIBRATED = 41;
    public static final int TYPE_HEADING = 42;
    public static final int TYPE_ALL = -1;
    public static final int TYPE_DEVICE_PRIVATE_BASE = 0x10000;

    public static final String STRING_TYPE_ACCELEROMETER = "android.sensor.accelerometer";
    public static final String STRING_TYPE_MAGNETIC_FIELD = "android.sensor.magnetic_field";
    public static final String STRING_TYPE_ORIENTATION = "android.sensor.orientation";
    public static final String STRING_TYPE_GYROSCOPE = "android.sensor.gyroscope";
    public static final String STRING_TYPE_LIGHT = "android.sensor.light";
    public static final String STRING_TYPE_PRESSURE = "android.sensor.pressure";
    public static final String STRING_TYPE_TEMPERATURE = "android.sensor.temperature";
    public static final String STRING_TYPE_PROXIMITY = "android.sensor.proximity";
    public static final String STRING_TYPE_GRAVITY = "android.sensor.gravity";
    public static final String STRING_TYPE_LINEAR_ACCELERATION = "android.sensor.linear_acceleration";
    public static final String STRING_TYPE_ROTATION_VECTOR = "android.sensor.rotation_vector";
    public static final String STRING_TYPE_RELATIVE_HUMIDITY = "android.sensor.relative_humidity";
    public static final String STRING_TYPE_AMBIENT_TEMPERATURE = "android.sensor.ambient_temperature";
    public static final String STRING_TYPE_MAGNETIC_FIELD_UNCALIBRATED = "android.sensor.magnetic_field_uncalibrated";
    public static final String STRING_TYPE_GAME_ROTATION_VECTOR = "android.sensor.game_rotation_vector";
    public static final String STRING_TYPE_GYROSCOPE_UNCALIBRATED = "android.sensor.gyroscope_uncalibrated";
    public static final String STRING_TYPE_SIGNIFICANT_MOTION = "android.sensor.significant_motion";
    public static final String STRING_TYPE_STEP_DETECTOR = "android.sensor.step_detector";
    public static final String STRING_TYPE_STEP_COUNTER = "android.sensor.step_counter";
    public static final String STRING_TYPE_GEOMAGNETIC_ROTATION_VECTOR = "android.sensor.geomagnetic_rotation_vector";
    public static final String STRING_TYPE_HEART_RATE = "android.sensor.heart_rate";
    public static final String STRING_TYPE_TILT_DETECTOR = "android.sensor.tilt_detector";
    public static final String STRING_TYPE_WAKE_GESTURE = "android.sensor.wake_gesture";
    public static final String STRING_TYPE_GLANCE_GESTURE = "android.sensor.glance_gesture";
    public static final String STRING_TYPE_PICK_UP_GESTURE = "android.sensor.pick_up_gesture";
    public static final String STRING_TYPE_WRIST_TILT_GESTURE = "android.sensor.wrist_tilt_gesture";
    public static final String STRING_TYPE_DEVICE_ORIENTATION = "android.sensor.device_orientation";
    public static final String STRING_TYPE_POSE_6DOF = "android.sensor.pose_6dof";
    public static final String STRING_TYPE_STATIONARY_DETECT = "android.sensor.stationary_detect";
    public static final String STRING_TYPE_MOTION_DETECT = "android.sensor.motion_detect";
    public static final String STRING_TYPE_HEART_BEAT = "android.sensor.heart_beat";
    public static final String STRING_TYPE_DYNAMIC_SENSOR_META = "android.sensor.dynamic_sensor_meta";
    public static final String STRING_TYPE_LOW_LATENCY_OFFBODY_DETECT = "android.sensor.low_latency_offbody_detect";
    public static final String STRING_TYPE_ACCELEROMETER_UNCALIBRATED = "android.sensor.accelerometer_uncalibrated";
    public static final String STRING_TYPE_HINGE_ANGLE = "android.sensor.hinge_angle";
    public static final String STRING_TYPE_HEAD_TRACKER = "android.sensor.head_tracker";
    public static final String STRING_TYPE_ACCELEROMETER_LIMITED_AXES = "android.sensor.accelerometer_limited_axes";
    public static final String STRING_TYPE_GYROSCOPE_LIMITED_AXES = "android.sensor.gyroscope_limited_axes";
    public static final String STRING_TYPE_ACCELEROMETER_LIMITED_AXES_UNCALIBRATED = "android.sensor.accelerometer_limited_axes_uncalibrated";
    public static final String STRING_TYPE_GYROSCOPE_LIMITED_AXES_UNCALIBRATED = "android.sensor.gyroscope_limited_axes_uncalibrated";
    public static final String STRING_TYPE_HEADING = "android.sensor.heading";

    public static final int REPORTING_MODE_CONTINUOUS = 0;
    public static final int REPORTING_MODE_ON_CHANGE = 1;
    public static final int REPORTING_MODE_ONE_SHOT = 2;
    public static final int REPORTING_MODE_SPECIAL_TRIGGER = 3;

    private final int type;
    private final String name;
    private final String stringType;
    private final float maxRange;
    private final float resolution;
    private final float power;
    private final int minDelayUs;
    private final int reportingMode;

    Sensor(int type, String name, String stringType, float maxRange,
           float resolution, float power, int minDelayUs, int reportingMode) {
        this.type = type;
        this.name = name;
        this.stringType = stringType;
        this.maxRange = maxRange;
        this.resolution = resolution;
        this.power = power;
        this.minDelayUs = minDelayUs;
        this.reportingMode = reportingMode;
    }

    public int    getType()         { return type; }
    public String getName()         { return name; }
    public String getVendor()       { return "Mac.apk"; }
    public int    getVersion()      { return 1; }
    public String getStringType()   { return stringType; }
    public float  getMaximumRange() { return maxRange; }
    public float  getResolution()   { return resolution; }
    public float  getPower()        { return power; }
    public int    getMinDelay()     { return minDelayUs; }
    // AOSP getMaxDelay default is 0 (no upper bound) for continuous-mode
    // sensors; on-change/one-shot/trigger report 0 as well.
    public int    getMaxDelay()     { return 0; }
    public int    getReportingMode() { return reportingMode; }
    public int    getFifoReservedEventCount() { return 0; }
    public int    getFifoMaxEventCount()      { return 0; }
    public int    getId()           { return -1; }
    public boolean isWakeUpSensor() { return false; }
    public boolean isDynamicSensor() { return false; }
    public boolean isAdditionalInfoSupported() { return false; }
    public int     getHighestDirectReportRateLevel() { return 0; }
    public boolean isDirectChannelTypeSupported(int sharedMemType) { return false; }

    @Override public String toString() {
        return "{Sensor name=\"" + name + "\", type=" + type + ", maxRange=" + maxRange
            + ", resolution=" + resolution + ", power=" + power + ", minDelay=" + minDelayUs + "}";
    }
}
