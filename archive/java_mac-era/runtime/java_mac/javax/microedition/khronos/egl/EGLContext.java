package javax.microedition.khronos.egl;
public abstract class EGLContext {
    public EGLContext() {}
    // AOSP declares this `public static EGL getEGL()` (frameworks/base
    // opengl/java/javax/microedition/khronos/egl/EGLContext.java returns the
    // EGL_INSTANCE singleton). The return type is the EGL *interface*, not
    // EGLContext -- bytecode that does `invokestatic ...getEGL()Ljavax/.../egl/EGL;`
    // (e.g. HCR's GLThread) needs that exact descriptor or it throws
    // NoSuchMethodError. Returns MacApkEGL10. Since 2026-09-26 that is a REAL
    // EGL10 over ANGLE for app-owned threads (Telegram's intro EGLThread
    // renders into a TextureView through it), while the GL thread of a live
    // SurfaceView subclass -- the vendored-GLSurfaceView forks (HCR, OpenTTD)
    // -- keeps the old refusal: EGL_NO_CONTEXT at eglCreateContext, so that
    // thread dies where it always did. libGDX's onCreate GLES-detection probe
    // (AndroidGraphics.checkGL20) still gets >= 1 config and still renders
    // through the MTKView path. MACAPK_EGL10_REAL=0 restores the refusal for
    // every thread. See MacApkEGL10 for the full contract. (Was, long ago:
    // return null, which NPE'd libGDX's probe at eglGetDisplay.)
    public static javax.microedition.khronos.egl.EGL getEGL() {
        return javax.microedition.khronos.egl.MacApkEGL10.INSTANCE;
    }
    public abstract javax.microedition.khronos.opengles.GL getGL();
}
