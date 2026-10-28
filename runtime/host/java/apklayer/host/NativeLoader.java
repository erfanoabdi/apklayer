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
 * Loads an app's native libraries (PLAN P4). The converter output routes the app's
 * {@code System.loadLibrary/load} and {@code Runtime.loadLibrary/load} calls here (DenyInstrument).
 * The library is mapped by bionic_translation's linker (it is linked against Android's bionic, not
 * glibc), its JNI_OnLoad runs, and every exported {@code Java_...} function is bound to its native
 * method with JNI RegisterNatives (HotSpot's own lookup cannot see libraries it did not load).
 *
 * <p>Needs the staged native runtime (scripts/build-native-arm64.sh) ({@code -Dapklayer.native=<dir>}) and
 * the app's extracted libs ({@code -Dapklayer.libdir=<dir>}).
 */
@DoNotInstrument
public final class NativeLoader {
    private static final Map<String, Long> LOADED = new HashMap<>();
    private static boolean ready;
    private static String searchPath = "";

    private NativeLoader() {}

    private static native void setLibraryPath(String dir);

    private static native long dlopen(String path);

    private static native long dlsym(long handle, String name);

    private static native String dlerror();

    private static native int callOnLoad(long fn);

    private static native int registerNative(Class<?> target, String name, String sig, long fn);

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

    public static void loadLibrary(String name) {
        String path = findLibrary(name);
        if (path == null) throw new UnsatisfiedLinkError("no " + name + " in "
                + System.getProperty("apklayer.libdir", "") + " (ABI not in the APK?)");
        load(path);
    }

    public static void load(String path) {
        File asked = new File(path);
        if (!asked.isFile()) {  // the app extracted its libraries to a path that does not exist here (OpenTTD: files/../lib): the same name in the app's lib dir is the library
            File alt = new File(System.getProperty("apklayer.libdir", ""), asked.getName());
            if (alt.isFile()) path = alt.getPath();
        }
        synchronized (NativeLoader.class) {
            if (LOADED.containsKey(path)) return;
            init();
            long handle = dlopen(new File(path).getName()); // bionic_translation finds a library by name in its path
            if (handle == 0 && path.startsWith("/")) {  // a library elsewhere (SoLoader's decompressed SuperPack libraries in the app's data): its directory joins the search path
                try { searchPath += ":" + new File(path).getParentFile().getCanonicalPath(); setLibraryPath(searchPath); } catch (java.io.IOException unreadable) { /* the error below says so */ }
                handle = dlopen(new File(path).getName());
            }
            if (handle == 0) throw new UnsatisfiedLinkError(path + ": " + dlerror());
            LOADED.put(path, handle);
            long onLoad = dlsym(handle, "JNI_OnLoad");
            if (onLoad != 0) {
                int version = callOnLoad(onLoad);
                if (version < 0) throw new UnsatisfiedLinkError(path + ": JNI_OnLoad failed");
            }
            int bound = bindByName(path, handle);
            System.out.println("[apklayer] loaded " + new File(path).getName() + " (JNI_OnLoad "
                    + (onLoad != 0 ? "ran" : "absent") + ", " + bound + " natives bound by name)");
        }
    }

    public static void runtimeLoadLibrary(Runtime r, String name) {
        loadLibrary(name);
    }

    public static void runtimeLoad(Runtime r, String path) {
        load(path);
    }

    private static void init() {
        if (ready) return;
        String dir = System.getProperty("apklayer.native");
        String provider = System.getProperty("apklayer.webviewprovider");
        String androidLibs = System.getProperty("apklayer.androidlibs"); // the device-library route: Android's own libraries stay on the path (set by the runtime shim)
        searchPath = (System.getProperty("apklayer.libdir", dir) + (provider == null ? "" : ":" + provider + "/lib") + ":" + (androidLibs != null ? androidLibs + ":" : "") + dir);
        setLibraryPath(searchPath); // app libs, the WebView provider's, then the bionic_translation libs
        ready = true;
    }

    // ---- binding Java_* functions by name ----

    private static int bindByName(String path, long handle) {
        int bound = 0;
        ClassLoader cl = NativeLoader.class.getClassLoader();
        for (String symbol : exportedJavaFunctions(path)) {
            try {
                String[] parts = demangle(symbol);
                Class<?> cls = Class.forName(parts[0], false, cl);
                long fn = dlsym(handle, symbol);
                for (Method m : cls.getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isNative(m.getModifiers()) || !m.getName().equals(parts[1])) continue;
                    String sig = descriptor(m);
                    if (parts[2] != null && !sig.startsWith("(" + parts[2])) continue; // overload suffix
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

    /** Names of the defined dynamic symbols that start with "Java_" (ELF64, little endian). */
    private static java.util.List<String> exportedJavaFunctions(String path) {
        java.util.List<String> names = new java.util.ArrayList<>();
        try {
            ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(new File(path).toPath())).order(ByteOrder.LITTLE_ENDIAN);
            if (b.getInt(0) != 0x464c457f || b.get(4) != 2) return names; // not ELF64
            long shoff = b.getLong(0x28);
            int shentsize = b.getShort(0x3A) & 0xffff, shnum = b.getShort(0x3C) & 0xffff;
            for (int i = 0; i < shnum; i++) {
                int sh = (int) shoff + i * shentsize;
                if (b.getInt(sh + 4) != 11) continue; // SHT_DYNSYM
                long symOff = b.getLong(sh + 0x18), symSize = b.getLong(sh + 0x20);
                int strSh = (int) shoff + b.getInt(sh + 0x28) * shentsize;
                long strOff = b.getLong(strSh + 0x18);
                for (long s = symOff; s + 24 <= symOff + symSize; s += 24) {
                    int nameIdx = b.getInt((int) s);
                    if ((b.getShort((int) s + 6) & 0xffff) == 0) continue; // undefined
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
