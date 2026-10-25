// android.opengl.EGLDisplay -- typed EGL handle wrapper. See EGLObjectHandle.
// Mirrors AOSP android.opengl.EGLDisplay (package-private ctor; the native EGL14/15
// bridges construct these via the (long) ctor through JNI). No em dashes.
package android.opengl;

public class EGLDisplay extends EGLObjectHandle {
    // Package-private no-arg ctor matches AOSP; the long-handle ctor is what
    // the native bridges actually call to box a real EGL handle.
    EGLDisplay(long handle) {
        super(handle);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof EGLDisplay)) return false;
        return getNativeHandle() == ((EGLDisplay) o).getNativeHandle();
    }
}
