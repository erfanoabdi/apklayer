// android.hardware.SensorListener -- the ORIGINAL (API 1) sensor callback,
// deprecated since API 3 in favour of SensorEventListener but still shipped by
// a great many older apps, which is exactly why it has to exist: an app whose
// only sensor path is this interface cannot even LINK without it, so it fails
// at class-load time rather than falling back to anything.
//
// Mirrors frameworks/base/core/java/android/hardware/SensorListener.java
// (android16-release). Values arrive in the legacy per-sensor layout and the
// legacy coordinate convention; see LegacySensorManager, which is what
// translates a modern SensorEvent into this shape.
package android.hardware;

@Deprecated
public interface SensorListener {
    /** `sensor` is a SensorManager.SENSOR_* BITMASK value, not a Sensor.TYPE_*. */
    public void onSensorChanged(int sensor, float[] values);

    public void onAccuracyChanged(int sensor, int accuracy);
}
