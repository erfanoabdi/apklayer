package hello.identity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** ART internals as app code inspects them (handoff item 7), Java side: Unsafe layout, object/String fields, hidden API, artMethod, hashes, messages. */
final class ArtProbes {
    private ArtProbes() {}

    /** Two adjacent static methods and fields: hooking frameworks (Pine, SandHook, Epic) take the ArtMethod size from their distance. */
    static final class Adjacent {
        static int first;
        static int second;
        static void a() {}
        static void b() {}
    }

    static final class Layout { int i; long j; Object o; byte b; }

    static Object unsafe() throws Exception {
        Class<?> c = Class.forName("sun.misc.Unsafe");
        Field f = c.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return f.get(null);
    }

    static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getMethod(name, types);
        return m.invoke(target, args);
    }

    static void run() {
        Out.probe("art.unsafe.class", () -> unsafe().getClass().getName());
        for (Class<?> a : new Class<?>[]{byte[].class, char[].class, int[].class, long[].class, Object[].class, String[].class})
            Out.probe("art.unsafe.arrayBaseOffset." + a.getSimpleName(), () -> call(unsafe(), "arrayBaseOffset", new Class<?>[]{Class.class}, a)
                    + " scale=" + call(unsafe(), "arrayIndexScale", new Class<?>[]{Class.class}, a));
        Out.probe("art.unsafe.addressSize", () -> call(unsafe(), "addressSize", new Class<?>[0]));
        Out.probe("art.unsafe.pageSize", () -> call(unsafe(), "pageSize", new Class<?>[0]));
        Out.probe("art.unsafe.objectFieldOffset.Layout", () -> {
            StringBuilder b = new StringBuilder();
            for (String n : new String[]{"i", "j", "o", "b"})
                b.append(n).append('=').append(call(unsafe(), "objectFieldOffset", new Class<?>[]{Field.class}, Layout.class.getDeclaredField(n))).append(' ');
            return b.toString().trim();
        });
        Out.probe("art.unsafe.objectFieldOffset.String", () -> {
            StringBuilder b = new StringBuilder();
            for (Field f : String.class.getDeclaredFields())
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                    b.append(f.getName()).append('=').append(call(unsafe(), "objectFieldOffset", new Class<?>[]{Field.class}, f)).append(' ');
            return b.toString().trim();
        });
        Out.probe("art.unsafe.stringCount", () -> {  // ART: count = length << 1 | uncompressed
            Field f = String.class.getDeclaredField("count");
            f.setAccessible(true);
            return "ascii=" + f.get("abc") + " utf16=" + f.get("abé中");
        });
        for (Class<?> c : new Class<?>[]{Object.class, String.class, Class.class, Thread.class, Throwable.class,
                java.lang.reflect.Executable.class, java.lang.reflect.Method.class, java.lang.reflect.Field.class, ClassLoader.class,
                java.lang.ref.Reference.class, java.lang.invoke.MethodHandle.class, java.nio.Buffer.class})
            Out.probe("art.fields." + c.getSimpleName(), () -> JavaProbes.fieldNames(c));
        Out.probe("art.executable.artMethod", () -> artMethod(Adjacent.class.getDeclaredMethod("a")));
        Out.probe("art.artMethod.delta", () -> {
            long a = artMethod(Adjacent.class.getDeclaredMethod("a")), b = artMethod(Adjacent.class.getDeclaredMethod("b"));
            return Math.abs(b - a);
        });
        Out.probe("art.field.artField", () -> {  // ART 10+: Field keeps dexFieldIndex/offset/accessFlags, no ArtField*
            Field f = Adjacent.class.getDeclaredField("second");
            StringBuilder b = new StringBuilder();
            for (String n : new String[]{"artField", "dexFieldIndex", "offset", "accessFlags"}) {
                try { Field g = Field.class.getDeclaredField(n); g.setAccessible(true); b.append(n).append('=').append(g.get(f)).append(' '); }
                catch (Throwable t) { b.append(n).append("=!").append(t.getClass().getSimpleName()).append(' '); }
            }
            return b.toString().trim();
        });
        Out.probe("art.class.accessFlags", () -> declared(Class.class, "accessFlags", Adjacent.class));
        Out.probe("art.class.status", () -> declared(Class.class, "status", Adjacent.class));
        Out.probe("art.class.objectSize", () -> declared(Class.class, "objectSize", Layout.class));
        Out.probe("art.class.dexCache", () -> declared(Class.class, "dexCache", Adjacent.class) != null);
        Out.probe("art.object.shadowKlass", () -> declared(Object.class, "shadow$_klass_", new Object()) != null);
        Out.probe("art.thread.nativePeer", () -> { Object v = declared(Thread.class, "nativePeer", Thread.currentThread()); return v == null ? "null" : (((Long) v) != 0 ? "nonzero" : "0"); });
        Out.probe("art.hiddenapi.setHiddenApiExemptions", () -> lookup(Class.forName("dalvik.system.VMRuntime"), "setHiddenApiExemptions", String[].class));
        Out.probe("art.hiddenapi.metaReflection", () -> {
            Method getDeclared = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Object m = getDeclared.invoke(Class.forName("dalvik.system.VMRuntime"), "setHiddenApiExemptions", new Class<?>[]{String[].class});
            return m == null ? "null" : "found";
        });
        Out.probe("art.hiddenapi.currentActivityThread", () -> {
            Object at = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null);
            return at == null ? "null" : at.getClass().getName();
        });
        Out.probe("art.hiddenapi.SystemProperties.get", () -> Class.forName("android.os.SystemProperties").getMethod("get", String.class)
                .invoke(null, "ro.build.version.sdk"));
        Out.probe("art.identityHash", ArtProbes::hashes);
        Out.probe("art.object.toString", () -> new Object().toString().replaceAll("@[0-9a-f]+", "@<hex>"));
        Out.probe("art.msg.npe", () -> { try { String s = null; s.length(); return "none"; } catch (NullPointerException e) { return e.getMessage(); } });
        Out.probe("art.msg.npeField", () -> { try { Layout l = null; return l.i; } catch (NullPointerException e) { return e.getMessage(); } });
        Out.probe("art.msg.aioobe", () -> { try { int[] a = new int[3]; return a[5]; } catch (ArrayIndexOutOfBoundsException e) { return e.getMessage(); } });
        Out.probe("art.msg.sioobe", () -> { try { return "abc".charAt(5); } catch (StringIndexOutOfBoundsException e) { return e.getMessage(); } });
        Out.probe("art.msg.cce", () -> { try { Object o = 1; return (String) o; } catch (ClassCastException e) { return e.getMessage(); } });
        Out.probe("art.msg.ase", () -> { try { Object[] a = new String[1]; a[0] = 1; return "none"; } catch (ArrayStoreException e) { return e.getMessage(); } });
        Out.probe("art.msg.nase", () -> { try { int n = -1; return new int[n].length; } catch (NegativeArraySizeException e) { return e.getMessage(); } });
        Out.probe("art.msg.arith", () -> { try { int z = 0; return 1 / z; } catch (ArithmeticException e) { return e.getMessage(); } });
        Out.probe("art.msg.nsme", () -> { try { return String.class.getMethod("nope"); } catch (NoSuchMethodException e) { return e.getMessage(); } });
        Out.probe("art.msg.unsatisfiedLink", () -> { try { System.loadLibrary("no_such_lib"); return "none"; }
            catch (UnsatisfiedLinkError e) { return JavaProbes.shorten(e.getMessage()); } });
        Out.probe("art.msg.stackOverflowDepth", () -> { depth = 0; try { recurse(); } catch (StackOverflowError e) { return (depth / 1000) + "k frames"; } return "none"; });
        Out.probe("art.string.intern", () -> ("a" + System.nanoTime()).intern() != null);
    }

    static int depth;

    static void recurse() { depth++; recurse(); }

    static long artMethod(Method m) throws Exception {
        Field f = Class.forName("java.lang.reflect.Executable").getDeclaredField("artMethod");
        f.setAccessible(true);
        return (Long) f.get(m);
    }

    static Object declared(Class<?> owner, String name, Object target) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    static String lookup(Class<?> c, String name, Class<?>... types) {
        try { return c.getDeclaredMethod(name, types) != null ? "found" : "null"; }
        catch (NoSuchMethodException e) { return "NoSuchMethodException"; }
    }

    private static String hashes() {
        int n = 4096, maxBits = 0, zero = 0, ascending = 0, prev = 0;
        long sum = 0;
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < n; i++) {
            int h = System.identityHashCode(new Object());
            maxBits = Math.max(maxBits, 32 - Integer.numberOfLeadingZeros(h));
            if (h == 0) zero++;
            if (h > prev) ascending++;
            prev = h;
            sum += h;
            seen.add(h);
        }
        return "maxBits=" + maxBits + " distinct=" + seen.size() + "/" + n + " zero=" + zero + " ascending=" + ascending + " meanBits="
                + (64 - Long.numberOfLeadingZeros(sum / n));
    }
}
