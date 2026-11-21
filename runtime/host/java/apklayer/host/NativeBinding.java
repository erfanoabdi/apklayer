package apklayer.host;

import java.lang.reflect.Method;

/** The JNI function behind a native method of a Robolectric-instrumented class (renamed {@code $$robo$$name$nativeBinding}); a shadow that overrides one native calls the real one through this. */
final class NativeBinding {
    private NativeBinding() {}

    static <T> T call(Class<?> owner, String name, Class<?>[] types, Object... args) { return callInstance(null, owner, name, types, args); }

    /** An instance native (receiver: the object; null for a static one). */
    @SuppressWarnings("unchecked")
    static <T> T callInstance(Object receiver, Class<?> owner, String name, Class<?>[] types, Object... args) {
        try {
            Method m = owner.getDeclaredMethod("$$robo$$" + name + "$nativeBinding", types);
            m.setAccessible(true);
            return (T) m.invoke(receiver, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            if (e.getCause() instanceof Error r) throw r;
            throw new RuntimeException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new LinkageError("native binding " + owner.getName() + "." + name, e);
        }
    }
}
