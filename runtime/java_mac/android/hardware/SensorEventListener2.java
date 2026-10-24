// android.hardware.SensorEventListener2 -- adds onFlushCompleted to
// SensorEventListener. Mirrors AOSP.
package android.hardware;

public interface SensorEventListener2 extends SensorEventListener {
    void onFlushCompleted(Sensor sensor);
}
