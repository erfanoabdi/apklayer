// android.graphics.drawable.LayerDrawable — Drawable that paints a
// stack of layers in z-order. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/LayerDrawable.java
// F-Droid's RippleDrawable / AppStatusNew background and Material's
// MaterialCardView ripple-on-top stack both build LayerDrawable
// instances at bind time. Without the (Drawable[]) constructor every
// card bind throws NoSuchMethodError, leaving the entire Latest tab
// unbound.
//
// Per-layer state follows AOSP's ChildDrawable record: each layer carries
// absolute + relative insets, an optional explicit width/height and a
// gravity, all resolved together at bounds-change time. Two gaps this
// closes: (a) the API-23 per-layer size/gravity surface
// (setLayerWidth/Height/Gravity/Size, the per-side inset setters,
// setLayerInsetRelative) was entirely absent, so any layer-list that sizes
// one layer independently threw NoSuchMethodError during inflate/bind;
// (b) isStateful()/setState()/getPadding() were never overridden, so a
// pressed/checked state change stopped dead at this drawable and never
// reached a stateful child (the standard Material ripple-over-
// StateListDrawable background), leaving buttons visually inert on press.
//
// A third gap: this class never implemented Drawable.Callback and never
// called setCallback on a child, so a layer that invalidates itself (a
// nested RippleDrawable's press animation, an AnimationDrawable frame
// timer, a tint update landing via invalidateSelf) had nowhere to send that
// request -- Drawable.invalidateSelf() no-ops when getCallback() is null,
// so the animation could tick forever without the hosting View ever being
// told to redraw. Now every entry point that attaches a child (the
// constructor via addLayer, setDrawable/setDrawableByLayerId, and the
// ConstantState's newDrawable() clone path) wires the callback, and
// invalidateDrawable/scheduleDrawable/unscheduleDrawable bubble the
// request up to whatever hosts this LayerDrawable, matching the pattern
// already used by DrawableContainer and DrawableWrapper in this package.
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.view.Gravity;

import java.util.ArrayList;
import java.util.List;

public class LayerDrawable extends Drawable implements Drawable.Callback {

    /** AOSP LayerDrawable.setTintList forwards the tint to EVERY layer
     *  (`for (i...) layers[i].mDrawable.setTintList(tint)`), because the
     *  container itself paints nothing. Without this a tint set on a
     *  `<ripple>`-rooted background (the standard shape for a Material key or
     *  button, and what `android:backgroundTint` lands on) stopped at the
     *  container and the inner `<shape>` painted its authored colour.
     *  Measured victim: OpenCalculator's keys, all one gray instead of the
     *  emulator's green/blue/pink per-key tints
     *  (docs/canvas-ab-analyst-report_2026-08-02.md item 2a). */
    @Override public void setTintList(android.content.res.ColorStateList tint) {
        super.setTintList(tint);
        try {
            int n = getNumberOfLayers();
            for (int i = 0; i < n; i++) {
                Drawable d = getDrawable(i);
                if (d != null) d.setTintList(tint);
            }
        } catch (Throwable ignored) {}
    }
    @Override public void setTintMode(android.graphics.PorterDuff.Mode mode) {
        super.setTintMode(mode);
        try {
            int n = getNumberOfLayers();
            for (int i = 0; i < n; i++) {
                Drawable d = getDrawable(i);
                if (d != null) d.setTintMode(mode);
            }
        } catch (Throwable ignored) {}
    }
    /** AOSP padding-mode constants (selectable per-instance). */
    public static final int PADDING_MODE_NEST  = 0;
    public static final int PADDING_MODE_STACK = 1;
    /** AOSP "no insets" sentinel returned from getLayerInsetXxx until
     *  the caller calls setLayerInset. */
    public static final int INSET_UNDEFINED = Integer.MIN_VALUE;

    /** AOSP LayerDrawable.ChildDrawable. Defaults are AOSP's verbatim:
     *  mInsetS/mInsetE = INSET_UNDEFINED (relative insets unset, so the
     *  absolute ones win), mWidth/mHeight = -1 ("use the intrinsic size"),
     *  mGravity = NO_GRAVITY (resolveGravity then fills the axis). */
    private static final class ChildDrawable {
        Drawable drawable;
        int insetL, insetT, insetR, insetB;
        int insetS = INSET_UNDEFINED;
        int insetE = INSET_UNDEFINED;
        int width  = -1;
        int height = -1;
        int gravity = Gravity.NO_GRAVITY;
        /** AOSP ChildDrawable.mId defaults to View.NO_ID (-1), not 0. 0 is a
         *  legal resource id, so a 0 default makes findDrawableByLayerId(0)
         *  match every un-identified layer. */
        int id = android.view.View.NO_ID;
        // AOSP caches each child's padding in the mPaddingL/T/R/B arrays and
        // refreshes it via refreshChildPadding; the cache is what both the
        // nested-padding walk and getPadding() read.
        int padL, padT, padR, padB;

        ChildDrawable() { }
        /** AOSP LayerState's copy constructor copies the RECORD by value while
         *  the child drawable itself is re-created from its own ConstantState.
         *  That is what makes an inset set on a copy invisible to the original
         *  (DRW.cs.ld.leakWithoutMutate = 40, not 80). */
        ChildDrawable(ChildDrawable orig) {
            Drawable d = orig.drawable;
            if (d != null) {
                ConstantState cs = d.getConstantState();
                drawable = (cs != null) ? cs.newDrawable() : d;
            }
            insetL = orig.insetL; insetT = orig.insetT;
            insetR = orig.insetR; insetB = orig.insetB;
            insetS = orig.insetS; insetE = orig.insetE;
            width = orig.width; height = orig.height;
            gravity = orig.gravity; id = orig.id;
            padL = orig.padL; padT = orig.padT; padR = orig.padR; padB = orig.padB;
        }
    }

    private final List<ChildDrawable> children = new ArrayList<>();
    private int paddingMode = PADDING_MODE_NEST;
    /** AOSP LayerState.mPaddingLeft/Top/Right/Bottom, -1 meaning "use the value
     *  computed by the padding mode" (LayerDrawable.java:1090-1101). */
    private int mPaddingLeft = -1, mPaddingTop = -1, mPaddingRight = -1, mPaddingBottom = -1;
    /** AOSP LayerState.mOpacityOverride, PixelFormat.UNKNOWN (0) meaning "not
     *  overridden" (:1946). */
    private int mOpacityOverride = android.graphics.PixelFormat.UNKNOWN;

    // AOSP: mStateSet starts as StateSet.WILD_CARD (the empty array), so the
    // first non-empty setState always dispatches.
    private int[] stateSet = new int[0];

    private final Rect tmpRect      = new Rect();
    private final Rect tmpContainer = new Rect();
    private final Rect tmpOutRect   = new Rect();

    public LayerDrawable() {}
    public LayerDrawable(Drawable[] layers) {
        if (layers != null) {
            for (Drawable d : layers) addLayer(d);
        }
    }

    /** Append a new layer. Returns the new layer's index. */
    public int addLayer(Drawable d) {
        ChildDrawable c = new ChildDrawable();
        c.drawable = d;
        // AOSP LayerDrawable's constructor and addLayer both do this
        // (LayerDrawable.java:144-146 / :482): make the container the
        // child's Callback so a self-invalidating layer can actually reach
        // invalidateDrawable below instead of silently going nowhere.
        if (d != null) d.setCallback(this);
        children.add(c);
        return children.size() - 1;
    }
    public int getNumberOfLayers() { return children.size(); }
    /** AOSP :656-661 range-checks only the UPPER bound and then indexes the raw
     *  array, so a too-large index raises IndexOutOfBoundsException while a
     *  negative one raises ArrayIndexOutOfBoundsException. The exception CLASS
     *  is the observable difference (DRW.ld.getDrawableTooLarge vs
     *  .getDrawableNegative), and a uniform bounds check -- or the null we used
     *  to return -- answers neither. */
    public Drawable getDrawable(int idx) {
        if (idx >= children.size()) throw new IndexOutOfBoundsException();
        if (idx < 0) throw new ArrayIndexOutOfBoundsException(idx);
        return children.get(idx).drawable;
    }
    // AOSP returns void (setDrawable always installs; index bounds throw).
    // frameworks/base/graphics/java/android/graphics/drawable/LayerDrawable.java
    // :633-638 detaches the outgoing child's callback before attaching the
    // incoming one's, so a replaced layer stops invalidating a container it
    // no longer belongs to, and the new one starts able to.
    public void setDrawable(int idx, Drawable d) {
        ChildDrawable c = child(idx);
        if (c == null) return;
        if (c.drawable != null) c.drawable.setCallback(null);
        c.drawable = d;
        if (d != null) d.setCallback(this);
    }
    public boolean setDrawableByLayerId(int id, Drawable d) {
        for (ChildDrawable c : children) {
            if (c.id == id) {
                if (c.drawable != null) c.drawable.setCallback(null);
                c.drawable = d;
                if (d != null) d.setCallback(this);
                return true;
            }
        }
        return false;
    }
    /** AOSP :518-529 walks DOWNWARD: "If multiple layers are found for the given
     *  ID, returns the Drawable for the matching layer at the HIGHEST index."
     *  A forward walk returns the lowest and silently picks the wrong layer for
     *  the duplicate-id case (DRW.ld.findByLayerId.duplicate). */
    public Drawable findDrawableByLayerId(int id) {
        for (int i = children.size() - 1; i >= 0; i--) {
            ChildDrawable c = children.get(i);
            if (c.id == id) return c.drawable;
        }
        return null;
    }
    /** AOSP LayerDrawable.findIndexByLayerId(int) (:598, API 29). Note it walks
     *  FORWARD and returns the LOWEST matching index -- the opposite of
     *  findDrawableByLayerId above, which AOSP documents as returning the
     *  highest. That asymmetry is upstream's; do not "fix" it. Returns -1 when
     *  no layer carries the id. */
    public int findIndexByLayerId(int id) {
        for (int i = 0, n = children.size(); i < n; i++) {
            if (children.get(i).id == id) return i;
        }
        return -1;
    }
    public void setId(int idx, int id) {
        ChildDrawable c = child(idx);
        if (c != null) c.id = id;
    }
    /** Same asymmetric bounds contract as getDrawable -- AOSP :554-559. */
    public int getId(int idx) {
        if (idx >= children.size()) throw new IndexOutOfBoundsException();
        if (idx < 0) throw new ArrayIndexOutOfBoundsException(idx);
        return children.get(idx).id;
    }

    private ChildDrawable child(int idx) {
        if (idx < 0 || idx >= children.size()) return null;
        return children.get(idx);
    }

    // ---- insets -----------------------------------------------------
    // AOSP routes both public forms through setLayerInsetInternal: the
    // absolute form clears the relative pair to INSET_UNDEFINED, the relative
    // form clears the absolute left/right instead. updateLayerBounds then
    // prefers a defined relative inset over the absolute one.
    public void setLayerInset(int idx, int l, int t, int r, int b) {
        setLayerInsetInternal(idx, l, t, r, b, INSET_UNDEFINED, INSET_UNDEFINED);
    }
    public void setLayerInsetRelative(int idx, int s, int t, int e, int b) {
        setLayerInsetInternal(idx, 0, t, 0, b, s, e);
    }
    private void setLayerInsetInternal(int idx, int l, int t, int r, int b, int s, int e) {
        ChildDrawable c = child(idx);
        if (c == null) return;
        c.insetL = l; c.insetT = t; c.insetR = r; c.insetB = b;
        c.insetS = s; c.insetE = e;
    }
    public void setLayerInsetLeft(int idx, int l)   { ChildDrawable c = child(idx); if (c != null) c.insetL = l; }
    public void setLayerInsetTop(int idx, int t)    { ChildDrawable c = child(idx); if (c != null) c.insetT = t; }
    public void setLayerInsetRight(int idx, int r)  { ChildDrawable c = child(idx); if (c != null) c.insetR = r; }
    public void setLayerInsetBottom(int idx, int b) { ChildDrawable c = child(idx); if (c != null) c.insetB = b; }
    public void setLayerInsetStart(int idx, int s)  { ChildDrawable c = child(idx); if (c != null) c.insetS = s; }
    public void setLayerInsetEnd(int idx, int e)    { ChildDrawable c = child(idx); if (c != null) c.insetE = e; }
    public int getLayerInsetLeft(int idx)   { ChildDrawable c = child(idx); return (c != null) ? c.insetL : 0; }
    public int getLayerInsetTop(int idx)    { ChildDrawable c = child(idx); return (c != null) ? c.insetT : 0; }
    public int getLayerInsetRight(int idx)  { ChildDrawable c = child(idx); return (c != null) ? c.insetR : 0; }
    public int getLayerInsetBottom(int idx) { ChildDrawable c = child(idx); return (c != null) ? c.insetB : 0; }
    // AOSP returns the RELATIVE inset here, which is INSET_UNDEFINED until
    // setLayerInsetRelative/Start/End is called -- not a mirror of left/right.
    public int getLayerInsetStart(int idx)  { ChildDrawable c = child(idx); return (c != null) ? c.insetS : INSET_UNDEFINED; }
    public int getLayerInsetEnd(int idx)    { ChildDrawable c = child(idx); return (c != null) ? c.insetE : INSET_UNDEFINED; }

    // ---- per-layer size + gravity (API 23) --------------------------
    public void setLayerSize(int idx, int w, int h) {
        ChildDrawable c = child(idx);
        if (c != null) { c.width = w; c.height = h; }
    }
    public void setLayerWidth(int idx, int w)  { ChildDrawable c = child(idx); if (c != null) c.width = w; }
    public int  getLayerWidth(int idx)         { ChildDrawable c = child(idx); return (c != null) ? c.width : -1; }
    public void setLayerHeight(int idx, int h) { ChildDrawable c = child(idx); if (c != null) c.height = h; }
    public int  getLayerHeight(int idx)        { ChildDrawable c = child(idx); return (c != null) ? c.height : -1; }
    public void setLayerGravity(int idx, int gravity) {
        ChildDrawable c = child(idx);
        if (c != null) c.gravity = gravity;
    }
    public int getLayerGravity(int idx) {
        ChildDrawable c = child(idx);
        return (c != null) ? c.gravity : Gravity.NO_GRAVITY;
    }

    public void setPaddingMode(int mode) { this.paddingMode = mode; }
    public int  getPaddingMode()         { return paddingMode; }

    // ---- Drawable.Callback: bubble a child layer's redraw/schedule request
    // up to whatever hosts this LayerDrawable (a View, or an outer Drawable
    // container) -- frameworks/base/graphics/java/android/graphics/drawable/
    // LayerDrawable.java:987-1009 (android11-release). AOSP's
    // invalidateDrawable also refreshes a memoized opacity/statefulness
    // cache (mLayerState.invalidateCache()) and can defer through a
    // mSuspendChildInvalidation flag while a batch of children are being
    // repositioned at once (used by its onLayoutDirectionChanged). Neither
    // has an analogue here: this file recomputes getOpacity()/isStateful()
    // fresh on every call instead of caching (see the "Deliberate
    // deviation" note on getOpacity() below), and nothing in this file
    // currently mutates many children in one call the way AOSP's direction
    // change does, so there is no invalidation storm to suppress. What
    // matters observably -- the invalidation actually reaching our own
    // callback instead of silently stopping -- is what this does. ----
    @Override
    public void invalidateDrawable(Drawable who) {
        invalidateSelf();
    }
    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        scheduleSelf(what, when);
    }
    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        unscheduleSelf(what);
    }

    // ---- state ------------------------------------------------------
    @Override public boolean isStateful() {
        for (ChildDrawable c : children) {
            if (c.drawable != null && c.drawable.isStateful()) return true;
        }
        return false;
    }
    /** AOSP splits this in two: Drawable.setState stores the set and calls
     *  onStateChange, LayerDrawable.onStateChange forwards to the children.
     *  Mac.apk's base Drawable.setState returns false without dispatching, so
     *  the store-and-dispatch half lives here, the same way StateListDrawable
     *  and DrawableWrapper in this package already handle it. Without this the
     *  View-side `background.setState(getDrawableState())` call reached a
     *  LayerDrawable and stopped: a pressed Material button never repainted. */
    @Override public boolean setState(int[] state) {
        // AOSP's mStateSet is never null (it starts as StateSet.WILD_CARD, the
        // empty array), so a null argument is normalised before the compare or
        // every call would look like a change and re-dispatch forever.
        final int[] next = (state != null) ? state : new int[0];
        if (java.util.Arrays.equals(stateSet, next)) return false;
        stateSet = next.clone();
        return onStateChange(stateSet);
    }
    @Override public int[] getState() { return stateSet.clone(); }
    @Override public boolean onStateChange(int[] state) {
        boolean changed = false;
        for (ChildDrawable c : children) {
            Drawable dr = c.drawable;
            if (dr != null && dr.isStateful() && dr.setState(state)) {
                refreshChildPadding(c);
                changed = true;
            }
        }
        if (changed) updateLayerBounds(getBounds());
        return changed;
    }
    /** AOSP :1513-1531: onLevelChange forwards UNCONDITIONALLY (unlike
     *  onStateChange, which only forwards to children that report isStateful).
     *  Without this override the base returns false and a LayerDrawable claimed
     *  its level change repainted nothing -- which, now that Drawable.setLevel
     *  returns onLevelChange's answer rather than "the level moved", would have
     *  stopped a ProgressBar's layer-list from invalidating. */
    @Override public boolean onLevelChange(int level) {
        boolean changed = false;
        for (ChildDrawable c : children) {
            Drawable dr = c.drawable;
            if (dr != null && dr.setLevel(level)) {
                refreshChildPadding(c);
                changed = true;
            }
        }
        if (changed) updateLayerBounds(getBounds());
        return changed;
    }
    @Override public void jumpToCurrentState() {
        for (ChildDrawable c : children) {
            if (c.drawable != null) c.drawable.jumpToCurrentState();
        }
    }
    @Override public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        for (ChildDrawable c : children) {
            if (c.drawable != null) c.drawable.setVisible(visible, restart);
        }
        return changed;
    }

    // ---- padding ----------------------------------------------------
    /** AOSP: PADDING_MODE_NEST sums each layer's padding, PADDING_MODE_STACK
     *  takes the per-side maximum. Returning the base Drawable default (all
     *  zero) made a nine-patch or shape layer's padding invisible to the
     *  hosting View, so text sat flush against a padded background. */
    @Override public boolean getPadding(Rect padding) {
        if (padding == null) return false;
        if (paddingMode == PADDING_MODE_NEST) computeNestedPadding(padding);
        else computeStackedPadding(padding);
        // AOSP :1049-1064: an explicitly-set padding overrides the computed one
        // PER DIMENSION, and only when it is >= 0 (-1 means "keep computed").
        // AOSP :1042-1047 -- RELATIVE padding overrides ABSOLUTE, and which of
        // start/end lands on left/right depends on the layout direction.
        // android.util.LayoutDirection.RTL == View.LAYOUT_DIRECTION_RTL == 1.
        // Spelled as the literal because android.util.LayoutDirection has no
        // port in this tree; the VALUE is public API and pinned.
        final boolean isLayoutRtl = getLayoutDirection() == 1;
        final int paddingRtlL = isLayoutRtl ? mPaddingEnd : mPaddingStart;
        final int paddingRtlR = isLayoutRtl ? mPaddingStart : mPaddingEnd;
        final int paddingL = paddingRtlL >= 0 ? paddingRtlL : mPaddingLeft;
        final int paddingR = paddingRtlR >= 0 ? paddingRtlR : mPaddingRight;
        if (paddingL >= 0)       padding.left = paddingL;
        if (mPaddingTop >= 0)    padding.top = mPaddingTop;
        if (paddingR >= 0)       padding.right = paddingR;
        if (mPaddingBottom >= 0) padding.bottom = mPaddingBottom;
        // AOSP :1066 -- the union test, NOT a constant. So an explicit
        // setPadding(0,0,0,0) reports FALSE (DRW.ld.explicitPaddingZero), which
        // is the opposite of GradientDrawable.setPadding's contract.
        return padding.left != 0 || padding.top != 0
            || padding.right != 0 || padding.bottom != 0;
    }
    /** AOSP :1090-1101. -1 in any dimension means "fall back to the value the
     *  padding mode computed". */
    public void setPadding(int left, int top, int right, int bottom) {
        mPaddingLeft = left; mPaddingTop = top;
        mPaddingRight = right; mPaddingBottom = bottom;
        // AOSP :1099-1100 CLEARS the relative values here, because relative
        // padding OVERRIDES absolute padding in getPadding(). Without this a
        // prior setPaddingRelative would keep winning and the absolute call
        // would silently do nothing on the horizontal axis.
        mPaddingStart = -1; mPaddingEnd = -1;
    }

    /** AOSP LayerDrawable.setPaddingRelative(start, top, end, bottom)
     *  (LayerDrawable.java:1124, API 23). Clears the absolute L/R values for the
     *  same reason the absolute setter clears these. */
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        mPaddingStart = start; mPaddingTop = top;
        mPaddingEnd = end;     mPaddingBottom = bottom;
        mPaddingLeft = -1; mPaddingRight = -1;
    }

    /** AOSP LayerDrawable.getLeftPadding()/etc (:1148-1232) -- the RAW explicit
     *  value, -1 when unset. They deliberately do NOT resolve against the
     *  computed padding; getPadding() does that. */
    public int getLeftPadding()   { return mPaddingLeft; }
    public int getRightPadding()  { return mPaddingRight; }
    public int getStartPadding()  { return mPaddingStart; }
    public int getEndPadding()    { return mPaddingEnd; }
    public int getTopPadding()    { return mPaddingTop; }
    public int getBottomPadding() { return mPaddingBottom; }
    private int mPaddingStart = -1, mPaddingEnd = -1;
    private void computeNestedPadding(Rect padding) {
        padding.set(0, 0, 0, 0);
        for (ChildDrawable c : children) {
            refreshChildPadding(c);
            padding.left   += c.padL;
            padding.top    += c.padT;
            padding.right  += c.padR;
            padding.bottom += c.padB;
        }
    }
    private void computeStackedPadding(Rect padding) {
        padding.set(0, 0, 0, 0);
        for (ChildDrawable c : children) {
            refreshChildPadding(c);
            padding.left   = Math.max(padding.left,   c.padL);
            padding.top    = Math.max(padding.top,    c.padT);
            padding.right  = Math.max(padding.right,  c.padR);
            padding.bottom = Math.max(padding.bottom, c.padB);
        }
    }
    private boolean refreshChildPadding(ChildDrawable c) {
        if (c.drawable == null) return false;
        c.drawable.getPadding(tmpRect);
        if (tmpRect.left != c.padL || tmpRect.top != c.padT
                || tmpRect.right != c.padR || tmpRect.bottom != c.padB) {
            c.padL = tmpRect.left;
            c.padT = tmpRect.top;
            c.padR = tmpRect.right;
            c.padB = tmpRect.bottom;
            return true;
        }
        return false;
    }

    // ---- bounds / draw ----------------------------------------------
    @Override public void draw(Canvas canvas) {
        // AOSP positions the layers from onBoundsChange (which the base class
        // now dispatches) and draw() just paints them. The recompute is kept
        // here because a caller may mutate the Rect it got from getBounds(),
        // which no setBounds call would then report; updateLayerBounds is
        // idempotent so the extra pass costs a walk, not a wrong answer.
        updateLayerBounds(getBounds());
        for (ChildDrawable c : children) {
            if (c.drawable != null) c.drawable.draw(canvas);
        }
    }
    /** AOSP :1536-1539. */
    @Override public void onBoundsChange(Rect bounds) {
        updateLayerBounds(bounds);
    }

    private void updateLayerBounds(Rect bounds) {
        int padL = 0, padT = 0, padR = 0, padB = 0;
        final int layoutDirection = getLayoutDirection();
        final boolean isLayoutRtl = layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL;
        final boolean nest = paddingMode == PADDING_MODE_NEST;
        for (ChildDrawable c : children) {
            final Drawable d = c.drawable;
            if (d == null) continue;
            final int insetRtlL = isLayoutRtl ? c.insetE : c.insetS;
            final int insetRtlR = isLayoutRtl ? c.insetS : c.insetE;
            final int insetL = insetRtlL == INSET_UNDEFINED ? c.insetL : insetRtlL;
            final int insetR = insetRtlR == INSET_UNDEFINED ? c.insetR : insetRtlR;
            tmpContainer.set(bounds.left + insetL + padL, bounds.top + c.insetT + padT,
                             bounds.right - insetR - padR, bounds.bottom - c.insetB - padB);
            final int intrinsicW = d.getIntrinsicWidth();
            final int intrinsicH = d.getIntrinsicHeight();
            final int gravity = resolveGravity(c.gravity, c.width, c.height, intrinsicW, intrinsicH);
            final int resolvedW = c.width  < 0 ? intrinsicW : c.width;
            final int resolvedH = c.height < 0 ? intrinsicH : c.height;
            applyGravity(gravity, resolvedW, resolvedH, tmpContainer, tmpOutRect, layoutDirection);
            d.setBounds(tmpOutRect.left, tmpOutRect.top, tmpOutRect.right, tmpOutRect.bottom);
            if (nest) {
                refreshChildPadding(c);
                padL += c.padL; padR += c.padR; padT += c.padT; padB += c.padB;
            }
        }
    }

    /** AOSP LayerDrawable.resolveGravity, verbatim: an axis with no gravity
     *  bit fills when the layer has no explicit size, and a layer with neither
     *  an explicit nor an intrinsic size always fills. */
    private static int resolveGravity(int gravity, int width, int height,
                                      int intrinsicWidth, int intrinsicHeight) {
        // AOSP calls Gravity.isHorizontal/isVertical here; this tree's Gravity
        // does not expose them, so the same mask test is inlined.
        if ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) == 0) {
            gravity |= (width < 0) ? Gravity.FILL_HORIZONTAL : Gravity.START;
        }
        if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == 0) {
            gravity |= (height < 0) ? Gravity.FILL_VERTICAL : Gravity.TOP;
        }
        if (width < 0 && intrinsicWidth < 0)   gravity |= Gravity.FILL_HORIZONTAL;
        if (height < 0 && intrinsicHeight < 0) gravity |= Gravity.FILL_VERTICAL;
        return gravity;
    }

    /** AOSP Gravity.apply(gravity, w, h, container, outRect, layoutDirection).
     *  android.view.Gravity.apply in this tree switches only on
     *  LEFT/RIGHT/CENTER and falls through to "place at the container's
     *  left/top at the requested size" for FILL_HORIZONTAL/FILL_VERTICAL.
     *  resolveGravity above fills both axes for the default un-sized layer, so
     *  calling it would shrink every ordinary layer to its intrinsic size
     *  instead of stretching it across the bounds. Gravity.java is outside this
     *  change, so placement is computed here off the same AXIS_PULL bits AOSP
     *  switches on. */
    private static void applyGravity(int gravity, int w, int h, Rect container,
                                     Rect outRect, int layoutDirection) {
        final int g = Gravity.getAbsoluteGravity(gravity, layoutDirection);
        final int xPull = (Gravity.AXIS_PULL_BEFORE | Gravity.AXIS_PULL_AFTER) << Gravity.AXIS_X_SHIFT;
        final int yPull = (Gravity.AXIS_PULL_BEFORE | Gravity.AXIS_PULL_AFTER) << Gravity.AXIS_Y_SHIFT;
        switch (g & xPull) {
            case 0:
                outRect.left = container.left + ((container.right - container.left - w) / 2);
                outRect.right = outRect.left + w;
                break;
            case Gravity.AXIS_PULL_BEFORE << Gravity.AXIS_X_SHIFT:
                outRect.left = container.left;
                outRect.right = outRect.left + w;
                break;
            case Gravity.AXIS_PULL_AFTER << Gravity.AXIS_X_SHIFT:
                outRect.right = container.right;
                outRect.left = outRect.right - w;
                break;
            default:
                outRect.left = container.left;
                outRect.right = container.right;
                break;
        }
        switch (g & yPull) {
            case 0:
                outRect.top = container.top + ((container.bottom - container.top - h) / 2);
                outRect.bottom = outRect.top + h;
                break;
            case Gravity.AXIS_PULL_BEFORE << Gravity.AXIS_Y_SHIFT:
                outRect.top = container.top;
                outRect.bottom = outRect.top + h;
                break;
            case Gravity.AXIS_PULL_AFTER << Gravity.AXIS_Y_SHIFT:
                outRect.bottom = container.bottom;
                outRect.top = outRect.bottom - h;
                break;
            default:
                outRect.top = container.top;
                outRect.bottom = container.bottom;
                break;
        }
    }

    @Override public void setAlpha(int alpha) {
        for (ChildDrawable c : children) if (c.drawable != null) c.drawable.setAlpha(alpha);
    }
    @Override public void setColorFilter(ColorFilter f) {
        for (ChildDrawable c : children) if (c.drawable != null) c.drawable.setColorFilter(f);
    }
    /** AOSP: the widest layer, counting its insets, its explicit width when
     *  one is set, and (in nest mode) the padding accumulated by the layers
     *  underneath it. */
    @Override public int getIntrinsicWidth() {
        int width = -1;
        int padL = 0, padR = 0;
        final boolean nest = paddingMode == PADDING_MODE_NEST;
        final boolean isLayoutRtl =
            getLayoutDirection() == android.view.View.LAYOUT_DIRECTION_RTL;
        for (ChildDrawable c : children) {
            if (c.drawable == null) continue;
            final int insetRtlL = isLayoutRtl ? c.insetE : c.insetS;
            final int insetRtlR = isLayoutRtl ? c.insetS : c.insetE;
            final int insetL = insetRtlL == INSET_UNDEFINED ? c.insetL : insetRtlL;
            final int insetR = insetRtlR == INSET_UNDEFINED ? c.insetR : insetRtlR;
            final int minWidth = c.width < 0 ? c.drawable.getIntrinsicWidth() : c.width;
            final int w = minWidth < 0 ? -1 : minWidth + insetL + insetR + padL + padR;
            if (w > width) width = w;
            if (nest) { refreshChildPadding(c); padL += c.padL; padR += c.padR; }
        }
        return width;
    }
    @Override public int getIntrinsicHeight() {
        int height = -1;
        int padT = 0, padB = 0;
        final boolean nest = paddingMode == PADDING_MODE_NEST;
        for (ChildDrawable c : children) {
            if (c.drawable == null) continue;
            final int minHeight = c.height < 0 ? c.drawable.getIntrinsicHeight() : c.height;
            final int h = minHeight < 0 ? -1 : minHeight + c.insetT + c.insetB + padT + padB;
            if (h > height) height = h;
            if (nest) { refreshChildPadding(c); padT += c.padT; padB += c.padB; }
        }
        return height;
    }
    /** AOSP :1438-1441 -- an explicit android:opacity on the layer-list wins
     *  over the computed answer. */
    public void setOpacity(int opacity) { mOpacityOverride = opacity; }

    /** AOSP LayerState.getOpacity (:2078-2112): seek the first NON-NULL child,
     *  take its opacity, then fold every later one through
     *  Drawable.resolveOpacity. With no children at all the answer is
     *  TRANSPARENT, not TRANSLUCENT (DRW.ld.empty.opacity).
     *
     *  The hardcoded TRANSLUCENT this replaces was not merely conservative: it
     *  made EVERY layer-list background report "I may show what is behind me",
     *  which is exactly the input View/ViewGroup use to decide whether they can
     *  skip drawing what is underneath.
     *
     *  Deliberate deviation: AOSP memoizes into mCheckedOpacity, so on a shared
     *  instance the ORDER of the questions is part of the answer. We recompute,
     *  which is the same value and is order-independent. */
    @Override public int getOpacity() {
        if (mOpacityOverride != android.graphics.PixelFormat.UNKNOWN) return mOpacityOverride;
        final int n = children.size();
        int firstIndex = -1;
        for (int i = 0; i < n; i++) {
            if (children.get(i).drawable != null) { firstIndex = i; break; }
        }
        int op;
        if (firstIndex >= 0) op = children.get(firstIndex).drawable.getOpacity();
        else return android.graphics.PixelFormat.TRANSPARENT;
        for (int i = firstIndex + 1; i < n; i++) {
            final Drawable dr = children.get(i).drawable;
            if (dr != null) op = Drawable.resolveOpacity(op, dr.getOpacity());
        }
        return op;
    }

    /** AOSP :1174-1186 delegates to each child's own getOutline and stops at the
     *  first non-empty one; the container itself has no shape. */
    @Override public void getOutline(android.graphics.Outline outline) {
        for (ChildDrawable c : children) {
            if (c.drawable != null) {
                c.drawable.getOutline(outline);
                if (!outline.isEmpty()) return;
            }
        }
    }

    @Override public int getChangingConfigurations() {
        return super.getChangingConfigurations() | mChangingConfigurations;
    }

    /** AOSP :1771-1777. A LayerDrawable's ConstantState produces a NEW
     *  LayerDrawable whose ChildDrawable RECORDS are copies -- see
     *  ChildDrawable(ChildDrawable). The base class's "hand back this same
     *  drawable" state cannot express that, so a caller that asked for a fresh
     *  copy got an alias and every setLayerInset/setLayerSize on it moved the
     *  original (DRW.cs.ld.leakWithoutMutate). */
    @Override public ConstantState getConstantState() {
        mChangingConfigurations = getChangingConfigurations();
        // LayerState rebuilds a PLAIN LayerDrawable. In AOSP each subclass
        // carries its own state (TransitionDrawable has TransitionState,
        // RippleDrawable has RippleState) so newDrawable() reproduces the right
        // class; ours do not, and handing a caller a LayerDrawable where it
        // asked for a TransitionDrawable would be a ClassCastException it never
        // had before. Subclasses therefore keep the base "this drawable is its
        // own state" behaviour until they define one.
        if (getClass() != LayerDrawable.class) return super.getConstantState();
        return new LayerState(this);
    }

    @Override public Drawable mutate() {
        if (!mMutated) {
            for (ChildDrawable c : children) {
                if (c.drawable != null) c.drawable.mutate();
            }
            mMutated = true;
        }
        return this;
    }
    private boolean mMutated;

    /** Copies the whole layer configuration, not just the child list. */
    static final class LayerState extends ConstantState {
        private final List<ChildDrawable> mChildren = new ArrayList<>();
        private final int mPaddingMode;
        private final int mPadL, mPadT, mPadR, mPadB;
        private final int mOpacityOverride;
        private final int mChangingConfigurations;

        LayerState(LayerDrawable orig) {
            for (ChildDrawable c : orig.children) mChildren.add(new ChildDrawable(c));
            mPaddingMode = orig.paddingMode;
            mPadL = orig.mPaddingLeft; mPadT = orig.mPaddingTop;
            mPadR = orig.mPaddingRight; mPadB = orig.mPaddingBottom;
            mOpacityOverride = orig.mOpacityOverride;
            mChangingConfigurations = orig.mChangingConfigurations;
        }

        @Override public Drawable newDrawable() {
            LayerDrawable ld = new LayerDrawable();
            for (ChildDrawable c : mChildren) {
                ChildDrawable copy = new ChildDrawable(c);
                // AOSP's equivalent clone-with-owner constructor
                // (LayerDrawable.java ChildDrawable, :1847-1872) sets the
                // callback LAST, "to prevent invalidation from propagating
                // before the constant state has been set" -- same ordering
                // here: ld.children hasn't seen this copy yet when we wire
                // it. Without this, a LayerDrawable handed back from
                // getConstantState().newDrawable() (the standard way a
                // shared Drawable resource becomes a mutable per-View copy)
                // would carry children whose invalidateSelf() still goes
                // nowhere, same as before this file implemented Callback at
                // all.
                if (copy.drawable != null) copy.drawable.setCallback(ld);
                ld.children.add(copy);
            }
            ld.paddingMode = mPaddingMode;
            ld.mPaddingLeft = mPadL; ld.mPaddingTop = mPadT;
            ld.mPaddingRight = mPadR; ld.mPaddingBottom = mPadB;
            ld.mOpacityOverride = mOpacityOverride;
            return ld;
        }
        @Override public int getChangingConfigurations() { return mChangingConfigurations; }
    }
}
