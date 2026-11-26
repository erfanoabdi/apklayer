package hello.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** libcore.io.Memory reads and writes raw addresses (native memory of the app's JNI libraries): YouTube's Elements engine reads its upb messages' has-bits through
 *  peekByte/peekLong; unbound natives returned 0 and every element looked empty (zero-height rows). */
public final class MemoryChecks {
    private MemoryChecks() {}

    public static void run() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field f = unsafeClass.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        Method allocate = unsafeClass.getMethod("allocateMemory", long.class), free = unsafeClass.getMethod("freeMemory", long.class);
        Method putLong = unsafeClass.getMethod("putLong", long.class, long.class), putByte = unsafeClass.getMethod("putByte", long.class, byte.class), getInt = unsafeClass.getMethod("getInt", long.class);
        long a = (Long) allocate.invoke(unsafe, 64L);
        try {
            putLong.invoke(unsafe, a, 0x0102030405060708L);
            putByte.invoke(unsafe, a + 8, (byte) 3);
            Class<?> memory = Class.forName("libcore.io.Memory");
            Method peekByte = memory.getMethod("peekByte", long.class), peekLong = memory.getMethod("peekLong", long.class, boolean.class);
            Method peekInt = memory.getMethod("peekInt", long.class, boolean.class), peekArray = memory.getMethod("peekByteArray", long.class, byte[].class, int.class, int.class);
            Method pokeInt = memory.getMethod("pokeInt", long.class, int.class, boolean.class);
            if ((Byte) peekByte.invoke(null, a + 8) != 3) throw new AssertionError("Memory.peekByte read " + peekByte.invoke(null, a + 8));
            if ((Long) peekLong.invoke(null, a, false) != 0x0102030405060708L) throw new AssertionError("Memory.peekLong " + Long.toHexString((Long) peekLong.invoke(null, a, false)));
            if ((Integer) peekInt.invoke(null, a, true) != 0x08070605) throw new AssertionError("Memory.peekInt swapped " + Integer.toHexString((Integer) peekInt.invoke(null, a, true)));
            byte[] got = new byte[4];
            peekArray.invoke(null, a, got, 0, 4);
            if (got[0] != 8 || got[3] != 5) throw new AssertionError("Memory.peekByteArray " + java.util.Arrays.toString(got));
            pokeInt.invoke(null, a + 16, 0x11223344, false);
            if ((Integer) getInt.invoke(unsafe, a + 16) != 0x11223344) throw new AssertionError("Memory.pokeInt wrote " + Integer.toHexString((Integer) getInt.invoke(unsafe, a + 16)));
        } finally {
            free.invoke(unsafe, a);
        }
    }
}
