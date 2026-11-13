package apklayer.host;

import android.system.ErrnoException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.robolectric.annotation.internal.DoNotInstrument;

/** Java views and child-process snapshots of the active guest libc environment. */
@DoNotInstrument
public final class GuestEnvironment {
    private GuestEnvironment() {}

    private static native String nativeGet(String name);
    private static native String[] nativeEntries();
    private static native int nativeSet(String name, String value, boolean overwrite);
    private static native int nativeUnset(String name);

    public static String getenv(String name) {
        Objects.requireNonNull(name, "name == null");
        NativeLoader.ensureInitialized();
        return nativeGet(name);
    }

    /** Android returns a fresh unmodifiable snapshot on every call, not HotSpot's cached map. */
    public static Map<String, String> getenv() { return Collections.unmodifiableMap(snapshot()); }

    public static String[] environ() {
        NativeLoader.ensureInitialized();
        return nativeEntries();
    }

    public static void setenv(String name, String value, boolean overwrite) throws ErrnoException {
        Objects.requireNonNull(name, "name == null");
        Objects.requireNonNull(value, "value == null");
        NativeLoader.ensureInitialized();
        int error = nativeSet(name, value, overwrite);
        if (error != 0) throw new ErrnoException("setenv", error);
    }

    public static void unsetenv(String name) throws ErrnoException {
        Objects.requireNonNull(name, "name == null");
        NativeLoader.ensureInitialized();
        int error = nativeUnset(name);
        if (error != 0) throw new ErrnoException("unsetenv", error);
    }

    private static Map<String, String> snapshot() {
        // Keep the JDK's validated StringEnvironment map (including null/type checks), but
        // populate a new instance from guest libc. Never change the host's cached environment.
        Map<String, String> result = new ProcessBuilder().environment();
        result.clear();
        String[] entries = environ();
        for (int i = entries.length - 1; i >= 0; i--) {
            int equals = entries[i].indexOf('=');
            if (equals >= 0) result.put(entries[i].substring(0, equals), entries[i].substring(equals + 1));
        }
        return result;
    }

    private static final class BuilderState {
        static final Field ENVIRONMENT = field();
        private static Field field() {
            try {
                Field field = ProcessBuilder.class.getDeclaredField("environment");
                field.setAccessible(true); // Main opens java.base before any app runs.
                return field;
            } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> current(ProcessBuilder builder) {
        Objects.requireNonNull(builder);
        try { return (Map<String, String>) BuilderState.ENVIRONMENT.get(builder); }
        catch (IllegalAccessException impossible) { throw new AssertionError(impossible); }
    }

    private static void assign(ProcessBuilder builder, Map<String, String> environment) {
        try { BuilderState.ENVIRONMENT.set(builder, environment); }
        catch (IllegalAccessException impossible) { throw new AssertionError(impossible); }
    }

    public static Map<String, String> environment(ProcessBuilder builder) {
        Map<String, String> result = current(builder);
        if (result == null) {
            result = snapshot();
            assign(builder, result);
        }
        return result;
    }

    public static Process start(ProcessBuilder builder) throws IOException {
        if (current(builder) != null) return builder.start();
        // Without environment(), Android inherits at each start; it does not freeze the
        // builder's environment. Restore null even when process creation fails.
        assign(builder, snapshot());
        try { return builder.start(); }
        finally { assign(builder, null); }
    }

    public static List<Process> startPipeline(List<ProcessBuilder> builders) throws IOException {
        Objects.requireNonNull(builders);
        java.util.ArrayList<ProcessBuilder> inherited = new java.util.ArrayList<>();
        try {
            for (ProcessBuilder builder : builders) {
                if (current(builder) == null) {
                    assign(builder, snapshot());
                    inherited.add(builder);
                }
            }
            return ProcessBuilder.startPipeline(builders);
        } finally {
            for (ProcessBuilder builder : inherited) assign(builder, null);
        }
    }

    public static Process exec(Runtime runtime, String command) throws IOException {
        return exec(runtime, command, null, null);
    }
    public static Process exec(Runtime runtime, String[] command) throws IOException {
        return exec(runtime, command, null, null);
    }
    public static Process exec(Runtime runtime, String command, String[] envp) throws IOException {
        return exec(runtime, command, envp, null);
    }
    public static Process exec(Runtime runtime, String[] command, String[] envp) throws IOException {
        return exec(runtime, command, envp, null);
    }
    public static Process exec(Runtime runtime, String command, String[] envp, File directory) throws IOException {
        Objects.requireNonNull(runtime);
        return runtime.exec(command, envp == null ? environ() : envp, directory);
    }
    public static Process exec(Runtime runtime, String[] command, String[] envp, File directory) throws IOException {
        Objects.requireNonNull(runtime);
        return runtime.exec(command, envp == null ? environ() : envp, directory);
    }

    /** Public JDK APIs only; virtual replacements prepend their original receiver. */
    static Method replacement(Method method) {
        Class<?> owner = method.getDeclaringClass();
        String name = method.getName();
        if (!(owner == System.class && name.equals("getenv"))
                && !(owner == ProcessBuilder.class && (name.equals("environment") || name.equals("start") || name.equals("startPipeline")))
                && !(owner == Runtime.class && name.equals("exec"))) return method;
        if (!Modifier.isPublic(method.getModifiers())) return method;
        Class<?>[] types = method.getParameterTypes();
        if (!Modifier.isStatic(method.getModifiers())) {
            Class<?>[] withReceiver = new Class<?>[types.length + 1];
            withReceiver[0] = owner;
            System.arraycopy(types, 0, withReceiver, 1, types.length);
            types = withReceiver;
        }
        try { return GuestEnvironment.class.getMethod(name, types); }
        catch (NoSuchMethodException missing) { return method; }
    }
}
