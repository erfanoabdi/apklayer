package apklayer.host;

import android.os.IBinder;
import android.os.Parcel;
import java.io.FileDescriptor;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowParcel;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/** Route explicitly selected native payloads to original JNI, preserving normal Java Parcels. */
@Implements(Parcel.class)
public class ShadowHostNativeParcel extends ShadowParcel {
    @RealObject private Parcel real;
    @Implementation protected void recycle() {
        NativeParcel.restore(real);
        ReflectionHelpers.callInstanceMethod(real, Shadow.directMethodName(Parcel.class.getName(), "recycle"));
    }
    @Implementation @Override protected void writeByteArray(byte[] data, int offset, int length) {
        if (data == null) { real.writeInt(-1); return; }
        if ((offset | length) < 0 || offset > data.length - length) throw new ArrayIndexOutOfBoundsException();
        nativeWriteByteArray(NativeParcel.pointer(real), data, offset, length);
    }
    @Implementation @Override protected void writeBlob(byte[] data, int offset, int length) {
        if (data == null) { real.writeInt(-1); return; }
        if ((offset | length) < 0 || offset > data.length - length) throw new ArrayIndexOutOfBoundsException();
        nativeWriteBlob(NativeParcel.pointer(real), data, offset, length);
    }
    @Implementation protected static void nativeDestroy(long peer) {
        Long original = NativeParcel.remove(peer);
        if (original == null) { ShadowParcel.nativeDestroy(peer); return; }
        NativeParcel.call("nativeDestroy", ClassParameter.from(long.class, peer));
        ShadowParcel.nativeDestroy(original);
    }
    @Implementation protected static int nativeDataSize(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeDataSize", ClassParameter.from(long.class, nativePtr));
        }
        return ShadowParcel.nativeDataSize(nativePtr);
    }
    @Implementation protected static int nativeDataAvail(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeDataAvail", ClassParameter.from(long.class, nativePtr));
        }
        return ShadowParcel.nativeDataAvail(nativePtr);
    }
    @Implementation protected static int nativeDataPosition(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeDataPosition", ClassParameter.from(long.class, nativePtr));
        }
        return ShadowParcel.nativeDataPosition(nativePtr);
    }
    @Implementation protected static int nativeDataCapacity(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeDataCapacity", ClassParameter.from(long.class, nativePtr));
        }
        return ShadowParcel.nativeDataCapacity(nativePtr);
    }
    @Implementation protected static void nativeSetDataSize(long nativePtr, int size) {
        if (NativeParcel.owns(nativePtr)) {
            NativeParcel.call("nativeSetDataSize", ClassParameter.from(long.class, nativePtr), ClassParameter.from(int.class, size));
            return;
        }
        ShadowParcel.nativeSetDataSize(nativePtr, size);
    }
    @Implementation protected static void nativeSetDataPosition(long nativePtr, int pos) {
        if (NativeParcel.owns(nativePtr)) {
            NativeParcel.call("nativeSetDataPosition", ClassParameter.from(long.class, nativePtr), ClassParameter.from(int.class, pos));
            return;
        }
        ShadowParcel.nativeSetDataPosition(nativePtr, pos);
    }
    @Implementation protected static void nativeSetDataCapacity(long nativePtr, int size) {
        if (NativeParcel.owns(nativePtr)) {
            NativeParcel.call("nativeSetDataCapacity", ClassParameter.from(long.class, nativePtr), ClassParameter.from(int.class, size));
            return;
        }
        ShadowParcel.nativeSetDataCapacity(nativePtr, size);
    }
    @Implementation protected static void nativeWriteByteArray(long nativePtr, byte[] b, int offset, int len) {
        if (NativeParcel.owns(nativePtr)) {
            NativeParcel.call("nativeWriteByteArray", ClassParameter.from(long.class, nativePtr), ClassParameter.from(byte[].class, b), ClassParameter.from(int.class, offset), ClassParameter.from(int.class, len));
            return;
        }
        ShadowParcel.nativeWriteByteArray(nativePtr, b, offset, len);
    }
    @Implementation protected static void nativeWriteBlob(long nativePtr, byte[] b, int offset, int len) {
        if (NativeParcel.owns(nativePtr)) {
            NativeParcel.call("nativeWriteBlob", ClassParameter.from(long.class, nativePtr), ClassParameter.from(byte[].class, b), ClassParameter.from(int.class, offset), ClassParameter.from(int.class, len));
            return;
        }
        ShadowParcel.nativeWriteBlob(nativePtr, b, offset, len);
    }
    @Implementation protected static int nativeWriteInt(long nativePtr, int val) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeWriteInt", ClassParameter.from(long.class, nativePtr), ClassParameter.from(int.class, val));
        }
        return ShadowParcel.nativeWriteInt(nativePtr, val);
    }
    @Implementation protected static int nativeWriteLong(long nativePtr, long val) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeWriteLong", ClassParameter.from(long.class, nativePtr), ClassParameter.from(long.class, val));
        }
        return ShadowParcel.nativeWriteLong(nativePtr, val);
    }
    @Implementation protected static int nativeWriteFloat(long nativePtr, float val) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeWriteFloat", ClassParameter.from(long.class, nativePtr), ClassParameter.from(float.class, val));
        }
        return ShadowParcel.nativeWriteFloat(nativePtr, val);
    }
    @Implementation protected static int nativeWriteDouble(long nativePtr, double val) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeWriteDouble", ClassParameter.from(long.class, nativePtr), ClassParameter.from(double.class, val));
        }
        return ShadowParcel.nativeWriteDouble(nativePtr, val);
    }
    @Implementation protected static void nativeWriteStrongBinder(long nativePtr, IBinder val) {
        if (NativeParcel.owns(nativePtr)) {
            throw new UnsupportedOperationException("Native Binder/FD payload bridging is not enabled");
        }
        ShadowParcel.nativeWriteStrongBinder(nativePtr, val);
    }
    @Implementation protected static byte[] nativeCreateByteArray(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeCreateByteArray", ClassParameter.from(long.class, nativePtr));
        }
        return ShadowParcel.nativeCreateByteArray(nativePtr);
    }
    @Implementation protected static byte[] nativeReadBlob(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeReadBlob", ClassParameter.from(long.class, nativePtr));
        }
        return ShadowParcel.nativeReadBlob(nativePtr);
    }
    @Implementation protected static boolean nativeReadByteArray(long nativePtr, byte[] dest, int destLen) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeReadByteArray", ClassParameter.from(long.class, nativePtr), ClassParameter.from(byte[].class, dest), ClassParameter.from(int.class, destLen));
        }
        return ShadowParcel.nativeReadByteArray(nativePtr, dest, destLen);
    }
    @Implementation protected static int nativeReadInt(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeReadInt", ClassParameter.from(long.class, nativePtr));
        }
        return ShadowParcel.nativeReadInt(nativePtr);
    }
    @Implementation protected static long nativeReadLong(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeReadLong", ClassParameter.from(long.class, nativePtr));
        }
        return ShadowParcel.nativeReadLong(nativePtr);
    }
    @Implementation protected static float nativeReadFloat(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeReadFloat", ClassParameter.from(long.class, nativePtr));
        }
        return ShadowParcel.nativeReadFloat(nativePtr);
    }
    @Implementation protected static double nativeReadDouble(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeReadDouble", ClassParameter.from(long.class, nativePtr));
        }
        return ShadowParcel.nativeReadDouble(nativePtr);
    }
    @Implementation protected static IBinder nativeReadStrongBinder(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            throw new UnsupportedOperationException("Native Binder/FD payload bridging is not enabled");
        }
        return ShadowParcel.nativeReadStrongBinder(nativePtr);
    }
    @Implementation protected static void nativeFreeBuffer(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            NativeParcel.call("nativeFreeBuffer", ClassParameter.from(long.class, nativePtr));
            return;
        }
        ShadowParcel.nativeFreeBuffer(nativePtr);
    }
    @Implementation protected static byte[] nativeMarshall(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeMarshall", ClassParameter.from(long.class, nativePtr));
        }
        return ShadowParcel.nativeMarshall(nativePtr);
    }
    @Implementation protected static void nativeUnmarshall(long nativePtr, byte[] data, int offset, int length) {
        if (NativeParcel.owns(nativePtr)) {
            NativeParcel.call("nativeUnmarshall", ClassParameter.from(long.class, nativePtr), ClassParameter.from(byte[].class, data), ClassParameter.from(int.class, offset), ClassParameter.from(int.class, length));
            return;
        }
        ShadowParcel.nativeUnmarshall(nativePtr, data, offset, length);
    }
    @Implementation protected static void nativeAppendFrom(long thisNativePtr, long otherNativePtr, int offset, int length) {
        if (NativeParcel.owns(thisNativePtr) || NativeParcel.owns(otherNativePtr)) {
            if (!NativeParcel.owns(thisNativePtr) || !NativeParcel.owns(otherNativePtr))
                throw new IllegalArgumentException("Cannot append between Java and native Parcel encodings");
            NativeParcel.call("nativeAppendFrom", ClassParameter.from(long.class, thisNativePtr), ClassParameter.from(long.class, otherNativePtr), ClassParameter.from(int.class, offset), ClassParameter.from(int.class, length));
            return;
        }
        ShadowParcel.nativeAppendFrom(thisNativePtr, otherNativePtr, offset, length);
    }
    @Implementation protected static void nativeWriteInterfaceToken(long nativePtr, String interfaceName) {
        if (NativeParcel.owns(nativePtr)) {
            NativeParcel.call("nativeWriteInterfaceToken", ClassParameter.from(long.class, nativePtr), ClassParameter.from(String.class, interfaceName));
            return;
        }
        ShadowParcel.nativeWriteInterfaceToken(nativePtr, interfaceName);
    }
    @Implementation protected static void nativeEnforceInterface(long nativePtr, String interfaceName) {
        if (NativeParcel.owns(nativePtr)) {
            throw new UnsupportedOperationException("Native interface enforcement needs the Java Binder policy cache");
        }
        ShadowParcel.nativeEnforceInterface(nativePtr, interfaceName);
    }
    @Implementation protected static long nativeWriteFileDescriptor(long nativePtr, FileDescriptor val) {
        if (NativeParcel.owns(nativePtr)) {
            throw new UnsupportedOperationException("Native Binder/FD payload bridging is not enabled");
        }
        return ShadowParcel.nativeWriteFileDescriptor(nativePtr, val);
    }
    @Implementation protected static FileDescriptor nativeReadFileDescriptor(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            throw new UnsupportedOperationException("Native Binder/FD payload bridging is not enabled");
        }
        return ShadowParcel.nativeReadFileDescriptor(nativePtr);
    }
    @Implementation protected static void nativeWriteString8(long nativePtr, String val) {
        if (NativeParcel.owns(nativePtr)) {
            NativeParcel.call("nativeWriteString8", ClassParameter.from(long.class, nativePtr), ClassParameter.from(String.class, val));
            return;
        }
        ShadowParcel.nativeWriteString8(nativePtr, val);
    }
    @Implementation protected static void nativeWriteString16(long nativePtr, String val) {
        if (NativeParcel.owns(nativePtr)) {
            NativeParcel.call("nativeWriteString16", ClassParameter.from(long.class, nativePtr), ClassParameter.from(String.class, val));
            return;
        }
        ShadowParcel.nativeWriteString16(nativePtr, val);
    }
    @Implementation protected static String nativeReadString8(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeReadString8", ClassParameter.from(long.class, nativePtr));
        }
        return ShadowParcel.nativeReadString8(nativePtr);
    }
    @Implementation protected static String nativeReadString16(long nativePtr) {
        if (NativeParcel.owns(nativePtr)) {
            return NativeParcel.call("nativeReadString16", ClassParameter.from(long.class, nativePtr));
        }
        return ShadowParcel.nativeReadString16(nativePtr);
    }
}
