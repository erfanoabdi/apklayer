package apklayer.host;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
        return GuestSystemProperties.replacement(m).invoke(receiver, args);
    }

    public static MethodHandle findStatic(MethodHandles.Lookup lookup, Class<?> owner,
            String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle original = lookup.findStatic(owner, name, type); // Preserve access/type checks.
        if (owner != System.class && owner != Boolean.class && owner != Integer.class
                && owner != Long.class) return original;
        Method method = owner.getDeclaredMethod(name, type.parameterArray());
        Method replacement = GuestSystemProperties.replacement(method);
        return replacement == method ? original : MethodHandles.publicLookup().unreflect(replacement);
    }

    public static MethodHandle unreflect(MethodHandles.Lookup lookup, Method method)
            throws IllegalAccessException {
        MethodHandle original = lookup.unreflect(method);
        Method replacement = GuestSystemProperties.replacement(method);
        return replacement == method ? original : MethodHandles.publicLookup().unreflect(replacement);
    }
}
