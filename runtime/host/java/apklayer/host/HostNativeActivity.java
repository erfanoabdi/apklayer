package apklayer.host;

import android.content.res.AssetManager;
import android.os.MessageQueue;
import java.lang.ref.WeakReference;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * android.app.NativeActivity over the framework's own JNI (android_app_NativeActivity.cpp, registered by bridge/native/runtime_shim.c): every
 * 3DMark benchmark and many NDK games. Before, its natives were unbound: loadNativeCode answered 0 and getDlError null ("Unable to load native
 * library ...: null"). The JNI opens the app's library through libapklayer_nativeloader.so (the native bridge for another CPU's library, whose
 * Berberis wraps ANativeActivity_onCreate) and hooks its work pipe into the main thread's native Looper, which AppHost polls; the queues it
 * reads get a real NativeMessageQueue (NativeQueueCarrier). The InputQueue the activity takes from its window likewise.
 */
final class HostNativeActivity {
    private HostNativeActivity() {}

    /** A subclass of Activity's shadow: Robolectric takes every activity's shadow as a ShadowActivity (apklayer's: ShadowHostActivity). */
    @Implements(value = android.app.NativeActivity.class, callNativeMethodsByDefault = true, isInAndroidSdk = false)
    public static class NativeActivity extends ShadowHostActivity {
        private static final Class<?>[] LOAD = {String.class, String.class, MessageQueue.class, String.class, String.class, String.class, int.class,
                AssetManager.class, byte[].class, ClassLoader.class, String.class};

        @org.robolectric.annotation.RealObject private android.app.NativeActivity activity;

        @Implementation protected long loadNativeCode(String path, String funcname, MessageQueue queue, String internalDataPath, String obbPath,
                String externalDataPath, int sdkVersion, AssetManager assetMgr, byte[] savedState, ClassLoader classLoader, String libraryPath) throws Exception {
            return NativeQueueCarrier.call(queue, carrier -> NativeBinding.<Long>callInstance(activity, android.app.NativeActivity.class, "loadNativeCode", LOAD,
                    path, funcname, carrier, internalDataPath, obbPath, externalDataPath, sdkVersion, assetMgr, savedState, classLoader, libraryPath));
        }
    }

    @Implements(className = "android.view.InputQueue", callNativeMethodsByDefault = true, isInAndroidSdk = false)
    public static final class InputQueue {
        @Implementation protected static long nativeInit(WeakReference<?> weakQueue, MessageQueue queue) throws Exception {
            return NativeQueueCarrier.call(queue, carrier -> NativeBinding.<Long>call(Class.forName("android.view.InputQueue"), "nativeInit",
                    new Class<?>[] {WeakReference.class, MessageQueue.class}, weakQueue, carrier));
        }
    }
}
