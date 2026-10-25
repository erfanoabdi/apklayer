// android.os.HardwarePropertiesManager
//
// AOSP's hardware-sensor service (CPU/GPU/battery/skin temperatures, fan speeds).
// Apps fetch it via getSystemService(HARDWARE_PROPERTIES_SERVICE). Mac.apk does
// not expose thermal/fan telemetry to the guest, so the temperature and fan
// queries return empty arrays -- the honest "no hardware-property telemetry"
// result. Mirrors
// frameworks/base/core/java/android/os/HardwarePropertiesManager.java.
package android.os;

import android.content.Context;

public class HardwarePropertiesManager {

    public static final int DEVICE_TEMPERATURE_CPU     = 0;
    public static final int DEVICE_TEMPERATURE_GPU     = 1;
    public static final int DEVICE_TEMPERATURE_BATTERY = 2;
    public static final int DEVICE_TEMPERATURE_SKIN    = 3;
    public static final int TEMPERATURE_CURRENT  = 0;
    public static final int TEMPERATURE_THROTTLING = 1;
    public static final int TEMPERATURE_SHUTDOWN = 2;
    public static final int TEMPERATURE_THROTTLING_BELOW_VR_MIN = 3;

    // AOSP: HardwarePropertiesManager.getDeviceTemperatures returns
    // UNDEFINED_TEMPERATURE entries on devices with no sensor; expose the
    // constant so callers can compare against it.
    public static final float UNDEFINED_TEMPERATURE = -Float.MAX_VALUE;

    public HardwarePropertiesManager() {}
    public HardwarePropertiesManager(Context context) {}

    public float[] getDeviceTemperatures(int type, int source) { return new float[0]; }
    // AOSP signature returns CpuUsageInfo[] (was Object[], which would
    // NoSuchMethodError a parent-first-shadowed caller). Now that
    // android.os.CpuUsageInfo exists in tree, return the real type.
    public CpuUsageInfo[] getCpuUsages() { return new CpuUsageInfo[0]; }
    public float[] getFanSpeeds() { return new float[0]; }
}
