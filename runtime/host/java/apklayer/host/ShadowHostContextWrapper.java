package apklayer.host;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContextWrapper;

/**
 * Until the app's own Application exists, ActivityThread.getApplication() is Robolectric's dummy initial application of package "android"; native
 * code that asks it for the package name (Tencent ACE at its static init: it caches the answer for good as its own name, and then cannot tell its
 * own package from a virtual container's host) gets the app's package, as on a device (patch 0029 made getApplication null for Java callers and
 * broke KrApplication; null for native callers made the game's startup stall). Only the name changes; getFilesDir still throws as before.
 * Off unless -Dapklayer.noDummyApp=true (APKLAYER_JOPTS).
 */
@Implements(value = android.content.ContextWrapper.class, isInAndroidSdk = false)
public class ShadowHostContextWrapper extends ShadowContextWrapper {
    @RealObject private android.content.ContextWrapper real;
    private static final boolean ON = Boolean.getBoolean("apklayer.noDummyApp");

    @Implementation protected String getPackageName() {
        String name = Shadow.directlyOn(real, "android.content.ContextWrapper", "getPackageName");
        if (ON && "android".equals(name) && real instanceof android.app.Application && real != RuntimeEnvironment.application
                && real == org.robolectric.util.ReflectionHelpers.getField(RuntimeEnvironment.getActivityThread(), "mInitialApplication") && fromNative()) {
            String app = System.getProperty("apklayer.package");
            if (app != null) return app;
        }
        return name;
    }

    /** The first frame that is not framework plumbing is a native method (a JNI call out of native code), or there is none (an attached native thread). */
    private static boolean fromNative() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(f -> !f.getClassName().startsWith("android.content.ContextWrapper") && !f.getClassName().startsWith("org.robolectric.")
                        && !f.getClassName().startsWith("apklayer.host.ShadowHostContextWrapper") && !f.getClassName().startsWith("java.lang.invoke.")
                        && !f.getClassName().startsWith("jdk.internal.") && !f.getClassName().startsWith("java.lang.reflect."))
                .findFirst().map(java.lang.StackWalker.StackFrame::isNativeMethod).orElse(true));
    }
}
