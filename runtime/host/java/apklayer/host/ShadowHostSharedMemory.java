package apklayer.host;

import android.os.SharedMemory;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.ReflectionHelpers;

/**
 * android.os.SharedMemory over a real memfd (libcutils' ashmem on memfd): Robolectric's keeps a temp file per create and maps only its own, so a
 * SharedMemory from another process failed ("Cannot find the backing file"). libcore's DirectByteBuffer(int,long,FileDescriptor,Runnable,boolean) that the
 * real map() builds does not exist on HotSpot, so map() is the JDK's mapped buffer: FileChannel.map of the descriptor reopened through /proc/self/fd
 * (a MAP_SHARED mapping of the same memory), unmapped by its cleaner or unmap().
 */
@Implements(className = "android.os.SharedMemory", isInAndroidSdk = false)
public class ShadowHostSharedMemory {
    private static final int MFD_CLOEXEC = 1, MFD_ALLOW_SEALING = 2, F_ADD_SEALS = 1033, F_SEAL_FUTURE_WRITE = 0x10;  // linux/memfd.h, fcntl.h

    @RealObject private SharedMemory real;

    @Implementation protected static FileDescriptor nCreate(String name, int size) throws ErrnoException {
        FileDescriptor fd = Os.memfd_create(name == null ? "" : name, MFD_CLOEXEC | MFD_ALLOW_SEALING);
        try { Os.ftruncate(fd, size); } catch (ErrnoException e) { Os.close(fd); throw e; }
        return fd;
    }

    @Implementation protected static int nGetSize(FileDescriptor fd) {
        try { return (int) Math.min(Integer.MAX_VALUE, new FileInputStream(fd).getChannel().size()); }  // fstat; the stream is never closed (the fd stays the caller's)
        catch (IOException e) { return -1; }
    }

    /** ashmem_set_prot_region on a memfd: taking PROT_WRITE away seals future writable mappings; anything else is kept as it is. */
    @Implementation protected static int nSetProt(FileDescriptor fd, int prot) {
        if ((prot & OsConstants.PROT_WRITE) != 0) return 0;
        try { Os.fcntlInt(fd, F_ADD_SEALS, F_SEAL_FUTURE_WRITE); return 0; } catch (ErrnoException e) { return e.errno; }
    }

    // shortcut: PROT_EXEC is not mapped executable and PROT_NONE maps read-only (FileChannel has no such modes); upgrade when an app needs either
    @Implementation protected ByteBuffer map(int prot, int offset, int length) throws ErrnoException {
        ReflectionHelpers.callInstanceMethod(real, "checkOpen");
        if (offset < 0) throw new IllegalArgumentException("Offset must be >= 0");
        if (length <= 0) throw new IllegalArgumentException("Length must be > 0");
        if ((long) offset + length > real.getSize()) throw new IllegalArgumentException("offset + length must not exceed getSize()");
        boolean write = (prot & OsConstants.PROT_WRITE) != 0;
        int fd = ReflectionHelpers.getField(ReflectionHelpers.getField(real, "mFileDescriptor"), "fd");
        try (RandomAccessFile file = new RandomAccessFile("/proc/self/fd/" + fd, write ? "rw" : "r")) {
            return file.getChannel().map(write ? MapMode.READ_WRITE : MapMode.READ_ONLY, offset, length);
        } catch (IOException e) {  // a sealed region (setProtect(PROT_READ)) refuses a writable mapping, as mmap does on Android
            throw new ErrnoException("mmap", OsConstants.EPERM, e);
        }
    }

    @Implementation protected static void unmap(ByteBuffer buffer) {
        if (!(buffer instanceof MappedByteBuffer) || !buffer.isDirect()) throw new IllegalArgumentException("ByteBuffer wasn't created by #map(int, int, int); can't unmap");
        UnsafeAccess.getUnsafe().invokeCleaner(buffer);
    }
}
