package apklayer.host;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Template of {@code java.lang.reflect.ApklayerArt} (JdkIdentityPatch copies it into java.base, renamed): the synthetic ART method table behind
 * the {@code Executable.artMethod} field JdkMembers adds. Method and Constructor call {@link #init} at the end of their
 * constructors. Android 13 (64-bit) layout, measured on Waydroid (tests/identity/results/waydroid.txt): an ArtMethod is 32 bytes (declaring_class_
 * u32 @0, access_flags_ u32 @4, dex_method_index_ u32 @8, method_index_ u16 @12, hotness_count_ u16 @14, data_ u64 @16, entry_point_from_quick_
 * compiled_code_ u64 @24); a class's methods are one LengthPrefixedArray (u32 length, data aligned to 8, so element 0 at +8), direct methods
 * first, then virtual ones, so adjacent methods differ by 32. Only java.base API plus jdk.internal.misc.Unsafe (this class runs in java.base,
 * also before the host starts: {@link #enabled} is set by GuestTraces.install).
 * On arm64 the tables live in the JNI arena and the entries ARE the guest's jmethodIDs: data_ holds the JVM's own id (jni_guest_surface.c binds it).
 * shortcut: tables are never freed (a class unloaded leaks 8+32n bytes), entry_point is 0 (no libart code to point at) and
 * declaring_class_ is the class's identity hash, not a heap reference; upgrade with the libart ELF work (docs/PLAN.md runtime identity 11).
 */
public final class JdkArt {
    private JdkArt() {}

    public static volatile boolean enabled;

    static final int PUBLIC_API = 0x10000000;  // kAccPublicApi, what Waydroid shows for an app's own members
    private static final jdk.internal.misc.Unsafe U = jdk.internal.misc.Unsafe.getUnsafe();
    private static final ThreadLocal<Boolean> building = new ThreadLocal<>();

    /** Class -> {Long table address, List<String> method keys in table order}. No nested types: JdkIdentityPatch copies only this one class file. */
    private static final java.util.Map<Class<?>, Object[]> TABLES = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static Object[] table(Class<?> c) {
        Object[] t = TABLES.get(c);
        if (t != null) return t;
        building.set(Boolean.TRUE);  // the Method objects made below keep artMethod 0
        try {
            List<String> direct = new ArrayList<>(), virtual = new ArrayList<>();
            for (Constructor<?> k : c.getDeclaredConstructors()) direct.add(key(k));
            for (Method m : c.getDeclaredMethods()) {
                int mod = m.getModifiers();
                (Modifier.isStatic(mod) || Modifier.isPrivate(mod) ? direct : virtual).add(key(m));
            }
            direct.sort(null);
            virtual.sort(null);
            long size = 8 + 32L * (direct.size() + virtual.size());
            long base = alloc(size);
            if (base == 0) return null;
            U.setMemory(base, size, (byte) 0);
            U.putInt(base, direct.size() + virtual.size());
            List<String> keys = new ArrayList<>(direct);
            keys.addAll(virtual);
            for (int i = 0; i < keys.size(); i++) {
                long at = base + 8 + 32L * i;
                U.putInt(at, c.hashCode());
                U.putInt(at + 8, i);
                U.putShort(at + 12, (short) i);
            }
            t = new Object[]{base, keys};
            Object[] raced = TABLES.putIfAbsent(c, t);
            if (raced != null) return raced;  // the loser's table stays unused in the arena (or malloc): a rare leak
            return t;
        } finally { building.remove(); }
    }

    private static long arenaNext, arenaEnd;

    /** The arm64 guest's jmethodIDs are these entries (bridge/native/jni_guest_surface.c maps a 256 MB arena, apklayer.art.arena = its hex base): bump allocation there; else malloc. */
    private static synchronized long alloc(long size) {
        if (arenaNext == 0) {
            String a = System.getProperty("apklayer.art.arena");
            if (a != null) { arenaNext = Long.parseUnsignedLong(a, 16); arenaEnd = arenaNext + (1L << 28); }
        }
        if (arenaNext == 0) return U.allocateMemory(size);
        long at = arenaNext;
        if (at + size > arenaEnd) return 0;  // full: the id stays the JVM's
        arenaNext = (at + size + 7) & ~7L;
        return at;
    }

    private static String key(Executable e) {
        StringBuilder b = new StringBuilder(e instanceof Constructor ? "<init>" : e.getName()).append('(');
        for (Class<?> p : e.getParameterTypes()) b.append(p.getName()).append(',');
        if (e instanceof Method m) b.append(')').append(m.getReturnType().getName());
        return b.toString();
    }

    private static final long ART_METHOD = offset(Executable.class, "artMethod");

    private static long offset(Class<?> c, String field) {
        try { return U.objectFieldOffset(c, field); } catch (Throwable t) { return -1; }
    }

    /** The table entry of a Method/Constructor that never ran its constructor (the JVM's own ToReflectedMethod builds them), 0 when there is none. */
    public static long of(Executable e) {
        if (ART_METHOD < 0) return 0;
        if (U.getLong(e, ART_METHOD) == 0) init(e);
        return U.getLong(e, ART_METHOD);
    }

    /** End of a Method/Constructor constructor: fill artMethod from the class's table (the temporary objects made while it is built keep 0). */
    public static void init(Executable e) {
        if (!enabled || ART_METHOD < 0 || building.get() != null) return;
        try {
            Object[] t = table(e.getDeclaringClass());
            if (t == null) return;
            @SuppressWarnings("unchecked") int i = ((List<String>) t[1]).indexOf(key(e));
            if (i < 0) return;
            long at = (Long) t[0] + 8 + 32L * i;
            U.putInt(at + 4, (e.getModifiers() & 0xffff) | PUBLIC_API);
            U.putLong(e, ART_METHOD, at);
        } catch (Throwable ignored) { }
    }
}
