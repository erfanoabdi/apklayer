// javax.microedition.khronos.egl.EGL10 -- the legacy EGL 1.0/1.1
// binding interface. Constants mirror AOSP's EGL10 (values confirmed
// against the Khronos EGL registry egl.h, 2026-06-11); the
// EGL_RENDERABLE_TYPE / EGL_OPENGL_ES*_BIT additions are EGL 1.3+
// values that AOSP's javax interface omits but app-side GLSurfaceView
// forks reference numerically -- declaring them here (interface int
// constants are compile-time-inlined, so additions are inert for app
// bytecode) lets our own framework code use names instead of hex.
package javax.microedition.khronos.egl;

public interface EGL10 extends EGL {
    // AOSP declares these three as non-null sentinel objects, each
    // wrapping a degenerate/zero native handle -- never literal null
    // (frameworks/base
    // opengl/java/javax/microedition/khronos/egl/EGL10.java, fetched
    // 2026-07-30: `new com.google.android.gles_jni.EGL{Display,Context,
    // Surface}Impl(0)`). We have no gles_jni impl package (MacApkEGL10's
    // live handles are its own nested wrapper classes over ANGLE, since
    // 2026-09-26), so these are bare anonymous instances of the same
    // abstract types -- identity-distinct from any live context/surface/
    // display we hand out, which is all the `==` comparisons real
    // EglHelper code performs against them require.
    //
    // Load-bearing (v1.0.841, docs/openttd-egl_2026-07-30.md): a
    // pre-`mSurfaceIsBad` GLSurfaceView fork (pelya/libsdl-android's SDL
    // 1.2 port, e.g. OpenTTD -- grep upstream for "mSurfaceIsBad" returns
    // zero hits) never checks eglCreateContext's return before calling
    // .getGL() on it. `null` here turned MacApkEGL10's deliberate
    // EGL_BAD_CONFIG failure (see that class -- fork GLThreads must not
    // survive with a USABLE context) into an uncaught
    // NullPointerException instead of the benign degrade AOSP's own
    // contract produces. Modern (post-mSurfaceIsBad) forks are
    // unaffected either way: real AOSP's own EglHelper.start() /
    // createSurface() guard with `x == null || x == EGL10.EGL_NO_CONTEXT`
    // (`x == null || x == EGL10.EGL_NO_SURFACE` for the surface; fetched
    // from the same file), so they already defend against both
    // representations and never reach a bare `.getGL()`.
    EGLContext EGL_NO_CONTEXT = new EGLContext() {
        // A context wrapping no real GL state hands back no usable GL --
        // matches the "fork GLThreads must not survive" invariant.
        // Returning a working GL10 here would let a surviving fork
        // thread start driving guest rendering itself, on a thread the
        // guest runtime never prepared. See MacApkEGL10.eglCreateContext.
        @Override public javax.microedition.khronos.opengles.GL getGL() { return null; }
    };
    EGLDisplay EGL_NO_DISPLAY = new EGLDisplay() {};
    EGLSurface EGL_NO_SURFACE = new EGLSurface() {};
    // AOSP declares EGL_DEFAULT_DISPLAY as Object (= null), not int. Bytecode
    // reads it as `getstatic ...EGL_DEFAULT_DISPLAY : Ljava/lang/Object;`
    // (e.g. HCR's FSGLSurfaceView$EglHelper.start), so an int field throws
    // NoSuchFieldError. (Surfaced once EGLContext.getEGL() was corrected to
    // return the EGL interface instead of throwing.)
    Object EGL_DEFAULT_DISPLAY = null;
    int EGL_NO_NATIVE_PIXMAP = 0;

    int EGL_DONT_CARE   = -1;

    // Errors.
    int EGL_SUCCESS             = 0x3000;
    int EGL_NOT_INITIALIZED     = 0x3001;
    int EGL_BAD_ACCESS          = 0x3002;
    int EGL_BAD_ALLOC           = 0x3003;
    int EGL_BAD_ATTRIBUTE       = 0x3004;
    int EGL_BAD_CONFIG          = 0x3005;
    int EGL_BAD_CONTEXT         = 0x3006;
    int EGL_BAD_CURRENT_SURFACE = 0x3007;
    int EGL_BAD_DISPLAY         = 0x3008;
    int EGL_BAD_MATCH           = 0x3009;
    int EGL_BAD_NATIVE_PIXMAP   = 0x300A;
    int EGL_BAD_NATIVE_WINDOW   = 0x300B;
    int EGL_BAD_PARAMETER       = 0x300C;
    int EGL_BAD_SURFACE         = 0x300D;

    // Config attributes.
    int EGL_BUFFER_SIZE         = 0x3020;
    int EGL_ALPHA_SIZE          = 0x3021;
    int EGL_BLUE_SIZE           = 0x3022;
    int EGL_GREEN_SIZE          = 0x3023;
    int EGL_RED_SIZE            = 0x3024;
    int EGL_DEPTH_SIZE          = 0x3025;
    int EGL_STENCIL_SIZE        = 0x3026;
    int EGL_CONFIG_CAVEAT       = 0x3027;
    int EGL_CONFIG_ID           = 0x3028;
    int EGL_LEVEL               = 0x3029;
    int EGL_MAX_PBUFFER_HEIGHT  = 0x302A;
    int EGL_MAX_PBUFFER_PIXELS  = 0x302B;
    int EGL_MAX_PBUFFER_WIDTH   = 0x302C;
    int EGL_NATIVE_RENDERABLE   = 0x302D;
    int EGL_NATIVE_VISUAL_ID    = 0x302E;
    int EGL_NATIVE_VISUAL_TYPE  = 0x302F;
    int EGL_SAMPLES             = 0x3031;
    int EGL_SAMPLE_BUFFERS      = 0x3032;
    int EGL_SURFACE_TYPE        = 0x3033;
    int EGL_TRANSPARENT_TYPE    = 0x3034;
    int EGL_TRANSPARENT_BLUE_VALUE  = 0x3035;
    int EGL_TRANSPARENT_GREEN_VALUE = 0x3036;
    int EGL_TRANSPARENT_RED_VALUE   = 0x3037;
    int EGL_NONE                = 0x3038;

    // Config attribute values.
    int EGL_SLOW_CONFIG             = 0x3050;
    int EGL_NON_CONFORMANT_CONFIG   = 0x3051;
    int EGL_TRANSPARENT_RGB         = 0x3052;

    // EGL 1.3+ additions referenced numerically by app forks.
    int EGL_RENDERABLE_TYPE = 0x3040;
    int EGL_CONFORMANT      = 0x3042;
    int EGL_OPENGL_ES_BIT   = 0x0001;
    int EGL_OPENGL_ES2_BIT  = 0x0004;
    int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    // Surface type bits.
    int EGL_PBUFFER_BIT = 0x0001;
    int EGL_PIXMAP_BIT  = 0x0002;
    int EGL_WINDOW_BIT  = 0x0004;

    int EGL_HEIGHT      = 0x3056;
    int EGL_WIDTH       = 0x3057;
    int EGL_VERSION     = 0x3054;
    int EGL_VENDOR      = 0x3053;
    int EGL_EXTENSIONS  = 0x3055;

    boolean eglInitialize(EGLDisplay display, int[] majorMinor);
    boolean eglTerminate(EGLDisplay display);
    EGLDisplay eglGetDisplay(Object nativeDisplay);
    boolean eglChooseConfig(EGLDisplay display, int[] attribList, EGLConfig[] configs, int configSize, int[] numConfig);
    boolean eglGetConfigAttrib(EGLDisplay display, EGLConfig config, int attribute, int[] value);
    EGLContext eglCreateContext(EGLDisplay display, EGLConfig config, EGLContext shareContext, int[] attribList);
    boolean eglDestroyContext(EGLDisplay display, EGLContext context);
    EGLSurface eglCreateWindowSurface(EGLDisplay display, EGLConfig config, Object nativeWindow, int[] attribList);
    boolean eglDestroySurface(EGLDisplay display, EGLSurface surface);
    boolean eglMakeCurrent(EGLDisplay display, EGLSurface draw, EGLSurface read, EGLContext context);
    boolean eglSwapBuffers(EGLDisplay display, EGLSurface surface);
    int eglGetError();
    String eglQueryString(EGLDisplay display, int name);
    // libGDX's AndroidGraphics.onSurfaceCreated reads the current context/
    // display/surface to detect context loss across pause/resume.
    int EGL_DRAW = 0x3059;
    int EGL_READ = 0x305A;
    EGLContext eglGetCurrentContext();
    EGLDisplay eglGetCurrentDisplay();
    EGLSurface eglGetCurrentSurface(int readdraw);

    default boolean eglGetConfigs(EGLDisplay display, EGLConfig[] configs, int config_size, int[] num_config) {
        if (num_config != null && num_config.length > 0) num_config[0] = 0;
        return true;
    }

    // ---- The rest of AOSP's EGL10 surface -------------------------------
    // AOSP declares all seven ABSTRACT (frameworks/base/opengl/java/javax/
    // microedition/khronos/egl/EGL10.java). Declared abstract here too --
    // MacApkEGL10 is the only implementation in this tree, and an app
    // IMPLEMENTS this interface essentially never (it is a platform binding
    // apps call, not one they provide), so abstract matches AOSP without the
    // breakage that would justify a default.

    /** Copy the colour buffer of a surface into a native pixmap. */
    boolean eglCopyBuffers(EGLDisplay display, EGLSurface surface, Object native_pixmap);

    /** Create an off-screen pbuffer surface. */
    EGLSurface eglCreatePbufferSurface(EGLDisplay display, EGLConfig config, int[] attrib_list);

    /** Create a surface that renders into a native pixmap. */
    EGLSurface eglCreatePixmapSurface(EGLDisplay display, EGLConfig config,
                                      Object native_pixmap, int[] attrib_list);

    /** Query a context attribute (EGL_CONFIG_ID, EGL_CONTEXT_CLIENT_TYPE, ...). */
    boolean eglQueryContext(EGLDisplay display, EGLContext context, int attribute, int[] value);

    /** Query a surface attribute (EGL_WIDTH, EGL_HEIGHT, EGL_CONFIG_ID, ...). */
    boolean eglQuerySurface(EGLDisplay display, EGLSurface surface, int attribute, int[] value);

    /** Block until all GL rendering issued on the current context completes. */
    boolean eglWaitGL();

    /** Block until native rendering on the given engine completes. */
    boolean eglWaitNative(int engine, Object bindTarget);
}

