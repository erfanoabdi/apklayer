package hello.nativeaudio;

import android.os.Binder;
import android.os.Parcel;
import java.lang.reflect.Method;
import java.util.Arrays;

final class ParcelCheck {
    private static void require(boolean value, String detail) {
        if (!value) throw new AssertionError(detail);
    }
    static void run() throws Exception {
        Class<?> helper = Class.forName("apklayer.host.NativeParcel");
        Method obtain = helper.getMethod("obtain"), active = helper.getMethod("activeParcels");
        for (int cycle = 0; cycle < 3; cycle++) {
            Parcel p = (Parcel) obtain.invoke(null), copy = (Parcel) obtain.invoke(null);
            try {
                require(((Number) active.invoke(null)).intValue() == 2, "native payload ownership");
                p.writeInt(0x12345678); p.writeLong(0x123456789abcdef0L);
                p.writeFloat(1.25f); p.writeDouble(-3.5);
                p.writeString("تست-😀"); p.writeString(null);
                p.writeByteArray(new byte[]{1, 2, 3, 4, 5});
                p.writeIntArray(new int[]{7, -8, 9});
                byte[] bytes = p.marshall();
                require(bytes[0] == 0x78 && bytes[1] == 0x56 && bytes[2] == 0x34 && bytes[3] == 0x12,
                        "actual Android little endian Parcel bytes");
                copy.unmarshall(bytes, 0, bytes.length); copy.setDataPosition(0);
                require(copy.readInt() == 0x12345678 && copy.readLong() == 0x123456789abcdef0L, "native integers");
                require(copy.readFloat() == 1.25f && copy.readDouble() == -3.5, "native floating point");
                require("تست-😀".equals(copy.readString()) && copy.readString() == null, "native UTF16/null");
                require(Arrays.equals(copy.createByteArray(), new byte[]{1, 2, 3, 4, 5}), "native byte array");
                require(Arrays.equals(copy.createIntArray(), new int[]{7, -8, 9}), "native int array");
                require(copy.dataAvail() == 0, "native data cursor");
                copy.setDataSize(0); copy.setDataPosition(0);
                copy.appendFrom(p, 0, p.dataSize());
                require(Arrays.equals(copy.marshall(), bytes), "native append");
                copy.setDataSize(0); copy.setDataPosition(0);
                copy.writeInterfaceToken("hello.nativeaudio.ParcelCheck"); copy.writeInt(42);
                require(copy.dataSize() > 16, "native interface header serialized");
                copy.setDataPosition(0);
                try { copy.enforceInterface("hello.nativeaudio.ParcelCheck"); throw new AssertionError("unsupported Java policy accepted"); }
                catch (UnsupportedOperationException expected) { }
                Parcel ordinary = Parcel.obtain();
                try {
                    ordinary.writeInt(66); ordinary.setDataPosition(0);
                    require(ordinary.readInt() == 66, "Java Parcel coexistence");
                    try { copy.appendFrom(ordinary, 0, 4); throw new AssertionError("mixed append accepted"); }
                    catch (IllegalArgumentException expected) { }
                } finally { ordinary.recycle(); }
                try { p.writeStrongBinder(new Binder()); throw new AssertionError("unsupported Binder accepted"); }
                catch (UnsupportedOperationException expected) { }
                try { p.writeByteArray(new byte[1], 1, 1); throw new AssertionError("array bounds accepted"); }
                catch (ArrayIndexOutOfBoundsException expected) { }
            } finally { p.recycle(); copy.recycle(); }
            require(((Number) active.invoke(null)).intValue() == 0, "native payload recycle");
        }
        System.out.println("[hello-native-audio] native Parcel PASS bytes/scalars/Unicode/arrays/append/coexistence/recycle");
    }
}
