package apklayer.host;

import java.lang.reflect.Field;
import org.robolectric.annotation.internal.DoNotInstrument;

/**
 * {@code sun.misc.Unsafe.getUnsafe()} for app code: ART hands the Unsafe to any caller, HotSpot only
 * to boot-loader classes. The converter output routes the app's static call here (DenyInstrument).
 */
@DoNotInstrument
public final class UnsafeAccess {
    private static sun.misc.Unsafe unsafe;

    private UnsafeAccess() {}

    public static synchronized sun.misc.Unsafe getUnsafe() {
        if (unsafe == null) {
            try {
                Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                unsafe = (sun.misc.Unsafe) f.get(null);
            } catch (ReflectiveOperationException e) {
                throw new SecurityException("Unsafe", e);
            }
        }
        return unsafe;
    }
}
