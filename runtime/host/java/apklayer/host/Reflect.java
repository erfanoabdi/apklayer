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
        if (replacement == m) return invokeAs(m, receiver, args);
        return replacement.invoke(receiver, args);
    }

    private static final StackWalker CALLER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /**
     * Method.invoke checks access against the class that calls it, which is this one (the converter routes every app call here): an app's
     * package-private or private call it may make would throw IllegalAccessException from apklayer.host.Reflect. The check is made for the app's
     * class (the caller of this method) and the call goes through a private copy of the Method made accessible.
     */
    private static Object invokeAs(Method m, Object receiver, Object[] args) throws IllegalAccessException, InvocationTargetException {
        Class<?> owner = m.getDeclaringClass();
        int mods = m.getModifiers();
        if (m.isAccessible() || Modifier.isPublic(mods) && Modifier.isPublic(owner.getModifiers())) return m.invoke(receiver, args);
        Class<?> caller = CALLER.walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass).filter(c -> c != Reflect.class).findFirst().orElse(Reflect.class));  // the app's class
        boolean allowed = Modifier.isPublic(mods) || caller == owner || caller.getNestHost() == owner.getNestHost()  // ART does not check the declaring class's access for a public member
                || (!Modifier.isPrivate(mods) && (caller.getPackageName().equals(owner.getPackageName()) && caller.getClassLoader() == owner.getClassLoader()
                    || Modifier.isProtected(mods) && owner.isAssignableFrom(caller)));
        if (!allowed) return m.invoke(receiver, args);  // the JDK's IllegalAccessException (as for any foreign caller)
        Method copy;
        try { copy = owner.getDeclaredMethod(m.getName(), m.getParameterTypes()); }
        catch (NoSuchMethodException gone) { return m.invoke(receiver, args); }
        copy.setAccessible(true);
        return copy.invoke(receiver, args);
    }

    /** Original and replacement, pairwise: the static methods guest native code reaches through JNI (bridge/native/jni_system_route.c). */
    static Method[] jniRoutes() {
        java.util.List<Method> pairs = new java.util.ArrayList<>();
        for (Class<?> owner : new Class<?>[] {System.class, Boolean.class, Integer.class, Long.class})
            for (Method method : owner.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) || !Modifier.isPublic(method.getModifiers())) continue;
                Method replacement = replacement(method);
                if (replacement != method && replacement.getReturnType() == method.getReturnType()) { pairs.add(method); pairs.add(replacement); }
            }
        return pairs.toArray(new Method[0]);
    }

    /** The class of app class loaders: a guest JNI call from no native module is the app's when an app class's native method runs. */
    static Class<?> appLoaderClass() { return dalvik.system.BaseDexClassLoader.class; }

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
