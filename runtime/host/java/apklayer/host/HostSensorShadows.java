package apklayer.host;

import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import java.lang.ref.WeakReference;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowLooper;

/**
 * The framework's SensorManager (SystemSensorManager and its JNI in libandroid_runtime) over the stack's SensorService and sensors HAL, used
 * instead of Robolectric's in-memory fake when this stack declares the sensors HAL (providers.toml: HostShadowProvider maps these then).
 * Events reach a listener the device's way: the JNI receiver watches the event channel on its looper thread's native Looper, which that
 * thread's queue polls (ShadowHostMessageQueue).
 */
final class HostSensorShadows {
    private HostSensorShadows() {}

    /** Whether this stack declares the sensors HAL (cli/stack.cpp stages its manifest and runs SensorService only then). */
    static boolean declared() {
        String root = System.getenv("APKLAYER_INSTANCE_ROOT");
        return root != null && new java.io.File(root, "vendor/etc/vintf/manifest/apklayer-sensors.xml").exists();
    }

    @Implements(className = "android.hardware.SensorManager", isInAndroidSdk = false) public static final class SensorManager {}
    @Implements(className = "android.hardware.SystemSensorManager", callNativeMethodsByDefault = true, isInAndroidSdk = false)
    public static final class SystemSensorManager {}

    @Implements(className = "android.hardware.SystemSensorManager$BaseEventQueue", callNativeMethodsByDefault = true, isInAndroidSdk = false)
    public static final class BaseEventQueue {
        private static final Class<?>[] INIT = {long.class, WeakReference.class, MessageQueue.class, String.class, int.class, String.class, String.class};

        /** The JNI reads msgQ.mPtr as a NativeMessageQueue (android_os_MessageQueue.cpp), which Robolectric's queue does not have: it gets a real one,
         *  made on the queue's own thread (whose native Looper it then uses), in a MessageQueue object that only carries the pointer. */
        @Implementation protected static long nativeInitBaseEventQueue(long nativeManager, WeakReference<?> eventQWeak, MessageQueue msgQ, String packageName,
                int mode, String opPackageName, String attributionTag) throws Exception {
            Looper looper = looperOf(msgQ);
            long[] ptr = new long[1];
            Runnable make = () -> ptr[0] = NativeBinding.<Long>call(MessageQueue.class, "nativeInit", new Class<?>[0]);
            if (looper.isCurrentThread()) make.run();
            else if (!new Handler(looper).runWithScissors(make, 5000)) throw new IllegalStateException("the sensor listener's looper does not run");
            java.lang.reflect.Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            MessageQueue carrier = (MessageQueue) unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, MessageQueue.class);
            java.lang.reflect.Field mPtr = MessageQueue.class.getDeclaredField("mPtr");
            mPtr.setAccessible(true);
            mPtr.setLong(carrier, ptr[0]);
            Class<?> owner = Class.forName("android.hardware.SystemSensorManager$BaseEventQueue");
            long queue = NativeBinding.<Long>call(owner, "nativeInitBaseEventQueue", INIT, nativeManager, eventQWeak, carrier, packageName, mode, opPackageName, attributionTag);
            NativeBinding.call(MessageQueue.class, "nativeDestroy", new Class<?>[] {long.class}, ptr[0]);  // nativeInit's reference: the receiver holds its own
            return queue;
        }

        private static Looper looperOf(MessageQueue queue) {
            for (Looper l : ShadowLooper.getAllLoopers()) if (l.getQueue() == queue) return l;
            throw new IllegalStateException("no looper for the sensor listener's queue");
        }
    }
}
