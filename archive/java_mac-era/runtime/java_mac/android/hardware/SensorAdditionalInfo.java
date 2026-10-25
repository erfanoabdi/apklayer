// android.hardware.SensorAdditionalInfo -- metadata frame some sensors
// emit between data frames. Public field shape + TYPE_* constants match
// AOSP. Mac.apk never emits these (synthetic sensors carry no extra
// info) but the type must exist for SensorEventCallback to reference it.
package android.hardware;

public class SensorAdditionalInfo {
    public final Sensor sensor;
    public final int type;
    public final int serial;
    public final int[] intValues;
    public final float[] floatValues;

    public static final int TYPE_FRAME_BEGIN = 0;
    public static final int TYPE_FRAME_END = 1;
    public static final int TYPE_UNTRACKED_DELAY = 0x10000;
    public static final int TYPE_INTERNAL_TEMPERATURE = 0x10001;
    public static final int TYPE_VEC3_CALIBRATION = 0x10002;
    public static final int TYPE_SENSOR_PLACEMENT = 0x10003;
    public static final int TYPE_SAMPLING = 0x10004;

    SensorAdditionalInfo(Sensor aSensor, int aType, int aSerial,
                         int[] aIntValues, float[] aFloatValues) {
        sensor = aSensor;
        type = aType;
        serial = aSerial;
        intValues = aIntValues;
        floatValues = aFloatValues;
    }
}
