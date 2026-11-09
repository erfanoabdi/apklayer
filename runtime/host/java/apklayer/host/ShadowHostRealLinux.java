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
        try {
            return (T) ReflectionHelpers.callInstanceMethod(linux, "$$robo$$" + name + "$nativeBinding", parameters);
        } catch (RuntimeException wrapped) {  // the ErrnoException as Os callers expect it (ParcelFileDescriptor reads its comm channel until EAGAIN)
            if (wrapped.getCause() instanceof android.system.ErrnoException errno) throw org.robolectric.util.Util.sneakyThrow(errno);
            throw wrapped;
        }
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

    /** The runtime is a 64-bit process named like Android's (SoLoader and others read /proc/self/exe to tell the bitness); other links are the host's. */
    @Implementation protected String readlink(String path) throws android.system.ErrnoException {
        if ("/proc/self/exe".equals(path)) return "/system/bin/app_process64";
        try { return java.nio.file.Files.readSymbolicLink(java.nio.file.Path.of(path)).toString(); }
        catch (java.io.IOException | RuntimeException notALink) { throw new android.system.ErrnoException("readlink", android.system.OsConstants.EINVAL, new java.io.IOException(notALink)); }
    }

    /** Os.getuid and the rest: the app's uid as Process.myUid and native code see it (Robolectric leaves them 0, root); an app's gid is its uid. */
    @Implementation protected int getuid() { return android.os.Process.myUid(); }
    @Implementation protected int geteuid() { return android.os.Process.myUid(); }
    @Implementation protected int getgid() { return android.os.Process.myUid(); }
    @Implementation protected int getegid() { return android.os.Process.myUid(); }

    @Implementation protected long lseek(FileDescriptor fd, long offset, int whence) {
        return natively("lseek", ClassParameter.from(FileDescriptor.class, fd), ClassParameter.from(long.class, offset), ClassParameter.from(int.class, whence));
    }
    /** Os.strerror, and with it every ErrnoException message (a native Robolectric leaves returning null: apps that print it crashed). */
    @Implementation protected String strerror(int errno) { return natively("strerror", ClassParameter.from(int.class, errno)); }
    @Implementation protected void ftruncate(FileDescriptor fd, long length) { natively("ftruncate", ClassParameter.from(FileDescriptor.class, fd), ClassParameter.from(long.class, length)); }
    @Implementation @Override protected FileDescriptor open(String path, int flags, int mode) {
        return natively("open", ClassParameter.from(String.class, path), ClassParameter.from(int.class, flags), ClassParameter.from(int.class, mode));
    }
    // the file system as it is (Robolectric's stat never fails and calls a missing path a symlink when its canonical form differs; symlink, rename, remove, chmod do nothing)
    // shortcut: fstat(fd) stays Robolectric's (mode 0): the real one stopped MediaProvider's scan after a write through openOutputStream (system-apps media: size 0), cause not found yet; upgrade once it is
    private static ClassParameter<String> path(String path) { return ClassParameter.from(String.class, path); }
    @Implementation @Override public android.system.StructStat stat(String path) { return natively("stat", path(path)); }
    // Robolectric's fstat(fd) is stat(null): with the real stat above that threw EFAULT for every descriptor IoBridge.open checks
    // (ParcelFileDescriptor.open, so JW Library's thumbnails never loaded). Its old answer, without the path: mode 0, size 0.
    @Implementation @Override protected android.system.StructStat fstat(FileDescriptor fd) { return new android.system.StructStat(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    @Implementation @Override protected android.system.StructStat lstat(String path) { return natively("lstat", path(path)); }
    @Implementation @Override public void mkdir(String path, int mode) { natively("mkdir", path(path), ClassParameter.from(int.class, mode)); }
    @Implementation protected void chmod(String path, int mode) { natively("chmod", path(path), ClassParameter.from(int.class, mode)); }
    @Implementation protected void remove(String path) { natively("remove", path(path)); }
    @Implementation protected boolean access(String path, int mode) { return natively("access", path(path), ClassParameter.from(int.class, mode)); }
    @Implementation protected void symlink(String oldPath, String newPath) { natively("symlink", path(oldPath), path(newPath)); }
    @Implementation protected void link(String oldPath, String newPath) { natively("link", path(oldPath), path(newPath)); }
    @Implementation protected void rename(String oldPath, String newPath) { natively("rename", path(oldPath), path(newPath)); }
    @Implementation protected FileDescriptor[] pipe2(int flags) { return natively("pipe2", ClassParameter.from(int.class, flags)); }
    @Implementation protected void socketpair(int domain, int type, int protocol, FileDescriptor one, FileDescriptor two) {
        natively("socketpair", ClassParameter.from(int.class, domain), ClassParameter.from(int.class, type), ClassParameter.from(int.class, protocol),
                ClassParameter.from(FileDescriptor.class, one), ClassParameter.from(FileDescriptor.class, two));
    }
    @Implementation protected FileDescriptor socket(int domain, int type, int protocol) {
        return natively("socket", ClassParameter.from(int.class, domain), ClassParameter.from(int.class, type), ClassParameter.from(int.class, protocol));
    }
    @Implementation protected void listen(FileDescriptor fd, int backlog) { natively("listen", ClassParameter.from(FileDescriptor.class, fd), ClassParameter.from(int.class, backlog)); }
    @Implementation protected FileDescriptor accept(FileDescriptor fd, java.net.SocketAddress peer) {
        return natively("accept", ClassParameter.from(FileDescriptor.class, fd), ClassParameter.from(java.net.SocketAddress.class, peer));
    }
    @Implementation protected void shutdown(FileDescriptor fd, int how) { natively("shutdown", ClassParameter.from(FileDescriptor.class, fd), ClassParameter.from(int.class, how)); }
    @Implementation protected int readBytes(FileDescriptor fd, Object buffer, int offset, int count) {
        return natively("readBytes", ClassParameter.from(FileDescriptor.class, fd), ClassParameter.from(Object.class, buffer), ClassParameter.from(int.class, offset), ClassParameter.from(int.class, count));
    }
    @Implementation protected int writeBytes(FileDescriptor fd, Object buffer, int offset, int count) {
        return natively("writeBytes", ClassParameter.from(FileDescriptor.class, fd), ClassParameter.from(Object.class, buffer), ClassParameter.from(int.class, offset), ClassParameter.from(int.class, count));
    }
    @Implementation protected int poll(android.system.StructPollfd[] fds, int timeoutMs) {
        return natively("poll", ClassParameter.from(android.system.StructPollfd[].class, fds), ClassParameter.from(int.class, timeoutMs));
    }

    // extended attributes (MediaProvider keeps a database's uuid in "user.uuid"): the user.* namespace through NIO, kept in memory where the file system has none
    private static final java.util.Map<String, byte[]> XATTR_FALLBACK = new java.util.concurrent.ConcurrentHashMap<>();
    private static java.nio.file.attribute.UserDefinedFileAttributeView xattrs(String path) {
        return java.nio.file.Files.getFileAttributeView(java.nio.file.Path.of(path), java.nio.file.attribute.UserDefinedFileAttributeView.class);
    }
    private static String xattrName(String name) { return name.startsWith("user.") ? name.substring(5) : name; }
    @Implementation protected byte[] getxattr(String path, String name) throws android.system.ErrnoException {
        byte[] kept = XATTR_FALLBACK.get(path + '\0' + name);
        if (kept != null) return kept.clone();
        try {
            java.nio.file.attribute.UserDefinedFileAttributeView view = xattrs(path);
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(view.size(xattrName(name)));
            view.read(xattrName(name), buffer);
            return buffer.array();
        } catch (java.io.IOException | UnsupportedOperationException none) { throw new android.system.ErrnoException("getxattr", android.system.OsConstants.ENODATA, none instanceof java.io.IOException io ? io : null); }
    }
    @Implementation protected void setxattr(String path, String name, byte[] value, int flags) throws android.system.ErrnoException {
        try { xattrs(path).write(xattrName(name), java.nio.ByteBuffer.wrap(value)); XATTR_FALLBACK.remove(path + '\0' + name); }
        catch (java.io.IOException | UnsupportedOperationException unsupported) { XATTR_FALLBACK.put(path + '\0' + name, value.clone()); }
    }
    @Implementation protected void removexattr(String path, String name) throws android.system.ErrnoException {
        XATTR_FALLBACK.remove(path + '\0' + name);
        try { xattrs(path).delete(xattrName(name)); } catch (java.io.IOException | UnsupportedOperationException none) { /* not there */ }
    }
}
