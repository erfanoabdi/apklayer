// android.hardware.SensorEvent -- data carrier handed to
// SensorEventListener.onSensorChanged. Public fields match AOSP
// (sensor, accuracy, timestamp, values). The values[] length depends
// on the sensor type; AOSP sizes it at construction. The constructor
// is package-private in AOSP, so SensorManager uses macapkNew to mint
// events with a type-appropriate values[] length.
package android.hardware;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class SensorEvent {
    public Sensor sensor;
    public long timestamp;
    public int accuracy;
    public final float[] values;

    /** AOSP SensorEvent.java:853 -- "Set to true when this is the first sensor
     *  event after a discontinuity." AOSP's own javadoc says the concept is
     *  "either not relevant to or not supported by most sensor types",
     *  TYPE_HEAD_TRACKER being the notable exception, so a device that reports
     *  no head tracker leaves this false on every event -- which is what the
     *  default already is. The field has to EXIST regardless: an app that
     *  reads it off a plain accelerometer event (a defensive `if
     *  (event.firstEventAfterDiscontinuity) resetFilter()`) gets a
     *  NoSuchFieldError otherwise, and a field access is not something an app
     *  can guard behind an SDK_INT check the way it guards a method call. */
    public boolean firstEventAfterDiscontinuity;

    SensorEvent(int valueSize) { this.values = new float[valueSize]; }
    public SensorEvent() { this(16); }

    /** Loader/SensorManager factory; constructor is package-private in AOSP. */
    public static SensorEvent macapkNew(int valueSize) { return new SensorEvent(valueSize); }
}
