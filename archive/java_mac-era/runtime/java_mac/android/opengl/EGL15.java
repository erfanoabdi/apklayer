// android.opengl.EGL15 -- EGL 1.4/1.5 Java binding.
//
// GENERATED Java decls (constants + native signatures) by
// debug/gles_gen/gen.py from android-34. The native bridges live in
// runtime/src/native_methods.cpp (hand-written: handle marshaling),
// registered in the kEgl14Methods/kEgl15Methods/kEglExtMethods tables.
// Mirrors AOSP android.opengl.EGL15. No em dashes.
package android.opengl;

import java.nio.Buffer;

public class EGL15 {
    public EGL15() {}

    public static final int EGL_CL_EVENT_HANDLE = 12444;
    public static final int EGL_CONDITION_SATISFIED = 12534;
    public static final int EGL_CONTEXT_MAJOR_VERSION = 12440;
    public static final int EGL_CONTEXT_MINOR_VERSION = 12539;
    public static final int EGL_CONTEXT_OPENGL_COMPATIBILITY_PROFILE_BIT = 2;
    public static final int EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT = 1;
    public static final int EGL_CONTEXT_OPENGL_DEBUG = 12720;
    public static final int EGL_CONTEXT_OPENGL_FORWARD_COMPATIBLE = 12721;
    public static final int EGL_CONTEXT_OPENGL_PROFILE_MASK = 12541;
    public static final int EGL_CONTEXT_OPENGL_RESET_NOTIFICATION_STRATEGY = 12733;
    public static final int EGL_CONTEXT_OPENGL_ROBUST_ACCESS = 12722;
    public static final long EGL_FOREVER = -1L;
    public static final int EGL_GL_COLORSPACE = 12445;
    public static final int EGL_GL_COLORSPACE_LINEAR = 12426;
    public static final int EGL_GL_COLORSPACE_SRGB = 12425;
    public static final int EGL_GL_RENDERBUFFER = 12473;
    public static final int EGL_GL_TEXTURE_2D = 12465;
    public static final int EGL_GL_TEXTURE_3D = 12466;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_NEGATIVE_X = 12468;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_NEGATIVE_Y = 12470;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_NEGATIVE_Z = 12472;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_POSITIVE_X = 12467;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_POSITIVE_Y = 12469;
    public static final int EGL_GL_TEXTURE_CUBE_MAP_POSITIVE_Z = 12471;
    public static final int EGL_GL_TEXTURE_LEVEL = 12476;
    public static final int EGL_GL_TEXTURE_ZOFFSET = 12477;
    public static final int EGL_IMAGE_PRESERVED = 12498;
    public static final int EGL_LOSE_CONTEXT_ON_RESET = 12735;
    public static final int EGL_NO_RESET_NOTIFICATION = 12734;
    public static final int EGL_OPENGL_ES3_BIT = 64;
    public static final int EGL_PLATFORM_ANDROID_KHR = 12609;
    public static final int EGL_SIGNALED = 12530;
    public static final int EGL_SYNC_CL_EVENT = 12542;
    public static final int EGL_SYNC_CL_EVENT_COMPLETE = 12543;
    public static final int EGL_SYNC_CONDITION = 12536;
    public static final int EGL_SYNC_FENCE = 12537;
    public static final int EGL_SYNC_FLUSH_COMMANDS_BIT = 1;
    public static final int EGL_SYNC_PRIOR_COMMANDS_COMPLETE = 12528;
    public static final int EGL_SYNC_STATUS = 12529;
    public static final int EGL_SYNC_TYPE = 12535;
    public static final int EGL_TIMEOUT_EXPIRED = 12533;
    public static final int EGL_UNSIGNALED = 12531;

    // The five handle sentinels. AOSP EGL15 declares these `public static final`
    // (EGL14's same-named fields are non-final). They ALIAS EGL14's instances on
    // purpose: apps compare handles with reference equality against values that
    // came back from EGL14 entry points, so two distinct "no display" objects
    // would make `display == EGL15.EGL_NO_DISPLAY` answer false for a display
    // EGL14 itself reported as absent.
    public static final EGLContext EGL_NO_CONTEXT = EGL14.EGL_NO_CONTEXT;
    public static final EGLDisplay EGL_NO_DISPLAY = EGL14.EGL_NO_DISPLAY;
    public static final EGLSurface EGL_NO_SURFACE = EGL14.EGL_NO_SURFACE;
    public static final EGLImage   EGL_NO_IMAGE   = new EGLImage(0L);
    public static final EGLSync    EGL_NO_SYNC    = new EGLSync(0L);

    public static native EGLSync eglCreateSync(EGLDisplay a0, int a1, long[] a2, int a3);
    public static native boolean eglGetSyncAttrib(EGLDisplay a0, EGLSync a1, int a2, long[] a3, int a4);
    public static native boolean eglDestroySync(EGLDisplay a0, EGLSync a1);
    public static native int eglClientWaitSync(EGLDisplay a0, EGLSync a1, int a2, long a3);
    public static native EGLDisplay eglGetPlatformDisplay(int a0, long a1, long[] a2, int a3);
    public static native EGLSurface eglCreatePlatformWindowSurface(EGLDisplay a0, EGLConfig a1, Buffer a2, long[] a3, int a4);
    public static native EGLSurface eglCreatePlatformPixmapSurface(EGLDisplay a0, EGLConfig a1, Buffer a2, long[] a3, int a4);
    public static native boolean eglWaitSync(EGLDisplay a0, EGLSync a1, int a2);
    public static native EGLImage eglCreateImage(EGLDisplay a0, EGLContext a1, int a2, long a3, long[] a4, int a5);
    public static native boolean eglDestroyImage(EGLDisplay a0, EGLImage a1);
}
