// android.hardware.TriggerEvent -- delivered to a TriggerEventListener
// when a one-shot sensor (e.g. significant-motion) fires. Public field
// shape matches AOSP. Constructor package-private in AOSP.
package android.hardware;

public final class TriggerEvent {
    public float[] values;
    public Sensor sensor;
    public long timestamp;

    TriggerEvent(int size) { values = new float[size]; }

    public static TriggerEvent macapkNew(int size) { return new TriggerEvent(size); }
}
