package hello.identity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Java and class identity (handoff item 5): properties, class availability, loaders, DexPathList, stack traces, VM helper classes. */
final class JavaProbes {
    private JavaProbes() {}

    static final String[] PROPERTIES = {"java.vm.name", "java.vm.vendor", "java.vm.version", "java.vm.specification.name",
        "java.vm.specification.vendor", "java.vm.specification.version", "java.runtime.name", "java.runtime.version", "java.version",
        "java.vendor", "java.vendor.url", "java.specification.name", "java.specification.vendor", "java.specification.version",
        "java.home", "java.class.version", "java.class.path", "java.boot.class.path", "java.library.path", "java.io.tmpdir",
        "java.compiler", "java.ext.dirs", "java.vm.vendor.url", "os.name", "os.arch", "os.version", "user.name", "user.home", "user.dir",
        "user.language", "user.region", "file.encoding", "file.separator", "path.separator", "line.separator", "android.icu.library.version",
        "android.icu.unicode.version", "android.openssl.version", "android.zlib.version", "http.agent", "javax.net.ssl.trustStore",
        "sun.boot.library.path", "sun.java.command", "sun.arch.data.model", "sun.cpu.endian", "sun.jnu.encoding", "jdk.debug",
        "java.vm.info", "java.vm.compressedOopsMode", "native.encoding", "stdout.encoding"};

    static final String[] CLASSES = {"dalvik.system.VMRuntime", "dalvik.system.VMStack", "dalvik.system.VMDebug", "dalvik.system.DexPathList",
        "dalvik.system.BaseDexClassLoader", "dalvik.system.PathClassLoader", "dalvik.system.InMemoryDexClassLoader", "dalvik.system.DexFile",
        "dalvik.system.ZygoteHooks", "dalvik.system.CloseGuard", "dalvik.system.BlockGuard", "dalvik.system.RuntimeHooks",
        "dalvik.annotation.optimization.FastNative", "java.lang.Daemons", "java.lang.VMClassLoader", "java.lang.BootClassLoader",
        "java.lang.AndroidHardcodedSystemProperties", "java.lang.invoke.MethodHandleImpl", "java.lang.invoke.Transformers",
        "java.lang.invoke.LambdaForm", "java.lang.invoke.DirectMethodHandle", "java.lang.invoke.VarHandle", "java.lang.invoke.StringConcatFactory",
        "java.lang.Module", "java.lang.StackWalker", "java.lang.ProcessHandle", "java.lang.Record", "java.lang.ref.Cleaner",
        "libcore.io.Libcore", "libcore.io.Os", "libcore.util.EmptyArray", "libcore.icu.ICU", "android.icu.util.ULocale",
        "sun.misc.Unsafe", "sun.misc.Signal", "sun.misc.Cleaner", "sun.reflect.Reflection", "jdk.internal.misc.Unsafe", "jdk.internal.misc.VM",
        "jdk.internal.reflect.Reflection", "jdk.internal.loader.ClassLoaders", "jdk.internal.vm.annotation.Contended", "jdk.internal.misc.SharedSecrets",
        "java.lang.management.ManagementFactory", "com.sun.management.HotSpotDiagnosticMXBean", "sun.management.VMManagement",
        "com.android.internal.os.ZygoteInit", "com.android.internal.os.RuntimeInit", "android.app.ActivityThread", "android.os.SystemProperties",
        "com.android.org.conscrypt.OpenSSLProvider", "org.apache.http.HttpResponse", "org.robolectric.RuntimeEnvironment",
        "org.robolectric.Robolectric", "org.robolectric.shadows.ShadowApplication", "org.robolectric.internal.bytecode.RobolectricInternals",
        "apklayer.host.Main", "apklayer.host.NativeLoader", "de.robv.android.xposed.XposedBridge", "com.saurik.substrate.MS"};

    static void run(Class<?> self) {
        for (String key : PROPERTIES) Out.probe("java.prop." + key, () -> System.getProperty(key));
        Out.probe("java.props.keys", () -> new TreeSet<>(System.getProperties().stringPropertyNames()).toString());
        Out.probe("java.props.count", () -> System.getProperties().size());
        Out.probe("java.props.hostish", () -> {  // keys a real ART never has
            List<String> host = new ArrayList<>();
            for (String k : System.getProperties().stringPropertyNames())
                if (k.startsWith("sun.") || k.startsWith("jdk.") || k.startsWith("apklayer") || k.startsWith("robolectric") || k.startsWith("javax.")) host.add(k);
            Collections.sort(host);
            return host;
        });
        for (String name : CLASSES) Out.probe("java.class." + name, () -> {
            try { Class<?> c = Class.forName(name, false, self.getClassLoader()); return "present loader=" + loaderName(c.getClassLoader()); }
            catch (ClassNotFoundException e) { return "absent"; }
        });
        Out.probe("java.cnfe.message", () -> {
            try { Class.forName("no.such.Klass", false, self.getClassLoader()); return "found"; }
            catch (ClassNotFoundException e) { return shorten(e.getMessage()) + " cause=" + e.getCause(); }
        });
        loaders(self);
        stacks(self);
        vm();
    }

    static String loaderName(ClassLoader l) { return l == null ? "null(boot)" : l.getClass().getName(); }

    static String shorten(String s) {
        return s == null ? "null" : s.replaceAll("/data/app/[^\\]\"]*?/hello\\.identity[^/\\]\"]*", "/data/app/<APP>").replaceAll("\\d{4,}", "<N>");
    }

    private static void loaders(Class<?> self) {
        Out.probe("java.loader.app.class", () -> loaderName(self.getClassLoader()));
        Out.probe("java.loader.app.toString", () -> shorten(String.valueOf(self.getClassLoader())));
        Out.probe("java.loader.chain", () -> {
            StringBuilder b = new StringBuilder();
            for (ClassLoader l = self.getClassLoader(); l != null; l = l.getParent()) b.append(l.getClass().getName()).append(" -> ");
            return b.append("null").toString();
        });
        Out.probe("java.loader.app.parent.toString", () -> String.valueOf(self.getClassLoader().getParent()));
        Out.probe("java.loader.system.class", () -> loaderName(ClassLoader.getSystemClassLoader()));
        Out.probe("java.loader.system.toString", () -> shorten(String.valueOf(ClassLoader.getSystemClassLoader())));
        Out.probe("java.loader.system.parent", () -> loaderName(ClassLoader.getSystemClassLoader().getParent()));
        Out.probe("java.loader.context", () -> loaderName(Thread.currentThread().getContextClassLoader()));
        Out.probe("java.loader.String", () -> loaderName(String.class.getClassLoader()));
        Out.probe("java.loader.Activity", () -> loaderName(android.app.Activity.class.getClassLoader()));
        Out.probe("java.loader.bootParentLoadsFramework", () -> {
            ClassLoader parent = self.getClassLoader().getParent();
            return parent == null ? "no parent" : loaderName(parent.loadClass("android.app.Activity").getClassLoader());
        });
        Out.probe("java.loader.resource.parent", () -> String.valueOf(self.getClassLoader().getParent().getResource("java/lang/Object.class")));
        Out.probe("java.loader.resource.app", () -> shorten(String.valueOf(self.getClassLoader().getResource("AndroidManifest.xml"))));
        Out.probe("java.loader.resource.objectClass", () -> String.valueOf(Object.class.getResource("Object.class")));
        Out.probe("java.dex.pathList", () -> {
            Field f = Class.forName("dalvik.system.BaseDexClassLoader").getDeclaredField("pathList");
            f.setAccessible(true);
            Object list = f.get(self.getClassLoader());
            return list == null ? "null" : list.getClass().getName() + " " + shorten(list.toString());
        });
        Out.probe("java.dex.dexElements", () -> {
            Field f = Class.forName("dalvik.system.BaseDexClassLoader").getDeclaredField("pathList");
            f.setAccessible(true);
            Object list = f.get(self.getClassLoader());
            Field e = list.getClass().getDeclaredField("dexElements");
            e.setAccessible(true);
            Object[] elements = (Object[]) e.get(list);
            StringBuilder b = new StringBuilder(elements.length + ":");
            for (Object el : elements) {
                b.append(' ').append(shorten(String.valueOf(el)));
                try {
                    Field df = el.getClass().getDeclaredField("dexFile"); df.setAccessible(true);
                    Object dex = df.get(el);
                    b.append(" dexFile=").append(dex == null ? "null" : dex.getClass().getName());
                    if (dex != null) {
                        Field cookie = dex.getClass().getDeclaredField("mCookie"); cookie.setAccessible(true);
                        Object c = cookie.get(dex);
                        b.append(" cookie=").append(c == null ? "null" : c.getClass().getSimpleName() + "[" + java.lang.reflect.Array.getLength(c) + "]");
                    }
                } catch (Throwable t) { b.append(" dexFile:").append(t.getClass().getSimpleName()).append(' ').append(shorten(t.getMessage())); }
            }
            return b.toString();
        });
        Out.probe("java.dex.nativeLibraryDirectories", () -> {
            Field f = Class.forName("dalvik.system.BaseDexClassLoader").getDeclaredField("pathList");
            f.setAccessible(true);
            Object list = f.get(self.getClassLoader());
            Field n = list.getClass().getDeclaredField("nativeLibraryDirectories");
            n.setAccessible(true);
            return shorten(String.valueOf(n.get(list)));
        });
        Out.probe("java.dex.findLibrary", () -> {
            Method m = ClassLoader.class.getDeclaredMethod("findLibrary", String.class);
            m.setAccessible(true);
            return shorten(String.valueOf(m.invoke(self.getClassLoader(), "hello")));
        });
        Out.probe("java.dex.BaseDexClassLoader.fields", () -> fieldNames(Class.forName("dalvik.system.BaseDexClassLoader")));
        Out.probe("java.dex.ClassLoader.fields", () -> fieldNames(ClassLoader.class));
    }

    static String fieldNames(Class<?> c) {
        TreeSet<String> names = new TreeSet<>();
        for (Field f : c.getDeclaredFields()) names.add(f.getName() + ":" + f.getType().getSimpleName());
        return names.toString();
    }

    private static boolean hostFrame(String cls) {
        return cls.startsWith("jdk.internal.") || cls.startsWith("sun.reflect") || cls.startsWith("org.robolectric") || cls.startsWith("apklayer.")
                || cls.startsWith("java.lang.invoke.LambdaForm") || cls.startsWith("org.junit") || cls.contains("$$Lambda$");
    }

    private static void stacks(Class<?> self) {
        Out.probe("java.stack.exception", () -> frames(new Exception().getStackTrace()));
        Out.probe("java.stack.thread", () -> frames(Thread.currentThread().getStackTrace()));
        Out.probe("java.stack.reflectInvoke", () -> {
            Method m = JavaProbes.class.getDeclaredMethod("traceHere");
            return frames((StackTraceElement[]) m.invoke(null), 6);
        });
        Out.probe("java.reflect.packagePrivate", () -> JavaProbes.class.getDeclaredMethod("secret", int.class).invoke(null, 41));
        Out.probe("java.reflect.privateInstance", () -> {
            Method m = Main.class.getDeclaredMethod("hidden"); // a private method of another class: IllegalAccessException on Android too
            try { return m.invoke(new Object()); } catch (IllegalAccessException | IllegalArgumentException e) { return e.getClass().getSimpleName(); }
        });
        Out.probe("java.stack.printTop", () -> {
            java.io.StringWriter w = new java.io.StringWriter();
            new Exception("probe").printStackTrace(new java.io.PrintWriter(w));
            String[] lines = w.toString().split("\n");
            return lines.length + " lines: " + lines[0] + " / " + lines[1].trim() + " / " + lines[lines.length - 1].trim();
        });
        Out.probe("java.reflect.unsafeGetUnsafe", () -> sun.misc.Unsafe.class.getDeclaredMethod("getUnsafe").invoke(null) != null);  // HiddenApiBypass's first step
        Out.probe("java.reflect.unsafeMethods", () -> { Method[] all = sun.misc.Unsafe.class.getDeclaredMethods(); int n = 0; for (Method m : all) if (m.getName().startsWith("get")) n++; return all.length + " methods, " + n + " get*, class loader " + sun.misc.Unsafe.class.getClassLoader(); });
        Out.probe("java.reflect.unsafeNames", () -> { java.util.TreeSet<String> s = new java.util.TreeSet<>(); for (Method m : sun.misc.Unsafe.class.getDeclaredMethods()) if (m.getName().startsWith("get") && !m.getName().startsWith("getInt") && !m.getName().startsWith("getL") && !m.getName().startsWith("getB") && !m.getName().startsWith("getO") && !m.getName().startsWith("getS") && !m.getName().startsWith("getC") && !m.getName().startsWith("getD") && !m.getName().startsWith("getF")) s.add(m.getName()); return s.toString(); });
        Out.probe("java.stack.callerOfStatic", () -> {
            Class<?> vmstack = Class.forName("dalvik.system.VMStack");
            Method m = vmstack.getDeclaredMethod("getCallingClassLoader");
            return loaderName((ClassLoader) m.invoke(null));
        });
        Out.probe("java.stack.allThreads", () -> {
            TreeMap<String, Integer> names = new TreeMap<>();
            for (Thread t : Thread.getAllStackTraces().keySet()) names.merge(t.getName().replaceAll("\\d+", "N"), 1, Integer::sum);
            return names.toString();
        });
        Out.probe("java.thread.main", () -> {
            Thread t = Thread.currentThread();
            return "name=" + t.getName() + " id=" + t.getId() + " prio=" + t.getPriority() + " group=" + t.getThreadGroup().getName()
                    + " daemon=" + t.isDaemon() + " class=" + t.getClass().getName();
        });
        Out.probe("java.thread.new", () -> {
            Thread t = new Thread(() -> {});
            return "name=" + t.getName() + " group=" + t.getThreadGroup().getName();
        });
    }

    static String secret(int n) { return "secret" + (n + 1); }

    static StackTraceElement[] traceHere() { return new Throwable().getStackTrace(); }

    static String frames(StackTraceElement[] trace) { return frames(trace, trace.length); }

    static String frames(StackTraceElement[] trace, int limit) {
        StringBuilder b = new StringBuilder("depth=" + trace.length);
        int host = 0;
        for (StackTraceElement f : trace) if (hostFrame(f.getClassName())) host++;
        b.append(" hostFrames=").append(host);
        for (int i = 0; i < Math.min(limit, trace.length); i++) b.append(" | ").append(trace[i]);
        return b.toString();
    }

    private static void vm() {
        Out.probe("java.vmdebug.features", () -> {
            Method m = Class.forName("dalvik.system.VMDebug").getDeclaredMethod("getVmFeatureList");
            return java.util.Arrays.toString((String[]) m.invoke(null));
        });
        Out.probe("java.vmdebug.isDebuggerConnected", () -> android.os.Debug.isDebuggerConnected());
        Out.probe("java.debug.runtimeStats", () -> new TreeMap<>(android.os.Debug.getRuntimeStats()).keySet().toString());
        Out.probe("java.debug.gcCount", () -> android.os.Debug.getRuntimeStat("art.gc.gc-count"));
        Out.probe("java.runtime.version", () -> String.valueOf(Runtime.class.getMethod("version").invoke(null)));
        Out.probe("java.runtime.cpus", () -> Runtime.getRuntime().availableProcessors());
        Out.probe("java.runtime.maxMemoryMB", () -> Runtime.getRuntime().maxMemory() >> 20);
        Out.probe("java.vmruntime", () -> {
            Class<?> t = Class.forName("dalvik.system.VMRuntime");
            Object r = t.getMethod("getRuntime").invoke(null);
            StringBuilder b = new StringBuilder();
            for (String n : new String[]{"vmVersion", "vmLibrary", "vmInstructionSet", "is64Bit", "isCheckJniEnabled", "isNativeDebuggable",
                    "isJavaDebuggable", "getTargetSdkVersion", "getTargetHeapUtilization"}) {
                try { b.append(n).append('=').append(t.getMethod(n).invoke(r)).append(' '); }
                catch (Throwable e) { b.append(n).append("=!").append(e.getClass().getSimpleName()).append(' '); }
            }
            for (String n : new String[]{"properties", "bootClassPath", "classPath"}) {
                try {
                    Object v = t.getMethod(n).invoke(r);
                    b.append(n).append('=').append(v instanceof String[] ? ((String[]) v).length + "entries" : shorten(String.valueOf(v))).append(' ');
                } catch (Throwable e) { b.append(n).append("=!").append(e.getClass().getSimpleName()).append(' '); }
            }
            return b.toString().trim();
        });
        Out.probe("java.security.providers", () -> {
            StringBuilder b = new StringBuilder();
            for (java.security.Provider p : java.security.Security.getProviders()) b.append(p.getName()).append(' ');
            return b.toString().trim();
        });
        Out.probe("java.ssl.default", () -> javax.net.ssl.SSLContext.getDefault().getProvider().getName());
        Out.probe("java.charset", () -> java.nio.charset.Charset.defaultCharset().name());
        Out.probe("java.methodhandle.class", () -> java.lang.invoke.MethodHandles.lookup()
                .findVirtual(String.class, "length", java.lang.invoke.MethodType.methodType(int.class)).getClass().getName());
        Out.probe("java.proxy.class", () -> java.lang.reflect.Proxy.newProxyInstance(JavaProbes.class.getClassLoader(), new Class<?>[]{Runnable.class},
                (p, m, a) -> null).getClass().getName());
        Out.probe("java.lambda.class", () -> { Runnable r = () -> {}; return r.getClass().getName().replaceAll("\\d+", "N"); });
        Out.probe("java.Object.hashCode.toString", () -> Object.class.getMethod("hashCode").toString());
        Out.probe("java.Thread.currentThread.toString", () -> Thread.class.getMethod("currentThread").toString());
        Out.probe("java.Object.getClass.toString", () -> Object.class.getMethod("getClass").toString());
        Out.probe("java.env.keys", () -> new TreeSet<>(System.getenv().keySet()).toString());
        Out.probe("java.mapLibraryName", () -> System.mapLibraryName("x"));
        Map<String, String> unused = null;
    }
}
