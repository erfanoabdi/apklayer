package apklayer.host;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.robolectric.annotation.internal.DoNotInstrument;

/**
 * An app's stack traces and thread list as Android shows them (installed into the patched java.base, JdkHooks, in app processes). The host's frames
 * (Robolectric, apklayer.host, the JDK's reflection plumbing) are dropped, Robolectric's `$$robo$$<class>$<method>` bodies are named back to the
 * method they stand for (their no-line stubs go), reflection shows Method.invoke(Native Method), the module prefix `java.base/` goes, and the main
 * thread's trace ends with the zygote's frames. A trace that starts inside the host (a failure of ours: the top frame is Robolectric's or
 * apklayer's) stays as it is, so crash logs keep their cause. `-Dapklayer.rawtraces=true` (APKLAYER_JOPTS) installs nothing: every trace is the JVM's.
 */
@DoNotInstrument
public final class GuestTraces {
    private GuestTraces() {}

    private static final String[] ZYGOTE = {
        "android.os.Looper.loopOnce(Looper.java:232)", "android.os.Looper.loop(Looper.java:317)",
        "android.app.ActivityThread.main(ActivityThread.java:8705)", "java.lang.reflect.Method.invoke(Native Method)",
        "com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:565)", "com.android.internal.os.ZygoteInit.main(ZygoteInit.java:1081)"};

    private static boolean host(String cls) {
        return cls.startsWith("org.robolectric.") || cls.startsWith("apklayer.") || cls.startsWith("jdk.internal.reflect.")
                || cls.startsWith("java.lang.invoke.") || cls.startsWith("jdk.internal.") || cls.startsWith("sun.reflect.")
                || cls.startsWith("org.junit.");
    }

    /** A trace whose cause is in the host: left alone. */
    private static boolean hostFailure(StackTraceElement[] trace) {
        if (trace.length == 0) return false;
        String top = trace[0].getClassName();
        return top.startsWith("org.robolectric.") || top.startsWith("apklayer.");
    }

    static StackTraceElement[] frames(StackTraceElement[] trace) {
        if (trace.length == 0 || hostFailure(trace)) return trace;
        List<StackTraceElement> out = new ArrayList<>(trace.length);
        boolean sawHost = false, main = false;
        for (int i = 0; i < trace.length; i++) {
            StackTraceElement f = trace[i];
            String cls = f.getClassName(), method = f.getMethodName();
            if (cls.equals("java.lang.reflect.Method") && method.equals("invoke") || cls.equals("java.lang.reflect.Constructor") && method.equals("newInstance")) {
                out.add(new StackTraceElement(cls, method, null, -2));  // (Native Method)
                continue;
            }
            if (host(cls)) { sawHost = true; continue; }
            if (method.startsWith("apklayer$")) {  // the renamed JDK body of a method JdkIdentityPatch wraps: Android's Thread.getStackTrace is VMStack's
                if (method.equals("apklayer$getStackTrace")) out.add(new StackTraceElement("dalvik.system.VMStack", "getThreadStackTrace", null, -2));
                continue;
            }
            if (cls.equals("java.lang.Thread") && method.equals("getStackTrace") && f.getLineNumber() < 0) f = new StackTraceElement(cls, method, "Thread.java", 1841);
            if (method.startsWith("$$robo$$")) {  // $$robo$$android_app_Activity$performCreate -> performCreate
                int dollar = method.lastIndexOf('$');
                out.add(new StackTraceElement(cls, method.substring(dollar + 1), f.getFileName(), f.getLineNumber()));
                continue;
            }
            if (f.getLineNumber() < 0 && f.getFileName() != null && !out.isEmpty() && i > 0) {  // the instrumented stub of a $$robo$$ body
                StackTraceElement before = trace[i - 1];
                if (before.getClassName().equals(cls) && before.getMethodName().startsWith("$$robo$$") && before.getMethodName().endsWith("$" + method)) continue;
            }
            if (f.getModuleName() != null || f.getClassLoaderName() != null) f = new StackTraceElement(cls, method, f.getFileName(), f.getLineNumber());
            out.add(f);
        }
        if (sawHost && Thread.currentThread().getName().equals("main") && !out.isEmpty()) {  // the sandbox's main thread runs the app's looper
            for (String frame : ZYGOTE) out.add(parse(frame));
        }
        return out.toArray(new StackTraceElement[0]);
    }

    private static StackTraceElement parse(String frame) {
        int paren = frame.indexOf('('), dot = frame.lastIndexOf('.', paren);
        String where = frame.substring(paren + 1, frame.length() - 1);
        int colon = where.indexOf(':');
        return where.startsWith("Native") ? new StackTraceElement(frame.substring(0, dot), frame.substring(dot + 1, paren), null, -2)
                : new StackTraceElement(frame.substring(0, dot), frame.substring(dot + 1, paren), where.substring(0, colon), Integer.parseInt(where.substring(colon + 1)));
    }

    /** ART's runtime threads the JVM has no counterpart of: unstarted stand-ins in the list (no host thread, empty trace). */
    private static final Thread[] ART_ONLY = {new Thread(null, null, "FinalizerWatchdogDaemon", 0), new Thread(null, null, "HeapTaskDaemon", 0),
            new Thread(null, null, "Jit thread pool worker thread 0", 0), new Thread(null, null, "Profile Saver", 0)};
    static { for (Thread t : ART_ONLY) t.setDaemon(true); }

    /** What Android has: the JVM's and apklayer's service threads are not an app's to see. */
    static Map<Thread, StackTraceElement[]> threads(Map<Thread, StackTraceElement[]> all) {
        Map<Thread, StackTraceElement[]> out = new LinkedHashMap<>();
        for (Thread t : ART_ONLY) out.put(t, new StackTraceElement[0]);
        for (Map.Entry<Thread, StackTraceElement[]> e : all.entrySet()) {
            String name = e.getKey().getName();
            if (e.getKey().threadId() == 1) continue;  // the launcher's own main thread, parked below the sandbox's
            if (name.startsWith("apklayer-") || name.startsWith("process reaper") || name.equals("Common-Cleaner") || name.equals("Notification Thread")
                    || name.startsWith("Cleaner-") || name.startsWith("ForkJoinPool.") || name.startsWith("HttpClient-") || name.startsWith("robolectric")) continue;
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /** The JVM's system threads under ART's names (they show in Thread.getAllStackTraces and /proc/self/task). */
    static void renameSystemThreads() {
        ThreadGroup root = Thread.currentThread().getThreadGroup();
        while (root.getParent() != null) root = root.getParent();
        Thread[] all = new Thread[root.activeCount() + 8];
        int n = root.enumerate(all, true);
        for (int i = 0; i < n; i++) {
            String name = all[i].getName();
            String art = switch (name) {
                case "Reference Handler" -> "ReferenceQueueDaemon";
                case "Finalizer" -> "FinalizerDaemon";
                case "Signal Dispatcher" -> "Signal Catcher";
                default -> null;
            };
            if (art != null) { try { all[i].setName(art); } catch (RuntimeException ignored) { } }
        }
    }

    /** Installs the hooks of an app process (not the hub; not with -Dapklayer.rawtraces=true). */
    public static void install() {
        if (Boolean.getBoolean("apklayer.rawtraces")) return;
        try {
            Class<?> hooks = Class.forName("java.lang.ApklayerHooks");
            set(hooks, "frames", (java.util.function.UnaryOperator<StackTraceElement[]>) GuestTraces::frames);
            set(hooks, "threads", (java.util.function.UnaryOperator<Map<Thread, StackTraceElement[]>>) GuestTraces::threads);
            set(hooks, "npe", (java.util.function.UnaryOperator<String>) GuestMessages::npe);
            set(hooks, "methodHidden", (java.util.function.BiPredicate<Class<?>, String>) GuestHiddenApi::hidden);
            set(hooks, "fields", (java.util.function.BiFunction<Class<?>, java.lang.reflect.Field[], java.lang.reflect.Field[]>) GuestHiddenApi::fields);
            renameSystemThreads();
        } catch (ReflectiveOperationException | RuntimeException absent) {
            System.err.println("[apklayer] identity hooks not installed (java.base without the identity patch): " + absent);
        }
    }

    private static void set(Class<?> hooks, String field, Object value) throws ReflectiveOperationException {
        java.lang.reflect.Field f = hooks.getDeclaredField(field);
        f.setAccessible(true);
        f.set(null, value);
    }
}
