// android.widget.EdgeEffect -- AOSP overscroll "glow" effect. A scrolling
// container (RecyclerView, ScrollView, NestedScrollView, ViewPager2) creates
// one per edge via EdgeEffectFactory and feeds it the overscroll pull; it
// draws the stretch/glow at the boundary and animates back on release.
// AOSP: frameworks/base/core/java/android/widget/EdgeEffect.java
//
// The previous bare stub had only a no-arg ctor. RecyclerView's default
// EdgeEffectFactory.createEdgeEffect does `new EdgeEffect(context)`, so the
// moment a drag-scroll reached a list boundary the onTouchEvent threw
// NoSuchMethodError -- caught by TouchDispatcher, but it interrupted the
// scroll gesture (relevant to the scroll-smoothness work). We implement the
// AOSP API surface; we do NOT paint a glow on the canvas (a cosmetic
// follow-up), so isFinished() is always true and draw() returns false -- the
// container never spins waiting for a glow animation, and overscroll simply
// has no visual effect. onPull/onPullDistance report the pull as fully
// consumed so the container's nested-scroll bookkeeping stays consistent.
package android.widget;

import android.content.Context;
import android.graphics.Canvas;

public class EdgeEffect {
    private int mWidth, mHeight;
    private int mColor = 0xff666666;

    public EdgeEffect() {}
    public EdgeEffect(Context context) {}
    public EdgeEffect(Context context, android.util.AttributeSet attrs) {}

    public void setSize(int width, int height) { mWidth = width; mHeight = height; }
    public int getMaxHeight() { return mHeight; }

    /** We render no glow, so the effect is always idle. Returning true keeps the
     *  scrolling container from scheduling extra invalidate frames for a glow
     *  animation that we don't paint. */
    public boolean isFinished() { return true; }
    public void finish() {}

    // Pull APIs: report the overscroll as consumed (the deprecated void forms +
    // the API-31 float form RecyclerView uses through EdgeEffectCompat).
    public void onPull(float deltaDistance) {}
    public void onPull(float deltaDistance, float displacement) {}
    public float onPullDistance(float deltaDistance, float displacement) { return deltaDistance; }
    public float getDistance() { return 0f; }

    public void onRelease() {}
    public void onAbsorb(int velocity) {}

    public void setColor(int color) { mColor = color; }
    public int getColor() { return mColor; }

    /** AOSP returns true if the effect still needs to animate (another frame).
     *  We never animate a glow, so always false (nothing drawn, idle). */
    public boolean draw(Canvas canvas) { return false; }

    // ---- API-36 surface completion ------------------------------------
    /** AOSP EdgeEffect.DEFAULT_BLEND_MODE (android16-release :77). AOSP seeds
     *  its glow Paint with it in the constructor, so a freshly-built effect
     *  reports SRC_ATOP from getBlendMode(). */
    public static final android.graphics.BlendMode DEFAULT_BLEND_MODE =
            android.graphics.BlendMode.SRC_ATOP;

    private android.graphics.BlendMode mBlendMode = DEFAULT_BLEND_MODE;

    /** AOSP :550 / :572 -- both go through the glow Paint. We paint no glow
     *  (see the header), so the blend mode is held directly; the get/set round
     *  trip and the SRC_ATOP default are identical either way. */
    public void setBlendMode(android.graphics.BlendMode blendmode) { mBlendMode = blendmode; }
    public android.graphics.BlendMode getBlendMode() { return mBlendMode; }
}
