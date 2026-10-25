// android.opengl.EGLObjectHandle -- base class for the EGL14/15 handle
// wrappers (EGLDisplay, EGLContext, EGLSurface, EGLConfig, EGLImage,
// EGLSync). Mirrors AOSP: an opaque native handle boxed in a Java object so
// the EGL14 binding can pass typed handles around instead of bare ints/longs.
//
// AOSP stores the handle in a private long and exposes getHandle()/
// getNativeHandle(). We keep the same observable surface. The native bridges
// in runtime/src/native_methods.cpp read/write mHandle by field id, so the
// field name + type (long mHandle) are load-bearing -- do not rename.
// (Project convention: no em dashes.)
package android.opengl;

public abstract class EGLObjectHandle {
    private final long mHandle;

    protected EGLObjectHandle(int handle) {
        mHandle = handle;
    }

    protected EGLObjectHandle(long handle) {
        mHandle = handle;
    }

    // Deprecated on AOSP in favor of getNativeHandle(); kept for source
    // compatibility with older apps. Truncates to int like AOSP did.
    public int getHandle() {
        if ((mHandle & 0xffffffffL) != mHandle) {
            throw new UnsupportedOperationException(
                "64-bit handle does not fit in an int");
        }
        return (int) mHandle;
    }

    public long getNativeHandle() {
        return mHandle;
    }

    @Override
    public int hashCode() {
        return (int) (mHandle ^ (mHandle >>> 32));
    }
}
