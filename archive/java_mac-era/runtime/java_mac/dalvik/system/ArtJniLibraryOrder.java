// dalvik.system.ArtJniLibraryOrder -- make HotSpot resolve an unregistered
// native method in the SAME library order ART does: per class loader, the
// loaded libraries in PATH-SORTED order.
//
// WHY. When two libraries loaded by one class loader both export the same
// Java_* symbol, the library searched first wins the binding.
//
//   ART (art runtime/jni/java_vm_ext.cc, main, FindNativeMethodInternal
//   lines 319-345 and the member at line 412):
//       for (const auto& lib : libraries_) {
//         SharedLibrary* const library = lib.second;
//         if (library->GetClassLoaderAllocator() != declaring_class_loader_allocator) {
//           // We only search libraries loaded by the appropriate ClassLoader.
//           continue;
//         }
//         // Try the short name then the long name...
//       ...
//       AllocationTrackingSafeMap<std::string, SharedLibrary*, kAllocatorTagJNILibraries> libraries_
//   and LoadNativeLibrary does `libraries_->Put(path, library)` (line 1108).
//   SafeMap is "Equivalent to std::map" with `Comparator = std::less<K>`
//   (libartbase/base/safe_map.h:28-35), so ART walks the libraries in
//   ascending std::string order of the PATH: byte-wise, unsigned-char
//   comparison (char_traits<char>::lt is specified "identically to the
//   built-in operator< for type unsigned char").
//
//   HotSpot (JDK 21 jdk/internal/loader/NativeLibraries.java):
//       58:  private final Map<String, NativeLibraryImpl> libraries = new ConcurrentHashMap<>();
//       102: for (NativeLibrary lib : libraries.values()) {
//   keyed by the canonical path (line 129 file.getCanonicalPath(), line 217
//   libraries.put(name, lib)). ClassLoader.findNative(loader, name) calls
//   loader.libraries.find(name) (ClassLoader.java:2464-2469), so HotSpot
//   walks the libraries in ConcurrentHashMap BIN order, i.e. by the hash of
//   the path. The winner therefore depends on WHERE the app is installed.
//
// Measured: Play-Minecraft 1.26.45.1 ships
// Java_com_xbox_httpclient_HttpClientRequest_OnRequestCompleted in BOTH
// libHttpClient.Android.so (0x3a1dc) and libPlayFabMultiplayer.so (0x383f38,
// a pairip-protected image whose .text there is still ciphertext). ART binds
// libHttpClient.Android ('H' < 'P'). HotSpot bound PlayFab's copy on every
// install root whose path hashes PlayFab first, and the first Xbox HTTP
// response then died with SIGILL at libPlayFabMultiplayer+0xdf544
// (docs/jni-native-binding-art-library-order_2026-10-01.md).
//
// HOW. HotSpot keeps the map private and final; there is no API to order it.
// So each Android class loader (every dalvik.system loader is a
// BaseDexClassLoader, which calls apply() from both constructors, before it
// can have loaded anything) gets its NativeLibraries map replaced by a
// ConcurrentSkipListMap sorted the way std::less<std::string> sorts: by the
// path's UTF-8 bytes, unsigned. NativeLibraries only uses isEmpty / get / put /
// values on it, all of which ConcurrentSkipListMap provides with the same
// concurrency guarantees; values() iterates in ascending key order.
// Reaching the map needs the java.lang open (ClassLoader.libraries), the
// patched jdk.internal.reflect.Reflection that stops filtering ClassLoader's
// fields (runtime/java_base_patch), and --add-opens
// java.base/jdk.internal.loader (runtime/src/jvm_loader.cpp).
//
// GATE. MACAPK_JNI_ART_LIB_ORDER (default ON). =0 leaves HotSpot's
// ConcurrentHashMap in place: the pre-change path, byte for byte.
// Offline gate: debug/jni_art_order_gate.sh (`jniorder` in debug/gates.sh).
package dalvik.system;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

final class ArtJniLibraryOrder {
    private ArtJniLibraryOrder() {}

    private static final boolean ENABLED = !"0".equals(System.getenv("MACAPK_JNI_ART_LIB_ORDER"));
    private static volatile boolean warned;
    private static volatile boolean announced;

    /** std::less<std::string> on the UTF-8 path: lexicographic, unsigned bytes. */
    static final Comparator<String> PATH_ORDER = (a, b) -> {
        if (a.equals(b)) return 0;
        return Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),
                                      b.getBytes(StandardCharsets.UTF_8));
    };

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void apply(ClassLoader loader) {
        if (!ENABLED || loader == null) return;
        try {
            Field lf = ClassLoader.class.getDeclaredField("libraries");
            lf.setAccessible(true);
            Object nl = lf.get(loader);
            if (nl == null) return;
            Field mf = nl.getClass().getDeclaredField("libraries");
            mf.setAccessible(true);
            Map old = (Map) mf.get(nl);
            if (old instanceof ConcurrentSkipListMap) return;
            ConcurrentSkipListMap sorted = new ConcurrentSkipListMap(PATH_ORDER);
            if (old != null) sorted.putAll(old);
            mf.set(nl, sorted);
            if (!announced) {
                // One line per process: the live arm confirmation for a log reader.
                announced = true;
                System.err.println("[jni-order] JNI libraries searched in ART path-sorted order per class loader"
                        + " (MACAPK_JNI_ART_LIB_ORDER=0 restores HotSpot hash order)");
            }
        } catch (Throwable t) {
            // Never succeed inertly: a JDK whose NativeLibraries changed shape,
            // or a launch without the opens, would silently fall back to hash
            // order -- say so once.
            if (!warned) {
                warned = true;
                System.err.println("[jni-order] WARN: could not install ART path-sorted JNI library order ("
                        + t + "); native lookup stays in HotSpot ConcurrentHashMap order");
            }
        }
    }
}
