package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowSystemProperties;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * android.os.SystemProperties over the instance's property store (libandroid_runtime's natives, the store the natives and `apklayer prop` use):
 * every key, not only the build keys Robolectric's build.prop has (ro.treble.enabled, ro.hardware.egl, persist.*, what `apklayer prop set` set).
 * Until the device runtime has registered the natives (Robolectric's own setup) and in a JVM without it, Robolectric's in-memory values answer.
 */
@Implements(className = "android.os.SystemProperties", isInAndroidSdk = false)  // the handle and change-callback natives stay Robolectric's no-ops
public class ShadowHostSystemProperties extends ShadowSystemProperties {
    private static final Object MISSING = new Object();

    @Implementation protected static String native_get(String key) { return native_get(key, ""); }  // the original's (Robolectric's answers from its map)
    @Implementation protected static String native_get(String key, String def) {
        Object value = store("native_get", ClassParameter.from(String.class, key), ClassParameter.from(String.class, def));
        return value != MISSING ? (String) value : ShadowSystemProperties.native_get(key, def);
    }
    @Implementation protected static int native_get_int(String key, int def) {
        Object value = store("native_get_int", ClassParameter.from(String.class, key), ClassParameter.from(int.class, def));
        return value != MISSING ? (Integer) value : ShadowSystemProperties.native_get_int(key, def);
    }
    @Implementation protected static long native_get_long(String key, long def) {
        Object value = store("native_get_long", ClassParameter.from(String.class, key), ClassParameter.from(long.class, def));
        return value != MISSING ? (Long) value : ShadowSystemProperties.native_get_long(key, def);
    }
    @Implementation protected static boolean native_get_boolean(String key, boolean def) {
        Object value = store("native_get_boolean", ClassParameter.from(String.class, key), ClassParameter.from(boolean.class, def));
        return value != MISSING ? (Boolean) value : ShadowSystemProperties.native_get_boolean(key, def);
    }
    @Implementation protected static void native_set(String key, String value) {
        if (store("native_set", ClassParameter.from(String.class, key), ClassParameter.from(String.class, value)) == MISSING) ShadowSystemProperties.native_set(key, value);
    }

    /** The original native's answer, MISSING while it is not registered. */
    private static Object store(String name, ClassParameter<?>... args) {
        try {
            return ReflectionHelpers.callStaticMethod(Shadow.class.getClassLoader(), "android.os.SystemProperties",
                    Shadow.directNativeMethodName("android.os.SystemProperties", name), args);
        } catch (UnsatisfiedLinkError notYet) { return MISSING; }
    }
}
