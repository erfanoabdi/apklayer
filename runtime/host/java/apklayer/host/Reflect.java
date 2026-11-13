package apklayer.host;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.robolectric.annotation.internal.DoNotInstrument;

/**
 * App reflection and method handles use the same Android compatibility entry points as direct calls.
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
        Method replacement = replacement(m);
        if (replacement != m && !Modifier.isStatic(m.getModifiers())) {
            if (receiver == null) throw new NullPointerException();
            if (!m.getDeclaringClass().isInstance(receiver)) throw new IllegalArgumentException("object is not an instance of declaring class");
            Object[] forwarded = new Object[(args == null ? 0 : args.length) + 1];
            forwarded[0] = receiver;
            if (args != null) System.arraycopy(args, 0, forwarded, 1, args.length);
            return replacement.invoke(null, forwarded);
        }
        return replacement.invoke(receiver, args);
    }

    private static Method replacement(Method method) {
        Method environment = GuestEnvironment.replacement(method);
        return environment == method ? GuestSystemProperties.replacement(method) : environment;
    }

    public static MethodHandle findStatic(MethodHandles.Lookup lookup, Class<?> owner,
            String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle original = lookup.findStatic(owner, name, type); // Preserve access/type checks.
        if (owner != System.class && owner != Boolean.class && owner != Integer.class
                && owner != Long.class && owner != ProcessBuilder.class) return original;
        Method method = owner.getDeclaredMethod(name, type.parameterArray());
        Method replacement = replacement(method);
        return replacement == method ? original : MethodHandles.publicLookup().unreflect(replacement);
    }

    public static MethodHandle findVirtual(MethodHandles.Lookup lookup, Class<?> owner,
            String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle original = lookup.findVirtual(owner, name, type);
        if (owner != Runtime.class && owner != ProcessBuilder.class) return original;
        Method method = owner.getMethod(name, type.parameterArray());
        Method replacement = replacement(method);
        return replacement == method ? original : MethodHandles.publicLookup().unreflect(replacement);
    }

    public static MethodHandle bind(MethodHandles.Lookup lookup, Object receiver,
            String name, MethodType type) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle original = lookup.bind(receiver, name, type);
        Class<?> owner = receiver.getClass();
        if (owner != Runtime.class && owner != ProcessBuilder.class) return original;
        Method method = owner.getMethod(name, type.parameterArray());
        Method replacement = replacement(method);
        return replacement == method ? original : MethodHandles.publicLookup().unreflect(replacement).bindTo(receiver);
    }

    public static MethodHandle findSpecial(MethodHandles.Lookup lookup, Class<?> owner,
            String name, MethodType type, Class<?> specialCaller) throws NoSuchMethodException, IllegalAccessException {
        MethodHandle original = lookup.findSpecial(owner, name, type, specialCaller);
        if (owner != Runtime.class && owner != ProcessBuilder.class) return original;
        Method method = owner.getMethod(name, type.parameterArray());
        Method replacement = replacement(method);
        return replacement == method ? original : MethodHandles.publicLookup().unreflect(replacement).asType(original.type());
    }

    public static MethodHandle unreflect(MethodHandles.Lookup lookup, Method method)
            throws IllegalAccessException {
        MethodHandle original = lookup.unreflect(method);
        Method replacement = replacement(method);
        return replacement == method ? original : MethodHandles.publicLookup().unreflect(replacement);
    }

    public static MethodHandle unreflectSpecial(MethodHandles.Lookup lookup, Method method, Class<?> specialCaller)
            throws IllegalAccessException {
        MethodHandle original = lookup.unreflectSpecial(method, specialCaller);
        Method replacement = replacement(method);
        return replacement == method ? original : MethodHandles.publicLookup().unreflect(replacement).asType(original.type());
    }
}
