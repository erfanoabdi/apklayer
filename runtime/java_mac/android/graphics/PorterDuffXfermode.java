// android.graphics.PorterDuffXfermode -- a Paint transfer mode that
// uses a Porter-Duff alpha-compositing mode. Material widgets use
// these for tint overlays. We don't actually composite (the Canvas
// stubs are no-ops on most paint operations), but the class must
// exist for callers to construct.
package android.graphics;

public class PorterDuffXfermode extends Xfermode {
    private final PorterDuff.Mode mode;
    public PorterDuffXfermode(PorterDuff.Mode mode) { this.mode = mode; }
    public PorterDuff.Mode getMode() { return mode; }
}
