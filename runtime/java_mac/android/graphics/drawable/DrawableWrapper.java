// android.graphics.drawable.DrawableWrapper -- AOSP API-23 base class
// that delegates every Drawable operation to a wrapped Drawable. It is
// the superclass of InsetDrawable, ScaleDrawable, RotateDrawable,
// ClipDrawable and the support-lib DrawableWrapperCompat. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/DrawableWrapper.java
//
// We implement the delegation faithfully so subclasses only need to
// override the small piece they change (e.g. ScaleDrawable's draw, or
// ClipDrawable's level-based clip). Implements Drawable.Callback so the
// wrapped drawable's invalidate/schedule bubble up to our own callback.
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;

public abstract class DrawableWrapper extends Drawable implements Drawable.Callback {
    private Drawable mDrawable;

    public DrawableWrapper(Drawable dr) {
        setWrappedDrawable(dr);
    }

    public Drawable getDrawable() { return mDrawable; }

    public void setDrawable(Drawable dr) { setWrappedDrawable(dr); }

    /** AOSP-named setter used by the support library wrapper. AOSP
     *  (DrawableWrapper.java:98-114) pushes the wrapper's own visibility, state,
     *  level, bounds and layout direction onto the new child -- "only setters for
     *  data that's stored in the base Drawable". That push is what gives a child
     *  attached AFTER setBounds (the <clip>/<scale>/<inset> inflation order) a
     *  geometry at all, which matters now that setBounds no longer forwards. */
    public void setWrappedDrawable(Drawable dr) {
        if (mDrawable != null) mDrawable.setCallback(null);
        mDrawable = dr;
        if (dr != null) {
            dr.setCallback(this);
            dr.setVisible(isVisible(), true);
            dr.setState(getState());
            dr.setLevel(getLevel());
            dr.setBounds(getBounds());
            dr.setLayoutDirection(getLayoutDirection());
        }
        invalidateSelf();
    }
    public Drawable getWrappedDrawable() { return mDrawable; }

    @Override public void draw(Canvas canvas) {
        if (mDrawable != null) mDrawable.draw(canvas);
    }

    @Override public void onBoundsChange(Rect bounds) {
        if (mDrawable != null) mDrawable.setBounds(bounds);
    }

    // NOTE: setBounds is deliberately NOT overridden, matching AOSP. It used to
    // be, forwarding the wrapper's bounds straight to the child -- which ran
    // AFTER super.setBounds and therefore CLOBBERED whatever the subclass's own
    // onBoundsChange had just computed. That is why an InsetDrawable's child sat
    // at the full 0,0,100,60 instead of the inset 4,4,96,56, and why a
    // ScaleDrawable at level 0 reported a full-size child instead of an empty
    // one (DRW.id.*.childBounds, DRW.lvl.scale.childBounds.0). The base class
    // now dispatches onBoundsChange, so the override is not only wrong, it is
    // unnecessary.

    @Override public void setAlpha(int alpha) {
        if (mDrawable != null) mDrawable.setAlpha(alpha);
    }
    @Override public int getAlpha() {
        return mDrawable != null ? mDrawable.getAlpha() : super.getAlpha();
    }
    @Override public void setColorFilter(ColorFilter cf) {
        if (mDrawable != null) mDrawable.setColorFilter(cf);
    }
    @Override public ColorFilter getColorFilter() {
        return mDrawable != null ? mDrawable.getColorFilter() : null;
    }
    @Override public boolean isStateful() {
        return mDrawable != null && mDrawable.isStateful();
    }
    @Override public boolean setState(int[] stateSet) {
        return mDrawable != null && mDrawable.setState(stateSet);
    }
    @Override public int[] getState() {
        return mDrawable != null ? mDrawable.getState() : super.getState();
    }
    @Override public boolean onStateChange(int[] state) {
        return mDrawable != null && mDrawable.setState(state);
    }
    @Override public boolean onLevelChange(int level) {
        return mDrawable != null && mDrawable.setLevel(level);
    }
    @Override public int getOpacity() {
        return mDrawable != null ? mDrawable.getOpacity() : -2; // TRANSPARENT
    }
    @Override public int getIntrinsicWidth() {
        return mDrawable != null ? mDrawable.getIntrinsicWidth() : -1;
    }
    @Override public int getIntrinsicHeight() {
        return mDrawable != null ? mDrawable.getIntrinsicHeight() : -1;
    }
    @Override public boolean getPadding(Rect padding) {
        return mDrawable != null && mDrawable.getPadding(padding);
    }
    /** AOSP DrawableWrapper.setVisible: `superChanged | changed`, a BITWISE or
     *  so both sides always evaluate and the child is always told. */
    @Override public boolean setVisible(boolean visible, boolean restart) {
        final boolean superChanged = super.setVisible(visible, restart);
        final boolean changed = mDrawable != null && mDrawable.setVisible(visible, restart);
        return superChanged | changed;
    }
    /** AOSP DrawableWrapper.getOutline: the wrapped drawable's outline, which is
     *  expressed in the CHILD's bounds -- so an InsetDrawable reports the inset
     *  rect (DRW.geom.insetInt.outline = 4,4,96,56), not its own. Falling
     *  through to Drawable.getOutline reported the wrapper's full bounds at
     *  alpha 0. */
    @Override public void getOutline(android.graphics.Outline outline) {
        if (mDrawable != null) mDrawable.getOutline(outline);
        else super.getOutline(outline);
    }
    @Override public void setTintList(android.content.res.ColorStateList tint) {
        if (mDrawable != null) mDrawable.setTintList(tint);
    }
    @Override public void setTintMode(android.graphics.PorterDuff.Mode mode) {
        if (mDrawable != null) mDrawable.setTintMode(mode);
    }
    @Override public void setAutoMirrored(boolean mirrored) {
        if (mDrawable != null) mDrawable.setAutoMirrored(mirrored);
    }
    @Override public boolean isAutoMirrored() {
        return mDrawable != null && mDrawable.isAutoMirrored();
    }
    @Override public void jumpToCurrentState() {
        if (mDrawable != null) mDrawable.jumpToCurrentState();
    }

    // Drawable.Callback: bubble the wrapped drawable's redraw/schedule up.
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

    public boolean hasFocusStateSpecified() { return false; }
}

