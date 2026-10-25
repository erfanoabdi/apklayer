package android.os;

import android.system.ErrnoException;
import android.system.OsConstants;

/**
 * AOSP-faithful android.os.ProxyFileDescriptorCallback (API 26). Supplied to
 * StorageManager.openProxyFileDescriptor so a virtual file's reads/writes are
 * serviced by app code. The default implementations throw EBADF/EINVAL exactly
 * like AOSP, so a subclass that only overrides the operations it supports
 * behaves identically. Mirrors
 * frameworks/base/core/java/android/os/ProxyFileDescriptorCallback.java.
 */
public abstract class ProxyFileDescriptorCallback {
    // AOSP uses OsConstants.EBADF; our OsConstants doesn't define it yet, so we
    // use the POSIX value (9) directly. The errno value is what matters to the
    // caller, not the symbol.
    private static final int EBADF = 9;

    public long onGetSize() throws ErrnoException {
        throw new ErrnoException("onGetSize", EBADF);
    }

    public int onRead(long offset, int size, byte[] data) throws ErrnoException {
        throw new ErrnoException("onRead", EBADF);
    }

    public int onWrite(long offset, int size, byte[] data) throws ErrnoException {
        throw new ErrnoException("onWrite", EBADF);
    }

    public void onFsync() throws ErrnoException {
        throw new ErrnoException("onFsync", OsConstants.EINVAL);
    }

    public abstract void onRelease();
}
