// android.hardware.SensorEventCallback -- AOSP convenience base that
// implements SensorEventListener2 with empty defaults plus an
// onSensorAdditionalInfo hook. Apps subclass and override only what
// they need.
package android.hardware;

public abstract class SensorEventCallback implements SensorEventListener2 {
    @Override public void onSensorChanged(SensorEvent event) {}
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public void onFlushCompleted(Sensor sensor) {}
    public void onSensorAdditionalInfo(SensorAdditionalInfo info) {}
}
