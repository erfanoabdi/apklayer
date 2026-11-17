package apklayer.host;

import android.app.Application;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowActivityThread;

/**
 * Before the app's own Application exists, ActivityThread.getApplication() answered Robolectric's dummy initial application of package "android"
 * (a system context: getFilesDir throws "No data directory found for package android"); a device answers null until handleBindApplication has made
 * the app's Application (Java callers must keep the dummy: patch 0029 broke KrApplication's MMKV.initialize). A caller from native code (JNI: the
 * frame below is a native method, or the thread has no Java frame) gets the device's answer. Tencent ACE (PGR) asks from the app's static init,
 * cached the package name 'android' for good and so could not tell its own package from a virtual container's host. Off unless
 * -Dapklayer.noDummyApp=true (APKLAYER_JOPTS).
 */
@Implements(value = android.app.ActivityThread.class, isInAndroidSdk = false)
public class ShadowHostActivityThread extends ShadowActivityThread {
    @RealObject private android.app.ActivityThread real;
    private static final boolean ON = Boolean.getBoolean("apklayer.noDummyApp");

    @Implementation @Override protected Application getApplication() {
        if (!ON) return super.getApplication();
        Application initial = org.robolectric.util.ReflectionHelpers.getField(real, "mInitialApplication");
        // the app's Application is RuntimeEnvironment.application from its creation (Robolectric sets mInitialApplication right after)
        if (initial != null && initial != RuntimeEnvironment.application && "android".equals(initial.getPackageName()) && fromNative()) return null;
        return super.getApplication();
    }

    /** The first frame that is not the framework's own plumbing is a native method (a JNI call out of native code), or there is none (an attached native thread). */
    private static boolean fromNative() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(f -> !f.getClassName().startsWith("android.app.ActivityThread") && !f.getClassName().startsWith("org.robolectric.")
                        && !f.getClassName().startsWith("apklayer.host.ShadowHostActivityThread") && !f.getClassName().startsWith("java.lang.invoke.")
                        && !f.getClassName().startsWith("jdk.internal.") && !f.getClassName().startsWith("java.lang.reflect."))
                .findFirst().map(java.lang.StackWalker.StackFrame::isNativeMethod).orElse(true));
    }
}
