// android.opengl.EGLExt -- EGL 1.4/1.5 Java binding.
//
// GENERATED Java decls (constants + native signatures) by
// debug/gles_gen/gen.py from android-34. The native bridges live in
// runtime/src/native_methods.cpp (hand-written: handle marshaling),
// registered in the kEgl14Methods/kEgl15Methods/kEglExtMethods tables.
// Mirrors AOSP android.opengl.EGLExt. No em dashes.
package android.opengl;

import java.nio.Buffer;

public class EGLExt {
    public EGLExt() {}

    public static final int EGL_CONTEXT_FLAGS_KHR = 12540;
    public static final int EGL_CONTEXT_MAJOR_VERSION_KHR = 12440;
    public static final int EGL_CONTEXT_MINOR_VERSION_KHR = 12539;
    public static final int EGL_NO_NATIVE_FENCE_FD_ANDROID = -1;
    public static final int EGL_OPENGL_ES3_BIT_KHR = 64;
    public static final int EGL_RECORDABLE_ANDROID = 12610;
    public static final int EGL_SYNC_NATIVE_FENCE_ANDROID = 12612;
    public static final int EGL_SYNC_NATIVE_FENCE_FD_ANDROID = 12613;
    public static final int EGL_SYNC_NATIVE_FENCE_SIGNALED_ANDROID = 12614;

    public static native boolean eglPresentationTimeANDROID(EGLDisplay a0, EGLSurface a1, long a2);

    /**
     * AOSP EGLExt.eglDupNativeFenceFDANDROID(EGLDisplay, EGLSync) (API 33):
     * duplicate an EGLSync's underlying Linux sync_file fd as a SyncFence.
     *
     * Deliberately NOT a native bridge. Mac.apk has no sync_file: host GPU
     * synchronisation is a Metal shared event behind ANGLE, and no fd ever
     * reaches guest Java. AOSP's documented answer when the fence cannot be
     * duplicated is an INVALID SyncFence, and that is exactly what we can
     * honestly produce -- isValid() false, getSignalTime() SIGNAL_TIME_INVALID,
     * await returning immediately. Callers already branch on isValid(), because
     * real devices return invalid fences too (querying a frame that produced
     * none).
     *
     * A fence claiming it signalled would make a caller believe it waited for
     * GPU work that never happened, and it would then read a half-drawn buffer
     * -- a failure that surfaces far from here. Absent-and-honest beats plausible.
     */
    public static android.hardware.SyncFence eglDupNativeFenceFDANDROID(
            EGLDisplay display, EGLSync sync) {
        return android.hardware.SyncFence.createEmpty();
    }
}
