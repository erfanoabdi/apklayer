package apklayer.host;

import java.lang.reflect.Field;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import sun.misc.Unsafe;

/**
 * libcore.io.Memory's natives (libjavacore's libcore_io_Memory.cpp): reads and writes at a raw address. Robolectric instruments the class and nothing binds
 * `$$robo$$peekByte$nativeBinding` and the rest, so every peek returned 0: YouTube's Elements engine reads its native upb messages' has-bits and fields through
 * Memory.peekByte/peekLong (wjt.g/h/i), saw every element as empty ("Element missing type extension") and converted all of them into zero-height components
 * (the gear's settings sheet, the description and the comments stayed blank). The same JVM process holds the native memory, so Unsafe reads and writes the address.
 * Array forms with `swap` reverse every element's bytes, as the native does. (com.android.i18n.timezone.internal.Memory has its own natives: bridge/native/i18n_memory.c.)
 */
@Implements(className = "libcore.io.Memory", isInAndroidSdk = false)
public class ShadowHostMemory {
    private static final Unsafe U;
    static {
        try { Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); U = (Unsafe) f.get(null); }
        catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }

    @Implementation protected static byte peekByte(long a) { return U.getByte(a); }
    @Implementation protected static short peekShortNative(long a) { return U.getShort(a); }
    @Implementation protected static int peekIntNative(long a) { return U.getInt(a); }
    @Implementation protected static long peekLongNative(long a) { return U.getLong(a); }
    @Implementation protected static void pokeByte(long a, byte v) { U.putByte(a, v); }
    @Implementation protected static void pokeShortNative(long a, short v) { U.putShort(a, v); }
    @Implementation protected static void pokeIntNative(long a, int v) { U.putInt(a, v); }
    @Implementation protected static void pokeLongNative(long a, long v) { U.putLong(a, v); }

    @Implementation protected static void peekByteArray(long a, byte[] dst, int off, int n) { U.copyMemory(null, a, dst, Unsafe.ARRAY_BYTE_BASE_OFFSET + off, n); }
    @Implementation protected static void pokeByteArray(long a, byte[] src, int off, int n) { U.copyMemory(src, Unsafe.ARRAY_BYTE_BASE_OFFSET + off, null, a, n); }

    @Implementation protected static void peekCharArray(long a, char[] dst, int off, int n, boolean swap) { peek(a, dst, Unsafe.ARRAY_CHAR_BASE_OFFSET, 2, off, n, swap); }
    @Implementation protected static void peekShortArray(long a, short[] dst, int off, int n, boolean swap) { peek(a, dst, Unsafe.ARRAY_SHORT_BASE_OFFSET, 2, off, n, swap); }
    @Implementation protected static void peekIntArray(long a, int[] dst, int off, int n, boolean swap) { peek(a, dst, Unsafe.ARRAY_INT_BASE_OFFSET, 4, off, n, swap); }
    @Implementation protected static void peekLongArray(long a, long[] dst, int off, int n, boolean swap) { peek(a, dst, Unsafe.ARRAY_LONG_BASE_OFFSET, 8, off, n, swap); }
    @Implementation protected static void peekFloatArray(long a, float[] dst, int off, int n, boolean swap) { peek(a, dst, Unsafe.ARRAY_FLOAT_BASE_OFFSET, 4, off, n, swap); }
    @Implementation protected static void peekDoubleArray(long a, double[] dst, int off, int n, boolean swap) { peek(a, dst, Unsafe.ARRAY_DOUBLE_BASE_OFFSET, 8, off, n, swap); }
    @Implementation protected static void pokeCharArray(long a, char[] src, int off, int n, boolean swap) { poke(a, src, Unsafe.ARRAY_CHAR_BASE_OFFSET, 2, off, n, swap); }
    @Implementation protected static void pokeShortArray(long a, short[] src, int off, int n, boolean swap) { poke(a, src, Unsafe.ARRAY_SHORT_BASE_OFFSET, 2, off, n, swap); }
    @Implementation protected static void pokeIntArray(long a, int[] src, int off, int n, boolean swap) { poke(a, src, Unsafe.ARRAY_INT_BASE_OFFSET, 4, off, n, swap); }
    @Implementation protected static void pokeLongArray(long a, long[] src, int off, int n, boolean swap) { poke(a, src, Unsafe.ARRAY_LONG_BASE_OFFSET, 8, off, n, swap); }
    @Implementation protected static void pokeFloatArray(long a, float[] src, int off, int n, boolean swap) { poke(a, src, Unsafe.ARRAY_FLOAT_BASE_OFFSET, 4, off, n, swap); }
    @Implementation protected static void pokeDoubleArray(long a, double[] src, int off, int n, boolean swap) { poke(a, src, Unsafe.ARRAY_DOUBLE_BASE_OFFSET, 8, off, n, swap); }

    private static void peek(long a, Object dst, long base, int width, int off, int n, boolean swap) {
        long at = base + (long) off * width;
        U.copyMemory(null, a, dst, at, (long) n * width);
        if (swap) swap(dst, at, width, n);
    }

    private static void poke(long a, Object src, long base, int width, int off, int n, boolean swap) {
        long at = base + (long) off * width;
        U.copyMemory(src, at, null, a, (long) n * width);
        if (swap) for (int i = 0; i < n; i++) reverse(a + (long) i * width, width);
    }

    private static void swap(Object arr, long at, int width, int n) {
        for (int i = 0; i < n; i++) {
            long p = at + (long) i * width;
            for (int lo = 0, hi = width - 1; lo < hi; lo++, hi--) {
                byte t = U.getByte(arr, p + lo);
                U.putByte(arr, p + lo, U.getByte(arr, p + hi));
                U.putByte(arr, p + hi, t);
            }
        }
    }

    private static void reverse(long p, int width) {
        for (int lo = 0, hi = width - 1; lo < hi; lo++, hi--) { byte t = U.getByte(p + lo); U.putByte(p + lo, U.getByte(p + hi)); U.putByte(p + hi, t); }
    }
}
