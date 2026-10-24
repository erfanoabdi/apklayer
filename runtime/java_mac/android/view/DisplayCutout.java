// android.view.DisplayCutout -- the notch / punch-hole geometry on API 28+.
// macOS windows have no cutout, so WindowInsets never carries a non-empty one
// and the framework's own instance is the empty `new DisplayCutout()`. An APP
// can still build one (for its own inset tests, or to feed a WindowInsets it
// constructs), and AOSP's contract is that what it put in comes back out -- so
// the constructors and the Builder now STORE their arguments instead of
// discarding them, and the getters answer from that state. An empty cutout
// still answers exactly as before (all zeros, empty rects), which is what
// WindowInsets.isEmptyCutout reads.
//
// Source: frameworks/base core/java/android/view/DisplayCutout.java (android16-release).
package android.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import android.graphics.Insets;
import android.graphics.Rect;

public class DisplayCutout {
    private static final Rect ZERO_RECT = new Rect();

    private final Insets mSafeInsets;
    private final Insets mWaterfallInsets;
    // AOSP indexes the four bounds by BOUNDS_POSITION_{LEFT,TOP,RIGHT,BOTTOM}
    // = 0..3 and keeps an always-length-4 array with empty rects for the
    // positions that have no cutout.
    private final Rect mBoundLeft, mBoundTop, mBoundRight, mBoundBottom;
    private final android.graphics.Path mCutoutPath;

    public DisplayCutout() {
        this(Insets.NONE, null, null, null, null, Insets.NONE, null);
    }

    /** AOSP DisplayCutout(Rect safeInsets, List&lt;Rect&gt; boundingRects), the
     *  API-28 form, deprecated at API 29. AOSP derives the four positional
     *  bounds from the list by testing each rect against the display edges;
     *  with no display metrics to test against, the rects are kept in the list
     *  form that getBoundingRects() returns and the positional getters answer
     *  from whichever rect matches the corresponding safe inset -- which for
     *  the single-cutout case AOSP itself produces is the same answer. */
    @Deprecated
    public DisplayCutout(Rect safeInsets, List<Rect> boundingRects) {
        mSafeInsets = safeInsets != null
                ? Insets.of(safeInsets.left, safeInsets.top, safeInsets.right, safeInsets.bottom)
                : Insets.NONE;
        mWaterfallInsets = Insets.NONE;
        mCutoutPath = null;
        Rect l = null, t = null, r = null, b = null;
        if (boundingRects != null) {
            for (Rect rect : boundingRects) {
                if (rect == null) continue;
                if (mSafeInsets.left > 0 && l == null)        l = rect;
                else if (mSafeInsets.top > 0 && t == null)    t = rect;
                else if (mSafeInsets.right > 0 && r == null)  r = rect;
                else if (mSafeInsets.bottom > 0 && b == null) b = rect;
            }
        }
        mBoundLeft = l; mBoundTop = t; mBoundRight = r; mBoundBottom = b;
    }

    /** AOSP DisplayCutout(Insets, Rect, Rect, Rect, Rect) (API 29). */
    public DisplayCutout(Insets safeInsets, Rect boundLeft, Rect boundTop,
            Rect boundRight, Rect boundBottom) {
        this(safeInsets, boundLeft, boundTop, boundRight, boundBottom, Insets.NONE, null);
    }

    /** AOSP DisplayCutout(Insets, Rect, Rect, Rect, Rect, Insets) (API 30). */
    public DisplayCutout(Insets safeInsets, Rect boundLeft, Rect boundTop,
            Rect boundRight, Rect boundBottom, Insets waterfallInsets) {
        this(safeInsets, boundLeft, boundTop, boundRight, boundBottom, waterfallInsets, null);
    }

    private DisplayCutout(Insets safeInsets, Rect boundLeft, Rect boundTop,
            Rect boundRight, Rect boundBottom, Insets waterfallInsets,
            android.graphics.Path cutoutPath) {
        mSafeInsets = safeInsets != null ? safeInsets : Insets.NONE;
        mWaterfallInsets = waterfallInsets != null ? waterfallInsets : Insets.NONE;
        mBoundLeft = boundLeft;
        mBoundTop = boundTop;
        mBoundRight = boundRight;
        mBoundBottom = boundBottom;
        mCutoutPath = cutoutPath;
    }

    /** AOSP DisplayCutout.getBoundingRects(): the non-empty bounds only, in
     *  left/top/right/bottom order. */
    public List<Rect> getBoundingRects() {
        List<Rect> out = new ArrayList<Rect>(4);
        for (Rect r : Arrays.asList(mBoundLeft, mBoundTop, mBoundRight, mBoundBottom)) {
            if (r != null && !r.isEmpty()) out.add(new Rect(r));
        }
        return out.isEmpty() ? Collections.<Rect>emptyList() : out;
    }

    /** AOSP DisplayCutout.getBoundingRect{Left,Top,Right,Bottom}() (API 29):
     *  each returns an EMPTY Rect (never null) when there is no cutout on that
     *  edge -- the @NonNull contract callers rely on. */
    public Rect getBoundingRectLeft()   { return mBoundLeft   != null ? new Rect(mBoundLeft)   : new Rect(); }
    public Rect getBoundingRectTop()    { return mBoundTop    != null ? new Rect(mBoundTop)    : new Rect(); }
    public Rect getBoundingRectRight()  { return mBoundRight  != null ? new Rect(mBoundRight)  : new Rect(); }
    public Rect getBoundingRectBottom() { return mBoundBottom != null ? new Rect(mBoundBottom) : new Rect(); }

    public int getSafeInsetTop()    { return mSafeInsets.top; }
    public int getSafeInsetBottom() { return mSafeInsets.bottom; }
    public int getSafeInsetLeft()   { return mSafeInsets.left; }
    public int getSafeInsetRight()  { return mSafeInsets.right; }

    /** AOSP getWaterfallInsets (API 30): the curved-edge (waterfall) insets.
     *  Rectangular windows have none. */
    public Insets getWaterfallInsets() { return mWaterfallInsets; }

    /** AOSP DisplayCutout.getCutoutPath() (API 31): the exact cutout outline,
     *  null when the display has no cutout. */
    public android.graphics.Path getCutoutPath() { return mCutoutPath; }

    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof DisplayCutout)) return false;
        DisplayCutout c = (DisplayCutout) o;
        return mSafeInsets.equals(c.mSafeInsets)
                && mWaterfallInsets.equals(c.mWaterfallInsets)
                && eq(mBoundLeft, c.mBoundLeft) && eq(mBoundTop, c.mBoundTop)
                && eq(mBoundRight, c.mBoundRight) && eq(mBoundBottom, c.mBoundBottom);
    }
    private static boolean eq(Rect a, Rect b) {
        return (a == null ? ZERO_RECT : a).equals(b == null ? ZERO_RECT : b);
    }
    @Override public int hashCode() {
        int h = mSafeInsets.hashCode();
        h = h * 31 + mWaterfallInsets.hashCode();
        h = h * 31 + getBoundingRects().hashCode();
        return h;
    }
    @Override public String toString() {
        return "DisplayCutout{insets=" + mSafeInsets + " waterfall=" + mWaterfallInsets
                + " boundingRect=" + getBoundingRects() + "}";
    }

    /** AOSP DisplayCutout.Builder (API 29). */
    public static final class Builder {
        private Insets mSafeInsets = Insets.NONE;
        private Insets mWaterfallInsets = Insets.NONE;
        private Rect mBoundLeft, mBoundTop, mBoundRight, mBoundBottom;
        private android.graphics.Path mCutoutPath;

        public Builder() { }
        public Builder setSafeInsets(Insets safeInsets) {
            mSafeInsets = safeInsets != null ? safeInsets : Insets.NONE; return this;
        }
        public Builder setWaterfallInsets(Insets waterfallInsets) {
            mWaterfallInsets = waterfallInsets != null ? waterfallInsets : Insets.NONE; return this;
        }
        public Builder setBoundingRectLeft(Rect boundingRect)   { mBoundLeft = boundingRect;   return this; }
        public Builder setBoundingRectTop(Rect boundingRect)    { mBoundTop = boundingRect;    return this; }
        public Builder setBoundingRectRight(Rect boundingRect)  { mBoundRight = boundingRect;  return this; }
        public Builder setBoundingRectBottom(Rect boundingRect) { mBoundBottom = boundingRect; return this; }
        public Builder setCutoutPath(android.graphics.Path path) { mCutoutPath = path; return this; }
        public DisplayCutout build() {
            return new DisplayCutout(mSafeInsets, mBoundLeft, mBoundTop, mBoundRight,
                    mBoundBottom, mWaterfallInsets, mCutoutPath);
        }
    }
}
