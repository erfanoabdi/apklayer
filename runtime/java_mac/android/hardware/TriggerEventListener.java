// android.hardware.TriggerEventListener -- callback for one-shot
// (significant-motion class) sensors registered via
// SensorManager.requestTriggerSensor. Abstract class in AOSP.
package android.hardware;

public abstract class TriggerEventListener {
    public abstract void onTrigger(TriggerEvent event);
}
