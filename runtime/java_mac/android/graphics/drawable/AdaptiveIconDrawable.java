// android.graphics.drawable.AdaptiveIconDrawable -- AOSP API-26 launcher
// icon that composites a background + foreground layer, both 108x108dp
// with the outer 18dp on each side reserved as a safe-zone bleed, then
// clips the result to a platform mask. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/AdaptiveIconDrawable.java
// PackageManager.getApplicationIcon and launcher widgets construct these.
// We implement the two-layer composite + a rounded-square mask clip
// (AOSP's default mask is a superellipse; a rounded rect is a faithful
// visual approximation in software). The 25% bleed/scale matches AOSP's
// getExtraInsetFraction so foreground art is centered.
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

public class AdaptiveIconDrawable extends Drawable implements Drawable.Callback {
    // AOSP: the layers extend 1/4 beyond the viewport on each axis.
    private static final float EXTRA_INSET_FRACTION = 0.25f;
    private static final float SAFEZONE_SCALE = 66f / 72f;

    private Drawable mBackground;
    private Drawable mForeground;
    private final Path mMask = new Path();
    private final RectF mTmpRect = new RectF();
    private int mLayersLeft, mLayersTop, mLayersRight, mLayersBottom;

    public AdaptiveIconDrawable(Drawable backgroundDrawable, Drawable foregroundDrawable) {
        this(backgroundDrawable, foregroundDrawable, null);
    }

    /** AOSP AdaptiveIconDrawable(background, foreground, monochrome) (API 33).
     *  The MONOCHROME layer is the themed-icon rendition: the launcher tints it
     *  and draws it INSTEAD of foreground+background when themed icons are on.
     *  It is never composited with the other two, so draw() ignores it -- which
     *  is what a launcher-less host does on device as well. */
    public AdaptiveIconDrawable(Drawable backgroundDrawable, Drawable foregroundDrawable,
            Drawable monochromeDrawable) {
        mBackground = backgroundDrawable;
        mForeground = foregroundDrawable;
        mMonochrome = monochromeDrawable;
        if (mBackground != null) mBackground.setCallback(this);
        if (mForeground != null) mForeground.setCallback(this);
        if (mMonochrome != null) mMonochrome.setCallback(this);
    }
    private Drawable mMonochrome;

    /** AOSP AdaptiveIconDrawable.getMonochrome() -- @Nullable; null when the
     *  icon ships no <monochrome> layer, which is the common case. */
    public Drawable getMonochrome() { return mMonochrome; }

    public Drawable getBackground() { return mBackground; }
    public Drawable getForeground() { return mForeground; }
    public void setBackground(Drawable d) {
        if (mBackground != null) mBackground.setCallback(null);
        mBackground = d;
        if (d != null) d.setCallback(this);
        invalidateSelf();
    }
    public void setForeground(Drawable d) {
        if (mForeground != null) mForeground.setCallback(null);
        mForeground = d;
        if (d != null) d.setCallback(this);
        invalidateSelf();
    }

    /** AOSP android14-release AdaptiveIconDrawable.java:846-883: the intrinsic
     *  size is the LARGEST child's intrinsic size (all three layers --
     *  background, foreground, monochrome -- N_CHILDREN = 3 at :975) scaled by
     *  DEFAULT_VIEW_PORT_SCALE = 1 / (1 + 2 * 1/4) (:106), i.e. the visible
     *  viewport of 108dp layers is 72dp. We inherited Drawable's -1 until
     *  v1.0.1648, which was harmless only while ImageView.onMeasure handed a
     *  size-less drawable the whole spec size; with AOSP's measure (a size-less
     *  drawable floors to 1px) a wrap_content ImageView showing
     *  @mipmap/ic_launcher would otherwise collapse to its padding. */
    private static final float DEFAULT_VIEW_PORT_SCALE = 1f / (1 + 2 * EXTRA_INSET_FRACTION);

    @Override public int getIntrinsicWidth() {
        return (int) (maxChildIntrinsic(true) * DEFAULT_VIEW_PORT_SCALE);
    }

    @Override public int getIntrinsicHeight() {
        return (int) (maxChildIntrinsic(false) * DEFAULT_VIEW_PORT_SCALE);
    }

    private int maxChildIntrinsic(boolean width) {
        int best = -1;
        for (Drawable d : new Drawable[] { mBackground, mForeground, mMonochrome }) {
            if (d == null) continue;
            int v = width ? d.getIntrinsicWidth() : d.getIntrinsicHeight();
            if (v > best) best = v;
        }
        return best;
    }

    /** AOSP getExtraInsetFraction() -- the fraction of bleed beyond the
     *  visible icon viewport reserved on each side. */
    public static float getExtraInsetFraction() { return EXTRA_INSET_FRACTION; }

    public Path getIconMask() { return mMask; }

    @Override public void onBoundsChange(Rect bounds) {
        if (bounds.isEmpty()) return;
        updateLayerBounds(bounds);
    }
    // setBounds is not overridden: the base class now dispatches
    // onBoundsChange, which runs updateLayerBounds one method up.

    private void updateLayerBounds(Rect bounds) {
        if (bounds.isEmpty()) return;
        // Each layer is inset NEGATIVELY (it bleeds out) by the extra-inset
        // fraction of the bounds, so the central viewport shows the safe zone.
        int cx = (int) (bounds.width() * EXTRA_INSET_FRACTION);
        int cy = (int) (bounds.height() * EXTRA_INSET_FRACTION);
        mLayersLeft = bounds.left - cx;
        mLayersTop = bounds.top - cy;
        mLayersRight = bounds.right + cx;
        mLayersBottom = bounds.bottom + cy;
        if (mBackground != null) mBackground.setBounds(mLayersLeft, mLayersTop, mLayersRight, mLayersBottom);
        if (mForeground != null) mForeground.setBounds(mLayersLeft, mLayersTop, mLayersRight, mLayersBottom);

        // Build the clip mask: a rounded square inscribed in the visible
        // bounds (AOSP uses a superellipse; rounded rect is a software-faithful
        // approximation). Corner radius = ~18% of the side, matching the
        // platform mask's roundness.
        mMask.reset();
        mTmpRect.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
        float radius = Math.min(bounds.width(), bounds.height()) * 0.18f;
        mMask.addRoundRect(mTmpRect, radius, radius, Path.Direction.CW);
    }

    @Override public void draw(Canvas canvas) {
        if (getBounds().isEmpty()) return;
        if (mMask.isEmpty()) updateLayerBounds(getBounds());
        final int saved = canvas.save();
        canvas.clipPath(mMask);
        // v1.0.501: translate to the (negative-inset) layer origin and draw each
        // layer with ORIGIN-based bounds. Our drawables are inconsistent about
        // honoring bounds.left/top -- ColorDrawable fills getBounds() but
        // VectorDrawable ignores left/top and draws at the canvas origin (it
        // assumes ImageView pre-translated). Pre-translating here + giving both
        // layers [0,0,w,h] bounds makes the bleed/centering correct for BOTH
        // (Duolingo's vector owl was drawing un-offset -> shoved low-right and
        // cropped; ColorDrawable backgrounds were unaffected).
        int lw = mLayersRight - mLayersLeft, lh = mLayersBottom - mLayersTop;
        drawLayer(canvas, mBackground, lw, lh);
        drawLayer(canvas, mForeground, lw, lh);
        canvas.restoreToCount(saved);
    }

    private void drawLayer(Canvas canvas, Drawable d, int lw, int lh) {
        if (d == null) return;
        int sc = canvas.save();
        canvas.translate(mLayersLeft, mLayersTop);
        d.setBounds(0, 0, lw, lh);
        d.draw(canvas);
        canvas.restoreToCount(sc);
    }

    @Override public void setAlpha(int alpha) {
        if (mBackground != null) mBackground.setAlpha(alpha);
        if (mForeground != null) mForeground.setAlpha(alpha);
    }
    @Override public void setColorFilter(ColorFilter cf) {
        if (mBackground != null) mBackground.setColorFilter(cf);
        if (mForeground != null) mForeground.setColorFilter(cf);
    }
    @Override public int getOpacity() { return mOpacity; }
    /** AOSP AdaptiveIconDrawable.setOpacity(int) (API 31) -- lets the caller
     *  override the reported opacity (a launcher that knows the background is a
     *  solid color sets OPAQUE so the compositor can skip what is behind it).
     *  Default stays PixelFormat.TRANSLUCENT (-3), which is what an adaptive
     *  icon with an arbitrary mask really is. */
    public void setOpacity(int opacity) { mOpacity = opacity; }
    private int mOpacity = android.graphics.PixelFormat.TRANSLUCENT;
    @Override public boolean isStateful() {
        return (mBackground != null && mBackground.isStateful())
            || (mForeground != null && mForeground.isStateful());
    }
    @Override public boolean onStateChange(int[] state) {
        boolean changed = false;
        if (mBackground != null) changed |= mBackground.setState(state);
        if (mForeground != null) changed |= mForeground.setState(state);
        return changed;
    }

    // Drawable.Callback: bubble layer redraws up.
    @Override public void invalidateDrawable(Drawable who) {
        Callback cb = getCallback();
        if (cb != null) cb.invalidateDrawable(this);
    }
    @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {
        Callback cb = getCallback();
        if (cb != null) cb.scheduleDrawable(this, what, when);
    }
    @Override public void unscheduleDrawable(Drawable who, Runnable what) {
        Callback cb = getCallback();
        if (cb != null) cb.unscheduleDrawable(this, what);
    }
}
