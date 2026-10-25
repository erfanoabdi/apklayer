// android.view.SurfaceHolder -- handle to a SurfaceView's drawing
// surface. AndEngine wires the renderer through this to receive
// surfaceCreated / surfaceChanged / surfaceDestroyed callbacks.
package android.view;

public interface SurfaceHolder {
    int SURFACE_TYPE_NORMAL    = 0;
    int SURFACE_TYPE_HARDWARE  = 1;
    int SURFACE_TYPE_GPU       = 2;
    int SURFACE_TYPE_PUSH_BUFFERS = 3;

    interface Callback {
        void surfaceCreated(SurfaceHolder holder);
        void surfaceChanged(SurfaceHolder holder, int format, int width, int height);
        void surfaceDestroyed(SurfaceHolder holder);
    }
    interface Callback2 extends Callback {
        void surfaceRedrawNeeded(SurfaceHolder holder);
        /** AOSP SurfaceHolder.Callback2.surfaceRedrawNeededAsync(SurfaceHolder,
         *  Runnable) (API 26): the non-blocking form. AOSP's own default body is
         *      surfaceRedrawNeeded(holder);
         *      drawingFinished.run();
         *  i.e. do the synchronous redraw and report completion immediately. */
        default void surfaceRedrawNeededAsync(SurfaceHolder holder, Runnable drawingFinished) {
            surfaceRedrawNeeded(holder);
            drawingFinished.run();
        }
    }

    void addCallback(Callback callback);
    void removeCallback(Callback callback);
    boolean isCreating();
    android.view.Surface getSurface();
    android.graphics.Rect getSurfaceFrame();
    void setFormat(int format);
    void setKeepScreenOn(boolean screenOn);
    void setFixedSize(int width, int height);
    void setSizeFromLayout();
    void setType(int type);
    // Canvas drawing path. v1.0.50: declared on the interface so
    // SurfaceHolderImpl can override with a real Canvas backed by a
    // per-holder Bitmap. Apps that draw to Canvas (Frozen Bubble,
    // Sgt Puzzles, ShatteredPD) now have somewhere to paint; getting
    // those pixels onto the Metal layer is a future task.
    default android.graphics.Canvas lockCanvas() { return null; }
    default android.graphics.Canvas lockCanvas(android.graphics.Rect dirty) { return lockCanvas(); }
    default android.graphics.Canvas lockHardwareCanvas() { return lockCanvas(); }
    default void unlockCanvasAndPost(android.graphics.Canvas canvas) {}

    /** AOSP SurfaceHolder.BadSurfaceTypeException: thrown by lockCanvas() when
     *  the surface is SURFACE_TYPE_PUSH_BUFFERS, which has no lockable software
     *  buffer. Mac.apk's SurfaceHolder is always software-lockable (see
     *  Surface.lockCanvas), so nothing throws it -- but an app that catches it
     *  by name needs the type. */
    class BadSurfaceTypeException extends RuntimeException {
        public BadSurfaceTypeException() { super(); }
        public BadSurfaceTypeException(String name) { super(name); }
    }
}
