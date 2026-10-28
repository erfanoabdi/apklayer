package apklayer.host;

import java.io.FileDescriptor;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowLinux;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * libcore.io.Linux: Robolectric's partial stand-in stays (stat, mkdir...), but the descriptor operations (dup, fcntl, pipe2, socketpair, open...) are real, from the
 * runtime shim (bridge/native/app_native.c): a descriptor in a Parcel must be a real one for the process that receives it (Chromium's renderer gets its IPC channel
 * and ICU data that way).
 */
@Implements(className = "libcore.io.Linux", isInAndroidSdk = false)
public class ShadowHostRealLinux extends ShadowLinux {
    @RealObject private Object linux;

    @SuppressWarnings("unchecked")
    private <T> T natively(String name, ClassParameter<?>... parameters) {
        return (T) ReflectionHelpers.callInstanceMethod(linux, "$$robo$$" + name + "$nativeBinding", parameters);
    }

    @Implementation protected void close(FileDescriptor fd) { natively("close", ClassParameter.from(FileDescriptor.class, fd)); }
    @Implementation protected FileDescriptor dup(FileDescriptor fd) { return natively("dup", ClassParameter.from(FileDescriptor.class, fd)); }
    @Implementation protected FileDescriptor dup2(FileDescriptor fd, int to) { return natively("dup2", ClassParameter.from(FileDescriptor.class, fd), ClassParameter.from(int.class, to)); }
    @Implementation protected int fcntlInt(FileDescriptor fd, int cmd, int arg) {
        return natively("fcntlInt", ClassParameter.from(FileDescriptor.class, fd), ClassParameter.from(int.class, cmd), ClassParameter.from(int.class, arg));
    }
    @Implementation protected int fcntlVoid(FileDescriptor fd, int cmd) { return natively("fcntlVoid", ClassParameter.from(FileDescriptor.class, fd), ClassParameter.from(int.class, cmd)); }
    /** Robolectric's pread reads the files its own open() made; descriptors here are real: read through a channel at the offset (what Xml.resolvePullParser needs to tell ABX from text). */
    @Implementation protected int pread(FileDescriptor fd, byte[] bytes, int byteOffset, int byteCount, long offset) throws android.system.ErrnoException {
        try { return Math.max(0, new java.io.FileInputStream(fd).getChannel().read(java.nio.ByteBuffer.wrap(bytes, byteOffset, byteCount), offset)); }
        catch (java.io.IOException failure) { throw new android.system.ErrnoException("pread", android.system.OsConstants.EIO, failure); }
    }

    @Implementation protected long lseek(FileDescriptor fd, long offset, int whence) {
        return natively("lseek", ClassParameter.from(FileDescriptor.class, fd), ClassParameter.from(long.class, offset), ClassParameter.from(int.class, whence));
    }
    @Implementation protected void ftruncate(FileDescriptor fd, long length) { natively("ftruncate", ClassParameter.from(FileDescriptor.class, fd), ClassParameter.from(long.class, length)); }
    @Implementation @Override protected FileDescriptor open(String path, int flags, int mode) {
        return natively("open", ClassParameter.from(String.class, path), ClassParameter.from(int.class, flags), ClassParameter.from(int.class, mode));
    }
    @Implementation protected FileDescriptor[] pipe2(int flags) { return natively("pipe2", ClassParameter.from(int.class, flags)); }
    @Implementation protected void socketpair(int domain, int type, int protocol, FileDescriptor one, FileDescriptor two) {
        natively("socketpair", ClassParameter.from(int.class, domain), ClassParameter.from(int.class, type), ClassParameter.from(int.class, protocol),
                ClassParameter.from(FileDescriptor.class, one), ClassParameter.from(FileDescriptor.class, two));
    }
}
