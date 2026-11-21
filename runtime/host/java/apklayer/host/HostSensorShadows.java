package apklayer.host;

import android.os.MessageQueue;
import java.lang.ref.WeakReference;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * The framework's SensorManager (SystemSensorManager and its JNI in libandroid_runtime) over the stack's SensorService and sensors HAL, used
 * instead of Robolectric's in-memory fake when this stack declares the sensors HAL (providers.toml: HostShadowProvider maps these then).
 * Events reach a listener the device's way: the JNI receiver watches the event channel on its looper thread's native Looper, which that
 * thread's queue polls (ShadowHostMessageQueue).
 */
final class HostSensorShadows {
    private HostSensorShadows() {}

    /** Whether this stack runs SensorService over a sensors HAL: cli/stack.cpp declares the HAL (providers.toml) and starts SensorService when its
     *  library is staged. Both, or libsensor would wait 300 s for a "sensorservice" that never comes. */
    static boolean declared() {
        String root = System.getenv("APKLAYER_INSTANCE_ROOT");
        return root != null && new java.io.File(root, "vendor/etc/vintf/manifest/apklayer-sensors.xml").exists()
                && new java.io.File(System.getProperty("apklayer.androidlibs", ""), "libapklayer_sensorservice.so").exists();
    }

    @Implements(className = "android.hardware.SensorManager", isInAndroidSdk = false) public static final class SensorManager {}
    @Implements(className = "android.hardware.SystemSensorManager", callNativeMethodsByDefault = true, isInAndroidSdk = false)
    public static final class SystemSensorManager {}

    @Implements(className = "android.hardware.SystemSensorManager$BaseEventQueue", callNativeMethodsByDefault = true, isInAndroidSdk = false)
    public static final class BaseEventQueue {
        private static final Class<?>[] INIT = {long.class, WeakReference.class, MessageQueue.class, String.class, int.class, String.class, String.class};

        /** The JNI reads msgQ.mPtr as a NativeMessageQueue: a real one, on the queue's own thread (NativeQueueCarrier). */
        @Implementation protected static long nativeInitBaseEventQueue(long nativeManager, WeakReference<?> eventQWeak, MessageQueue msgQ, String packageName,
                int mode, String opPackageName, String attributionTag) throws Exception {
            Class<?> owner = Class.forName("android.hardware.SystemSensorManager$BaseEventQueue");
            return NativeQueueCarrier.call(msgQ, carrier -> NativeBinding.<Long>call(owner, "nativeInitBaseEventQueue", INIT, nativeManager, eventQWeak, carrier,
                    packageName, mode, opPackageName, attributionTag));
        }
    }
}
