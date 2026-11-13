package apklayer.host;

import android.system.ErrnoException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.StringTokenizer;
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
        static final Method ARRAY_ENVIRONMENT = arrayEnvironment();
        private static Method arrayEnvironment() {
            try {
                Method method = ProcessBuilder.class.getDeclaredMethod("environment", String[].class);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
        }
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
        try (Launch request = new Launch(builder)) {
            try { return builder.start(); }
            catch (IOException failure) { throw request.failure(failure); }
        }
    }

    public static List<Process> startPipeline(List<ProcessBuilder> builders) throws IOException {
        Objects.requireNonNull(builders);
        ArrayList<Launch> requests = new ArrayList<>();
        try {
            for (ProcessBuilder builder : builders) requests.add(new Launch(builder));
            return ProcessBuilder.startPipeline(builders);
        } catch (IOException failure) {
            for (Launch request : requests) failure = request.failure(failure);
            throw failure;
        } finally {
            for (int i = requests.size() - 1; i >= 0; i--) requests.get(i).close();
        }
    }

    // UNIXProcess caches the parent's PATH on first launch, used only for explicit environments.
    private static boolean pathInitialized;
    private static String initialPath;
    private static synchronized String searchPath(boolean explicit) {
        String live = getenv("PATH");
        if (!pathInitialized) { initialPath = live; pathInitialized = true; }
        String path = explicit ? initialPath : live;
        if (path != null) return path; // Empty PATH means the child's current directory.
        return explicit ? ":/bin:/usr/bin" : "/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:/apex/com.android.virt/bin:/system_ext/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin";
    }

    // This impossible proc-fd filename fails closed when our preload is unavailable.
    private static final String EXEC_SEARCH = "/proc/self/fd/apklayer-exec-search/";
    private static final class Launch implements AutoCloseable {
        final ProcessBuilder builder;
        final List<String> command;
        final boolean inherited;
        boolean adapted;

        Launch(ProcessBuilder builder) {
            this.builder = Objects.requireNonNull(builder);
            command = builder.command();
            inherited = current(builder) == null;
            // Let ProcessBuilder produce its own argument errors without initializing PATH.
            boolean valid = !command.isEmpty();
            for (String arg : command) valid &= arg != null && arg.indexOf('\0') < 0;
            if (valid) {
                String path = searchPath(!inherited);
                if (command.get(0).indexOf('/') < 0) {
                    ArrayList<String> wrapped = new ArrayList<>(command.size() + 3);
                    wrapped.add(EXEC_SEARCH + command.get(0)); wrapped.add(inherited ? "1i" : "1e"); wrapped.add(path);
                    wrapped.addAll(command);
                    builder.command(wrapped);
                    adapted = true;
                }
            }
            try { if (inherited) assign(builder, snapshot()); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        IOException failure(IOException failure) {
            if (!adapted) return failure;
            String marker = EXEC_SEARCH + command.get(0);
            if (!failure.getMessage().contains(marker)) return failure;
            return new IOException(failure.getMessage().replace(marker, command.get(0)), failure.getCause());
        }

        @Override public void close() {
            if (adapted) builder.command(command);
            if (inherited) assign(builder, null);
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
        if (command.isEmpty()) throw new IllegalArgumentException("Empty command");
        StringTokenizer tokens = new StringTokenizer(command);
        String[] argv = new String[tokens.countTokens()];
        for (int i = 0; tokens.hasMoreTokens(); i++) argv[i] = tokens.nextToken();
        return exec(runtime, argv, envp, directory);
    }
    public static Process exec(Runtime runtime, String[] command, String[] envp, File directory) throws IOException {
        Objects.requireNonNull(runtime);
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory);
        if (envp != null) {
            try { BuilderState.ARRAY_ENVIRONMENT.invoke(builder, (Object) envp); }
            catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtimeFailure) throw runtimeFailure;
                if (failure.getCause() instanceof Error error) throw error;
                throw new AssertionError(failure.getCause());
            } catch (IllegalAccessException impossible) { throw new AssertionError(impossible); }
        }
        return start(builder);
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
