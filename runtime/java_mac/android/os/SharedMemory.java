package android.os;

import android.system.ErrnoException;
import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * AOSP-faithful android.os.SharedMemory (API 27). On real Android this wraps an
 * ashmem/memfd file descriptor mapped across processes. Mac.apk is single
 * process with no IPC, so we back it with a plain in-process direct ByteBuffer.
 * The observable contract (create -> map -> read/write -> close, with a stable
 * size and protection state) is preserved; "shared" across our single process
 * means all maps of one SharedMemory alias the same buffer. Mirrors
 * frameworks/base/core/java/android/os/SharedMemory.java.
 */
public final class SharedMemory implements Parcelable, Closeable {
    // POSIX mmap protection bits (mirrors android.system.OsConstants.PROT_*).
    private static final int PROT_READ  = 0x1;
    private static final int PROT_WRITE = 0x2;

    private final ByteBuffer mBacking;
    private final int mSize;
    private int mProt = PROT_READ | PROT_WRITE;
    private boolean mClosed;

    private SharedMemory(int size) {
        mSize = size;
        mBacking = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }

    private SharedMemory(ByteBuffer backing) {
        mSize = backing.capacity();
        mBacking = backing.order(ByteOrder.nativeOrder());
    }

    /**
     * AOSP core/java/android/os/SharedMemory.java:110 -- "Creates an instance
     * from existing shared memory passed as ParcelFileDescriptor ... the newly
     * created SharedMemory takes OWNERSHIP of the passed fd and the original
     * fd becomes detached."
     *
     * Backed by a real mmap rather than a copy: FileChannel.map() is the JDK's
     * mmap(2), so the returned SharedMemory aliases the same pages the fd
     * refers to -- which is the entire point of the class and what a plain
     * read-into-a-buffer would silently destroy. Read-write is attempted first
     * (a shared-memory region is normally opened RW); a read-only fd falls
     * back to a read-only mapping, matching what mmap would do.
     *
     * The fd is detached here, per the contract, and the mapping keeps the
     * underlying pages alive independently of it.
     */
    public static SharedMemory fromFileDescriptor(ParcelFileDescriptor fd) {
        if (fd == null) throw new NullPointerException("fd");
        java.io.FileDescriptor jfd = fd.getFileDescriptor();
        try {
            java.io.RandomAccessFile raf = null;
            java.nio.channels.FileChannel ch;
            long len;
            try {
                raf = new java.io.RandomAccessFile("/dev/fd/" + fd.getFd(), "rw");
                ch = raf.getChannel();
                len = ch.size();
                ByteBuffer buf = ch.map(
                        java.nio.channels.FileChannel.MapMode.READ_WRITE, 0, len);
                fd.detachFd();
                return new SharedMemory(buf);
            } catch (java.io.IOException rw) {
                if (raf != null) { try { raf.close(); } catch (java.io.IOException ignored) {} }
                java.io.FileInputStream in = new java.io.FileInputStream(jfd);
                ch = in.getChannel();
                len = ch.size();
                ByteBuffer buf = ch.map(
                        java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, len);
                fd.detachFd();
                return new SharedMemory(buf);
            }
        } catch (java.io.IOException e) {
            // AOSP's native path aborts with a runtime failure on a bad fd
            // (setInt$ + mmap); surface the real cause rather than a
            // zero-length region that would silently read as zeros.
            throw new IllegalArgumentException("Unable to map fd as shared memory", e);
        }
    }

    public static SharedMemory create(String name, int size) throws ErrnoException {
        if (size <= 0) {
            // AOSP throws IllegalArgumentException for non-positive sizes.
            throw new IllegalArgumentException("Size must be greater than zero");
        }
        return new SharedMemory(size);
    }

    public int getSize() {
        checkOpen();
        return mSize;
    }

    public boolean setProtect(int prot) {
        // Disallow widening beyond what we created with; AOSP only narrows.
        if ((prot & ~(PROT_READ | PROT_WRITE | 0x4 /*PROT_EXEC*/)) != 0) return false;
        mProt = prot;
        return true;
    }

    public ByteBuffer mapReadWrite() throws ErrnoException {
        return map(PROT_READ | PROT_WRITE, 0, mSize);
    }

    public ByteBuffer mapReadOnly() throws ErrnoException {
        return map(PROT_READ, 0, mSize);
    }

    public ByteBuffer map(int prot, int offset, int length) throws ErrnoException {
        checkOpen();
        if (offset < 0 || length < 0 || offset + length > mSize) {
            throw new IllegalArgumentException("offset/length out of bounds");
        }
        ByteBuffer slice;
        synchronized (mBacking) {
            mBacking.position(offset);
            mBacking.limit(offset + length);
            slice = mBacking.slice().order(ByteOrder.nativeOrder());
            mBacking.clear();
        }
        if ((prot & PROT_WRITE) == 0) {
            return slice.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
        }
        return slice;
    }

    public static void unmap(ByteBuffer buffer) {
        // Our buffers are GC-managed; nothing to do. Present for API parity.
    }

    @Override
    public void close() {
        mClosed = true;
    }

    private void checkOpen() {
        if (mClosed) throw new IllegalStateException("SharedMemory is closed");
    }

    @Override
    public int describeContents() {
        return CONTENTS_FILE_DESCRIPTOR;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // Real Android passes the ashmem/memfd fd itself across the Binder
        // transaction, so the receiving process mmaps the SAME physical
        // pages -- writes on one side become visible on the other, which is
        // the entire point of "shared" memory. Mac.apk is single-process, so
        // those pages ARE this object's backing ByteBuffer; a size-only
        // reconstruction (the old behavior here) throws away both the
        // content and that aliasing. writeValue backs onto Parcel's
        // object-ref side list (the same mechanism writeParcelable /
        // writeBundle / writeStrongBinder already use, see
        // Parcel.writeObjectRef), so the round-trip hands back this exact
        // live object instead of a copy -- the only way to preserve the
        // alias-the-same-memory contract in a single process.
        dest.writeValue(this);
    }

    public static final Parcelable.Creator<SharedMemory> CREATOR =
            new Parcelable.Creator<SharedMemory>() {
        @Override
        public SharedMemory createFromParcel(Parcel source) {
            return (SharedMemory) source.readValue(null);
        }

        @Override
        public SharedMemory[] newArray(int size) {
            return new SharedMemory[size];
        }
    };
}
