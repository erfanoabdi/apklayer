package apklayer.host;

import java.util.List;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * dalvik.system.VMStack over the JVM's StackWalker (art/runtime/native/dalvik_system_VMStack.cc on Android). Its natives are unbound on the JVM and the
 * instrumented framework returned null: Flogger's Android backend names its loggers after getStackClass2().getName(), and Play services' Phenotype
 * provider (ConfigurationChimeraProvider.<clinit>) died on the null name, again in every process that started it.
 * Frame 0 is the method that asks; getStackClass1 is its caller, getStackClass2 the caller's caller, as ART's VMStack documents them.
 */
@Implements(className = "dalvik.system.VMStack", isInAndroidSdk = false)
public class ShadowHostVMStack {
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /** The app's frames from the asking method on: this shadow, VMStack itself and Robolectric's dispatch (reflection, method handles) left out. */
    private static List<Class<?>> callers(int count) {
        return WALKER.walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass).filter(c -> !skipped(c.getName())).limit(count).toList());
    }

    private static boolean skipped(String name) {
        return name.equals(ShadowHostVMStack.class.getName()) || name.equals("dalvik.system.VMStack") || name.startsWith("org.robolectric.")
                || name.startsWith("java.lang.invoke.") || name.startsWith("jdk.internal.reflect.") || name.startsWith("java.lang.reflect.");
    }

    private static Class<?> at(int index) {
        List<Class<?>> classes = callers(index + 1);
        return classes.size() > index ? classes.get(index) : null;
    }

    @Implementation protected static Class<?> getStackClass1() { return at(1); }

    @Implementation protected static Class<?> getStackClass2() { return at(2); }

    /** The defining loader of the asking method's caller (ART: the caller's caller of getCallingClassLoader). */
    @Implementation protected static ClassLoader getCallingClassLoader() {
        Class<?> caller = at(1);
        return caller == null ? null : caller.getClassLoader();
    }

    /** The loader of the nearest frame whose class is not the boot class path's (ObjectInputStream resolves classes with it). */
    @Implementation protected static ClassLoader getClosestUserClassLoader() {
        return WALKER.walk(frames -> frames.map(f -> f.getDeclaringClass().getClassLoader()).filter(l -> l != null && l != ClassLoader.getPlatformClassLoader())
                .findFirst().orElse(null));
    }

    @Implementation protected static StackTraceElement[] getThreadStackTrace(Thread thread) { return thread.getStackTrace(); }

    @Implementation protected static int fillStackTraceElements(Thread thread, StackTraceElement[] into) {
        StackTraceElement[] trace = thread.getStackTrace();
        int n = Math.min(trace.length, into.length);
        System.arraycopy(trace, 0, into, 0, n);
        return n;
    }
}
