// android.view.RenderNode -- the legacy pre-API-29 @hide display-list class
// (frameworks_base android-sdk-sources-for-api-level-28 android/view/RenderNode.java).
// Distinct from the modern public android.graphics.RenderNode (API 29+),
// which this tree already implements faithfully.
//
// Why this exists: androidx Compose ships a compat layer that calls this
// hidden class DIRECTLY for devices in the API 23-28 range (RenderNodeApi23 /
// GraphicsLayerV23; verified against androidx-main source). Our
// Build.SDK_INT is 29 (Q), so both of Compose's layer-selection paths
// (RenderNodeLayer.android.kt and AndroidGraphicsContext.android.kt) always
// pick the modern android.graphics.RenderNode instead -- this class is very
// likely dead code under our current configuration. It was previously a
// v1.0.92 auto-generated no-arg-constructor-only stub, so ANY method call
// (including a plain property setter) NoSuchMethodError'd; that is fixed
// below for the whole non-recording property surface, cheap insurance
// against SDK_INT ever changing or some other caller reaching this class.
//
// start(int,int)/end(DisplayListCanvas) -- the actual display-list recording
// entry points -- are intentionally NOT implemented here. AOSP's start()
// returns DisplayListCanvas.obtain(this, w, h) and end() calls
// canvas.finishRecording(); a faithful implementation needs
// android.view.DisplayListCanvas to extend Canvas and wrap a real recording
// surface the way android.graphics.RenderNode's own start()/end() already
// wrap RecordingCanvas. That is a change to DisplayListCanvas.java, a
// separate file. Leaving start()/end() unimplemented is not a regression
// (they NoSuchMethodError'd before this change too); everything else below
// is fully functional.
//
// Every property setter/getter below delegates to an internally-held
// android.graphics.RenderNode, which already implements the identical
// property bag (alpha/translation/scale/rotation/pivot/clip/outline/
// elevation/shadow-colors) correctly -- see that class for the render-side
// semantics. This class only translates the older API-23..28 method names
// (setRotation instead of setRotationZ, individual setLeft/Top/Right/Bottom
// instead of setPosition, etc) onto that delegate.
package android.view;

import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;

public class RenderNode {
    private final android.graphics.RenderNode mDelegate;
    private final View mOwningView;

    private RenderNode(String name, View owningView) {
        mDelegate = new android.graphics.RenderNode(name);
        mOwningView = owningView;
    }

    /** Kept for source-compat with any caller using the no-arg form. */
    public RenderNode() {
        mDelegate = new android.graphics.RenderNode();
        mOwningView = null;
    }

    public static RenderNode create(String name, View owningView) {
        return new RenderNode(name, owningView);
    }

    /** AOSP: literally a no-op at this API level ("TODO: Removed temporarily"). */
    public void destroy() {}

    /** Position-change callbacks are not wired to a real render thread here;
     *  a no-op is the honest "unsupported, doesn't crash" answer, same
     *  pattern as the addAction()-style no-ops elsewhere in this tree. */
    public void requestPositionUpdates(SurfaceView view) {}

    // ---- Recording state -------------------------------------------------
    public void discardDisplayList() { mDelegate.discardDisplayList(); }
    public boolean isValid() { return mDelegate.isValid(); }

    // ---- Matrix ------------------------------------------------------
    public boolean hasIdentityMatrix() { return mDelegate.hasIdentityMatrix(); }
    public void getMatrix(Matrix outMatrix) { mDelegate.getMatrix(outMatrix); }
    public void getInverseMatrix(Matrix outMatrix) { mDelegate.getInverseMatrix(outMatrix); }

    // ---- Layer / paint (no android.graphics.RenderNode equivalent for the
    // legacy int layerType concept, so this is simple local round-trip state,
    // matching the "setter returns whether changed" contract every other
    // property here follows). ---------------------------------------------
    private int mLayerType;
    private Paint mLayerPaint;
    public boolean setLayerType(int layerType) {
        boolean c = layerType != mLayerType; mLayerType = layerType; return c;
    }
    public boolean setLayerPaint(Paint paint) {
        boolean c = paint != mLayerPaint; mLayerPaint = paint; return c;
    }

    // ---- Clip / outline ----------------------------------------------
    public boolean setClipBounds(Rect rect) { return mDelegate.setClipRect(rect); }
    public boolean setClipToBounds(boolean clipToBounds) { return mDelegate.setClipToBounds(clipToBounds); }
    public boolean setClipToOutline(boolean clipToOutline) { return mDelegate.setClipToOutline(clipToOutline); }
    public boolean getClipToOutline() { return mDelegate.getClipToOutline(); }
    public boolean setOutline(Outline outline) { return mDelegate.setOutline(outline); }

    public boolean setProjectBackwards(boolean shouldProject) { return mDelegate.setProjectBackwards(shouldProject); }
    public boolean setProjectionReceiver(boolean shouldReceive) { return mDelegate.setProjectionReceiver(shouldReceive); }

    // Circular reveal clip has no android.graphics.RenderNode equivalent;
    // local round-trip state (not composed into the render transform, same
    // documented limitation android.graphics.RenderNode already carries for
    // 3D rotationX/Y on a 2D software canvas).
    private boolean mRevealClip;
    private float mRevealX, mRevealY, mRevealRadius;
    public boolean setRevealClip(boolean shouldClip, float x, float y, float radius) {
        boolean c = shouldClip != mRevealClip || x != mRevealX || y != mRevealY || radius != mRevealRadius;
        mRevealClip = shouldClip; mRevealX = x; mRevealY = y; mRevealRadius = radius;
        return c;
    }

    // Static / animation matrix: no android.graphics.RenderNode equivalent;
    // stored for round-trip, not composed into the final transform (same
    // "stored but not applied" pattern the delegate documents elsewhere).
    private Matrix mStaticMatrix;
    public boolean setStaticMatrix(Matrix matrix) {
        boolean c = matrix != mStaticMatrix; mStaticMatrix = matrix; return c;
    }
    private Matrix mAnimationMatrix;
    public boolean setAnimationMatrix(Matrix matrix) {
        boolean c = matrix != mAnimationMatrix; mAnimationMatrix = matrix; return c;
    }

    public boolean hasShadow() { return mDelegate.hasShadow(); }
    public boolean setSpotShadowColor(int color) { return mDelegate.setSpotShadowColor(color); }
    public boolean setAmbientShadowColor(int color) { return mDelegate.setAmbientShadowColor(color); }
    public int getSpotShadowColor() { return mDelegate.getSpotShadowColor(); }
    public int getAmbientShadowColor() { return mDelegate.getAmbientShadowColor(); }

    // ---- Alpha / overlap / elevation ----------------------------------
    public boolean setAlpha(float alpha) { return mDelegate.setAlpha(alpha); }
    public float getAlpha() { return mDelegate.getAlpha(); }
    public boolean setHasOverlappingRendering(boolean hasOverlappingRendering) {
        return mDelegate.setHasOverlappingRendering(hasOverlappingRendering);
    }
    public boolean hasOverlappingRendering() { return mDelegate.hasOverlappingRendering(); }
    public boolean setElevation(float lift) { return mDelegate.setElevation(lift); }
    public float getElevation() { return mDelegate.getElevation(); }

    // ---- Translation / rotation / scale / pivot / camera ---------------
    public boolean setTranslationX(float v) { return mDelegate.setTranslationX(v); }
    public float getTranslationX() { return mDelegate.getTranslationX(); }
    public boolean setTranslationY(float v) { return mDelegate.setTranslationY(v); }
    public float getTranslationY() { return mDelegate.getTranslationY(); }
    public boolean setTranslationZ(float v) { return mDelegate.setTranslationZ(v); }
    public float getTranslationZ() { return mDelegate.getTranslationZ(); }
    /** Legacy name for the Z-axis rotation (android.graphics.RenderNode
     *  calls this setRotationZ, and aliases setRotation to it). */
    public boolean setRotation(float v) { return mDelegate.setRotation(v); }
    public float getRotation() { return mDelegate.getRotation(); }
    public boolean setRotationX(float v) { return mDelegate.setRotationX(v); }
    public float getRotationX() { return mDelegate.getRotationX(); }
    public boolean setRotationY(float v) { return mDelegate.setRotationY(v); }
    public float getRotationY() { return mDelegate.getRotationY(); }
    public boolean setScaleX(float v) { return mDelegate.setScaleX(v); }
    public float getScaleX() { return mDelegate.getScaleX(); }
    public boolean setScaleY(float v) { return mDelegate.setScaleY(v); }
    public float getScaleY() { return mDelegate.getScaleY(); }
    public boolean setPivotX(float v) { return mDelegate.setPivotX(v); }
    public float getPivotX() { return mDelegate.getPivotX(); }
    public boolean setPivotY(float v) { return mDelegate.setPivotY(v); }
    public float getPivotY() { return mDelegate.getPivotY(); }
    public boolean isPivotExplicitlySet() { return mDelegate.isPivotExplicitlySet(); }
    public boolean resetPivot() { return mDelegate.resetPivot(); }
    public boolean setCameraDistance(float distance) { return mDelegate.setCameraDistance(distance); }
    public float getCameraDistance() { return mDelegate.getCameraDistance(); }

    // ---- Position --------------------------------------------------------
    // android.graphics.RenderNode exposes setPosition(l,t,r,b) rather than
    // individual per-edge setters; compose the legacy per-edge setters from
    // it plus the existing getters so the "changed" contract stays correct
    // (setPosition's return already reflects whether ANY edge moved, which
    // is exactly right when only one edge is passed through).
    public boolean setLeft(int left) {
        return mDelegate.setPosition(left, mDelegate.getTop(), mDelegate.getRight(), mDelegate.getBottom());
    }
    public boolean setTop(int top) {
        return mDelegate.setPosition(mDelegate.getLeft(), top, mDelegate.getRight(), mDelegate.getBottom());
    }
    public boolean setRight(int right) {
        return mDelegate.setPosition(mDelegate.getLeft(), mDelegate.getTop(), right, mDelegate.getBottom());
    }
    public boolean setBottom(int bottom) {
        return mDelegate.setPosition(mDelegate.getLeft(), mDelegate.getTop(), mDelegate.getRight(), bottom);
    }
    public boolean setLeftTopRightBottom(int left, int top, int right, int bottom) {
        return mDelegate.setPosition(left, top, right, bottom);
    }
    public boolean offsetLeftAndRight(int offset) { return mDelegate.offsetLeftAndRight(offset); }
    public boolean offsetTopAndBottom(int offset) { return mDelegate.offsetTopAndBottom(offset); }

    // ---- Debug tooling (systrace/dumpsys) -- no observable app-visible
    // effect either way; a no-op / delegate-derived estimate is faithful. --
    public void output() {}
    public int getDebugSize() { return (int) mDelegate.computeApproximateMemoryUsage(); }

    // NOTE: addAnimator(RenderNodeAnimator)/isAttached()/
    // registerVectorDrawableAnimator(...)/endAllAnimators() are intentionally
    // omitted. They need a RenderNodeAnimator type (does not exist anywhere
    // in this tree) plus View's mAttachInfo.mViewRootImpl render-thread
    // animator registration, which this tree's View.java does not model
    // (it uses a different "macapkAttached" stand-in, not a real AttachInfo/
    // ViewRootImpl pair). Wiring these would be a View.java-level change,
    // out of scope for this class.

    /** Legacy display-list recording (pre-RecordingCanvas). Mac.apk draws
     *  immediately, so a recording canvas is a plain no-op Canvas. */
    public android.view.DisplayListCanvas start(int width, int height) { return new android.view.DisplayListCanvas(); }
    public void end(android.view.DisplayListCanvas canvas) { }
    public void destroyDisplayListData() { }
}

