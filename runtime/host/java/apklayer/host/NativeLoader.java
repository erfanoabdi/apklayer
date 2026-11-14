package apklayer.host;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.robolectric.annotation.internal.DoNotInstrument;

/**
 * Loads an app's native libraries (PLAN archive P4). The converter output routes the app's
 * {@code System.loadLibrary/load} and {@code Runtime.loadLibrary/load} calls here (DenyInstrument).
 * The library is mapped by bionic_translation's linker (it is linked against Android's bionic, not
 * glibc), its JNI_OnLoad runs, and every exported {@code Java_...} function is bound to its native
 * method with JNI RegisterNatives (HotSpot's own lookup cannot see libraries it did not load).
 *
 * <p>Needs the native runtime meson installs ({@code -Dapklayer.native=<dir>}) and
 * the app's extracted libs ({@code -Dapklayer.libdir=<dir>}).
 */
@DoNotInstrument
public final class NativeLoader {
    private static final Map<String, Long> LOADED = new HashMap<>();
    private static boolean ready;
    private static volatile boolean environmentTargetReady;
    private static volatile LinkageError environmentSetupFailure;
    private static String systemPath = "", appPath = "";
    /** The CPU the app's libraries are for when it is not this one ("arm64" on x86_64): they load through the native bridge (native_bridge.c, set at install). */
    private static String bridge() { return System.getProperty("apklayer.nativebridge"); }  // read when used: a spare-born process gets it with its launch

    private NativeLoader() {}

    /** The linker's two namespaces: the system's (device libraries) and the app's own (its libraries first, then the system's), as Android's classloader namespace. */
    private static native void setLibraryPath(String system, String app);

    private static native long dlopen(String path);

    private static native long dlsym(long handle, String name);

    private static native String dlerror();

    /** JNI_OnLoad, with FindClass answering through that loader meanwhile (the library's, as ART's System.load gives it). */
    private static native int callOnLoad(long fn, ClassLoader loader);

    /** The native bridge (native_bridge.c): null when ready, else why not. */
    private static native String bridgeInit(String bridge, String isa, String libdir, String codeCache);

    private static native long bridgeLoad(String path);

    private static native String bridgeError();

    /** A trampoline for a guest function: a JNI method by its shorty, JNI_OnLoad with none. */
    private static native long bridgeSymbol(long handle, String name, String shorty);

    private static native int registerNative(Class<?> target, String name, String sig, long fn);

    private static native void skipNativeExitHandlers();
    private static native void setProcessName(String name);
    private static native void mapOwnData(String pkg, int uid, String groups, String root);
    /** An app spare's launch (spare_target.c): the message on fd, whose stdout and stderr replace this process's; null when the holder is gone. */
    static native String receiveTarget(int fd);
    /** Every class the JVM has loaded, of every loader (JVMTI, app_native.c): CdsDump links them before the dump; null when JVMTI refuses. */
    static native Class<?>[] loadedClasses();

    /** The process's command line and name as Android's Process.setArgV0 sets them (native code reads /proc/self/cmdline). */
    static void nameProcess(String name) {
        try { setProcessName(name); } catch (LinkageError noShim) { /* no native runtime in this process */ }
    }

    /** True once this process maps the app's /data/data, /data/user/0 and /data/user_de/0 names to its data directories (mapOwnDataDirs). */
    static boolean ownDataMapped() { return environmentTargetReady; }

    /** The app's own /data/data/<pkg>, /data/user/0/<pkg> (root/app) and /data/user_de/0/<pkg> (root/device) for Java's and native file calls, and its
     *  uid and groups as native code sees them: getuid, getgroups and the owner of the user's files (bridge/native/paths.c). */
    static void mapOwnDataDirs(String pkg, String root) {
        int uid = android.os.Process.myUid();
        try {
            mapOwnData(pkg, uid, appGroups(pkg, uid), root);
            environmentTargetReady = true;
        } catch (LinkageError noShim) {
            environmentSetupFailure = noShim;
        }
    }

    /** The supplementary groups zygote gives an app process (ProcessList.startProcessLocked; cli/sandbox.cpp appGroups): inet with INTERNET,
     *  everybody, its cache and shared app gids, ascending as the kernel keeps them. */
    static String appGroups(String pkg, int uid) {
        int appId = uid % 100000 - 10000;
        if (appId < 0) return "";
        boolean inet = false;
        String apps = System.getProperty("apklayer.apps");
        if (apps != null) {
            try (var in = Files.newBufferedReader(new File(new File(apps, pkg), "info.properties").toPath())) {
                var info = new java.util.Properties();
                info.load(in);
                inet = java.util.Arrays.asList(info.getProperty("permissions", "").split(";")).contains("android.permission.INTERNET");
            } catch (IOException none) { /* no install record: no permission gids */ }
        }
        return (inet ? "3003," : "") + "9997," + (20000 + appId) + "," + (50000 + appId);
    }

    /** The process is about to exit: it ends as Android ends an app process (killed), without the native libraries' static destructors (native_loader.c). */
    static void endWithoutNativeDestructors() {
        try { skipNativeExitHandlers(); } catch (LinkageError noShim) { /* no native runtime in this process: a plain exit */ }
    }

    private static final int EXIT_DEADLINE_S = 10;  // past `apklayer stop`'s 5 s and the hub's 4 s for its hosts: only a stuck end reaches it
    private static native void exitDeadline(int seconds);
    /** From the start of the JVM's end (SIGTERM, System.exit) the process has EXIT_DEADLINE_S, whatever a hook or HotSpot's exit waits for (native_loader.c). */
    static void killIfExitStalls() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { exitDeadline(EXIT_DEADLINE_S); } catch (LinkageError noShim) { /* no native runtime: HotSpot's own exit */ }
        }, "apklayer-exit-deadline"));
    }

    // ---- entry points the converter routes the app's calls to ----

    public static String findLibrary(String name) {
        String dir = System.getProperty("apklayer.libdir", "");
        File f = new File(dir, "lib" + name + ".so");
        String provider = System.getProperty("apklayer.webviewprovider");
        if (!f.isFile() && provider != null) f = new File(provider + "/lib", "lib" + name + ".so");
        String androidLibs = System.getProperty("apklayer.androidlibs");
        if (!f.isFile() && androidLibs != null) f = new File(androidLibs, "lib" + name + ".so");
        String bionic = System.getenv("BIONIC_LD_LIBRARY_PATH");  // the device libraries (liblog, libandroid...): apps that load them by name (SoLoader)
        if (!f.isFile() && bionic != null) for (String dir2 : bionic.split(":")) { File g = new File(dir2, "lib" + name + ".so"); if (g.isFile()) { f = g; break; } }
        return f.isFile() ? f.getAbsolutePath() : null;
    }

    private static final StackWalker CALLER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public static void loadLibrary(String name) {
        loadLibrary(name, CALLER.getCallerClass().getClassLoader());
    }

    private static void loadLibrary(String name, ClassLoader loader) {
        String path = bridge() != null && new File(System.getProperty("apklayer.libdir", ""), "lib" + name + ".so").isFile() ? bridgeLibrary(name) : findLibrary(name);
        if (path == null) {  // ART's text (nativeloader's dlopen of the bare name); where it was looked for goes to our log
            System.err.println("[apklayer] loadLibrary: no lib" + name + ".so in " + System.getProperty("apklayer.libdir", "") + " (ABI not in the APK?)");
            throw new UnsatisfiedLinkError("dlopen failed: library \"" + System.mapLibraryName(name) + "\" not found");
        }
        load(path, loader);
    }

    /** The app's own library under the native bridge (its other names are this CPU's: the WebView provider's, the system's). */
    private static String bridgeLibrary(String name) {
        return new File(System.getProperty("apklayer.libdir", ""), "lib" + name + ".so").getAbsolutePath();
    }

    /** A library for the bridge's CPU (an AArch64 ELF): it loads through the native bridge; any other (the WebView provider's) natively. */
    private static boolean guest(String path) {
        if (bridge() == null) return false;
        try (java.io.RandomAccessFile f = new java.io.RandomAccessFile(path, "r")) {
            f.seek(18);
            return (f.read() | f.read() << 8) == 183;  // e_machine EM_AARCH64
        } catch (IOException notAFile) {
            return false;
        }
    }

    public static void load(String path) {
        load(path, CALLER.getCallerClass().getClassLoader());
    }

    /** Loads the library for the classes of that loader (the caller's, as System.load on Android): its JNI_OnLoad and its Java_* functions see them. */
    public static void load(String path, ClassLoader loader) {
        File asked = new File(path);
        if (!asked.isFile()) {  // the app extracted its libraries to a path that does not exist here (OpenTTD: files/../lib): the same name in the app's lib dir is the library
            File alt = new File(System.getProperty("apklayer.libdir", ""), asked.getName());
            if (alt.isFile()) path = alt.getPath();
        }
        synchronized (NativeLoader.class) {
            if (LOADED.containsKey(path)) return;
            init();
            boolean guest = guest(path);
            long handle = guest ? bridgeLoad(path) : dlopen(path); // by path: a library in an app directory goes into the app's namespace
            if (handle == 0 && !guest && path.startsWith("/")) {  // a library elsewhere (SoLoader's decompressed SuperPack libraries in the app's data): its directory joins the app's path
                try { appPath += ":" + new File(path).getParentFile().getCanonicalPath(); setLibraryPath(systemPath, appPath); } catch (java.io.IOException unreadable) { /* the error below says so */ }
                handle = dlopen(path);
            }
            if (handle == 0) throw new UnsatisfiedLinkError(path + ": " + (guest ? bridgeError() : dlerror()));
            LOADED.put(path, handle);
            long onLoad = guest ? bridgeSymbol(handle, "JNI_OnLoad", null) : dlsym(handle, "JNI_OnLoad");
            if (onLoad != 0) {
                int version = callOnLoad(onLoad, loader);
                if (version < 0) throw new UnsatisfiedLinkError(path + ": JNI_OnLoad failed");
            }
            int bound = bindByName(path, handle, loader, guest);
            System.out.println("[apklayer] loaded " + new File(path).getName() + " (JNI_OnLoad "
                    + (onLoad != 0 ? "ran" : "absent") + ", " + bound + " natives bound by name)");
        }
    }

    public static void runtimeLoadLibrary(Runtime r, String name) {
        loadLibrary(name, CALLER.getCallerClass().getClassLoader());
    }

    public static void runtimeLoad(Runtime r, String path) {
        load(path, CALLER.getCallerClass().getClassLoader());
    }

    /** Framework bootstrap/spares use the native backend's service view. Only a mapped app
     * may initialize its ABI route; a spare does not yet know that route or its library path. */
    static void ensureInitialized() {
        if (environmentSetupFailure != null) throw environmentSetupFailure;
        if (!environmentTargetReady) return;
        synchronized (NativeLoader.class) { init(); }
    }

    private static void init() {
        if (ready) return;
        String provider = System.getProperty("apklayer.webviewprovider");
        String androidLibs = System.getProperty("apklayer.androidlibs"); // the device-library route: Android's own libraries stay on the path (set by the runtime shim)
        appPath = System.getProperty("apklayer.libdir", "");
        // only Android (bionic) libraries: apklayer's native dir holds glibc ones, which bionic_translation's dlopen leaves to glibc (LD_LIBRARY_PATH) when they are not here
        systemPath = (provider == null ? "" : provider + "/lib") + (androidLibs != null ? (provider == null ? "" : ":") + androidLibs : "");
        setLibraryPath(systemPath, appPath); // the WebView provider's libs, then Android's
        if (bridge() != null) {  // the bridge library (an Android library of this CPU, on the system path) and the app's guest namespace
            long start = System.nanoTime();
            String error = bridgeInit("libberberis_" + bridge() + ".so", bridge(), appPath, "/data/user/0/" + System.getProperty("apklayer.package", "") + "/code_cache");
            if (error != null) throw new UnsatisfiedLinkError("native bridge: " + error);
            System.out.println("[apklayer] native bridge " + bridge() + " ready (" + (System.nanoTime() - start) / 1_000_000 + " ms)");
        }
        // `apklayer stop` (SIGTERM) and System.exit end the JVM through exit() on its VM thread too: without this the app libraries' static destructors
        // run there (OpenTTD's terminate()d, SIGSEGV in vm_exit); the hooks run before exit(), so the skip is still registered last
        Runtime.getRuntime().addShutdownHook(new Thread(NativeLoader::endWithoutNativeDestructors, "apklayer-native-exit"));
        ready = true;
    }

    // ---- binding Java_* functions by name ----

    private static int bindByName(String path, long handle, ClassLoader cl, boolean guest) {
        int bound = 0;
        for (String symbol : exportedJavaFunctions(path)) {
            try {
                String[] parts = demangle(symbol);
                Class<?> cls = Class.forName(parts[0], false, cl);
                long fn = guest ? 0 : dlsym(handle, symbol);
                for (Method m : cls.getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isNative(m.getModifiers()) || !m.getName().equals(parts[1])) continue;
                    String sig = descriptor(m);
                    if (parts[2] != null && !sig.startsWith("(" + parts[2] + ")")) continue; // overload suffix: the whole argument list ("__" is no arguments)
                    if (guest) fn = bridgeSymbol(handle, symbol, shorty(m)); // a trampoline per signature
                    if (registerNative(cls, m.getName(), sig, fn) == 0) bound++;
                }
            } catch (ClassNotFoundException | LinkageError e) {
                // a class of this library that the app never ships: nothing to bind
            }
        }
        return bound;
    }

    /**
     * JNI short/long name -> {class name, method name, parameter descriptor of a long (overloaded)
     * name or null}. In a mangled name '_' followed by 0-3 is an escape ("_1" = '_', "_2" = ';',
     * "_3" = '[', "_0xxxx" = a UTF-16 unit); any other '_' stands for '.' / '/'.
     */
    static String[] demangle(String symbol) {
        String rest = symbol.substring("Java_".length());
        String params = null;
        int longName = rest.indexOf("__");
        // "Native__1getPointer" is class Native, method _getPointer ("_1" escapes an underscore): the overload separator is not followed by an escape digit
        while (longName >= 0 && longName + 2 < rest.length() && rest.charAt(longName + 2) >= '0' && rest.charAt(longName + 2) <= '3') longName = rest.indexOf("__", longName + 1);
        if (longName >= 0) {
            params = unmangle(rest.substring(longName + 2), '/').get(0);
            rest = rest.substring(0, longName);
        }
        java.util.List<String> parts = unmangle(rest, '\0'); // split at every separator
        String method = parts.remove(parts.size() - 1);
        return new String[] {String.join(".", parts), method, params};
    }

    /** Unmangles s; each separator '_' either splits the result (sep == 0) or becomes sep. */
    private static java.util.List<String> unmangle(String s, char sep) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char next = i + 1 < s.length() ? s.charAt(i + 1) : 0;
            if (c != '_') {
                cur.append(c);
            } else if (next == '1') {
                cur.append('_');
                i++;
            } else if (next == '2') {
                cur.append(';');
                i++;
            } else if (next == '3') {
                cur.append('[');
                i++;
            } else if (next == '0' && i + 5 < s.length() + 1) {
                cur.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                i += 5;
            } else if (sep == 0) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(sep);
            }
        }
        out.add(cur.toString());
        return out;
    }

    /** JNI's shorty of a method: the return type, then the parameters, every reference type as L. */
    static String shorty(Method m) {
        StringBuilder sb = new StringBuilder(shortyChar(m.getReturnType()));
        for (Class<?> p : m.getParameterTypes()) sb.append(shortyChar(p));
        return sb.toString();
    }

    private static String shortyChar(Class<?> c) {
        String d = descriptor(c);
        return d.length() == 1 ? d : "L";
    }

    private static String descriptor(Method m) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : m.getParameterTypes()) sb.append(descriptor(p));
        return sb.append(')').append(descriptor(m.getReturnType())).toString();
    }

    private static String descriptor(Class<?> c) {
        if (c == void.class) return "V";
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == char.class) return "C";
        if (c == short.class) return "S";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        if (c.isArray()) return "[" + descriptor(c.getComponentType());
        return "L" + c.getName().replace('.', '/') + ";";
    }

    /** Names of the defined dynamic symbols that start with "Java_" (ELF64 or, in the 32-bit app process, ELF32; little endian). */
    private static java.util.List<String> exportedJavaFunctions(String path) {
        java.util.List<String> names = new java.util.ArrayList<>();
        try {
            ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(new File(path).toPath())).order(ByteOrder.LITTLE_ENDIAN);
            if (b.getInt(0) != 0x464c457f || (b.get(4) != 1 && b.get(4) != 2)) return names; // not ELF
            boolean e64 = b.get(4) == 2;
            long shoff = e64 ? b.getLong(0x28) : b.getInt(0x20) & 0xffffffffL;
            int shentsize = b.getShort(e64 ? 0x3A : 0x2E) & 0xffff, shnum = b.getShort(e64 ? 0x3C : 0x30) & 0xffff;
            int symEnt = e64 ? 24 : 16, shndx = e64 ? 6 : 14;  // Elf64_Sym / Elf32_Sym: size, st_shndx
            for (int i = 0; i < shnum; i++) {
                int sh = (int) shoff + i * shentsize;
                if (b.getInt(sh + 4) != 11) continue; // SHT_DYNSYM
                long symOff = e64 ? b.getLong(sh + 0x18) : b.getInt(sh + 0x10), symSize = e64 ? b.getLong(sh + 0x20) : b.getInt(sh + 0x14);
                int strSh = (int) shoff + b.getInt(sh + (e64 ? 0x28 : 0x18)) * shentsize;
                long strOff = e64 ? b.getLong(strSh + 0x18) : b.getInt(strSh + 0x10);
                for (long s = symOff; s + symEnt <= symOff + symSize; s += symEnt) {
                    int nameIdx = b.getInt((int) s);
                    if ((b.getShort((int) s + shndx) & 0xffff) == 0) continue; // undefined
                    int p = (int) strOff + nameIdx, end = p;
                    while (b.get(end) != 0) end++;
                    String name = new String(b.array(), p, end - p, StandardCharsets.US_ASCII);
                    if (name.startsWith("Java_")) names.add(name);
                }
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("[apklayer] cannot read symbols of " + path + ": " + e);
        }
        return names;
    }
}
