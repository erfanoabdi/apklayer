// libcore.io.Memory -- raw-memory access helpers used by Android's
// internal serialization paths (notably Google Play Billing's
// nano-proto codec and a few other Bionic-only libraries). NOT a
// public SDK class but apps reach it through the bootclassloader.
//
// AOSP source (Bionic-side, not frameworks):
//   bionic/libc/include/sys/system_properties.h plus
//   libcore/luni/src/main/java/libcore/io/Memory.java
//
// The API is essentially Unsafe-style memory load/store helpers.
// Google Play Billing's zzho/zzgo static initializers call peekInt /
// pokeInt; we satisfy with a Direct-ByteBuffer-backed implementation
// that's safe for the byte-array overloads.
//
// THE boolean-swap OVERLOADS (added for protobuf-lite, v1.0.296):
// protobuf-lite (bundled in androidx.datastore-preferences, GMS, Firebase,
// gRPC, etc.) probes for "am I on Android" via
// `Class.forName("libcore.io.Memory")`. Because Mac.apk ships this class,
// protobuf-lite picks its Android `MemoryAccessor` -- which it only
// instantiates if libcore.io.Memory exposes the REAL AOSP boolean-swap
// signatures (peekLong(long,boolean), pokeLong(long,long,boolean), ...).
// AOSP's libcore.io.Memory uses `boolean swapBytes` params (NOT ByteOrder);
// our ByteOrder overloads below don't match its reflective getMethod() probe,
// so the accessor came back NULL and every protobuf field access NPE'd
// (Reddit's DataStore-backed session manager: writeData -> p.q ->
// qvg0.i(long,Object) on a null accessor). The Android accessor's HEAP field
// ops go through Unsafe object+offset (HotSpot-safe); only its raw-address
// ops use these methods (direct ByteBuffers, which DO have real native
// addresses on HotSpot). So we implement the real AOSP API faithfully via
// sun.misc.Unsafe. Purely additive -- the ByteOrder overloads stay for
// existing callers (Billing). See AOSP
// libcore/luni/src/main/java/libcore/io/Memory.java for the exact contract.
package libcore.io;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class Memory {
    private Memory() {}

    // Raw-address peek/poke (direct ByteBuffer addresses). AOSP's
    // libcore.io.Memory uses sun.misc.Unsafe directly; we reach the SAME
    // Unsafe methods through constant MethodHandles bound to the real
    // `theUnsafe` singleton, resolving the class by NAME (Class.forName)
    // so the type `sun.misc.Unsafe` never appears at compile time. That is
    // byte-for-byte equivalent at runtime (the handles call the same
    // native intrinsics) but avoids javac's non-suppressible "Unsafe is
    // internal proprietary API" warning. The handles are static final, so
    // HotSpot constant-folds + inlines invokeExact down to the same code a
    // direct Unsafe call would emit. If Unsafe can't be obtained (JVM
    // forbids it), UNSAFE_OK is false and the address overloads degrade to
    // no-ops -- but the methods still EXIST, satisfying protobuf-lite's
    // reflective method-existence probe (the reason this class is shipped).
    private static final java.lang.invoke.MethodHandle GET_BYTE, GET_SHORT, GET_INT, GET_LONG;
    private static final java.lang.invoke.MethodHandle PUT_BYTE, PUT_SHORT, PUT_INT, PUT_LONG;
    private static final boolean UNSAFE_OK;
    static {
        java.lang.invoke.MethodHandle gb = null, gs = null, gi = null, gl = null,
                                      pb = null, ps = null, pi = null, pl = null;
        boolean ok = false;
        try {
            Class<?> uc = Class.forName("sun.misc.Unsafe");
            java.lang.reflect.Field f = uc.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Object u = f.get(null);
            if (u != null) {
                java.lang.invoke.MethodHandles.Lookup lk = java.lang.invoke.MethodHandles.lookup();
                gb = lk.findVirtual(uc, "getByte",  java.lang.invoke.MethodType.methodType(byte.class,  long.class)).bindTo(u);
                gs = lk.findVirtual(uc, "getShort", java.lang.invoke.MethodType.methodType(short.class, long.class)).bindTo(u);
                gi = lk.findVirtual(uc, "getInt",   java.lang.invoke.MethodType.methodType(int.class,   long.class)).bindTo(u);
                gl = lk.findVirtual(uc, "getLong",  java.lang.invoke.MethodType.methodType(long.class,  long.class)).bindTo(u);
                pb = lk.findVirtual(uc, "putByte",  java.lang.invoke.MethodType.methodType(void.class, long.class, byte.class)).bindTo(u);
                ps = lk.findVirtual(uc, "putShort", java.lang.invoke.MethodType.methodType(void.class, long.class, short.class)).bindTo(u);
                pi = lk.findVirtual(uc, "putInt",   java.lang.invoke.MethodType.methodType(void.class, long.class, int.class)).bindTo(u);
                pl = lk.findVirtual(uc, "putLong",  java.lang.invoke.MethodType.methodType(void.class, long.class, long.class)).bindTo(u);
                ok = true;
            }
        } catch (Throwable t) {
            ok = false;
        }
        GET_BYTE = gb; GET_SHORT = gs; GET_INT = gi; GET_LONG = gl;
        PUT_BYTE = pb; PUT_SHORT = ps; PUT_INT = pi; PUT_LONG = pl;
        UNSAFE_OK = ok;
    }

    /** Byte order of the host platform. AOSP's Memory uses native
     *  order; we forward to ByteOrder.nativeOrder(). */
    public static ByteOrder peekByteOrder() { return ByteOrder.nativeOrder(); }

    // ===== Real AOSP libcore.io.Memory API: boolean-swap overloads =====
    // `swapBytes` means "the value's byte order is the opposite of native".
    // We read/write in native order via Unsafe (address) or direct shifts
    // (byte[]) and reverse the bytes when swapBytes is set.

    public static byte peekByte(long address) {
        if (!UNSAFE_OK) return 0;
        try { return (byte) GET_BYTE.invokeExact(address); }
        catch (Throwable t) { return 0; }
    }
    public static short peekShort(long address, boolean swapBytes) {
        if (!UNSAFE_OK) return 0;
        try {
            short v = (short) GET_SHORT.invokeExact(address);
            return swapBytes ? Short.reverseBytes(v) : v;
        } catch (Throwable t) { return 0; }
    }
    public static int peekInt(long address, boolean swapBytes) {
        if (!UNSAFE_OK) return 0;
        try {
            int v = (int) GET_INT.invokeExact(address);
            return swapBytes ? Integer.reverseBytes(v) : v;
        } catch (Throwable t) { return 0; }
    }
    public static long peekLong(long address, boolean swapBytes) {
        if (!UNSAFE_OK) return 0L;
        try {
            long v = (long) GET_LONG.invokeExact(address);
            return swapBytes ? Long.reverseBytes(v) : v;
        } catch (Throwable t) { return 0L; }
    }
    public static void pokeByte(long address, byte value) {
        if (!UNSAFE_OK) return;
        try { PUT_BYTE.invokeExact(address, value); } catch (Throwable t) { }
    }
    public static void pokeShort(long address, short value, boolean swapBytes) {
        if (!UNSAFE_OK) return;
        try { PUT_SHORT.invokeExact(address, (short) (swapBytes ? Short.reverseBytes(value) : value)); }
        catch (Throwable t) { }
    }
    public static void pokeInt(long address, int value, boolean swapBytes) {
        if (!UNSAFE_OK) return;
        try { PUT_INT.invokeExact(address, swapBytes ? Integer.reverseBytes(value) : value); }
        catch (Throwable t) { }
    }
    public static void pokeLong(long address, long value, boolean swapBytes) {
        if (!UNSAFE_OK) return;
        try { PUT_LONG.invokeExact(address, swapBytes ? Long.reverseBytes(value) : value); }
        catch (Throwable t) { }
    }
    public static void peekByteArray(long address, byte[] dst, int dstOffset, int byteCount) {
        if (!UNSAFE_OK) return;
        try { for (int i = 0; i < byteCount; i++) dst[dstOffset + i] = (byte) GET_BYTE.invokeExact(address + i); }
        catch (Throwable t) { }
    }
    public static void pokeByteArray(long address, byte[] src, int srcOffset, int byteCount) {
        if (!UNSAFE_OK) return;
        try { for (int i = 0; i < byteCount; i++) PUT_BYTE.invokeExact(address + i, src[srcOffset + i]); }
        catch (Throwable t) { }
    }

    // byte[]-backed boolean-swap overloads (AOSP also exposes these).
    public static short peekShort(byte[] src, int offset, boolean swapBytes) {
        return peekShort(src, offset, swapBytes ? swappedOrder() : ByteOrder.nativeOrder());
    }
    public static int peekInt(byte[] src, int offset, boolean swapBytes) {
        return peekInt(src, offset, swapBytes ? swappedOrder() : ByteOrder.nativeOrder());
    }
    public static long peekLong(byte[] src, int offset, boolean swapBytes) {
        return peekLong(src, offset, swapBytes ? swappedOrder() : ByteOrder.nativeOrder());
    }
    public static void pokeShort(byte[] dst, int offset, short value, boolean swapBytes) {
        pokeShort(dst, offset, value, swapBytes ? swappedOrder() : ByteOrder.nativeOrder());
    }
    public static void pokeInt(byte[] dst, int offset, int value, boolean swapBytes) {
        pokeInt(dst, offset, value, swapBytes ? swappedOrder() : ByteOrder.nativeOrder());
    }
    public static void pokeLong(byte[] dst, int offset, long value, boolean swapBytes) {
        pokeLong(dst, offset, value, swapBytes ? swappedOrder() : ByteOrder.nativeOrder());
    }
    private static ByteOrder swappedOrder() {
        return ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN
            ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    }

    // ----- Raw-address ByteOrder overloads (legacy; now Unsafe-backed) -----
    public static int peekInt(long address, ByteOrder bo) {
        return peekInt(address, bo != ByteOrder.nativeOrder());
    }
    public static long peekLong(long address, ByteOrder bo) {
        return peekLong(address, bo != ByteOrder.nativeOrder());
    }
    public static short peekShort(long address, ByteOrder bo) {
        return peekShort(address, bo != ByteOrder.nativeOrder());
    }
    public static void pokeInt(long address, int value, ByteOrder bo) {
        pokeInt(address, value, bo != ByteOrder.nativeOrder());
    }
    public static void pokeLong(long address, long value, ByteOrder bo) {
        pokeLong(address, value, bo != ByteOrder.nativeOrder());
    }
    public static void pokeShort(long address, short value, ByteOrder bo) {
        pokeShort(address, value, bo != ByteOrder.nativeOrder());
    }

    // ----- Byte-array peek/poke (well-defined; we honor them) -----
    public static int peekInt(byte[] src, int offset, ByteOrder bo) {
        ByteBuffer bb = ByteBuffer.wrap(src, offset, 4);
        bb.order(bo);
        return bb.getInt();
    }
    public static long peekLong(byte[] src, int offset, ByteOrder bo) {
        ByteBuffer bb = ByteBuffer.wrap(src, offset, 8);
        bb.order(bo);
        return bb.getLong();
    }
    public static short peekShort(byte[] src, int offset, ByteOrder bo) {
        ByteBuffer bb = ByteBuffer.wrap(src, offset, 2);
        bb.order(bo);
        return bb.getShort();
    }
    public static void pokeInt(byte[] dst, int offset, int value, ByteOrder bo) {
        ByteBuffer bb = ByteBuffer.wrap(dst, offset, 4);
        bb.order(bo);
        bb.putInt(value);
    }
    public static void pokeLong(byte[] dst, int offset, long value, ByteOrder bo) {
        ByteBuffer bb = ByteBuffer.wrap(dst, offset, 8);
        bb.order(bo);
        bb.putLong(value);
    }
    public static void pokeShort(byte[] dst, int offset, short value, ByteOrder bo) {
        ByteBuffer bb = ByteBuffer.wrap(dst, offset, 2);
        bb.order(bo);
        bb.putShort(value);
    }

    /** Pair-wise byte-array swap used by NIO and proto codecs. */
    public static void memmove(Object dstObject, int dstOffset,
                                Object srcObject, int srcOffset, long byteCount) {
        if (dstObject instanceof byte[] && srcObject instanceof byte[]) {
            System.arraycopy(srcObject, srcOffset, dstObject, dstOffset, (int) byteCount);
        }
    }

    public static byte peekByte(int address) { return peekByte((long) address); }
    public static void pokeByte(int address, byte value) { pokeByte((long) address, value); }
    public static void peekByteArray(int address, byte[] dst, int dstOffset, int byteCount) {
        peekByteArray((long) address, dst, dstOffset, byteCount);
    }
    public static void pokeByteArray(int address, byte[] src, int srcOffset, int byteCount) {
        pokeByteArray((long) address, src, srcOffset, byteCount);
    }
}

