package apklayer.host;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.robolectric.annotation.internal.DoNotInstrument;

/**
 * The app's {@code Method.invoke} (routed here by DenyInstrument). Only difference: reflective
 * {@code sun.misc.Unsafe.getUnsafe()} works for app code, as on ART (HotSpot rejects non-boot callers).
 */
@DoNotInstrument
public final class Reflect {
    private Reflect() {}

    public static Object invoke(Method m, Object receiver, Object[] args)
            throws IllegalAccessException, InvocationTargetException {
        if (m.getDeclaringClass() == sun.misc.Unsafe.class && m.getName().equals("getUnsafe")
                && m.getParameterCount() == 0) {
            return UnsafeAccess.getUnsafe();
        }
        return m.invoke(receiver, args);
    }
}
