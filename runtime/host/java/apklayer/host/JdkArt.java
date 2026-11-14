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
 * shortcut: tables are never freed (a class unloaded leaks 8+32n bytes), data_/entry_point are 0 (no libart code to point at) and
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
            long base = U.allocateMemory(size);
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
            if (raced != null) { U.freeMemory(base); return raced; }
            return t;
        } finally { building.remove(); }
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

    /** End of a Method/Constructor constructor: fill artMethod from the class's table (the temporary objects made while it is built keep 0). */
    public static void init(Executable e) {
        if (!enabled || ART_METHOD < 0 || building.get() != null) return;
        try {
            Object[] t = table(e.getDeclaringClass());
            @SuppressWarnings("unchecked") int i = ((List<String>) t[1]).indexOf(key(e));
            if (i < 0) return;
            long at = (Long) t[0] + 8 + 32L * i;
            U.putInt(at + 4, (e.getModifiers() & 0xffff) | PUBLIC_API);
            U.putLong(e, ART_METHOD, at);
        } catch (Throwable ignored) { }
    }
}
