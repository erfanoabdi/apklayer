// android.graphics.drawable.DrawableContainer -- AOSP base class that
// holds a set of child drawables and shows exactly one "current" child
// at a time (selected by subclasses: StateListDrawable picks by state,
// LevelListDrawable by level, AnimationDrawable by frame index). AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/DrawableContainer.java
//
// We implement the container mechanics (selectDrawable(index), the
// current-drawable delegation of draw/bounds/alpha/state) so subclasses
// only choose the index. Implements Drawable.Callback so a child's
// invalidate bubbles to our own callback. NOTE: our existing
// StateListDrawable predates this class and extends Drawable directly --
// it is intentionally left as-is (other code links it); new subclasses
// (LevelListDrawable, AnimationDrawable) extend this.
//
// 2026-09-26 (gate MACAPK_SLD_STATE_FORWARD, default ON): the child
// forwarding now mirrors AOSP android14-release DrawableContainer.java in
// full. What selectDrawable pushed before was alpha (always, even when no
// caller had set one), a non-null colour filter, state, level and bounds.
// AOSP's initializeDrawableForDisplay (:523-566) also pushes visibility,
// tint list, tint mode, dither, layout direction, auto-mirroring and the
// hotspot bounds, does it with the child's invalidations blocked, hides the
// OUTGOING child (:479-481), and gives every added child mutate() +
// setVisible(false, true) (DrawableContainerState.addChild :837-859). The
// container-level values AOSP keeps on DrawableContainerState live in
// MacapkChildConfig below, which StateListDrawable (a parallel
// implementation, see its header) shares so the two cannot drift.
// MACAPK_SLD_STATE_FORWARD=0 restores the previous path exactly.
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;

import java.util.ArrayList;

public class DrawableContainer extends Drawable implements Drawable.Callback {
    /** Default ON. `MACAPK_SLD_STATE_FORWARD=0` restores the pre-2026-09-26
     *  container forwarding, in this class AND in StateListDrawable (which
     *  reads this same constant so the two halves cannot disagree). The bug it
     *  bisects: a selector never pushed its state vector onto the row it
     *  selected, so a stateful row (a shape whose solid is a colour selector, a
     *  nested selector, a ripple) was evaluated at the EMPTY state forever --
     *  Organic Maps' Download button rendered #e1e1e1 grey instead of its
     *  enabled accent #249cf2. See debug/sldstate_gate.sh. */
    static final boolean MACAPK_SLD_STATE_FORWARD =
        !"0".equals(System.getenv("MACAPK_SLD_STATE_FORWARD"));

    private final ArrayList<Drawable> mChildren = new ArrayList<>();
    private int mCurIndex = -1;
    private Drawable mCurrDrawable;
    private int mAlpha = 0xFF;
    private ColorFilter mColorFilter;
    /** The DrawableContainerState half AOSP pushes onto each selected child
     *  (used only when MACAPK_SLD_STATE_FORWARD is on). */
    private final MacapkChildConfig mCfg = new MacapkChildConfig();

    /** Adds a child to the container; returns its index. Subclasses (or
     *  our LevelList/Animation impls) call this from their add methods. */
    protected int addChild(Drawable dr) {
        if (MACAPK_SLD_STATE_FORWARD) {
            if (dr != null) MacapkChildConfig.adoptChild(this, dr);
        } else {
            if (dr != null) dr.setCallback(this);
        }
        mChildren.add(dr);
        return mChildren.size() - 1;
    }
    protected Drawable getChild(int index) {
        if (index < 0 || index >= mChildren.size()) return null;
        return mChildren.get(index);
    }
    protected int getChildCount() { return mChildren.size(); }

    /** Make the child at index the current drawable. Returns true if the
     *  current drawable actually changed. */
    public boolean selectDrawable(int index) {
        if (index == mCurIndex) return false;
        if (MACAPK_SLD_STATE_FORWARD) {
            // AOSP selectDrawable :455-516 with no enter/exit fade (we have no
            // fade support, so AOSP's mLastDrawable is always null here and the
            // exitFadeDuration<=0 branch is the one that runs): hide the
            // outgoing child (:479-481), initialise the incoming one (:483-496).
            if (mCurrDrawable != null) mCurrDrawable.setVisible(false, false);
            if (index >= 0 && index < mChildren.size()) {
                final Drawable d = mChildren.get(index);
                mCurrDrawable = d;
                mCurIndex = index;
                if (d != null) mCfg.initializeForDisplay(this, d);
            } else {
                mCurrDrawable = null;
                mCurIndex = -1;
            }
            invalidateSelf();
            return true;
        }
        if (index >= 0 && index < mChildren.size()) {
            Drawable d = mChildren.get(index);
            mCurrDrawable = d;
            mCurIndex = index;
            if (d != null) {
                d.setAlpha(mAlpha);
                if (mColorFilter != null) d.setColorFilter(mColorFilter);
                d.setState(getState());
                d.setLevel(getLevel());
                d.setBounds(getBounds());
            }
            invalidateSelf();
            return true;
        } else {
            mCurrDrawable = null;
            mCurIndex = -1;
            invalidateSelf();
            return true;
        }
    }

    @Override public Drawable getCurrent() { return mCurrDrawable; }

    /** AOSP DrawableContainer.hasFocusStateSpecified() (:247) -- delegates to
     *  the currently-shown child. */
    @Override public boolean hasFocusStateSpecified() {
        return mCurrDrawable != null && mCurrDrawable.hasFocusStateSpecified();
    }

    @Override public void draw(Canvas canvas) {
        if (mCurrDrawable != null) mCurrDrawable.draw(canvas);
    }
    // setBounds is NOT overridden: the base class dispatches onBoundsChange,
    // which does the same forwarding one line down. AOSP overrides only
    // onBoundsChange for the same reason.
    @Override public void onBoundsChange(Rect bounds) {
        if (mCurrDrawable != null) mCurrDrawable.setBounds(bounds);
    }
    @Override public void setAlpha(int alpha) {
        if (MACAPK_SLD_STATE_FORWARD) {
            // AOSP :142-155: only a first call or a real change forwards, and
            // it records that an alpha was set so later selections replay it.
            if (mCfg.setAlpha(alpha) && mCurrDrawable != null) mCurrDrawable.setAlpha(alpha);
            mAlpha = alpha;
            return;
        }
        mAlpha = alpha;
        if (mCurrDrawable != null) mCurrDrawable.setAlpha(alpha);
    }
    @Override public int getAlpha() { return mAlpha; }
    @Override public void setColorFilter(ColorFilter cf) {
        if (MACAPK_SLD_STATE_FORWARD) {
            // AOSP :172-183: "has a colour filter" is set even for null, and
            // from then on a selected child gets the filter INSTEAD of the tint.
            if (mCfg.setColorFilter(cf) && mCurrDrawable != null) mCurrDrawable.setColorFilter(cf);
            mColorFilter = cf;
            return;
        }
        mColorFilter = cf;
        if (mCurrDrawable != null) mCurrDrawable.setColorFilter(cf);
    }
    @Override public ColorFilter getColorFilter() { return mColorFilter; }

    // ---- AOSP forwarding that had no override here (each: gate-off = the
    // base Drawable behaviour this class inherited before). -----------------

    /** AOSP :185-196 -- recorded on the state, forwarded to the current child
     *  only when it changed; initializeDrawableForDisplay replays it. The base
     *  field is kept in step so getTintList() still answers. */
    @Override public void setTintList(android.content.res.ColorStateList tint) {
        if (!MACAPK_SLD_STATE_FORWARD) { super.setTintList(tint); return; }
        mTintList = tint;
        if (mCfg.setTintList(tint) && mCurrDrawable != null) mCurrDrawable.setTintList(tint);
    }
    /** AOSP routes setTintMode through setTintBlendMode (:198-209); our leaves
     *  implement the PorterDuff form, so that is the one recorded and replayed. */
    @Override public void setTintMode(android.graphics.PorterDuff.Mode mode) {
        if (!MACAPK_SLD_STATE_FORWARD) { super.setTintMode(mode); return; }
        mTintMode = mode;
        if (mCfg.setTintMode(mode) && mCurrDrawable != null) mCurrDrawable.setTintMode(mode);
    }
    @Override public void setTintBlendMode(android.graphics.BlendMode blendMode) {
        if (!MACAPK_SLD_STATE_FORWARD) { super.setTintBlendMode(blendMode); return; }
        if (mCfg.setTintBlendMode(blendMode) && mCurrDrawable != null) mCurrDrawable.setTintBlendMode(blendMode);
    }
    /** AOSP :162-170. */
    @Override public void setDither(boolean dither) {
        if (!MACAPK_SLD_STATE_FORWARD) { super.setDither(dither); return; }
        if (mCfg.setDither(dither) && mCurrDrawable != null) mCurrDrawable.setDither(dither);
    }
    /** AOSP :257-270. */
    @Override public void setAutoMirrored(boolean mirrored) {
        if (!MACAPK_SLD_STATE_FORWARD) { super.setAutoMirrored(mirrored); return; }
        if (mCfg.setAutoMirrored(mirrored) && mCurrDrawable != null) mCurrDrawable.setAutoMirrored(mirrored);
    }
    @Override public boolean isAutoMirrored() {
        if (!MACAPK_SLD_STATE_FORWARD) return super.isAutoMirrored();
        return mCfg.autoMirrored;
    }
    /** AOSP :300-305. */
    @Override public void setHotspot(float x, float y) {
        if (!MACAPK_SLD_STATE_FORWARD) { super.setHotspot(x, y); return; }
        if (mCurrDrawable != null) mCurrDrawable.setHotspot(x, y);
    }
    /** AOSP :307-318. */
    @Override public void setHotspotBounds(int left, int top, int right, int bottom) {
        if (!MACAPK_SLD_STATE_FORWARD) { super.setHotspotBounds(left, top, right, bottom); return; }
        mCfg.setHotspotBounds(left, top, right, bottom);
        if (mCurrDrawable != null) mCurrDrawable.setHotspotBounds(left, top, right, bottom);
    }
    /** AOSP :320-327 -- the recorded rect, else the base answer. */
    @Override public void getHotspotBounds(Rect outRect) {
        if (MACAPK_SLD_STATE_FORWARD && mCfg.hotspotBounds != null) { outRect.set(mCfg.hotspotBounds); return; }
        super.getHotspotBounds(outRect);
    }
    /** AOSP :418-428 -- the container's own flag, then the current child. */
    @Override public boolean setVisible(boolean visible, boolean restart) {
        if (!MACAPK_SLD_STATE_FORWARD) return super.setVisible(visible, restart);
        final boolean changed = super.setVisible(visible, restart);
        if (mCurrDrawable != null) mCurrDrawable.setVisible(visible, restart);
        return changed;
    }
    /** AOSP :351-356 -> DrawableContainerState.setLayoutDirection :931-950:
     *  EVERY child takes the direction, and the answer is the CURRENT one's. */
    @Override public boolean onLayoutDirectionChanged(int layoutDirection) {
        if (!MACAPK_SLD_STATE_FORWARD) return super.onLayoutDirectionChanged(layoutDirection);
        boolean changed = false;
        for (int i = 0; i < mChildren.size(); i++) {
            final Drawable d = mChildren.get(i);
            if (d == null) continue;
            final boolean childChanged = d.setLayoutDirection(layoutDirection);
            if (i == mCurIndex) changed = childChanged;
        }
        return changed;
    }
    /** AOSP :374-388 (no constant size in our state): the current child's. */
    @Override public int getMinimumWidth() {
        if (!MACAPK_SLD_STATE_FORWARD) return super.getMinimumWidth();
        return mCurrDrawable != null ? mCurrDrawable.getMinimumWidth() : 0;
    }
    @Override public int getMinimumHeight() {
        if (!MACAPK_SLD_STATE_FORWARD) return super.getMinimumHeight();
        return mCurrDrawable != null ? mCurrDrawable.getMinimumHeight() : 0;
    }
    /** AOSP :127-133. */
    @Override public android.graphics.Insets getOpticalInsets() {
        if (!MACAPK_SLD_STATE_FORWARD) return super.getOpticalInsets();
        return mCurrDrawable != null ? mCurrDrawable.getOpticalInsets() : android.graphics.Insets.NONE;
    }
    /** AOSP DrawableContainer.getOpacity (:614-618) has a clause that is easy to
     *  drop: an INVISIBLE container is TRANSPARENT regardless of its children,
     *  and so is one with no current child. Otherwise the answer is
     *  DrawableContainerState.getOpacity -- the resolveOpacity fold over ALL the
     *  children, not the current one's opacity (:1179-1201). */
    @Override public int getOpacity() {
        if (mCurrDrawable == null || !mCurrDrawable.isVisible()) {
            return android.graphics.PixelFormat.TRANSPARENT;
        }
        return macapkChildrenOpacity();
    }
    /** DrawableContainerState.getOpacity: seed from child 0, fold the rest. */
    final int macapkChildrenOpacity() {
        final int n = mChildren.size();
        if (n == 0) return android.graphics.PixelFormat.TRANSPARENT;
        int op = android.graphics.PixelFormat.OPAQUE;
        boolean seeded = false;
        for (int i = 0; i < n; i++) {
            final Drawable d = mChildren.get(i);
            if (d == null) continue;
            final int childOp = d.getOpacity();
            op = seeded ? Drawable.resolveOpacity(op, childOp) : childOp;
            seeded = true;
        }
        return seeded ? op : android.graphics.PixelFormat.TRANSPARENT;
    }
    @Override public boolean isStateful() {
        for (Drawable d : mChildren) if (d != null && d.isStateful()) return true;
        return false;
    }
    @Override public boolean onStateChange(int[] state) {
        if (mCurrDrawable != null) return mCurrDrawable.setState(state);
        return false;
    }
    /** AOSP :595-601 -- forwards to the current child. Without it the base
     *  returns false, and since Drawable.setLevel now reports onLevelChange's
     *  answer, a LevelListDrawable/AnimationDrawable would claim a level change
     *  repainted nothing. */
    @Override public boolean onLevelChange(int level) {
        if (mCurrDrawable != null) return mCurrDrawable.setLevel(level);
        return false;
    }
    /** AOSP :603-608. */
    @Override public void getOutline(android.graphics.Outline outline) {
        if (mCurrDrawable != null) mCurrDrawable.getOutline(outline);
    }
    @Override public int getIntrinsicWidth() {
        return mCurrDrawable != null ? mCurrDrawable.getIntrinsicWidth() : -1;
    }
    @Override public int getIntrinsicHeight() {
        return mCurrDrawable != null ? mCurrDrawable.getIntrinsicHeight() : -1;
    }
    /** AOSP :578-593 asks DrawableContainerState.getConstantPadding FIRST -- the
     *  per-side MAX over ALL children, not the current child's padding -- and
     *  only falls back to the current child (then to super) when no child has
     *  any. The container's padding must not jump as the state changes, which is
     *  the whole point of caching it as "constant"
     *  (DRW.sl.constantPadding.afterPressed is the same rect as before the
     *  press). */
    @Override public boolean getPadding(Rect padding) {
        if (MACAPK_SLD_STATE_FORWARD) {
            // AOSP :104-125, including the RTL mirror of an auto-mirrored
            // container (needsMirroring :100-102) that the path below omits.
            final Rect r = macapkConstantPadding();
            final boolean result;
            if (r != null) {
                if (padding != null) padding.set(r);
                result = (r.left | r.top | r.right | r.bottom) != 0;
            } else if (mCurrDrawable != null) {
                result = mCurrDrawable.getPadding(padding);
            } else {
                result = super.getPadding(padding);
            }
            if (padding != null) mCfg.mirrorPadding(this, padding);
            return result;
        }
        final Rect r = macapkConstantPadding();
        if (r != null) {
            if (padding != null) padding.set(r);
            return (r.left | r.top | r.right | r.bottom) != 0;
        }
        if (mCurrDrawable != null) return mCurrDrawable.getPadding(padding);
        return super.getPadding(padding);
    }
    /** DrawableContainerState.getConstantPadding, minus the memoization (which
     *  AOSP only keeps to avoid re-walking; the value is the same). Returns null
     *  when NO child reports a padding, which is what selects the fallback. */
    final Rect macapkConstantPadding() {
        Rect r = null;
        final Rect t = new Rect();
        for (int i = 0; i < mChildren.size(); i++) {
            final Drawable d = mChildren.get(i);
            if (d == null) continue;
            if (d.getPadding(t)) {
                if (r == null) r = new Rect(0, 0, 0, 0);
                if (t.left > r.left) r.left = t.left;
                if (t.top > r.top) r.top = t.top;
                if (t.right > r.right) r.right = t.right;
                if (t.bottom > r.bottom) r.bottom = t.bottom;
            }
        }
        return r;
    }
    @Override public void jumpToCurrentState() {
        if (mCurrDrawable != null) {
            mCurrDrawable.jumpToCurrentState();
            // AOSP :281-286 re-applies a set alpha after the jump.
            if (MACAPK_SLD_STATE_FORWARD && mCfg.hasAlpha) mCurrDrawable.setAlpha(mCfg.alpha);
        }
    }

    // Drawable.Callback: bubble a child's redraw/schedule up.
    @Override public void invalidateDrawable(Drawable who) {
        Callback cb = getCallback();
        if (cb != null && who == mCurrDrawable) cb.invalidateDrawable(this);
    }
    /** AOSP :404-416 forwards a schedule only for the CURRENT child, like
     *  invalidateDrawable; a hidden row's animation must not keep ticking the
     *  host. */
    @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {
        Callback cb = getCallback();
        if (MACAPK_SLD_STATE_FORWARD && who != mCurrDrawable) return;
        if (cb != null) cb.scheduleDrawable(this, what, when);
    }
    @Override public void unscheduleDrawable(Drawable who, Runnable what) {
        Callback cb = getCallback();
        if (MACAPK_SLD_STATE_FORWARD && who != mCurrDrawable) return;
        if (cb != null) cb.unscheduleDrawable(this, what);
    }

    /**
     * The shared constant state of a DrawableContainer: the child list every
     * instance of this drawable shares.
     *
     * AppCompat, Material and animated-selector code reach for this to WALK the
     * states -- `((DrawableContainerState) dr.getConstantState()).getChildren()`
     * -- to tint or inspect each child. Returning null from getConstantState()
     * (the previous behaviour) made those walks NPE or silently skip, so tinting
     * a selector drawable tinted nothing.
     *
     * Backed by the container's REAL child list, so getChildren() reflects what
     * is actually in the container rather than an empty array.
     *
     * Source: frameworks/base/graphics/java/android/graphics/drawable/DrawableContainer.java
     * (nested class DrawableContainerState)
     */
    public static class DrawableContainerState extends ConstantState {
        private final DrawableContainer mOwner;

        DrawableContainerState(DrawableContainer owner) { mOwner = owner; }

        public final int getChildCount() { return mOwner.mChildren.size(); }

        public final Drawable getChild(int index) {
            return (index >= 0 && index < mOwner.mChildren.size())
                    ? mOwner.mChildren.get(index) : null;
        }

        /**
         * AOSP returns the backing array, which may be LONGER than the child
         * count (it is grown in blocks); callers are documented to index it
         * with getChildCount(). We hand back an exact-size array, which is a
         * strict subset of that contract and safe for every such caller.
         */
        public final Drawable[] getChildren() {
            return mOwner.mChildren.toArray(new Drawable[0]);
        }

        // AOSP: `public final int addChild(Drawable)` -- returns the new child's
        // index, which callers (e.g. StateListDrawable.addState) use directly.
        // frameworks/base/graphics/java/android/graphics/drawable/DrawableContainer.java
        public final int addChild(Drawable dr) { return mOwner.addChild(dr); }

        @Override public Drawable newDrawable() { return mOwner; }

        @Override public int getChangingConfigurations() { return 0; }

        // ---- AOSP DrawableContainerState, the rest of the surface -----------
        // frameworks/base/graphics/java/android/graphics/drawable/DrawableContainer.java
        // :1046-1224. All of it computes over the CHILDREN, which we already
        // hold (mOwner.mChildren), so these are faithful rather than stubs. The
        // caching flags are AOSP's: each answer is memoized until something
        // invalidates it, and callers can observe that (a child added after the
        // first getOpacity() does not change the answer on device either).

        private boolean mVariablePadding = false;
        private boolean mCheckedPadding;
        private Rect mConstantPadding;

        private boolean mConstantSize = false;
        private boolean mCheckedConstantSize;
        private int mConstantWidth, mConstantHeight;
        private int mConstantMinimumWidth, mConstantMinimumHeight;

        private int mEnterFadeDuration;
        private int mExitFadeDuration;

        private boolean mCheckedOpacity;
        private int mOpacity;
        private boolean mCheckedStateful;
        private boolean mStateful;
        private boolean mCheckedConstantState;
        private boolean mCanConstantState;

        /** AOSP :1046. When variable padding is on, getConstantPadding() stops
         *  answering and the container asks the CURRENT child instead. */
        public final void setVariablePadding(boolean variable) { mVariablePadding = variable; }

        /** AOSP :1050 -- the per-edge MAXIMUM of every child's padding, or null
         *  when padding is variable (or no child has any). */
        public final Rect getConstantPadding() {
            if (mVariablePadding) return null;
            if (mConstantPadding != null || mCheckedPadding) return mConstantPadding;

            Rect r = null;
            final Rect t = new Rect();
            for (int i = 0, n = getChildCount(); i < n; i++) {
                Drawable d = getChild(i);
                if (d != null && d.getPadding(t)) {
                    if (r == null) r = new Rect(0, 0, 0, 0);
                    if (t.left > r.left) r.left = t.left;
                    if (t.top > r.top) r.top = t.top;
                    if (t.right > r.right) r.right = t.right;
                    if (t.bottom > r.bottom) r.bottom = t.bottom;
                }
            }
            mCheckedPadding = true;
            return (mConstantPadding = r);
        }

        public final void setConstantSize(boolean constant) { mConstantSize = constant; }
        public final boolean isConstantSize() { return mConstantSize; }

        public final int getConstantWidth() {
            if (!mCheckedConstantSize) computeConstantSize();
            return mConstantWidth;
        }

        public final int getConstantHeight() {
            if (!mCheckedConstantSize) computeConstantSize();
            return mConstantHeight;
        }

        public final int getConstantMinimumWidth() {
            if (!mCheckedConstantSize) computeConstantSize();
            return mConstantMinimumWidth;
        }

        public final int getConstantMinimumHeight() {
            if (!mCheckedConstantSize) computeConstantSize();
            return mConstantMinimumHeight;
        }

        /** AOSP :1119. Note the ASYMMETRIC seeds -- intrinsic width/height start
         *  at -1 (so an all-intrinsic-less container reports -1, "no intrinsic
         *  size") while the minimums start at 0. Getting that backwards makes a
         *  selector claim a 0x0 intrinsic size and collapse. */
        protected void computeConstantSize() {
            mCheckedConstantSize = true;
            mConstantWidth = mConstantHeight = -1;
            mConstantMinimumWidth = mConstantMinimumHeight = 0;
            for (int i = 0, n = getChildCount(); i < n; i++) {
                final Drawable dr = getChild(i);
                if (dr == null) continue;
                int s = dr.getIntrinsicWidth();
                if (s > mConstantWidth) mConstantWidth = s;
                s = dr.getIntrinsicHeight();
                if (s > mConstantHeight) mConstantHeight = s;
                s = dr.getMinimumWidth();
                if (s > mConstantMinimumWidth) mConstantMinimumWidth = s;
                s = dr.getMinimumHeight();
                if (s > mConstantMinimumHeight) mConstantMinimumHeight = s;
            }
        }

        public final void setEnterFadeDuration(int duration) { mEnterFadeDuration = duration; }
        public final int getEnterFadeDuration() { return mEnterFadeDuration; }
        public final void setExitFadeDuration(int duration) { mExitFadeDuration = duration; }
        public final int getExitFadeDuration() { return mExitFadeDuration; }

        /** AOSP :1157 -- child 0's opacity folded over the rest with
         *  Drawable.resolveOpacity; TRANSPARENT when there are no children. */
        public final int getOpacity() {
            if (mCheckedOpacity) return mOpacity;
            final int n = getChildCount();
            int op = (n > 0 && getChild(0) != null)
                    ? getChild(0).getOpacity() : android.graphics.PixelFormat.TRANSPARENT;
            for (int i = 1; i < n; i++) {
                Drawable d = getChild(i);
                if (d != null) op = Drawable.resolveOpacity(op, d.getOpacity());
            }
            mOpacity = op;
            mCheckedOpacity = true;
            return op;
        }

        /** AOSP :1176 -- stateful if ANY child is. */
        public final boolean isStateful() {
            if (mCheckedStateful) return mStateful;
            boolean stateful = false;
            for (int i = 0, n = getChildCount(); i < n; i++) {
                Drawable d = getChild(i);
                if (d != null && d.isStateful()) { stateful = true; break; }
            }
            mStateful = stateful;
            mCheckedStateful = true;
            return stateful;
        }

        /** AOSP :1198 grows the backing Drawable[] from oldSize to newSize.
         *  Our children live in an ArrayList that grows itself, so there is no
         *  array to reallocate -- but the method is public API and subclasses
         *  (StateListDrawable's own state, AnimatedStateListDrawable) both call
         *  it and OVERRIDE it, so it has to exist and be overridable. */
        public void growArray(int oldSize, int newSize) { }

        /** AOSP :1204 -- true only if EVERY child has a constant state.
         *  synchronized in AOSP; kept, since the memo is written here. */
        public synchronized boolean canConstantState() {
            if (mCheckedConstantState) return mCanConstantState;
            mCheckedConstantState = true;
            for (int i = 0, n = getChildCount(); i < n; i++) {
                Drawable d = getChild(i);
                if (d == null || d.getConstantState() == null) {
                    mCanConstantState = false;
                    return false;
                }
            }
            mCanConstantState = true;
            return true;
        }
    }

    private DrawableContainerState mConstantState;

    /** AOSP always returns a DrawableContainerState here, never null. */
    @Override public ConstantState getConstantState() {
        if (mConstantState == null) mConstantState = new DrawableContainerState(this);
        return mConstantState;
    }

    public void setEnterFadeDuration(int ms) { }
    public void setExitFadeDuration(int ms) { }

    /** AOSP DrawableContainer.setConstantState(DrawableContainerState)
     *  (DrawableContainer.java:1228) -- PROTECTED; swaps the backing state and
     *  re-resolves the currently-selected child through it. */
    protected void setConstantState(DrawableContainerState state) {
        mConstantState = state;
        if (mCurIndex >= 0 && state != null) {
            mCurrDrawable = state.getChild(mCurIndex);
        }
    }

    /**
     * The container-level values AOSP keeps on DrawableContainerState (the
     * mHasAlpha/mAlpha pair lives on the container itself) and replays onto
     * every child it selects. Shared by this class and StateListDrawable, which
     * does not extend us (see its header), so the one AOSP routine exists once.
     *
     * Each setter returns whether AOSP would forward the call to the current
     * child: DrawableContainer forwards only on a first call or a real change
     * (setAlpha :144, setColorFilter :176, setTintList :189,
     * setTintBlendMode :202, setDither :164, setAutoMirrored :259).
     */
    static final class MacapkChildConfig {
        boolean hasAlpha;
        int alpha = 0xFF;
        boolean hasColorFilter;
        ColorFilter colorFilter;
        boolean hasTintList;
        android.content.res.ColorStateList tintList;
        /** AOSP's mHasTintMode. Our leaves implement the tint mode through the
         *  PorterDuff setter, so whichever of the two forms the caller used is
         *  the one replayed onto a newly selected child. */
        boolean hasTintMode;
        android.graphics.PorterDuff.Mode tintMode;
        android.graphics.BlendMode blendMode;
        /** DrawableContainer.DEFAULT_DITHER (:58). */
        boolean dither = true;
        boolean autoMirrored;
        Rect hotspotBounds;
        private BlockInvalidateCallback block;

        boolean setAlpha(int a) {
            if (hasAlpha && alpha == a) return false;
            hasAlpha = true;
            alpha = a;
            return true;
        }
        boolean setColorFilter(ColorFilter cf) {
            hasColorFilter = true;
            if (colorFilter == cf) return false;
            colorFilter = cf;
            return true;
        }
        boolean setTintList(android.content.res.ColorStateList t) {
            hasTintList = true;
            if (tintList == t) return false;
            tintList = t;
            return true;
        }
        boolean setTintMode(android.graphics.PorterDuff.Mode m) {
            hasTintMode = true;
            if (tintMode == m && blendMode == null) return false;
            tintMode = m;
            blendMode = null;
            return true;
        }
        boolean setTintBlendMode(android.graphics.BlendMode b) {
            hasTintMode = true;
            if (blendMode == b && tintMode == null) return false;
            blendMode = b;
            tintMode = null;
            return true;
        }
        boolean setDither(boolean d) {
            if (dither == d) return false;
            dither = d;
            return true;
        }
        boolean setAutoMirrored(boolean m) {
            if (autoMirrored == m) return false;
            autoMirrored = m;
            return true;
        }
        void setHotspotBounds(int l, int t, int r, int b) {
            if (hotspotBounds == null) hotspotBounds = new Rect(l, t, r, b);
            else hotspotBounds.set(l, t, r, b);
        }

        /** AOSP getPadding :118-123 / needsMirroring :100-102: an auto-mirrored
         *  container under RTL swaps left and right. */
        void mirrorPadding(Drawable owner, Rect padding) {
            if (autoMirrored && owner.getLayoutDirection() == android.util.LayoutDirection.RTL) {
                final int left = padding.left;
                padding.left = padding.right;
                padding.right = left;
            }
        }

        /** DrawableContainerState.addChild :843-845, in AOSP's order: the
         *  child's own copy of any shared constant state, hidden until it is
         *  selected, and calling back into the container. */
        static void adoptChild(Drawable owner, Drawable dr) {
            dr.mutate();
            dr.setVisible(false, true);
            dr.setCallback((Callback) owner);
        }

        /**
         * AOSP DrawableContainer.initializeDrawableForDisplay (:523-566),
         * verbatim in order, minus the enter fade (we have none, so the
         * `mEnterFadeDuration <= 0` half of :534 always holds). The child's
         * callback is swapped for one that swallows invalidations while the
         * setters run: "we don't want any of these setters causing an
         * invalidate() since that may call back into DrawableContainer"
         * (:528-530). The caller invalidates once afterwards (:513).
         *
         * The load-bearing line is `d.setState(owner.getState())` (:552):
         * without it a selected row that is itself stateful (a <shape> whose
         * <solid> is a colour selector, a nested selector, a ripple) keeps the
         * empty state it was inflated with, whatever state the container is in.
         */
        void initializeForDisplay(Drawable owner, Drawable d) {
            if (block == null) block = new BlockInvalidateCallback();
            d.setCallback(block.wrap(d.getCallback()));
            try {
                if (hasAlpha) d.setAlpha(alpha);
                if (hasColorFilter) {
                    // "Color filter always overrides tint." (:539)
                    d.setColorFilter(colorFilter);
                } else {
                    if (hasTintList) d.setTintList(tintList);
                    if (hasTintMode) {
                        if (blendMode != null) d.setTintBlendMode(blendMode);
                        else d.setTintMode(tintMode);
                    }
                }
                d.setVisible(owner.isVisible(), true);
                d.setDither(dither);
                d.setState(owner.getState());
                d.setLevel(owner.getLevel());
                d.setBounds(owner.getBounds());
                d.setLayoutDirection(owner.getLayoutDirection());
                d.setAutoMirrored(autoMirrored);
                final Rect hb = hotspotBounds;
                if (hb != null) d.setHotspotBounds(hb.left, hb.top, hb.right, hb.bottom);
            } finally {
                d.setCallback(block.unwrap());
            }
        }
    }

    /** AOSP DrawableContainer.BlockInvalidateCallback (:1248-1280), verbatim:
     *  swallows invalidations, forwards schedules to the wrapped callback.
     *  Package-private rather than private so StateListDrawable's copy of the
     *  selection routine (through MacapkChildConfig) can use it. */
    static final class BlockInvalidateCallback implements Drawable.Callback {
        private Drawable.Callback mCallback;

        BlockInvalidateCallback wrap(Drawable.Callback callback) {
            mCallback = callback;
            return this;
        }

        Drawable.Callback unwrap() {
            final Drawable.Callback callback = mCallback;
            mCallback = null;
            return callback;
        }

        @Override public void invalidateDrawable(Drawable who) {
            // Ignore invalidation.
        }

        @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {
            if (mCallback != null) mCallback.scheduleDrawable(who, what, when);
        }

        @Override public void unscheduleDrawable(Drawable who, Runnable what) {
            if (mCallback != null) mCallback.unscheduleDrawable(who, what);
        }
    }
}

