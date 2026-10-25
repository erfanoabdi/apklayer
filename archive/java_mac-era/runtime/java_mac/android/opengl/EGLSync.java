// android.opengl.EGLSync -- typed EGL handle wrapper. See EGLObjectHandle.
// Mirrors AOSP android.opengl.EGLSync (package-private ctor; the native EGL14/15
// bridges construct these via the (long) ctor through JNI). No em dashes.
package android.opengl;

public class EGLSync extends EGLObjectHandle {
    // Package-private no-arg ctor matches AOSP; the long-handle ctor is what
    // the native bridges actually call to box a real EGL handle.
    EGLSync(long handle) {
        super(handle);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof EGLSync)) return false;
        return getNativeHandle() == ((EGLSync) o).getNativeHandle();
    }
}
