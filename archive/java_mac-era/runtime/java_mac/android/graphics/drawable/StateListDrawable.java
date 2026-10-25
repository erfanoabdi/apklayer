// android.graphics.drawable.StateListDrawable -- the drawable behind an XML
// <selector>: a list of (state-spec, Drawable) rows, of which exactly one is
// "current" at a time. AOSP:
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-11.0.0_r48/graphics/java/android/graphics/drawable/StateListDrawable.java
//
// AOSP declares `extends DrawableContainer`. We do not: this class predates
// DrawableContainer in the tree and other code links it directly, so the
// container mechanics it needs are implemented here rather than inherited.
// Every one is cited against its AOSP home (StateListDrawable.java, or
// DrawableContainer.java / DrawableContainerState where AOSP puts it).
//
// v1.0.923 -- what was missing, and why each mattered:
//
//  * setState did its OWN comparison ("did the selected index move?") instead
//    of Drawable's ("did the state VECTOR move?"). Those differ on the very
//    first call: a fresh selector had no selection yet, so setState(new int[0])
//    -- which is the state the drawable already has -- reported true and told
//    every caller a repaint was needed (DRW.sm.stateList.setstateEmpty, ref 0).
//    The fix is to stop overriding setState and let the base compare, which in
//    turn requires addState to select eagerly the way AOSP does (:84-90).
//
//  * getOpacity returned UNKNOWN(0) unconditionally. UNKNOWN is contagious --
//    Drawable.resolveOpacity turns any pair containing it into UNKNOWN -- so a
//    single selector background made every LayerDrawable above it unknowable
//    too.
//
//  * getPadding fell through to the base zero. AOSP uses the CONSTANT padding:
//    the per-side max over ALL rows, so the padding does not jump when the
//    state changes (DRW.sl.constantPadding.afterPressed).
//
// v1.0.962 -- this class did not declare `implements Drawable.Callback`, even
// though it already propagated a row's invalidate/schedule/unschedule (each
// row's Callback was a private anonymous proxy object, set via
// drawable.setCallback(macapkChildCallback())). That made every
// addState()-ed child's getCallback() answer the proxy instead of this
// selector, unlike real Android where StateListDrawable inherits
// DrawableContainer's `dr.setCallback(mOwner)` (DrawableContainer.java:846,
// android11-release) -- an identity mismatch any code that compares
// child.getCallback() against the expected container would see, on top of
// the plain instanceof/cast gap. Fixed by implementing Callback directly on
// this class and wiring addState to setCallback(this); the propagation LOGIC
// (who == current() filtering) is unchanged, just moved out of the proxy.
//
// 2026-09-26 (gate MACAPK_SLD_STATE_FORWARD, default ON, read from
// DrawableContainer so both containers flip together): the selected row is
// now INITIALISED the way AOSP DrawableContainer.initializeDrawableForDisplay
// does it (android14-release :523-566) and the container's state reaches the
// current row on every change (DrawableContainer.onStateChange :329-338, the
// `super` of StateListDrawable.onStateChange :103-115). Before, selectDrawable
// pushed only level/bounds/visibility and onStateChange's `super` was
// Drawable's (a tint check), so a row that is itself stateful was evaluated at
// the EMPTY state forever: Organic Maps' Download button is
// ripple -> selector -> a <shape> whose <solid> is @color/button_accent
// (enabled -> #249cf2, default black_12 = 0x1e000000), and the selector picked
// the right row but never told it "enabled", so it painted 12% black over the
// white ripple mask layer = #e1e1e1 grey. Alpha / colour filter / tint go
// through the same AOSP routine (recorded, forwarded to the current row,
// replayed on selection) instead of being sprayed over every row, and bounds
// reach only the current row (onBoundsChange :231-239) now that selection
// pushes them. MACAPK_SLD_STATE_FORWARD=0 restores every previous behaviour
// below exactly; debug/sldstate_gate.sh is the instrument.
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

public class StateListDrawable extends Drawable implements Drawable.Callback {
    private static final class Entry {
        final int[] stateSet;
        final Drawable drawable;
        Entry(int[] s, Drawable d) { stateSet = s; drawable = d; }
    }
    private final List<Entry> entries = new ArrayList<>();
    private int currentIndex = -1;
    /** AOSP's container-level values (DrawableContainerState + mHasAlpha),
     *  shared implementation with DrawableContainer. Gate-on only. */
    private final DrawableContainer.MacapkChildConfig mCfg = new DrawableContainer.MacapkChildConfig();
    private static final boolean FORWARD = DrawableContainer.MACAPK_SLD_STATE_FORWARD;

    public StateListDrawable() {}

    /** AOSP :84-90 re-runs onStateChange after every add "in case the new state
     *  matches our current state", which is what gives a freshly built selector
     *  a current drawable without anyone calling setState. */
    public void addState(int[] stateSet, Drawable drawable) {
        if (drawable == null) return;
        entries.add(new Entry(stateSet == null ? new int[0] : stateSet.clone(), drawable));
        if (FORWARD) {
            // DrawableContainerState.addChild :843-845: mutate, hide until
            // selected, call back into the container.
            DrawableContainer.MacapkChildConfig.adoptChild(this, drawable);
        } else {
            // AOSP DrawableContainerState.addChild: "dr.setCallback(mOwner)" --
            // the container itself, not a proxy (DrawableContainer.java:846).
            drawable.setCallback(this);
        }
        onStateChange(getState());
    }

    public int getStateCount() { return entries.size(); }

    /** AOSP's DrawableContainerState arrays are OVER-ALLOCATED (10 slots,
     *  grown in blocks of 10) and getStateSet/getStateDrawable index the raw
     *  array, so an index past the row COUNT but inside the capacity returns
     *  NULL rather than throwing (DRW.sl.stateSetOutOfRange, ref NULL).
     *  Reproducing the block growth exactly is what makes index 9 on a 3-row
     *  selector answer NULL instead of IndexOutOfBoundsException. */
    private int macapkCapacity() { return ((entries.size() / 10) + 1) * 10; }

    public int[] getStateSet(int index) {
        if (index < 0 || index >= macapkCapacity()) throw new ArrayIndexOutOfBoundsException(index);
        if (index >= entries.size()) return null;
        return entries.get(index).stateSet.clone();
    }
    public Drawable getStateDrawable(int index) {
        if (index < 0 || index >= macapkCapacity()) throw new ArrayIndexOutOfBoundsException(index);
        if (index >= entries.size()) return null;
        return entries.get(index).drawable;
    }

    /** AOSP StateListDrawableState.indexOfStateSet, exposed as
     *  findStateDrawableIndex since API 29: the FIRST row whose spec MATCHES the
     *  given vector, which is not the same as the first row EQUAL to it -- a
     *  negated row like {!enabled} matches the empty vector, so
     *  findStateDrawableIndex(new int[0]) on a pressed/!enabled/wildcard
     *  selector answers 1, not 2 (DRW.sl.findIndex.wildcard). */
    public int findStateDrawableIndex(int[] stateSet) {
        for (int i = 0; i < entries.size(); i++) {
            if (stateSetMatches(entries.get(i).stateSet, stateSet)) return i;
        }
        return -1;
    }
    /** Pre-API-29 name for an EXACT spec lookup; kept for callers that used it. */
    public int getStateDrawableIndex(int[] stateSet) {
        for (int i = 0; i < entries.size(); ++i) {
            if (java.util.Arrays.equals(entries.get(i).stateSet, stateSet)) return i;
        }
        return -1;
    }

    /** AOSP :92-95 -- hardcoded true, even for a selector with zero rows. A
     *  computed answer says false for an empty one, and every View built on it
     *  then skips its state refresh entirely. */
    @Override public boolean isStateful() { return true; }

    /** AOSP :98-101 -> StateListState.hasFocusStateSpecified :354-356:
     *  StateSet.containsAttribute(mStateSets, state_focused), which counts a
     *  NEGATED spec (-id) as well. */
    @Override public boolean hasFocusStateSpecified() {
        if (!FORWARD) return super.hasFocusStateSpecified();
        for (Entry e : entries) {
            for (int s : e.stateSet) {
                if (s == STATE_FOCUSED || -s == STATE_FOCUSED) return true;
            }
        }
        return false;
    }
    /** android.R.attr.state_focused (public-final.xml, 0x0101009c). */
    private static final int STATE_FOCUSED = 0x0101009c;

    /** AOSP :104-115. setState itself is NOT overridden: Drawable.setState owns
     *  the "did the vector change" comparison and only then calls this. AOSP's
     *  `super` here is DrawableContainer.onStateChange (:329-338), which hands
     *  the new vector to the CURRENT row before the selection moves; the
     *  newly selected row then gets it from initializeDrawableForDisplay. */
    @Override public boolean onStateChange(int[] stateSet) {
        final boolean changed;
        if (FORWARD) {
            // No exit fade, so AOSP's mLastDrawable branch cannot fire here.
            final Drawable cur = current();
            changed = cur != null && cur.setState(stateSet);
        } else {
            changed = super.onStateChange(stateSet);
        }
        int idx = findStateDrawableIndex(stateSet);
        if (idx < 0) idx = findStateDrawableIndex(new int[0]);
        return selectDrawable(idx) || changed;
    }

    /** DrawableContainer.selectDrawable: returns whether the CURRENT drawable
     *  moved, and pushes the container's own alpha/state/level/bounds onto the
     *  newly selected child. */
    private boolean selectDrawable(int index) {
        if (index == currentIndex) return false;
        if (FORWARD) {
            // AOSP DrawableContainer.selectDrawable :455-516 with no fades: the
            // outgoing row is hidden (:479-481) and the incoming one gets the
            // full initializeDrawableForDisplay (:523-566), state included.
            final Drawable old = current();
            if (old != null) old.setVisible(false, false);
            currentIndex = (index >= 0 && index < entries.size()) ? index : -1;
            final Drawable d = current();
            if (d != null) mCfg.initializeForDisplay(this, d);
            invalidateSelf();
            return true;
        }
        currentIndex = (index >= 0 && index < entries.size()) ? index : -1;
        Drawable d = current();
        if (d != null) {
            d.setLevel(getLevel());
            d.setBounds(getBounds());
            d.setVisible(isVisible(), false);
        }
        invalidateSelf();
        return true;
    }

    /** AOSP StateSet.stateSetMatches: a positive spec id must be PRESENT in the
     *  state vector, a negative one (-id, emitted for android:state_X="false")
     *  means id must be ABSENT, and an empty spec matches anything. A SUPERSET
     *  matches -- {pressed,enabled} satisfies the {pressed} row. */
    private static boolean stateSetMatches(int[] spec, int[] state) {
        for (int r : spec) {
            boolean mustMatch = r > 0;
            int want = mustMatch ? r : -r;
            boolean found = false;
            if (state != null) {
                for (int s : state) { if (s == want) { found = true; break; } }
            }
            if (mustMatch != found) return false;
        }
        return true;
    }

    /** The drawable for the current state, or null before anything matched. */
    private Drawable current() {
        return (currentIndex >= 0 && currentIndex < entries.size())
                ? entries.get(currentIndex).drawable : null;
    }

    @Override public void draw(Canvas canvas) {
        Drawable d = current();
        if (FORWARD) {
            // AOSP :84-92: selection and onBoundsChange keep the current row's
            // bounds, so draw does not re-push them.
            if (d != null) d.draw(canvas);
            return;
        }
        if (d != null) { d.setBounds(getBounds()); d.draw(canvas); }
    }
    /** AOSP DrawableContainer.getCurrent(): the active child, not `this`. */
    @Override public Drawable getCurrent() { return current(); }

    @Override public void onBoundsChange(Rect bounds) {
        if (FORWARD) {
            // AOSP :231-239: the current row only; a row selected later gets
            // the bounds from initializeDrawableForDisplay (:554).
            final Drawable d = current();
            if (d != null) d.setBounds(bounds);
            return;
        }
        // Every row is kept in sync rather than just the current one: a later
        // state change swaps in a sibling that would otherwise carry stale
        // bounds, and this class has no per-select bounds push at draw time.
        for (Entry e : entries) if (e.drawable != null) e.drawable.setBounds(bounds);
    }
    /** AOSP :142-155: recorded, forwarded to the current row on a first call
     *  or a change, replayed onto each row as it is selected. */
    @Override public void setAlpha(int alpha) {
        if (FORWARD) {
            final Drawable d = current();
            if (mCfg.setAlpha(alpha) && d != null) d.setAlpha(alpha);
            return;
        }
        for (Entry e : entries) if (e.drawable != null) e.drawable.setAlpha(alpha);
    }
    /** AOSP :157-160 answers the container's own alpha (0xFF until set). */
    @Override public int getAlpha() {
        if (FORWARD) return mCfg.alpha;
        return super.getAlpha();
    }
    /** AOSP :172-183. */
    @Override public void setColorFilter(ColorFilter cf) {
        if (FORWARD) {
            final Drawable d = current();
            if (mCfg.setColorFilter(cf) && d != null) d.setColorFilter(cf);
            return;
        }
        for (Entry e : entries) if (e.drawable != null) e.drawable.setColorFilter(cf);
    }
    /** AOSP :185-196. The base field is kept in step so getTintList() answers. */
    @Override public void setTintList(android.content.res.ColorStateList t) {
        if (FORWARD) {
            mTintList = t;
            final Drawable d = current();
            if (mCfg.setTintList(t) && d != null) d.setTintList(t);
            return;
        }
        super.setTintList(t);
        for (Entry e : entries) if (e.drawable != null) e.drawable.setTintList(t);
    }
    /** AOSP funnels setTintMode into setTintBlendMode (:198-209); our leaves
     *  implement the PorterDuff form, so that is what is recorded and replayed. */
    @Override public void setTintMode(android.graphics.PorterDuff.Mode mode) {
        if (!FORWARD) { super.setTintMode(mode); return; }
        mTintMode = mode;
        final Drawable d = current();
        if (mCfg.setTintMode(mode) && d != null) d.setTintMode(mode);
    }
    @Override public void setTintBlendMode(android.graphics.BlendMode blendMode) {
        if (!FORWARD) { super.setTintBlendMode(blendMode); return; }
        final Drawable d = current();
        if (mCfg.setTintBlendMode(blendMode) && d != null) d.setTintBlendMode(blendMode);
    }
    /** AOSP :162-170. */
    @Override public void setDither(boolean dither) {
        if (!FORWARD) { super.setDither(dither); return; }
        final Drawable d = current();
        if (mCfg.setDither(dither) && d != null) d.setDither(dither);
    }
    /** AOSP :257-270. */
    @Override public void setAutoMirrored(boolean mirrored) {
        if (!FORWARD) { super.setAutoMirrored(mirrored); return; }
        final Drawable d = current();
        if (mCfg.setAutoMirrored(mirrored) && d != null) d.setAutoMirrored(mirrored);
    }
    @Override public boolean isAutoMirrored() {
        if (!FORWARD) return super.isAutoMirrored();
        return mCfg.autoMirrored;
    }
    /** AOSP :300-305. */
    @Override public void setHotspot(float x, float y) {
        if (!FORWARD) { super.setHotspot(x, y); return; }
        final Drawable d = current();
        if (d != null) d.setHotspot(x, y);
    }
    /** AOSP :307-318. */
    @Override public void setHotspotBounds(int left, int top, int right, int bottom) {
        if (!FORWARD) { super.setHotspotBounds(left, top, right, bottom); return; }
        mCfg.setHotspotBounds(left, top, right, bottom);
        final Drawable d = current();
        if (d != null) d.setHotspotBounds(left, top, right, bottom);
    }
    /** AOSP :320-327. */
    @Override public void getHotspotBounds(Rect outRect) {
        if (FORWARD && mCfg.hotspotBounds != null) { outRect.set(mCfg.hotspotBounds); return; }
        super.getHotspotBounds(outRect);
    }
    /** AOSP :351-356 -> DrawableContainerState.setLayoutDirection :931-950:
     *  every row takes the direction; the answer is the current row's. */
    @Override public boolean onLayoutDirectionChanged(int layoutDirection) {
        if (!FORWARD) return super.onLayoutDirectionChanged(layoutDirection);
        boolean changed = false;
        for (int i = 0; i < entries.size(); i++) {
            final Drawable d = entries.get(i).drawable;
            if (d == null) continue;
            final boolean childChanged = d.setLayoutDirection(layoutDirection);
            if (i == currentIndex) changed = childChanged;
        }
        return changed;
    }
    /** AOSP :374-388 (no constant size in our selector): the current row's. */
    @Override public int getMinimumWidth() {
        if (!FORWARD) return super.getMinimumWidth();
        final Drawable d = current();
        return d != null ? d.getMinimumWidth() : 0;
    }
    @Override public int getMinimumHeight() {
        if (!FORWARD) return super.getMinimumHeight();
        final Drawable d = current();
        return d != null ? d.getMinimumHeight() : 0;
    }
    /** AOSP :127-133. */
    @Override public android.graphics.Insets getOpticalInsets() {
        if (!FORWARD) return super.getOpticalInsets();
        final Drawable d = current();
        return d != null ? d.getOpticalInsets() : android.graphics.Insets.NONE;
    }
    /** AOSP DrawableContainer.setVisible forwards to the current child, which is
     *  what makes getOpacity's "invisible current child" clause reachable
     *  (DRW.sl.opacity.afterInvisible). */
    @Override public boolean setVisible(boolean visible, boolean restart) {
        final boolean changed = super.setVisible(visible, restart);
        Drawable d = current();
        if (d != null) d.setVisible(visible, restart);
        return changed;
    }
    /** AOSP DrawableContainer.onLevelChange -- forward to the current child. */
    @Override public boolean onLevelChange(int level) {
        Drawable d = current();
        return d != null && d.setLevel(level);
    }
    @Override public void jumpToCurrentState() {
        Drawable d = current();
        if (d != null) {
            d.jumpToCurrentState();
            // AOSP :281-286 re-applies a set alpha after the jump.
            if (FORWARD && mCfg.hasAlpha) d.setAlpha(mCfg.alpha);
        }
    }
    @Override public void getOutline(android.graphics.Outline outline) {
        Drawable d = current();
        if (d != null) d.getOutline(outline);
    }

    @Override public int getIntrinsicWidth()  { Drawable d = current(); return d != null ? d.getIntrinsicWidth()  : -1; }
    @Override public int getIntrinsicHeight() { Drawable d = current(); return d != null ? d.getIntrinsicHeight() : -1; }

    /** DrawableContainerState.getConstantPadding: the per-side MAX over ALL
     *  rows, not the current row's padding, so the padding a hosting View adopts
     *  does not change when the selector changes state. */
    @Override public boolean getPadding(Rect padding) {
        Rect r = null;
        final Rect t = new Rect();
        for (Entry e : entries) {
            if (e.drawable == null) continue;
            if (e.drawable.getPadding(t)) {
                if (r == null) r = new Rect(0, 0, 0, 0);
                if (t.left > r.left) r.left = t.left;
                if (t.top > r.top) r.top = t.top;
                if (t.right > r.right) r.right = t.right;
                if (t.bottom > r.bottom) r.bottom = t.bottom;
            }
        }
        if (FORWARD) {
            // AOSP :104-125, with the RTL mirror of an auto-mirrored selector.
            final boolean result;
            if (r != null) {
                if (padding != null) padding.set(r);
                result = (r.left | r.top | r.right | r.bottom) != 0;
            } else {
                final Drawable d = current();
                result = d != null ? d.getPadding(padding) : super.getPadding(padding);
            }
            if (padding != null) mCfg.mirrorPadding(this, padding);
            return result;
        }
        if (r != null) {
            if (padding != null) padding.set(r);
            return (r.left | r.top | r.right | r.bottom) != 0;
        }
        Drawable d = current();
        if (d != null) return d.getPadding(padding);
        return super.getPadding(padding);
    }

    /** AOSP DrawableContainer.getOpacity: TRANSPARENT when there is no current
     *  child or it is invisible, otherwise the resolveOpacity fold over ALL the
     *  rows (a selector must report what it might show in ANY state, not just
     *  the one it is in). */
    @Override public int getOpacity() {
        Drawable cur = current();
        if (cur == null || !cur.isVisible()) return PixelFormat.TRANSPARENT;
        int op = PixelFormat.OPAQUE;
        boolean seeded = false;
        for (Entry e : entries) {
            if (e.drawable == null) continue;
            final int childOp = e.drawable.getOpacity();
            op = seeded ? Drawable.resolveOpacity(op, childOp) : childOp;
            seeded = true;
        }
        return seeded ? op : PixelFormat.TRANSPARENT;
    }

    // Drawable.Callback: bubble the CURRENT row's redraw/schedule request up
    // to whoever hosts this selector -- matches DrawableContainer's own
    // implementation exactly (DrawableContainer.java:391-417,
    // android11-release): "who == current()" filters out a non-visible row's
    // invalidation, the same way AOSP filters on "who == mCurrDrawable". AOSP
    // also refreshes a memoized opacity/statefulness cache here
    // (mDrawableContainerState.invalidateCache()); this class recomputes both
    // fresh on every call (see getOpacity() above) so there is no cache to
    // invalidate.
    @Override public void invalidateDrawable(Drawable who) {
        Callback cb = getCallback();
        if (cb != null && who == current()) cb.invalidateDrawable(this);
    }
    // AOSP :404-416 also filters schedules to the current row.
    @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {
        Callback cb = getCallback();
        if (FORWARD && who != current()) return;
        if (cb != null) cb.scheduleDrawable(this, what, when);
    }
    @Override public void unscheduleDrawable(Drawable who, Runnable what) {
        Callback cb = getCallback();
        if (FORWARD && who != current()) return;
        if (cb != null) cb.unscheduleDrawable(this, what);
    }
}
