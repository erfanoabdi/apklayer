// android.view.TextureView -- a View that hosts a content stream (video, camera,
// GL) through a SurfaceTexture. Was a 1-method empty stub, so video players
// (ExoPlayer/Media3), camera preview, and any TextureView-based renderer
// NoSuchMethodError'd on getSurfaceTexture()/setSurfaceTextureListener()/
// getSurface() and could not obtain a producer surface at all.
//
// We back it with a SurfaceTexture and drive the SurfaceTextureListener lifecycle
// off the View attach/size machinery: when the view is attached and laid out we
// publish onSurfaceTextureAvailable(width,height); on size change we fire
// onSurfaceTextureSizeChanged; on detach onSurfaceTextureDestroyed. The API
// surface and listener contract match AOSP
// frameworks/base/core/java/android/view/TextureView.java.
//
// CONTENT actually paints now, through the software half of the consumer:
// on real Android a TextureView's SurfaceTexture is consumed by the HWUI
// render thread (updateTexImage + composite); here the Canvas pipeline is the
// render thread, so draw() latches the newest producer frame into a Bitmap
// (SurfaceTexture.macapkLatchBitmap) and blits it across the view bounds. The
// camera capture session was the first real producer; a MediaCodec video
// decoder targeting a Surface built from this view's SurfaceTexture
// (ExoPlayer/Media3's PlayerView in surface_type="texture_view" mode, or any
// app driving MediaCodec directly -- both do `new Surface(surfaceTexture)`,
// AOSP's real and only hand-off point, most naturally from inside
// onSurfaceTextureAvailable) is the second. That registration lives in
// Surface's own SurfaceTexture-arg constructor, NOT here -- see its doc and
// SurfaceTexture.macapkOfferDecodedFrame for how a decoded frame finds its
// way back with NO Surface-identity plumbing anywhere upstream of it. Either
// producer's frame arrival fires the SurfaceTexture's frame-available
// callback, which -- exactly like AOSP's TextureView.mUpdateListener --
// invalidates this view, and the next draw latches. onSurfaceTextureUpdated
// fires per freshly latched frame (AOSP: "the specified SurfaceTexture is
// updated through updateTexImage()"), NOT per draw -- firing it on every draw
// would let an app that invalidates from the callback spin the UI thread
// forever.
//
// TRANSFORMS: unlike SurfaceView (a hole punched through the window, with a
// SEPARATE fullscreen compositing layer that has no notion of this view's
// position, size, rotation or alpha at all), this view's video frame is
// blitted into the SAME Canvas the rest of the widget tree draws into, which
// is rasterized to one shared bitmap and presented as a single flat layer
// (see ViewCanvasDriver). So setRotation()/setScaleX()/setAlpha()/etc. on a
// video-playing TextureView apply through the ordinary View transform path --
// ViewGroup.drawChild() -> child.macapkApplyTransform(canvas) -- exactly as
// they would for any other View subclass; no special-casing was needed or
// added here.
//
// lockCanvas()/unlockCanvasAndPost() draw into the SAME composite path: the
// producer paints a bitmap, unlock publishes it as the view's content. AOSP
// forbids lockCanvas while another producer (camera/decoder) is connected, and
// so do we -- null, not a second producer fighting over the stream.
package android.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;

public class TextureView extends View {
    public interface SurfaceTextureListener {
        void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height);
        void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height);
        boolean onSurfaceTextureDestroyed(SurfaceTexture surface);
        void onSurfaceTextureUpdated(SurfaceTexture surface);
    }

    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private SurfaceTextureListener mListener;
    private boolean mAvailable;
    private boolean mOpaque = true;
    private final Matrix mMatrix = new Matrix();
    private boolean mMatrixChanged;
    private int mSurfaceWidth, mSurfaceHeight;

    /** AOSP TextureView sizes its SurfaceTexture's DEFAULT BUFFER to the view:
     *  getTextureLayer() calls mSurface.setDefaultBufferSize(getWidth(),
     *  getHeight()) before onSurfaceTextureAvailable, onSizeChanged calls it
     *  before onSurfaceTextureSizeChanged, and an adopted texture
     *  (setSurfaceTexture) gets the view size the next time the layer updates
     *  (frameworks/base core/java/android/view/TextureView.java,
     *  android14-release, :440-449 and :452-470). We never did, so a producer
     *  that allocates against the default size -- an EGL window surface over
     *  this texture, BufferQueueCore's 1x1 default when nobody set one
     *  (frameworks/native libs/gui/BufferQueueCore.cpp mDefaultWidth(1),
     *  mDefaultHeight(1)) -- rendered at 1x1 instead of the view size
     *  (docs/telegram-intro-logo_2026-09-26.md §6). The camera path also reads
     *  this size (CameraCaptureSessionImpl), so an app that never sets one now
     *  asks for the view size, which the capture backend rounds to the
     *  nearest-area format, as camera2 rounds a SurfaceTexture size on a
     *  device. A view that collapses to 0 on one axis passes that 0 on, as AOSP
     *  does; SurfaceTexture.setDefaultBufferSize then keeps the previous size,
     *  as BufferQueueConsumer's BAD_VALUE does. BISECT GATE:
     *  MACAPK_TEXTUREVIEW_BUFSIZE=0 restores the pre-change path (no
     *  setDefaultBufferSize from this class at all, and SurfaceTexture stores
     *  every value again). */
    private static final boolean sSetDefaultBufferSize =
        !"0".equals(System.getenv("MACAPK_TEXTUREVIEW_BUFSIZE"));

    /** Last latch generation this view has drawn, vs the SurfaceTexture's own
     *  counter -- how draw() tells "new frame" from "same frame redrawn". */
    private int mDrawnGeneration;
    /** Scratch rects for the frame blit; reused so draw() allocates nothing. */
    private final android.graphics.Rect mSrcRect = new android.graphics.Rect();
    private final android.graphics.Rect mDstRect = new android.graphics.Rect();

    // lockCanvas() producer state. mLockedCanvas is non-null while locked;
    // mCanvasContent is the last published bitmap, composited by draw().
    private Bitmap mCanvasBitmap;
    private android.graphics.Canvas mLockedCanvas;
    private Bitmap mCanvasContent;

    /** AOSP TextureView.mUpdateListener: a frame arrival invalidates the view;
     *  the next draw latches it. Registered on every SurfaceTexture this view
     *  owns; an app that registers its own listener on our SurfaceTexture
     *  replaces it and stops the preview, exactly as it would on Android. */
    private final SurfaceTexture.OnFrameAvailableListener mUpdateListener =
            new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    invalidate();
                }
            };

    public TextureView(Context context) { super(context); }
    public TextureView(Context context, android.util.AttributeSet attrs) { super(context, attrs); }
    public TextureView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    public TextureView(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    // TextureView renders only with hardware acceleration in AOSP. We do not
    // gate on that here.
    public boolean isOpaque() { return mOpaque; }
    public void setOpaque(boolean opaque) { mOpaque = opaque; }

    // NOTE: View declares these hooks public in our framework (AOSP has them
    // protected), so the overrides must also be public to avoid narrowing.
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        ensureSurfaceTexture();
        publishAvailableIfReady();
    }

    @Override
    public void onDetachedFromWindow() {
        if (mSurfaceTexture != null && mListener != null && mAvailable) {
            try { mListener.onSurfaceTextureDestroyed(mSurfaceTexture); }
            catch (Throwable ignored) {}
        }
        if (mSurfaceTexture != null) {
            // Undo the video-target registration Surface's constructor made
            // (see ensureSurfaceTexture() below), so a decoded-video frame
            // arriving after this view is gone does not resurrect a detached
            // SurfaceTexture as the render target (harmless -- the frame
            // just latches into a Bitmap nobody draws -- but pointless work
            // and a misleading log if anyone traces it).
            mSurfaceTexture.macapkUnregisterVideoTargetIfSelf();
        }
        if (mSurface != null) {
            try { mSurface.release(); } catch (Throwable ignored) {}
        }
        mAvailable = false;
        super.onDetachedFromWindow();
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mAvailable && mSurfaceTexture != null) {
            // AOSP :444: the buffer follows the view before the listener hears.
            if (sSetDefaultBufferSize) mSurfaceTexture.setDefaultBufferSize(w, h);
            if (mListener != null && (w != mSurfaceWidth || h != mSurfaceHeight)) {
                mSurfaceWidth = w; mSurfaceHeight = h;
                try { mListener.onSurfaceTextureSizeChanged(mSurfaceTexture, w, h); }
                catch (Throwable ignored) {}
            }
        } else {
            publishAvailableIfReady();
        }
    }

    private void ensureSurfaceTexture() {
        if (mSurfaceTexture == null) {
            mSurfaceTexture = new SurfaceTexture();
            mSurface = new Surface(mSurfaceTexture);
            // AOSP registers TextureView's own frame listener at texture
            // creation (mUpdateListener); a producer frame then invalidates.
            mSurfaceTexture.setOnFrameAvailableListener(mUpdateListener);
        }
    }

    private void publishAvailableIfReady() {
        if (mAvailable) return;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        ensureSurfaceTexture();
        // AOSP :466: sized to the view BEFORE onSurfaceTextureAvailable, so a
        // producer the app connects from inside the callback sees w x h.
        if (sSetDefaultBufferSize) mSurfaceTexture.setDefaultBufferSize(w, h);
        mSurfaceWidth = w; mSurfaceHeight = h;
        mAvailable = true;
        if (mListener != null) {
            try { mListener.onSurfaceTextureAvailable(mSurfaceTexture, w, h); }
            catch (Throwable ignored) {}
        }
    }

    public void setSurfaceTextureListener(SurfaceTextureListener listener) {
        mListener = listener;
        // If the surface is already available, AOSP fires the callback as soon
        // as the listener registers.
        if (mAvailable && mListener != null && mSurfaceTexture != null) {
            try { mListener.onSurfaceTextureAvailable(mSurfaceTexture, mSurfaceWidth, mSurfaceHeight); }
            catch (Throwable ignored) {}
        }
    }
    public SurfaceTextureListener getSurfaceTextureListener() { return mListener; }

    public SurfaceTexture getSurfaceTexture() { return mSurfaceTexture; }

    public void setSurfaceTexture(SurfaceTexture surfaceTexture) {
        mSurfaceTexture = surfaceTexture;
        mSurface = (surfaceTexture != null) ? new Surface(surfaceTexture) : null;
        if (surfaceTexture != null) {
            // The adopted texture becomes this view's stream; its frames must
            // reach our draw loop (AOSP re-registers mUpdateListener too).
            surfaceTexture.setOnFrameAvailableListener(mUpdateListener);
            // AOSP getTextureLayer's mUpdateSurface branch: an adopted texture
            // is set "to use the current view size" (:487).
            if (sSetDefaultBufferSize && getWidth() > 0 && getHeight() > 0) {
                surfaceTexture.setDefaultBufferSize(getWidth(), getHeight());
            }
        }
        mAvailable = surfaceTexture != null;
        if (mAvailable) publishAvailableIfReady();
    }

    public boolean isAvailable() { return mAvailable; }

    // AOSP returns null until the surface is available; consumers (camera/video)
    // grab this Surface to send frames into.
    //
    // NOTE: getSurface() itself is NOT part of real AOSP's TextureView API
    // (verified against frameworks/base core/java/android/view/TextureView.java,
    // android13-release -- it declares no such method; a real app instead
    // does `new Surface(textureView.getSurfaceTexture())`, or uses the
    // SurfaceTexture handed to onSurfaceTextureAvailable directly). It is
    // kept here as a pre-existing Mac.apk-internal convenience (mSurface is
    // already built and cached; nothing above this method depends on the
    // method NOT existing), but the video-decode-target registration lives
    // in Surface's own SurfaceTexture-arg constructor instead -- see that
    // constructor's doc -- specifically BECAUSE that is the real, single
    // hand-off point every construction path (a real app's `new
    // Surface(surfaceTexture)`, and this class's own ensureSurfaceTexture()
    // below) funnels through, whereas this method would miss every real app.
    public Surface getSurface() {
        ensureSurfaceTexture();
        return mSurface;
    }

    // Transform applied to the texture when drawn. We store it faithfully even
    // though our draw path does not yet apply it.
    public void setTransform(Matrix transform) {
        mMatrix.set(transform);
        mMatrixChanged = true;
    }
    public Matrix getTransform(Matrix transform) {
        if (transform == null) transform = new Matrix();
        transform.set(mMatrix);
        return transform;
    }

    // Snapshot the current content. Without a real GL-backed texture we cannot
    // read back pixels, so return the caller's bitmap unchanged (AOSP returns a
    // bitmap of the current content). A null arg yields a blank bitmap sized to
    // the view so callers do not NPE.
    public Bitmap getBitmap() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        return getBitmap(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888));
    }
    public Bitmap getBitmap(int width, int height) {
        return getBitmap(Bitmap.createBitmap(Math.max(1, width), Math.max(1, height),
                Bitmap.Config.ARGB_8888));
    }
    public Bitmap getBitmap(Bitmap bitmap) {
        return bitmap;
    }

    // AOSP: `public Canvas lockCanvas()` / `public Canvas lockCanvas(Rect)`.
    // The return TYPE is linkable ABI -- app bytecode compiled against the SDK
    // does `Canvas c = tv.lockCanvas();` and cannot link to a void descriptor
    // (verified against the android-30 android.jar). Semantics mirrored from
    // AOSP TextureView.lockCanvas: null when the view is not available, when
    // already locked, or when another producer (the camera session) is
    // connected to the SurfaceTexture -- AOSP's nativeLockCanvas fails the
    // same way rather than letting two producers fight over one stream.
    public android.graphics.Canvas lockCanvas() {
        return lockCanvas(null);
    }

    public android.graphics.Canvas lockCanvas(android.graphics.Rect dirty) {
        if (!mAvailable || mLockedCanvas != null) return null;
        if (mSurfaceTexture != null && mSurfaceTexture.macapkHasCameraSource()) return null;
        // Same rule for a connected EGL window surface (the GL producer): the
        // stream has ONE producer, and nativeLockCanvas cannot connect a second.
        if (mSurfaceTexture != null && mSurfaceTexture.macapkHasProducer()) return null;
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        if (mCanvasBitmap == null
                || mCanvasBitmap.getWidth() != w || mCanvasBitmap.getHeight() != h) {
            mCanvasBitmap = Bitmap.createBitmapWithCanvasBacking(w, h);
        }
        mLockedCanvas = new android.graphics.Canvas(mCanvasBitmap);
        return mLockedCanvas;
    }

    public void unlockCanvasAndPost(android.graphics.Canvas canvas) {
        // AOSP ignores an unlock with the wrong (or no) lock rather than
        // throwing: "if canvas is not the Canvas returned by lockCanvas, this
        // method does nothing".
        if (canvas == null || canvas != mLockedCanvas) return;
        mLockedCanvas = null;
        mCanvasContent = mCanvasBitmap;
        invalidate();
    }

    @Override
    public void draw(android.graphics.Canvas canvas) {
        // TextureView in AOSP does not draw via the app's Canvas machinery --
        // the render thread composites the SurfaceTexture's latest image. Our
        // render thread IS the Canvas pipeline, so this draw performs the
        // consumer latch + composite: newest producer frame (camera) first,
        // else the lockCanvas content, else nothing (a stream with no frames
        // paints nothing, honestly).
        Bitmap frame = null;
        boolean fresh = false;
        if (mSurfaceTexture != null) {
            frame = mSurfaceTexture.macapkLatchBitmap();
            int gen = mSurfaceTexture.macapkLatchGeneration();
            fresh = gen != mDrawnGeneration;
            mDrawnGeneration = gen;
        }
        if (frame == null) frame = mCanvasContent;
        if (frame != null && canvas != null) {
            mSrcRect.set(0, 0, frame.getWidth(), frame.getHeight());
            mDstRect.set(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(frame, mSrcRect, mDstRect, null);
        }
        if (fresh) applyUpdate();
        mMatrixChanged = false;
    }

    private void applyUpdate() {
        // Fired per freshly latched frame, matching AOSP ("the specified
        // SurfaceTexture is updated through updateTexImage()") -- NOT per
        // draw, which would spin any app that invalidates from the callback.
        if (mAvailable && mListener != null && mSurfaceTexture != null) {
            try { mListener.onSurfaceTextureUpdated(mSurfaceTexture); }
            catch (Throwable ignored) {}
        }
    }
}
