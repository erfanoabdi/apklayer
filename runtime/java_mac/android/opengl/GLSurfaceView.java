// android.opengl.GLSurfaceView -- the canonical "OpenGL surface in a
// View" wrapper. AndEngine builds its rendering on top of this; many
// other engines do the same. v2's impl maps it to a native MTKView via
// JNI -- the renderer's onSurfaceCreated/Changed/onDrawFrame callbacks
// fire from the Cocoa display link callback.
package android.opengl;

import android.content.Context;
import android.view.SurfaceView;

import com.macapk.loader.MetalSurfaceCallbacks;
import com.macapk.loader.NativeCocoa;
import com.macapk.loader.NativeLog;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLSurfaceView extends SurfaceView
        implements MetalSurfaceCallbacks, android.view.SurfaceHolder.Callback2 {
    public static final int RENDERMODE_WHEN_DIRTY    = 0;
    public static final int RENDERMODE_CONTINUOUSLY  = 1;

    public static final int DEBUG_CHECK_GL_ERROR = 1;
    public static final int DEBUG_LOG_GL_CALLS   = 2;

    private Renderer renderer;
    // volatile: written on the UI thread (setRenderMode), read on the render tick
    // (AOSP writes it under sGLThreadManager and reads it under the same lock).
    private volatile int renderMode = RENDERMODE_CONTINUOUSLY;

    // ---- MACAPK_GLSV_RENDER_MODE (2026-10-01, default ON) -------------------
    // AOSP GLThread draws only when readyToDraw() (android14-release
    // opengl/java/android/opengl/GLSurfaceView.java :1628-1632):
    //     return (!mPaused) && mHasSurface && (!mSurfaceIsBad)
    //         && (mWidth > 0) && (mHeight > 0)
    //         && (mRequestRender || (mRenderMode == RENDERMODE_CONTINUOUSLY));
    // mRequestRender starts true (GLThread() :1258), is set by requestRender()
    // (:1650-1655), onResume() (:1747) and onWindowResize() (:1768), and is
    // cleared as a frame is taken (guardedRun :1467). Ours stored setRenderMode
    // and never read it, and requestRender() was a no-op, so a WHEN_DIRTY view
    // drew every vsync. That is how a PAUSED cocos2d-x game kept drawing:
    // Cocos2dxGLSurfaceView.onPause does setRenderMode(RENDERMODE_WHEN_DIRTY)
    // and skips super.onPause(), relying on exactly this term to stop its
    // GLThread. Measured with debug/glcover (an opaque Activity started over a
    // cocos-shaped game): the game drew 2 more frames after its onPause, into
    // the shared EGL surface the new top's window had just been bound to
    // (docs/gd-meltdown-interstitial-hidden-window_2026-10-01.md).
    // =0 restores the old path byte for byte: the mode is stored, never read,
    // never validated, and requestRender() does nothing.
    static final boolean RENDER_MODE =
        !"0".equals(System.getenv("MACAPK_GLSV_RENDER_MODE"));
    /** AOSP GLThread.mRequestRender (initially true). Any thread sets it;
     *  the render tick takes it with getAndSet(false), as guardedRun clears it
     *  under sGLThreadManager before the draw, so a request that lands DURING
     *  a draw yields one more draw rather than being lost. */
    private final java.util.concurrent.atomic.AtomicBoolean requestRenderPending =
        new java.util.concurrent.atomic.AtomicBoolean(true);
    /** Frames withheld by the render-mode term (diagnostic count). */
    private long renderModeWithheld;
    private int lastWidthPx;
    private int lastHeightPx;
    // Which renderer instance has received its onSurfaceCreated /
    // onSurfaceChanged lifecycle, and at what pixel size. Tracks the
    // INSTANCE (not a bool) so a renderer installed via setRenderer()
    // AFTER the MTKView already fired the one-shot surfaceChanged still
    // gets its GL setup before the first onDrawFrame. This is the common
    // shape for apps that create the renderer in Activity.onCreate: view
    // inflation registers the GLSurfaceView as the MTKView delegate, the
    // native side's single macapkSurfaceChanged can fire before the
    // app's GL20Renderer constructor calls setRenderer(this), and the
    // old "fire onSurfaceCreated only if renderer!=null right now" logic
    // then dropped the callbacks entirely -> shaders never compiled,
    // viewport/projection never set -> every frame drew nothing (Bouncy
    // / Vector Pinball: pure-black GL field beneath a transparent
    // overlay). See ensureLifecycleDelivered().
    private Renderer lifecycleDeliveredTo;
    private int      lifecycleDeliveredW = -1;
    private int      lifecycleDeliveredH = -1;
    private long     drawFrameCount;
    /** How many times renderer.onDrawFrame has thrown. Rate-limits the full
     *  cause-chain dump (it used to print on every frame, ~60x/second) and
     *  identifies the FIRST throw, which is the one worth reporting as a
     *  crash. See the catch block in macapkDrawFrame. */
    private long     rendererThrowCount;
    /** Set once the renderer has thrown, when MACAPK_GL_RENDERER_THROW_FATAL=1:
     *  AOSP's GLThread is dead after an uncaught renderer exception and never
     *  calls onDrawFrame again. Default OFF -- see the catch block. */
    private boolean  rendererDead;

    /** MACAPK_GL_RENDERER_THROW_FATAL=1 -> adopt AOSP's "the GLThread is dead"
     *  semantics after a renderer throws, instead of re-entering a renderer
     *  that has already failed. Read once; default OFF because it could not be
     *  swept (the framework jar is rebuilt/staged by the boss, so a Java edit
     *  is inert in the session that writes it). */
    private static Boolean sRendererThrowFatal;
    private static boolean macapkRendererThrowIsFatal() {
        Boolean v = sRendererThrowFatal;
        if (v == null) {
            String e = null;
            try { e = System.getenv("MACAPK_GL_RENDERER_THROW_FATAL"); }
            catch (Throwable ignored) { }
            v = Boolean.valueOf("1".equals(e));
            sRendererThrowFatal = v;
        }
        return v.booleanValue();
    }

    // v1.0.504: the most-recent GLSurfaceView that had a non-null renderer
    // installed via setRenderer(). Used by ActivityHost's fallback-content-view
    // safety net to AVOID creating an empty fallback GLSurfaceView, which would
    // steal the native render-delegate binding from this real, renderer-bearing
    // view -> every frame skipped -> black. AndEngine's RenderSurfaceView is the
    // canonical case: it setRenderer()s itself in onCreate, then the fallback
    // (which runs when the standard content view isn't set yet) constructed an
    // empty GLSurfaceView whose ctor re-bound the native delegate to itself.
    // Generic: any engine that installs a renderer is protected; engines that
    // never call setRenderer (cocos2d-x: mRenderer + reflection) leave this null
    // and are unaffected.
    private static volatile GLSurfaceView sLastRendererBearing;

    // ---- AOSP pause / exit contract (MACAPK_GLSV_PAUSE, 2026-09-27) --------
    //
    // AOSP (android14-release opengl/java/android/opengl/GLSurfaceView.java):
    //   onPause()  -> mGLThread.onPause() (:578-580): set mRequestPaused, then
    //                 BLOCK the caller in `while ((!mExited) && (!mPaused))
    //                 sGLThreadManager.wait()` (:1721-1740) until the GL thread
    //                 is between frames and has seen the pause.
    //   readyToDraw() needs `!mPaused` (:1628-1632), so no onSurfaceCreated /
    //                 onSurfaceChanged / onDrawFrame runs until onResume()
    //                 clears mRequestPaused (:1741). Queued events still run.
    //   onDetachedFromWindow() -> mGLThread.requestExitAndWait() (:630-639):
    //                 the GL thread exits and never calls the renderer again;
    //   onAttachedToWindow() starts a NEW GLThread when mDetached (:610-627).
    // Apps rely on this ordering. AndEngine's BaseGameActivity.onPause calls
    // RenderSurfaceView.onPause() BEFORE Engine.stop/onDestroy, and its
    // UpdateThread <-> renderer handshake (EngineLock: waitUntilCanDraw /
    // waitUntilCanUpdate) is only safe because the renderer stops calling
    // onDrawFrame before the UpdateThread exits; without that the render
    // thread (ours: the AppKit main thread) parks forever in waitUntilCanDraw
    // after onDestroy. Our onPause/onResume were empty, which is why the
    // org.andengine.b.c lock shadow had to exist. docs/andengine-lock-shadow-
    // retired_2026-09-27.md.
    //
    // Ours has no GLThread: the render thread is whichever thread calls
    // macapkDrawFrame (drawInMTKView, the AppKit main thread). So "paused" is
    // "no renderer call is in flight and none will start", and the wait is
    // BOUNDED (PAUSE_WAIT_MS) because the thread we wait on is AppKit main --
    // a wedged main thread must not wedge the owner thread too. The bound is
    // logged loudly when it is hit. =0 restores the no-op onPause/onResume and
    // a renderer that is driven after detach, exactly as before -- and, through
    // AppClassLoader.RETIRE_ENGINE_SHADOWS, the org.andengine.b.c shadow, since
    // the app's own lock under the old no-op onPause wedges AppKit main.
    static final boolean GLSV_PAUSE =
        !"0".equals(System.getenv("MACAPK_GLSV_PAUSE"));
    private static final long PAUSE_WAIT_MS = 2000L;
    /** Guards the four fields below. Never held while calling the renderer. */
    private final Object pauseLock = new Object();
    /** AOSP mRequestPaused: set by onPause, cleared by onResume. */
    private boolean requestPaused;
    /** AOSP mExited: set by onDetachedFromWindow, cleared by a re-attach. */
    private boolean exited;
    /** Renderer calls in flight, all threads: a render tick's events +
     *  lifecycle + onDrawFrame, or a surfaceChanged's lifecycle delivery. */
    private int rendererCalls;
    /** The last thread to start one (named in the timeout warning). */
    private Thread rendererCallThread;
    /** This thread's share of rendererCalls. A renderer (or queued event) that
     *  pauses its own view must not wait on itself, and a surfaceChanged
     *  reached from inside a render tick nests in it rather than replacing it.
     *  Read and written only by its own thread, so outside pauseLock. */
    private final ThreadLocal<int[]> myRendererCalls = new ThreadLocal<>();

    /** For the offline gate (debug/geh_andengine-lock-shadow_gate.sh). */
    public static boolean macapkPauseContractEnabled() { return GLSV_PAUSE; }

    // Static GL10 stub instance handed to the Renderer's callbacks.
    // AndEngine et al. don't actually call GLES1 through it; they
    // ignore the parameter and use android.opengl.GLES20 directly.
    private static final GL10 STUB_GL10 = new StubGL10();

    /** Mac.apk addition: the shared GL10 implementation, for
     *  SurfaceView's reflection-driven renderer dispatch. On real
     *  Android the GL10 handed to onDrawFrame/onSurfaceCreated is
     *  never null; engines that issue GLES1-style calls through it
     *  (Halfbrick's GameManager.SystemInit calls gl.glFrontFace)
     *  NPE per frame if the reflection path passes null. */
    public static GL10 macapkSharedGL10() { return STUB_GL10; }

    public GLSurfaceView(Context c) {
        super(c);
        // SurfaceView's ctor already pointed nativeHandle at the singleton
        // MTKView. Wire OUR per-frame callback to it so onDrawFrame fires
        // here. If multiple GLSurfaceView instances are constructed (e.g.
        // a fallback + the real one when an app's onCreate threw partway),
        // the most-recently-set delegate wins -- matching setContentView's
        // "latest wins" semantics.
        long handle = getNativeHandle();
        if (handle != 0) NativeCocoa.setRenderer(handle, this);
    }
    public GLSurfaceView(Context c, android.util.AttributeSet a) { this(c); }

    public void setRenderer(Renderer r) {
        this.renderer = r;
        // v1.0.504: claim "renderer-bearing" status so the fallback safety net
        // won't clobber our native binding with an empty GLSurfaceView.
        // Only the retired fallback reads it, so with MACAPK_EMPTY_WINDOW_DECOR
        // on (the default: no framework-built GLSurfaceView, nothing to guard)
        // it is never written and no longer pins the last GL activity.
        if (r != null && !com.macapk.loader.ActivityHost.EMPTY_WINDOW_DECOR) sLastRendererBearing = this;
        // NOTE: do NOT fire onSurfaceCreated here. setRenderer is called
        // from the app's onCreate (off the render frame), where the ANGLE
        // EGL context is NOT current -- initShaders() and friends would
        // run their glCreateProgram/glCompileShader against no context and
        // silently fail. The lifecycle is delivered lazily from
        // ensureLifecycleDelivered(), called inside macapkDrawFrame /
        // macapkSurfaceChanged where we are between AngleBeginFrame and
        // AngleEndFrame (context current).
        NativeLog.i("GLSurfaceView",
            "setRenderer(" + (r != null ? r.getClass().getName() : "null")
            + ") on " + getClass().getName()
            + " (surface " + lastWidthPx + "x" + lastHeightPx + ")");
    }
    public Renderer getRenderer() { return renderer; }

    /** Mac.apk framework-internal (see SurfaceView.macapkRendersOnOsMain):
     *  a set Renderer is driven by macapkDrawFrame on the AppKit thread, which
     *  therefore is this view's GL thread. */
    @Override public boolean macapkRendersOnOsMain() {
        return renderer != null || super.macapkRendersOnOsMain();
    }

    /** v1.0.504: true when a GLSurfaceView with a live renderer has been
     *  installed for the given activity context -- so ActivityHost should NOT
     *  create an empty fallback content view that would steal the native render
     *  binding (the AndEngine RenderSurfaceView black-screen). The context-chain
     *  match keeps a renderer left over from a PRIOR activity from suppressing a
     *  genuinely-needed fallback in a later one. */
    public static boolean macapkHasRendererBearingViewFor(android.content.Context activityCtx) {
        GLSurfaceView v = sLastRendererBearing;
        if (v == null || v.renderer == null || activityCtx == null) return false;
        android.content.Context c = v.getContext();
        while (c != null) {
            if (c == activityCtx) return true;
            if (c instanceof android.content.ContextWrapper)
                c = ((android.content.ContextWrapper) c).getBaseContext();
            else break;
        }
        return false;
    }

    /** Deliver onSurfaceCreated (once per renderer instance) and
     *  onSurfaceChanged (once per renderer + size) to the current
     *  renderer. MUST be called only from within a frame (between
     *  AngleBeginFrame/EndFrame) so the GL context is current. Idempotent
     *  via the lifecycleDeliveredTo / lifecycleDeliveredW/H latches.
     *  Returns false only while MACAPK_GL_WAIT_FRAME holds the FIRST
     *  onSurfaceCreated back because the view has no frame yet (the caller
     *  must not draw then, as AOSP's GLThread does not); true otherwise. */
    private boolean ensureLifecycleDelivered() {
        Renderer r = renderer;
        if (r == null) return true;
        int w = lastWidthPx, h = lastHeightPx;
        if (w <= 0 || h <= 0) {
            w = NativeCocoa.phoneWidth();
            h = NativeCocoa.phoneHeight();
            if (w <= 0) w = 800;
            if (h <= 0) h = 600;
            lastWidthPx = w;
            lastHeightPx = h;
        }
        // MACAPK_GL_WAIT_FRAME (2026-09-27): AOSP creates a renderer's surface
        // only once the view has a frame (SurfaceView mHaveFrame, GLThread
        // readyToDraw) -- see SurfaceView.macapkRendererMayStart. The app's own
        // onSizeChanged has then run, which is what cocos2d-x / axmol read in
        // onSurfaceCreated. Checked before the size below, so a view laid out
        // smaller than its window is sized from that layout (bouncy).
        if (lifecycleDeliveredTo != r && !macapkRendererMayStart(w, h)) return false;
        // v1.0.996: the renderer gets the APP WINDOW's surface, not the display's.
        // lastWidthPx/lastHeightPx are the full DISPLAY drawable (that is what the
        // native one-shot publishes, and presentOverlayIfNeeded/WindowOverlayStack
        // still want it), but AOSP hands a SurfaceView's renderer the view's own
        // laid-out size, which excludes the system-bar regions the OS owns. See
        // StatusBarDriver.contentBoxHeightPx for the AOSP citation and the AVD
        // measurement. No-op for every immersive/edge-to-edge app (inset 0), so the
        // Unity/native-EGL population is untouched.
        // v1.0.997: both axes. A side navigation bar narrows the window, and the
        // renderer must be told the same width the ANGLE layer was sized to.
        // 2026-09-26: through SurfaceView.macapkRendererSurfaceSize, the ONE
        // renderer accessor shared with the reflected path. It is this same
        // window box unless the view does not fill its window and sits on the
        // GL layer's origin, where it is the view's own laid-out size, as AOSP
        // gives it (SurfaceView.updateSurface :985-988; bouncy's GLFieldView
        // under its ScoreView). MACAPK_SV_VIEW_SIZE_GL=0 restores the box.
        final int displayW = w, displayH = h;
        int[] rendererSize = macapkRendererSurfaceSize(w, h);
        w = rendererSize[0];
        h = rendererSize[1];
        macapkWarnGlSizeSplit(w, h, displayW, displayH);
        try {
            // CRITICAL ORDER: populate the renderer's cached screen size
            // BEFORE onSurfaceCreated. cocos2d-x / axmol renderers (OpenGD's
            // AxmolRenderer, any Cocos2dxRenderer) call nativeInit(width,
            // height) from onSurfaceCreated, reading the size that
            // setScreenWidthAndHeight cached. If we fire onSurfaceCreated
            // first, nativeInit sees (0,0) -> zero GL viewport -> every
            // glDraw* clips to nothing -> black at 60fps. (Regression from
            // the v1.0.288 refactor that moved this call after
            // onSurfaceCreated; it black-screened OpenGD.)
            // MACAPK_GL_WAIT_FRAME: gone. It called the method BY NAME, on every
            // frame, overwriting the app's own fields; now the first
            // onSurfaceCreated waits for the view's frame, and the app's own
            // onSizeChanged has made the same call by then (AOSP order).
            if (!macapkGlWaitFrame()) GLSurfaceView.tryInvokeSetScreenSize(r, w, h);
            final GL10 gl = wrappedGL();
            if (lifecycleDeliveredTo != r) {
                // The renderer runs as the app's GLThread (no Looper):
                // Looper.macapkEnterGlRenderer, docs/gl-thread-has-no-looper_2026-10-01.md
                android.os.Looper.macapkEnterGlRenderer();
                try { r.onSurfaceCreated(gl, javax.microedition.khronos.egl.MacApkEGL10.macapkWindowSurfaceConfig()); }
                finally { android.os.Looper.macapkExitGlRenderer(); }
                lifecycleDeliveredTo = r;
                lifecycleDeliveredW = -1;   // force onSurfaceChanged below
                NativeLog.i("GLSurfaceView",
                    "onSurfaceCreated -> " + r.getClass().getName());
            }
            if (lifecycleDeliveredW != w || lifecycleDeliveredH != h) {
                android.os.Looper.macapkEnterGlRenderer();
                try { r.onSurfaceChanged(gl, w, h); }
                finally { android.os.Looper.macapkExitGlRenderer(); }
                lifecycleDeliveredW = w;
                lifecycleDeliveredH = h;
                NativeLog.i("GLSurfaceView",
                    "onSurfaceChanged(" + w + "x" + h + ") -> "
                    + r.getClass().getName());
            }
        } catch (Throwable t) {
            NativeLog.e("GLSurfaceView", "lifecycle delivery threw: " + t);
        }
        return true;
    }

    // ---- MetalSurfaceCallbacks: called from MTKView delegate via JNI ----
    @Override public void macapkDrawFrame() {
        if (GLSV_PAUSE && renderer != null) {
            macapkDrawFramePausable();
            return;
        }
        macapkDrawFrameBody();
    }

    /** MACAPK_GLSV_PAUSE: one render-thread tick under the AOSP pause/exit
     *  contract. The paused check and the in-flight mark are ONE step under
     *  pauseLock (macapkEnterRendererCall), so an onPause that has returned can
     *  never be followed by a renderer call that had already passed its check.
     *  While paused/exited: queued GL events still run (AOSP guardedRun takes
     *  mEventQueue before it looks at the pause state) and the view tree /
     *  overlays / surface convergence are still driven, exactly as the
     *  rendererDead branch does; only the renderer's lifecycle and onDrawFrame
     *  are withheld. A withheld tick that composites nothing touches no
     *  default-framebuffer attachment, so ANGLE-Metal obtains no drawable and
     *  eglSwapBuffers presents nothing (SurfaceMtl.mm swapImpl presents only an
     *  obtained drawable): the last frame stays on screen, as AOSP's compositor
     *  keeps a paused GLSurfaceView's last buffer. */
    private void macapkDrawFramePausable() {
        if (!macapkEnterRendererCall()) {
            if (!rendererDead) macapkRunQueuedGlEvents();
            macapkDriveOverlayForCurrentSurface();
            presentOverlayIfNeeded();
            macapkConvergeSurfaceLifecycle(lastWidthPx, lastHeightPx);
            return;
        }
        try {
            macapkDrawFrameBody();
        } finally {
            macapkExitRendererCall();
        }
    }

    /** AOSP readyToDraw() (:1628-1632) needs !mPaused, and an exited GLThread
     *  calls nothing: false while onPause / onDetachedFromWindow withhold the
     *  renderer. Otherwise marks a renderer call in flight in the same step
     *  (under pauseLock), and the caller MUST macapkExitRendererCall(). */
    private boolean macapkEnterRendererCall() {
        synchronized (pauseLock) {
            if (requestPaused || exited) return false;
            rendererCalls++;
            rendererCallThread = Thread.currentThread();
        }
        int[] mine = myRendererCalls.get();
        if (mine == null) { mine = new int[1]; myRendererCalls.set(mine); }
        mine[0]++;
        return true;
    }

    private void macapkExitRendererCall() {
        int[] mine = myRendererCalls.get();
        if (mine != null && mine[0] > 0) mine[0]--;
        synchronized (pauseLock) {
            if (rendererCalls > 0) rendererCalls--;
            if (rendererCalls == 0) rendererCallThread = null;
            pauseLock.notifyAll();
        }
    }

    /** AOSP GLThread.onPause / requestExitAndWait: wait until no renderer call
     *  is in flight on ANOTHER thread. Returns at once for this thread's own
     *  calls (a renderer or queued event that pauses its own view), and after
     *  PAUSE_WAIT_MS otherwise, loudly. */
    private void macapkAwaitRendererIdle(String why) {
        final long deadline = System.nanoTime() + PAUSE_WAIT_MS * 1_000_000L;
        int[] mine = myRendererCalls.get();
        final int own = mine != null ? mine[0] : 0;
        synchronized (pauseLock) {
            while (rendererCalls - own > 0) {
                long leftMs = (deadline - System.nanoTime()) / 1_000_000L;
                if (leftMs <= 0) {
                    NativeLog.w("GLSurfaceView", why + ": renderer call still in flight on "
                        + (rendererCallThread != null ? rendererCallThread.getName() : "?")
                        + " after " + PAUSE_WAIT_MS + " ms; continuing (AOSP waits "
                        + "unbounded; ours is bounded because the render thread is "
                        + "AppKit main). No further renderer call will START.");
                    return;
                }
                try {
                    pauseLock.wait(leftMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void macapkDrawFrameBody() {
        // Two-tier dispatch:
        //   (1) If the subclass called setRenderer() on us, that
        //       renderer takes precedence and we forward directly.
        //   (2) Otherwise, defer to SurfaceView's reflection-driven
        //       auto-discovery (which walks the class hierarchy for
        //       any field with an onDrawFrame method). Cocos2dx-style
        //       wrappers (Cocos2dxGLSurfaceView extends our
        //       GLSurfaceView) store their renderer in `mRenderer`
        //       and never call setRenderer(); without this fallback
        //       per-frame nativeRender() never fires, dt stays 0,
        //       transitions freeze, and the menu is unresponsive
        //       even though touches arrive at the JNI bridge.
        if (renderer != null) {
            // v1.0.1639: run queued GL events FIRST, as AOSP's GLThread does
            // (guardedRun checks mEventQueue before every draw step and empties
            // it). See macapkRunQueuedGlEvents. Not once the renderer is dead:
            // an AOSP GLThread that threw is gone and runs nothing again.
            if (!rendererDead) macapkRunQueuedGlEvents();
            // Deliver onSurfaceCreated/onSurfaceChanged first if they
            // haven't reached this renderer yet (renderer installed after
            // the native one-shot surfaceChanged already fired). Cheap
            // after the first frame (latched). Context is current here.
            if (!ensureLifecycleDelivered()) {
                // MACAPK_GL_WAIT_FRAME: no onSurfaceCreated yet, so no
                // onDrawFrame (AOSP GLThread readyToDraw). The tree is still
                // driven -- the overlay drive is what gets the view laid out --
                // and the base convergence still runs, as on a drawn frame.
                macapkDriveOverlayForCurrentSurface();
                presentOverlayIfNeeded();
                macapkConvergeSurfaceLifecycle(lastWidthPx, lastHeightPx);
                return;
            }
            // MACAPK_GLSV_RENDER_MODE: AOSP readyToDraw's last term. A withheld
            // tick is the !ensureLifecycleDelivered tick above: the tree, the
            // overlays and the surface convergence are still driven, the
            // renderer is not, and (no default-framebuffer attachment touched)
            // ANGLE presents nothing, so the last frame stays on screen.
            //
            // A pending self-capture / debug dump of FBO 0 is the one reason to
            // draw anyway: glReadPixels on a default framebuffer nothing was
            // drawn to makes ANGLE fetch a NEW drawable (older pool contents)
            // that eglSwapBuffers would then PRESENT, so the instrument must see
            // a REAL frame. The native side answers it (macapkWithheldTickNeedsFrame)
            // and, when it answers no, knows this tick drew nothing and skips the
            // readback itself. The draw is an app-visible "expose" -- the same
            // extra onDrawFrame AOSP gives a surfaceRedrawNeeded / requestRender.
            if (!macapkTakeRenderRequest() && !macapkWithheldTickNeedsFrame()) {
                if ((++renderModeWithheld) == 1) {
                    NativeLog.i("GLSurfaceView", "RENDERMODE_WHEN_DIRTY: withholding "
                        + renderer.getClass().getName() + ".onDrawFrame until requestRender()"
                        + " (AOSP readyToDraw; MACAPK_GLSV_RENDER_MODE)");
                }
                macapkDriveOverlayForCurrentSurface();
                presentOverlayIfNeeded();
                macapkConvergeSurfaceLifecycle(lastWidthPx, lastHeightPx);
                return;
            }
            macapkDetectDrawBehindTop(renderer);
            long n = ++drawFrameCount;
            if (n == 1 || n == 60 || n == 600 || (n % 1800) == 0) {
                NativeLog.i("GLSurfaceView",
                    "macapkDrawFrame #" + n + " -> "
                    + renderer.getClass().getName() + ".onDrawFrame");
            }
            if (rendererDead) {
                // AOSP-faithful: a GLThread that threw is DEAD and never
                // calls onDrawFrame again (see the catch below). Only
                // reachable when MACAPK_GL_RENDERER_THROW_FATAL=1.
                macapkDriveOverlayForCurrentSurface();
                presentOverlayIfNeeded();
                return;
            }
            try {
                android.os.Looper.macapkEnterGlRenderer();
                try { renderer.onDrawFrame(STUB_GL10); }
                finally { android.os.Looper.macapkExitGlRenderer(); }
            } catch (Throwable t) {
                // WHAT AOSP DOES. GLSurfaceView.GLThread.guardedRun() calls
                //   mRenderer.onDrawFrame(gl);
                // with NO try/catch, so a throwing renderer propagates out of
                // GLThread.run() -> ThreadGroup.uncaughtException ->
                // RuntimeInit's handler, which logs
                // "FATAL EXCEPTION: GLThread" and kills the process. The
                // renderer is never called again -- the thread is gone.
                //
                // WHAT WE USED TO DO: log the chain and keep going, forever.
                // Two measured problems (2026-07-29):
                //   1. The failure was INVISIBLE as a failure. ShatteredPD's
                //      GdxRuntimeException became a silent, permanently blank
                //      window instead of a crash -- three sessions were spent
                //      scoring its own error activity as "renders".
                //   2. It logged the FULL cause chain + every stack frame on
                //      EVERY frame, i.e. ~60x/second forever.
                //
                // Now: (a) rate-limit the full dump, (b) route the FIRST throw
                // through the thread's UncaughtExceptionHandler so it lands in
                // the log as the same "FATAL EXCEPTION: <thread>" record a real
                // device produces (Boot installs that handler; this is the same
                // path an uncaught guest exception takes anywhere else), and
                // (c) optionally stop driving the renderer, which is the
                // actually-AOSP-faithful outcome.
                //
                // (c) IS GATED DEFAULT-OFF (MACAPK_GL_RENDERER_THROW_FATAL=1).
                // Java edits are inert until the shared jar is rebuilt, so this
                // could not be swept: an app that throws on frame 1 and would
                // otherwise limp on becomes permanently stopped, which is a
                // visible sweep change even though it matches AOSP. (a) and (b)
                // are pure logging and always on.
                long nthrow = ++rendererThrowCount;
                if (nthrow == 1 || (nthrow % 600) == 0) {
                    NativeLog.e("GLSurfaceView",
                        "renderer.onDrawFrame threw (#" + nthrow + "): " + t);
                    // Log the full chain so root-cause frames are visible
                    // rather than just the toString of the top exception.
                    // Otherwise a NullPointerException 12 frames deep looks
                    // identical regardless of which code path actually
                    // touched a null reference.
                    Throwable cur = t;
                    int depth = 0;
                    while (cur != null && depth < 6) {
                        NativeLog.e("GLSurfaceView", "  at "
                            + cur.getClass().getName() + ": "
                            + cur.getMessage());
                        for (StackTraceElement el : cur.getStackTrace()) {
                            NativeLog.e("GLSurfaceView", "    " + el);
                        }
                        cur = cur.getCause();
                        depth++;
                    }
                }
                if (nthrow == 1) {
                    // Make it visible as a CRASH, exactly once. Never let the
                    // reporter itself break the render loop.
                    try {
                        Thread cu = Thread.currentThread();
                        Thread.UncaughtExceptionHandler h =
                            cu.getUncaughtExceptionHandler();
                        if (h != null) h.uncaughtException(cu, t);
                    } catch (Throwable reportFailed) {
                        NativeLog.e("GLSurfaceView",
                            "uncaught-handler dispatch threw: " + reportFailed);
                    }
                    if (macapkRendererThrowIsFatal()) {
                        rendererDead = true;
                        NativeLog.e("GLSurfaceView",
                            "renderer marked DEAD (AOSP GLThread semantics; "
                            + "MACAPK_GL_RENDERER_THROW_FATAL=1) -- "
                            + renderer.getClass().getName()
                            + " will not be driven again");
                    }
                }
            }
            // v1.0.835: ALSO drive the ViewCanvasDriver overlay. This branch
            // returns without calling super, so SurfaceView's prologue never
            // ran -- and presentOverlayIfNeeded() below covers only
            // WindowManager overlays (Dialog/PopupWindow), NOT the sibling
            // widgets that live in the ViewCanvasDriver overlay. That is why
            // Bouncy drew nothing: its GL tier was healthy (1,800+ frames,
            // 3,600 glDrawElements, err=0x0) but its actual UI is ordinary
            // widgets sharing a ViewGroup with the GL surface, and the overlay
            // carrying them was never driven once. Measured discriminator:
            // `grep -c "ViewCanvasDriver.*fire #"` = sgtpuzzles 9, bouncy 0,
            // jetpackjoyride 0.
            macapkDriveOverlayForCurrentSurface();
            presentOverlayIfNeeded();
            // v1.0.964: this branch returns WITHOUT calling super, so the base
            // class's once-per-frame surface/focus convergence never runs from
            // the moment the app installs a Renderer. That orphaned
            // onWindowFocusChanged(true) for every GLSurfaceView app whose
            // first macapkSurfaceChanged had to defer (no holder callback yet
            // + activity not resumed yet) -- measured on Vector Pinball, where
            // the missing focus meant FieldDriver.start() never ran and the GL
            // table drew 560 zero-area triangles. Drawing and window focus are
            // unrelated on real Android; keep them unrelated here.
            macapkConvergeSurfaceLifecycle(lastWidthPx, lastHeightPx);
            return;
        }
        // super (SurfaceView.macapkDrawFrame) composites overlays for EVERY
        // surface now -- including the reflection-dispatched engines that never
        // reach this class's own renderer path -- so calling it again here would
        // only repeat the measure pass. The early-return branch above still
        // needs its own call, because that path never reaches super.
        super.macapkDrawFrame();
    }

    /** 2026-07-23: composite any WindowManager overlay (Dialog/AlertDialog/
     *  PopupWindow) on top of this GL frame -- see
     *  WindowOverlayStack.presentForGl's doc for why this is needed (that
     *  class's bounds/render pass previously only ran from ViewCanvasDriver's
     *  Canvas-app draw loop, which a GL app never reaches). presentForGl
     *  itself is cheap when no overlay is showing (one boolean check) --
     *  MUST still be called in that case (not short-circuited here) so it
     *  can detect the empty TRANSITION and clear a just-dismissed dialog's
     *  last-rendered frame instead of leaving it stuck on screen. Uses the
     *  last known surface size (populated by macapkSurfaceChanged); harmless
     *  no-op before the first surfaceChanged (size still 0). */
    private void presentOverlayIfNeeded() {
        try {
            com.macapk.loader.WindowOverlayStack.presentForGl(lastWidthPx, lastHeightPx);
        } catch (Throwable t) {
            NativeLog.w("GLSurfaceView", "presentOverlayIfNeeded threw: " + t);
        }
    }
    /** MACAPK_GLQ_DRAIN=0 restores the pre-v1.0.1639 model exactly: no
     *  renderer-branch drain, the launch-burst drain in ActivityHost back on,
     *  and TouchDispatcher's renderer-direct delivery for every engine. */
    private static final boolean GLQ_DRAIN =
        !"0".equals(System.getenv("MACAPK_GLQ_DRAIN"));

    /** True when queueEvent work is serviced per frame on the render thread by
     *  a GLSurfaceView with a Renderer. Only under the real looper: in the
     *  legacy model GLOBAL_QUEUE holds ALL main Handler.post work, which the
     *  overlay driver already drains, and that path stays byte-identical. */
    public static boolean macapkGlQueueDrainEnabled() {
        return android.os.Looper.MACAPK_REAL_LOOPER && GLQ_DRAIN;
    }

    /** v1.0.1639: service GLSurfaceView.queueEvent work on the render thread.
     *
     *  AOSP (android14 GLSurfaceView.GLThread.guardedRun): the loop checks
     *  mEventQueue before anything else (only mShouldExit comes first), runs one
     *  event and `continue`s without drawing, so EVERY pending event runs before
     *  onSurfaceCreated / onSurfaceChanged / onDrawFrame -- while paused, before
     *  a surface exists, and in RENDERMODE_WHEN_DIRTY with no requestRender.
     *
     *  Ours: queueEvent lands in Handler's GLOBAL_QUEUE (enqueueGlThread), which
     *  SurfaceView.macapkDrawFrame drains -- but the Renderer branch of
     *  macapkDrawFrame returns before reaching it, so for every setRenderer app
     *  (cocos2d-x, axmol, King, libGDX, Arc) queued work ran only in
     *  ActivityHost.performLaunch's one-shot drain, on the owner thread with no
     *  GL context. That is why a Stage-2 relaunched cocos2d-x activity rendered
     *  black: the old instance's handleOnPause ran at the new launch, and the new
     *  instance's handleOnResume (queued later, from focus) never ran at all.
     *
     *  The queue stays process-wide on purpose: the new view's constructor
     *  re-targets the single MTKView delegate, after which the old view gets no
     *  frames, so a per-view queue would strand the old instance's onPause. FIFO
     *  keeps the one order the owner thread produced (old pause, then new
     *  resume). */
    private static void macapkRunQueuedGlEvents() {
        if (!macapkGlQueueDrainEnabled()) return;
        try {
            android.os.Handler.drainGlobal();
        } catch (Throwable t) {
            NativeLog.e("GLSurfaceView", "GL event drain threw: " + t);
        }
    }

    /** Native entry (drawInMTKView, render thread, context current). Runs
     *  queued GL events before the lifecycle delivery below, as AOSP does before
     *  onSurfaceCreated: on a new delegate's first tick this is what makes an old
     *  instance's queued pause run BEFORE the new instance's nativeInit. The
     *  SurfaceHolder.Callback form (surfaceChanged) goes straight to the body --
     *  it can be reached from any thread. */
    @Override public void macapkSurfaceChanged(int widthPx, int heightPx) {
        // 2026-09-26: the native display arrives HERE (the MTKView delegate),
        // so record it for SurfaceView's window-sized consumers (the widget
        // overlay, the WindowManager overlay, the lockCanvas placement) before
        // anything below can re-enter with a holder's size. From then on
        // SurfaceView no longer takes the per-frame converge size as the
        // display (SurfaceView.macapkNoteNativeDisplaySize).
        macapkNoteNativeDisplaySize(widthPx, heightPx);
        if (renderer != null && !rendererDead) macapkRunQueuedGlEvents();
        macapkSurfaceChangedBody(widthPx, heightPx);
    }

    private void macapkSurfaceChangedBody(int widthPx, int heightPx) {
        // AOSP GLThread.onWindowResize (android14-release GLSurfaceView.java
        // :1763-1769) sets mSizeChanged and mRequestRender on EVERY surfaceChanged
        // delivery, whether or not the size differs (a surface recreated when the
        // activity comes back to the front re-reports the same size), so a
        // WHEN_DIRTY view draws once per delivery. Deliveries here are one-shot per
        // size/surface (macapkSurfaceChanged native; the holder's fireChanged on a
        // converged size change), never per frame.
        if (RENDER_MODE) requestRenderPending.set(true);
        // Record the surface size unconditionally so a renderer installed
        // LATER (via setRenderer from onCreate, after this one-shot native
        // callback) can still be sized correctly by ensureLifecycleDelivered.
        lastWidthPx = widthPx;
        lastHeightPx = heightPx;
        // Publish the size for keyboard-driven touches (key-binding profiles,
        // Space Bar Taps Center), which place their finger in surface pixels.
        // SurfaceView.macapkSurfaceChanged does this, but the renderer branch
        // below returns without calling super, so a GL game with its own
        // renderer (Flappy Bird's AndEngine view) left the size at 0x0 and every
        // bound key was swallowed with no touch.
        try { com.macapk.loader.TouchDispatcher.macapkNoteSurfaceSize(widthPx, heightPx); }
        catch (Throwable ignored) { }
        NativeLog.i("GLSurfaceView", "macapkSurfaceChanged " + widthPx + "x"
            + heightPx + " on " + getClass().getName()
            + " renderer=" + (renderer != null ? renderer.getClass().getName() : "null"));
        if (renderer != null) {
            // Context is current here (we run between AngleBeginFrame and
            // AngleEndFrame). Deliver the GL lifecycle now; the per-frame
            // ensureLifecycleDelivered() in macapkDrawFrame is the
            // backstop for the renderer-set-after-surfaceChanged ordering.
            // MACAPK_GLSV_PAUSE: AOSP readyToDraw() needs !mPaused, so a paused
            // or exited view records the size only; the first unpaused tick's
            // ensureLifecycleDelivered delivers it (AOSP GLThread.onWindowResize
            // :1763-1795 only records mWidth/mHeight; onSurfaceChanged follows
            // onResume). The check and the in-flight mark are one step, as on a
            // render tick, so an onPause racing this (the SurfaceHolder.Callback
            // form can run on any thread) waits for the delivery to finish.
            if (!GLSV_PAUSE) {
                ensureLifecycleDelivered();
            } else if (macapkEnterRendererCall()) {
                try {
                    ensureLifecycleDelivered();
                } finally {
                    macapkExitRendererCall();
                }
            }
            // v1.0.964: super is deliberately skipped here (it would
            // re-dispatch onSurfaceCreated/onSurfaceChanged to a renderer
            // ensureLifecycleDelivered has already driven), but the base
            // class's SURFACE/FOCUS bookkeeping is not part of that dispatch
            // and must still run -- otherwise an app that installs its
            // Renderer before the first surface callback never records a
            // pending surface size and never gets onWindowFocusChanged(true).
            macapkConvergeSurfaceLifecycle(widthPx, heightPx);
            return;
        }
        super.macapkSurfaceChanged(widthPx, heightPx);
    }

    /** Reached only with MACAPK_GL_WAIT_FRAME=0; delete with that gate. */
    private static void tryInvokeSetScreenSize(Object target, int w, int h) {
        if (target == null) return;
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            try {
                java.lang.reflect.Method m =
                    c.getDeclaredMethod("setScreenWidthAndHeight", int.class, int.class);
                m.setAccessible(true);
                m.invoke(target, w, h);
                return;
            } catch (NoSuchMethodException nsme) {
                c = c.getSuperclass();
            } catch (Throwable ignore) {
                return;
            }
        }
    }

    // GL10 implementation handed to Renderer callbacks. Mirrors the
    // full AOSP GL10 interface: methods with a direct GLES2 equivalent
    // delegate to android.opengl.GLES20 (which routes to ANGLE), so
    // legacy texture-upload code paths (Jetpack Joyride's splash
    // builds its textures through gl.glGenTextures/glTexImage2D on
    // this handle) produce real GL objects. Fixed-function-only entry
    // points (matrix stack, lighting, fog, client arrays) have no
    // GLES2 equivalent and are accepted as no-ops -- engines that
    // pass them use the values at draw time through their own GLES2
    // shaders, not through this handle. Fixed-point (16.16 "x")
    // variants convert to float and reuse the float path.
    private static final class StubGL10 implements GL10 {
        private static float fx(int v) { return v / 65536f; }
        @Override public void glActiveTexture(int texture) { android.opengl.GLES20.glActiveTexture(texture); }
        @Override public void glAlphaFunc(int func, float ref) {}
        @Override public void glAlphaFuncx(int func, int ref) {}
        @Override public void glBindTexture(int target, int texture) { android.opengl.GLES20.glBindTexture(target, texture); }
        @Override public void glBlendFunc(int sfactor, int dfactor) { android.opengl.GLES20.glBlendFunc(sfactor, dfactor); }
        @Override public void glClear(int mask) { android.opengl.GLES20.glClear(mask); }
        @Override public void glClearColor(float r, float g, float b, float a) { android.opengl.GLES20.glClearColor(r, g, b, a); }
        @Override public void glClearColorx(int r, int g, int b, int a) { android.opengl.GLES20.glClearColor(fx(r), fx(g), fx(b), fx(a)); }
        @Override public void glClearDepthf(float depth) { android.opengl.GLES20.glClearDepthf(depth); }
        @Override public void glClearDepthx(int depth) { android.opengl.GLES20.glClearDepthf(fx(depth)); }
        @Override public void glClearStencil(int s) { android.opengl.GLES20.glClearStencil(s); }
        @Override public void glClientActiveTexture(int texture) {}
        @Override public void glColor4f(float r, float g, float b, float a) {}
        @Override public void glColor4x(int r, int g, int b, int a) {}
        @Override public void glColorMask(boolean r, boolean g, boolean b, boolean a) { android.opengl.GLES20.glColorMask(r, g, b, a); }
        @Override public void glColorPointer(int size, int type, int stride, java.nio.Buffer pointer) {}
        @Override public void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, int imageSize, java.nio.Buffer data) { android.opengl.GLES20.glCompressedTexImage2D(target, level, internalformat, width, height, border, imageSize, data); }
        @Override public void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int imageSize, java.nio.Buffer data) { android.opengl.GLES20.glCompressedTexSubImage2D(target, level, xoffset, yoffset, width, height, format, imageSize, data); }
        @Override public void glCopyTexImage2D(int target, int level, int internalformat, int x, int y, int width, int height, int border) { android.opengl.GLES20.glCopyTexImage2D(target, level, internalformat, x, y, width, height, border); }
        @Override public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) { android.opengl.GLES20.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height); }
        @Override public void glCullFace(int mode) { android.opengl.GLES20.glCullFace(mode); }
        @Override public void glDeleteTextures(int n, int[] textures, int offset) { android.opengl.GLES20.glDeleteTextures(n, textures, offset); }
        @Override public void glDeleteTextures(int n, java.nio.IntBuffer textures) { android.opengl.GLES20.glDeleteTextures(n, textures); }
        @Override public void glDepthFunc(int func) { android.opengl.GLES20.glDepthFunc(func); }
        @Override public void glDepthMask(boolean flag) { android.opengl.GLES20.glDepthMask(flag); }
        @Override public void glDepthRangef(float zNear, float zFar) { android.opengl.GLES20.glDepthRangef(zNear, zFar); }
        @Override public void glDepthRangex(int zNear, int zFar) { android.opengl.GLES20.glDepthRangef(fx(zNear), fx(zFar)); }
        @Override public void glDisable(int cap) { android.opengl.GLES20.glDisable(cap); }
        @Override public void glDisableClientState(int array) {}
        @Override public void glDrawArrays(int mode, int first, int count) { android.opengl.GLES20.glDrawArrays(mode, first, count); }
        @Override public void glDrawElements(int mode, int count, int type, java.nio.Buffer indices) { android.opengl.GLES20.glDrawElements(mode, count, type, indices); }
        @Override public void glEnable(int cap) { android.opengl.GLES20.glEnable(cap); }
        @Override public void glEnableClientState(int array) {}
        @Override public void glFinish() { android.opengl.GLES20.glFinish(); }
        @Override public void glFlush() { android.opengl.GLES20.glFlush(); }
        @Override public void glFogf(int pname, float param) {}
        @Override public void glFogfv(int pname, float[] params, int offset) {}
        @Override public void glFogfv(int pname, java.nio.FloatBuffer params) {}
        @Override public void glFogx(int pname, int param) {}
        @Override public void glFogxv(int pname, int[] params, int offset) {}
        @Override public void glFogxv(int pname, java.nio.IntBuffer params) {}
        @Override public void glFrontFace(int mode) { android.opengl.GLES20.glFrontFace(mode); }
        @Override public void glFrustumf(float l, float r, float b, float t, float n, float f) {}
        @Override public void glFrustumx(int l, int r, int b, int t, int n, int f) {}
        @Override public void glGenTextures(int n, int[] textures, int offset) { android.opengl.GLES20.glGenTextures(n, textures, offset); }
        @Override public void glGenTextures(int n, java.nio.IntBuffer textures) { android.opengl.GLES20.glGenTextures(n, textures); }
        @Override public int glGetError() { return android.opengl.GLES20.glGetError(); }
        @Override public void glGetIntegerv(int pname, int[] params, int offset) { android.opengl.GLES20.glGetIntegerv(pname, params, offset); }
        @Override public void glGetIntegerv(int pname, java.nio.IntBuffer params) { android.opengl.GLES20.glGetIntegerv(pname, params); }
        // Route the legacy GL10 string query to the real ANGLE GLES20
        // call so libGDX's capability detection gets the actual values.
        @Override public String glGetString(int name) { return android.opengl.GLES20.glGetString(name); }
        @Override public void glHint(int target, int mode) { android.opengl.GLES20.glHint(target, mode); }
        @Override public void glLightModelf(int pname, float param) {}
        @Override public void glLightModelfv(int pname, float[] params, int offset) {}
        @Override public void glLightModelfv(int pname, java.nio.FloatBuffer params) {}
        @Override public void glLightModelx(int pname, int param) {}
        @Override public void glLightModelxv(int pname, int[] params, int offset) {}
        @Override public void glLightModelxv(int pname, java.nio.IntBuffer params) {}
        @Override public void glLightf(int light, int pname, float param) {}
        @Override public void glLightfv(int light, int pname, float[] params, int offset) {}
        @Override public void glLightfv(int light, int pname, java.nio.FloatBuffer params) {}
        @Override public void glLightx(int light, int pname, int param) {}
        @Override public void glLightxv(int light, int pname, int[] params, int offset) {}
        @Override public void glLightxv(int light, int pname, java.nio.IntBuffer params) {}
        @Override public void glLineWidth(float width) { android.opengl.GLES20.glLineWidth(width); }
        @Override public void glLineWidthx(int width) { android.opengl.GLES20.glLineWidth(fx(width)); }
        @Override public void glLoadIdentity() {}
        @Override public void glLoadMatrixf(float[] m, int offset) {}
        @Override public void glLoadMatrixf(java.nio.FloatBuffer m) {}
        @Override public void glLoadMatrixx(int[] m, int offset) {}
        @Override public void glLoadMatrixx(java.nio.IntBuffer m) {}
        @Override public void glLogicOp(int opcode) {}
        @Override public void glMaterialf(int face, int pname, float param) {}
        @Override public void glMaterialfv(int face, int pname, float[] params, int offset) {}
        @Override public void glMaterialfv(int face, int pname, java.nio.FloatBuffer params) {}
        @Override public void glMaterialx(int face, int pname, int param) {}
        @Override public void glMaterialxv(int face, int pname, int[] params, int offset) {}
        @Override public void glMaterialxv(int face, int pname, java.nio.IntBuffer params) {}
        @Override public void glMatrixMode(int mode) {}
        @Override public void glMultMatrixf(float[] m, int offset) {}
        @Override public void glMultMatrixf(java.nio.FloatBuffer m) {}
        @Override public void glMultMatrixx(int[] m, int offset) {}
        @Override public void glMultMatrixx(java.nio.IntBuffer m) {}
        @Override public void glMultiTexCoord4f(int target, float s, float t, float r, float q) {}
        @Override public void glMultiTexCoord4x(int target, int s, int t, int r, int q) {}
        @Override public void glNormal3f(float nx, float ny, float nz) {}
        @Override public void glNormal3x(int nx, int ny, int nz) {}
        @Override public void glNormalPointer(int type, int stride, java.nio.Buffer pointer) {}
        @Override public void glOrthof(float l, float r, float b, float t, float n, float f) {}
        @Override public void glOrthox(int l, int r, int b, int t, int n, int f) {}
        @Override public void glPixelStorei(int pname, int param) { android.opengl.GLES20.glPixelStorei(pname, param); }
        @Override public void glPointSize(float size) {}
        @Override public void glPointSizex(int size) {}
        @Override public void glPolygonOffset(float factor, float units) { android.opengl.GLES20.glPolygonOffset(factor, units); }
        @Override public void glPolygonOffsetx(int factor, int units) { android.opengl.GLES20.glPolygonOffset(fx(factor), fx(units)); }
        @Override public void glPopMatrix() {}
        @Override public void glPushMatrix() {}
        @Override public void glReadPixels(int x, int y, int width, int height, int format, int type, java.nio.Buffer pixels) { android.opengl.GLES20.glReadPixels(x, y, width, height, format, type, pixels); }
        @Override public void glRotatef(float angle, float x, float y, float z) {}
        @Override public void glRotatex(int angle, int x, int y, int z) {}
        @Override public void glSampleCoverage(float value, boolean invert) { android.opengl.GLES20.glSampleCoverage(value, invert); }
        @Override public void glSampleCoveragex(int value, boolean invert) { android.opengl.GLES20.glSampleCoverage(fx(value), invert); }
        @Override public void glScalef(float x, float y, float z) {}
        @Override public void glScalex(int x, int y, int z) {}
        @Override public void glScissor(int x, int y, int width, int height) { android.opengl.GLES20.glScissor(x, y, width, height); }
        @Override public void glShadeModel(int mode) {}
        @Override public void glStencilFunc(int func, int ref, int mask) { android.opengl.GLES20.glStencilFunc(func, ref, mask); }
        @Override public void glStencilMask(int mask) { android.opengl.GLES20.glStencilMask(mask); }
        @Override public void glStencilOp(int fail, int zfail, int zpass) { android.opengl.GLES20.glStencilOp(fail, zfail, zpass); }
        @Override public void glTexCoordPointer(int size, int type, int stride, java.nio.Buffer pointer) {}
        @Override public void glTexEnvf(int target, int pname, float param) {}
        @Override public void glTexEnvfv(int target, int pname, float[] params, int offset) {}
        @Override public void glTexEnvfv(int target, int pname, java.nio.FloatBuffer params) {}
        @Override public void glTexEnvx(int target, int pname, int param) {}
        @Override public void glTexEnvxv(int target, int pname, int[] params, int offset) {}
        @Override public void glTexEnvxv(int target, int pname, java.nio.IntBuffer params) {}
        @Override public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, java.nio.Buffer pixels) { android.opengl.GLES20.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels); }
        @Override public void glTexParameterf(int target, int pname, float param) { android.opengl.GLES20.glTexParameterf(target, pname, param); }
        // GLES1's fixed-point glTexParameterx carries enum values
        // (GL_LINEAR, GL_REPEAT, ...) as plain ints -- route to the
        // integer parameter entry point, not a 16.16 conversion.
        @Override public void glTexParameterx(int target, int pname, int param) { android.opengl.GLES20.glTexParameteri(target, pname, param); }
        @Override public void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, java.nio.Buffer pixels) { android.opengl.GLES20.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels); }
        @Override public void glTranslatef(float x, float y, float z) {}
        @Override public void glTranslatex(int x, int y, int z) {}
        @Override public void glVertexPointer(int size, int type, int stride, java.nio.Buffer pointer) {}
        @Override public void glViewport(int x, int y, int w, int h) { android.opengl.GLES20.glViewport(x, y, w, h); }
    }

    /** AOSP :494-496 -> GLThread.setRenderMode (:1634-1642): range-checked,
     *  and read by every render tick (readyToDraw). See RENDER_MODE. */
    public void setRenderMode(int mode) {
        if (RENDER_MODE && !(RENDERMODE_WHEN_DIRTY <= mode && mode <= RENDERMODE_CONTINUOUSLY)) {
            throw new IllegalArgumentException("renderMode");
        }
        this.renderMode = mode;
    }
    public int  getRenderMode()         { return renderMode; }

    /** AOSP :516-518 -> GLThread.requestRender (:1650-1655): one more frame for
     *  a RENDERMODE_WHEN_DIRTY view. May be called from any thread. */
    public void requestRender() {
        if (RENDER_MODE) requestRenderPending.set(true);
    }

    /** The render-mode term of AOSP readyToDraw(), taken once per tick:
     *  CONTINUOUSLY always draws; WHEN_DIRTY draws only when a request is
     *  pending, and consumes it. Package-private for the offline gate. */
    boolean macapkTakeRenderRequest() {
        if (!RENDER_MODE) return true;
        // guardedRun clears mRequestRender for EVERY frame it takes (:1467),
        // whatever the mode, so a request made under CONTINUOUSLY is consumed
        // there and cannot buy a frame after a later switch to WHEN_DIRTY.
        final boolean requested = requestRenderPending.getAndSet(false);
        return requested || renderMode == RENDERMODE_CONTINUOUSLY;
    }

    /** Set false once the native half is missing (a plain-JVM gate registers none):
     *  the question is then answered "no" without raising per tick. */
    private static volatile boolean sWithheldNativeOk = true;

    /** This tick is about to be withheld (WHEN_DIRTY, no request): tell the native
     *  frame end so it neither reads back nor dumps a frame nothing was drawn to,
     *  and learn whether a self-capture / dump is waiting for real pixels, in which
     *  case the tick draws after all. Package-private for the offline gate. */
    boolean macapkWithheldTickNeedsFrame() {
        if (!sWithheldNativeOk) return false;
        try {
            // A game whose Activity is stopped under another never draws for an
            // instrument either: that is exactly the draw into the top's EGL
            // surface this term exists to stop (the "gl" source then simply
            // does not claim, and the canvas / composite sources serve).
            return com.macapk.loader.NativeCocoa.beginWithheldTick(!macapkOwnerStoppedUnderTop());
        } catch (UnsatisfiedLinkError e) {
            sWithheldNativeOk = false;
            return false;
        }
    }

    /** AOSP :578-580 -> GLThread.onPause (:1721-1740): request the pause and
     *  wait until the render thread is between frames. No renderer yet means
     *  no GLThread in AOSP (it would NPE); ours stays a no-op there, which
     *  keeps the reflection-driven views (cocos2d-x v2 never calls
     *  setRenderer) exactly as before. See GLSV_PAUSE. */
    public void onPause() {
        if (!GLSV_PAUSE || renderer == null) return;
        synchronized (pauseLock) {
            requestPaused = true;
            pauseLock.notifyAll();
        }
        macapkAwaitRendererIdle("onPause");
    }

    /** AOSP :591-593 -> GLThread.onResume (:1741-1760): clear the pause. AOSP
     *  also waits for the GL thread to leave the paused state; ours needs no
     *  wait, the next render tick simply draws. */
    public void onResume() {
        // AOSP GLThread.onResume :1747 sets mRequestRender (a resumed WHEN_DIRTY
        // view draws once).
        if (RENDER_MODE) requestRenderPending.set(true);
        if (!GLSV_PAUSE || renderer == null) return;
        synchronized (pauseLock) {
            requestPaused = false;
            pauseLock.notifyAll();
        }
    }

    /** AOSP :610-627: a re-attached view with a renderer gets a NEW GLThread,
     *  which starts unpaused and not exited. (A new AOSP GLThread also makes a
     *  new EGL context and re-delivers onSurfaceCreated; ours keeps the one
     *  shared context, so the renderer's latched lifecycle stands.) */
    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!GLSV_PAUSE || renderer == null) return;
        synchronized (pauseLock) {
            if (exited) {
                exited = false;
                requestPaused = false;
                pauseLock.notifyAll();
            }
        }
    }

    /** AOSP :630-639: mGLThread.requestExitAndWait() -- the renderer is never
     *  called again for this attachment. Reached from ActivityHost's teardown
     *  (macapkRemoveWindow) and from a ViewGroup removal. */
    @Override public void onDetachedFromWindow() {
        if (GLSV_PAUSE && renderer != null) {
            synchronized (pauseLock) {
                exited = true;
                pauseLock.notifyAll();
            }
            macapkAwaitRendererIdle("onDetachedFromWindow");
        }
        super.onDetachedFromWindow();
    }
    public void queueEvent(Runnable r) {
        // GLSurfaceView.queueEvent runs work on the GL render thread. In
        // Mac.apk that thread is the OS main thread (where ANGLE's context is
        // current and onDrawFrame fires), serviced by Handler.drainGlobal each
        // frame (renderer path: macapkRunQueuedGlEvents, v1.0.1639; reflection
        // path: SurfaceView.macapkDrawFrame). Route to the GL-thread queue (GLOBAL_QUEUE) via
        // enqueueGlThread -- NOT the main loop (Tier 2 M2). In the historical
        // model main Handler.post also landed in GLOBAL_QUEUE, so this is
        // identical when MACAPK_REAL_LOOPER is off and the correct GL/main
        // split when it is on. (AndEngine queues UpdateThread runnables here
        // every frame; enqueueGlThread is a plain deque add -- no per-call
        // Handler allocation.)
        if (r == null) return;
        // A queued event runs on the GLThread too (AOSP guardedRun takes it off
        // mEventQueue and runs it there), so it runs as the Looper-less GL
        // thread. Wrapped HERE, not around the drain: the same queue also
        // carries host-path taps and fork-GLThread work that are not ours to
        // re-label. docs/gl-thread-has-no-looper_2026-10-01.md
        final Runnable ev = r;
        android.os.Handler.enqueueGlThread(!android.os.Looper.macapkGlThreadNoLooperEnabled() ? r : () -> {
            android.os.Looper.macapkEnterGlRenderer();
            try { ev.run(); }
            finally { android.os.Looper.macapkExitGlRenderer(); }
        });
    }

    // ---- EGL configuration the app asked for -------------------------
    // AOSP GLSurfaceView's setters do exactly one thing: record the
    // request (checkRenderThreadState(); mField = value) so the GL thread's
    // EglHelper can pass it to eglCreateContext / eglChooseConfig later.
    // Mac.apk never runs the app's EGL thread -- rendering goes through the
    // MTKView/ANGLE path -- but RECORDING the request is still the setter's
    // job, and dropping it on the floor is what makes a
    // setEGLContextClientVersion(3) app silently run against the ES2
    // context angle_glue.cpp hardcodes. Field names/defaults mirror AOSP
    // (mEGLContextClientVersion defaults to 0; the chooser/factory
    // references default to null).
    private int mEGLContextClientVersion;
    private EGLConfigChooser mEGLConfigChooser;
    private EGLContextFactory mEGLContextFactory;
    private EGLWindowSurfaceFactory mEGLWindowSurfaceFactory;
    // Component sizes requested through the two NUMERIC
    // setEGLConfigChooser overloads, as {r, g, b, a, depth, stencil}.
    // Stays null when the app installed its own EGLConfigChooser object,
    // because the only way to learn that object's requirements is to RUN
    // it against a live EGLDisplay -- i.e. to stand up a second, competing
    // EGL driver, which is exactly what the javax.microedition.khronos.egl
    // (MacApkEGL10) design deliberately avoids.
    private int[] mRequestedConfigSizes;

    public void setEGLContextClientVersion(int version) {
        mEGLContextClientVersion = version;
    }
    public void setEGLConfigChooser(EGLConfigChooser chooser) {
        mEGLConfigChooser = chooser;
    }
    public void setEGLConfigChooser(boolean needDepth) {
        // AOSP delegates to `new SimpleEGLConfigChooser(needDepth)`, whose
        // ComponentSizeChooser super() call is
        // (8, 8, 8, 0, withDepthBuffer ? 16 : 0, 0).
        setEGLConfigChooser(8, 8, 8, 0, needDepth ? 16 : 0, 0);
    }
    public void setEGLConfigChooser(int redSize, int greenSize, int blueSize,
                                    int alphaSize, int depthSize, int stencilSize) {
        mRequestedConfigSizes = new int[] {
            redSize, greenSize, blueSize, alphaSize, depthSize, stencilSize
        };
    }
    // libGDX (AndroidGraphics) installs custom EGL factories before
    // setRenderer. Mac.apk never runs the app's EGL thread, so the
    // factories are never invoked -- but the setters must exist or
    // onCreate dies with NoSuchMethodError (ShatteredPD / every libGDX
    // game), and the reference is kept so the request is at least
    // inspectable rather than lost.
    public void setEGLContextFactory(EGLContextFactory factory) {
        mEGLContextFactory = factory;
    }
    public void setEGLWindowSurfaceFactory(EGLWindowSurfaceFactory factory) {
        mEGLWindowSurfaceFactory = factory;
    }

    /** Mac.apk addition (AOSP has no public getter): the value the app
     *  passed to setEGLContextClientVersion, or 0 if it never called it.
     *  Read by the EGL-context creation path so a GLES3 request produces
     *  a GLES3 context instead of the hardcoded ES2 one. */
    public int macapkRequestedEGLContextClientVersion() {
        return mEGLContextClientVersion;
    }
    /** Mac.apk addition: the {r, g, b, a, depth, stencil} sizes requested
     *  through a numeric setEGLConfigChooser overload, or null if the app
     *  requested nothing (or installed its own chooser object). A copy, so
     *  a caller cannot mutate our record of the request. */
    public int[] macapkRequestedEGLConfigSizes() {
        return mRequestedConfigSizes == null ? null
                                             : mRequestedConfigSizes.clone();
    }
    /** Mac.apk addition: true when the app installed any of its own EGL
     *  chooser/context/window-surface factories. Diagnostic only -- the
     *  factory objects are never invoked (running them would mean standing
     *  up a second EGL driver alongside ANGLE). */
    public boolean macapkHasCustomEGLFactories() {
        return mEGLConfigChooser != null
            || mEGLContextFactory != null
            || mEGLWindowSurfaceFactory != null;
    }
    public void setDebugFlags(int flags) {}
    public int  getDebugFlags() { return 0; }
    public void setPreserveEGLContextOnPause(boolean preserve) {}
    public boolean getPreserveEGLContextOnPause() { return false; }

    // ---- Public callback / chooser interfaces ----
    public interface Renderer {
        void onSurfaceCreated(GL10 gl, EGLConfig config);
        void onSurfaceChanged(GL10 gl, int width, int height);
        void onDrawFrame(GL10 gl);
    }
    public interface EGLConfigChooser {
        EGLConfig chooseConfig(javax.microedition.khronos.egl.EGL10 egl,
                               javax.microedition.khronos.egl.EGLDisplay display);
    }
    public interface EGLContextFactory {
        javax.microedition.khronos.egl.EGLContext createContext(
            javax.microedition.khronos.egl.EGL10 egl,
            javax.microedition.khronos.egl.EGLDisplay display,
            javax.microedition.khronos.egl.EGLConfig eglConfig);
        void destroyContext(javax.microedition.khronos.egl.EGL10 egl,
                            javax.microedition.khronos.egl.EGLDisplay display,
                            javax.microedition.khronos.egl.EGLContext context);
    }
    public interface EGLWindowSurfaceFactory {
        javax.microedition.khronos.egl.EGLSurface createWindowSurface(
            javax.microedition.khronos.egl.EGL10 egl,
            javax.microedition.khronos.egl.EGLDisplay display,
            javax.microedition.khronos.egl.EGLConfig config,
            Object nativeWindow);
        void destroySurface(javax.microedition.khronos.egl.EGL10 egl,
                            javax.microedition.khronos.egl.EGLDisplay display,
                            javax.microedition.khronos.egl.EGLSurface surface);
    }
    public interface GLWrapper {
        javax.microedition.khronos.opengles.GL wrap(javax.microedition.khronos.opengles.GL gl);
    }

    // ---- AOSP GLWrapper + SurfaceHolder.Callback2 (added 2026-08-24) --------
    // opengl/java/android/opengl/GLSurfaceView.java (android16-release).

    private GLWrapper mGLWrapper;

    /** AOSP GLSurfaceView.setGLWrapper(GLWrapper) -- "Set the glWrapper. If the
     *  glWrapper is not null, its wrap() method is called whenever a surface is
     *  created. A GLWrapper can be used to wrap the GL object that's passed to
     *  the renderer", the documented hook for GL tracing/error-checking layers
     *  (GLDebugHelper.wrap is the canonical caller). Must be called before
     *  setRenderer on device; we apply it lazily instead, which accepts that
     *  ordering and the reverse. */
    public void setGLWrapper(GLWrapper glWrapper) { mGLWrapper = glWrapper; }

    /** The GL10 the renderer sees: AOSP's EglHelper.createGL() runs the wrapper
     *  over the raw GL and hands the RESULT to the renderer, so a wrapper that
     *  returns something which is not a GL10 raises ClassCastException there
     *  too -- that is upstream's contract, not a bug here. */
    private GL10 wrappedGL() {
        if (mGLWrapper == null) return STUB_GL10;
        javax.microedition.khronos.opengles.GL g = mGLWrapper.wrap(STUB_GL10);
        return (GL10) g;
    }

    // AOSP registers GLSurfaceView as its own holder's Callback2 in init() and
    // forwards each callback into the GL thread. We have no GL thread: the
    // MTKView delegate (macapkSurfaceChanged / macapkDrawFrame above) drives the
    // renderer directly. These five exist so the class SATISFIES
    // SurfaceHolder.Callback2 -- without the interface, `holder.addCallback(view)`
    // and every `instanceof SurfaceHolder.Callback` on a GLSurfaceView go the
    // wrong way, which is a type-level failure no method-level diff can see --
    // and so an app that drives a GLSurfaceView from its OWN holder callbacks
    // reaches the same code path the native one does.

    /** AOSP: mGLThread.surfaceCreated(). Our surface already exists by the time
     *  a renderer can be installed, so there is no thread to unblock. */
    @Override public void surfaceCreated(android.view.SurfaceHolder holder) { }

    /** AOSP: mGLThread.surfaceDestroyed(). */
    @Override public void surfaceDestroyed(android.view.SurfaceHolder holder) { }

    /** AOSP: mGLThread.onWindowResize(w, h) -- note it IGNORES `format`. */
    @Override public void surfaceChanged(android.view.SurfaceHolder holder,
            int format, int w, int h) {
        // 2026-09-26 (MACAPK_SV_DISPLAY_SIZE, which either MACAPK_SV_VIEW_SIZE
        // gate forces on): w x h is the holder's BUFFER, and lastWidthPx /
        // lastHeightPx are the DISPLAY drawable (the renderer is sized from
        // them through macapkRendererSurfaceSize, the WindowManager overlay by
        // presentOverlayIfNeeded). The two differ once the holder reports a
        // view that does not fill its window (SurfaceView MACAPK_SV_VIEW_SIZE):
        // storing the buffer here shrank the overlays -- and through them the
        // widget tree -- to the GL view, a loop, for any app that registers
        // this view as its own holder's Callback, as AOSP GLSurfaceView.init
        // does. Re-run the delivery for the display this view already has; the
        // renderer's size follows from it. Before any display is known, and
        // with the gates off, the old path.
        if (macapkHolderSizeIsBufferSize() && lastWidthPx > 0 && lastHeightPx > 0) {
            macapkSurfaceChangedBody(lastWidthPx, lastHeightPx);
            return;
        }
        macapkSurfaceChangedBody(w, h);
    }

    /** AOSP GLSurfaceView.surfaceRedrawNeeded is @Deprecated and its body is
     *  EMPTY -- the async form below is the live one. Faithful. */
    @Deprecated
    @Override public void surfaceRedrawNeeded(android.view.SurfaceHolder holder) { }

    /** AOSP: mGLThread.requestRenderAndNotify(finishDrawing) -- render once, then
     *  run the callback. The request is real (requestRender(): one frame for a
     *  RENDERMODE_WHEN_DIRTY view under MACAPK_GLSV_RENDER_MODE; under
     *  CONTINUOUSLY the next display-link tick draws anyway), but we have no
     *  frame-completion signal to defer the callback to, so it fires
     *  immediately; leaving it unrun would HANG the caller, which waits on it. */
    // No @Override: SurfaceHolder.Callback2.surfaceRedrawNeededAsync is a DEFAULT
    // method, and the framework jar this file is compiled against may predate it.
    public void surfaceRedrawNeededAsync(android.view.SurfaceHolder holder,
            Runnable finishDrawing) {
        requestRender();
        if (finishDrawing != null) finishDrawing.run();
    }
}
