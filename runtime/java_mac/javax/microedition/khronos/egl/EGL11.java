// javax.microedition.khronos.egl.EGL11 -- EGL 1.1. AOSP:
// frameworks/base/opengl/java/javax/microedition/khronos/egl/EGL11.java
//
// The whole of EGL 1.1's addition over 1.0, at this binding level, is one
// error code: EGL_CONTEXT_LOST. Everything else is inherited.
//
// Value confirmed against the Khronos EGL registry (egl.h: #define
// EGL_CONTEXT_LOST 0x300E) and against AOSP core/api/current.txt, which
// records it as 12302 decimal.
package javax.microedition.khronos.egl;

public interface EGL11 extends EGL10 {
    /** Returned by eglGetError when a power-management event caused the
     *  context to be lost and it must be recreated. */
    int EGL_CONTEXT_LOST = 0x300E;
}
