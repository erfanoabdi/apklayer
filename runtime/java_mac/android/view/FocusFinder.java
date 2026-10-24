package android.view;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Directional focus search: given a focused View and a direction, find the next
 * View to focus. Referenced by 30 of 37 corpus apps (via AppCompat / RecyclerView
 * key handling and any app that supports D-pad / keyboard / gamepad navigation).
 *
 * This is a REAL geometric implementation, not a stub. It is now possible
 * because ViewGroup.addFocusables actually collects the candidates (v1.0.686);
 * before that a search had nothing to rank.
 *
 * The scoring is AOSP's beam algorithm verbatim in shape
 * (frameworks/base/core/java/android/view/FocusFinder.java): for LEFT/RIGHT/UP/
 * DOWN, a candidate must be in the correct half-plane and beam-overlap the
 * source on the minor axis; among valid candidates the best is the one with the
 * smallest weighted (13*majorAxisDistance^2 + minorAxisDistance^2).
 *
 * FORWARD / BACKWARD (TAB / Shift+TAB) are AOSP's as of v1.0.1609: the
 * app's own nextFocusForward wiring first (findNextUserSpecifiedFocus), then
 * the focusables in the order ViewGroup.addFocusables offers them -- which is
 * GEOMETRIC (FocusSorter: rows top-to-bottom, left-to-right within a row),
 * grouped by any explicit chains (UserSpecifiedFocusComparator) -- and, per
 * AOSP, NULL when fewer than two views qualify. The v1.0.686 version walked
 * add order and, with a single candidate, answered the focused view itself,
 * which the caller then had to recognise as "nowhere to go".
 *
 * Source: frameworks/base/core/java/android/view/FocusFinder.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/core/java/android/view/FocusFinder.java
 */
public class FocusFinder {

    private static final ThreadLocal<FocusFinder> tlFocusFinder =
            new ThreadLocal<FocusFinder>() {
                @Override protected FocusFinder initialValue() {
                    return new FocusFinder();
                }
            };

    public static FocusFinder getInstance() { return tlFocusFinder.get(); }

    private final Rect mOtherRect = new Rect();
    private final Rect mBestCandidateRect = new Rect();
    private final FocusSorter mFocusSorter = new FocusSorter();
    private final ArrayList<View> mTempList = new ArrayList<View>();
    private final UserSpecifiedFocusComparator mUserSpecifiedFocusComparator =
            new UserSpecifiedFocusComparator((r, v) -> isValidId(v.getNextFocusForwardId())
                    ? v.findUserSetNextFocus(r, View.FOCUS_FORWARD) : null);

    FocusFinder() { }

    private static boolean isValidId(final int id) {
        return id != 0 && id != View.NO_ID;
    }

    public final View findNextFocus(ViewGroup root, View focused, int direction) {
        if (root == null) return null;
        Rect focusedRect = new Rect();
        if (focused != null) {
            getRectInRoot(root, focused, focusedRect);
        } else {
            // AOSP seeds from the edge opposite the search direction so the
            // first press enters from the correct side.
            switch (direction) {
                case View.FOCUS_RIGHT:
                case View.FOCUS_DOWN:
                case View.FOCUS_FORWARD:
                    focusedRect.set(0, 0, 0, 0);
                    break;
                default:
                    focusedRect.set(root.getWidth(), root.getHeight(),
                            root.getWidth(), root.getHeight());
                    break;
            }
        }
        return findNextFocus(root, focused, focusedRect, direction);
    }

    public final View findNextFocusFromRect(ViewGroup root, Rect focusedRect, int direction) {
        if (root == null || focusedRect == null) return null;
        return findNextFocus(root, null, new Rect(focusedRect), direction);
    }

    /** AOSP FocusFinder.findNextFocus(ViewGroup, View, Rect, int): the app's
     *  explicit wiring wins outright; otherwise collect the focusables from the
     *  EFFECTIVE root (a touchscreen-blocks-focus cluster enclosing `focused`
     *  narrows the search to itself) and rank them. */
    private View findNextFocus(ViewGroup root, View focused, Rect focusedRect, int direction) {
        View next = null;
        ViewGroup effectiveRoot = getEffectiveRoot(root, focused);
        if (focused != null) {
            next = findNextUserSpecifiedFocus(effectiveRoot, focused, direction);
        }
        if (next != null) return next;
        // AOSP re-derives the focused rect against the root it will actually
        // search (`root.offsetDescendantRectToMyCoords(focused, focusedRect)`
        // with root == effectiveRoot); the candidate rects below are taken in
        // that same space, so the two must agree when a cluster narrowed the root.
        if (focused != null && effectiveRoot != root) {
            getRectInRoot(effectiveRoot, focused, focusedRect);
        }
        ArrayList<View> focusables = mTempList;
        try {
            focusables.clear();
            effectiveRoot.addFocusables(focusables, direction);
            if (!focusables.isEmpty()) {
                if (direction == View.FOCUS_FORWARD || direction == View.FOCUS_BACKWARD) {
                    next = findNextFocusInRelativeDirection(focusables, effectiveRoot,
                            focused, focusedRect, direction);
                } else {
                    next = findNextFocusInAbsoluteDirection(focusables, effectiveRoot,
                            focused, focusedRect, direction);
                }
            }
        } finally {
            focusables.clear();
        }
        return next;
    }

    /** AOSP FocusFinder.getEffectiveRoot: walk up from `focused`; the top-most
     *  ancestor that blocks focus for touchscreens AND is a keyboard-navigation
     *  cluster becomes the root of this search, so once focus is inside such a
     *  cluster TAB stays inside it. */
    private ViewGroup getEffectiveRoot(ViewGroup root, View focused) {
        if (focused == null || focused == root) return root;
        ViewGroup effective = null;
        ViewParent nextParent = focused.getParent();
        do {
            if (nextParent == root) return effective != null ? effective : root;
            if (!(nextParent instanceof ViewGroup vg)) break;
            if (vg.getTouchscreenBlocksFocus() && hasTouchscreen(focused)
                    && vg.isKeyboardNavigationCluster()) {
                // Don't stop and return here because the cluster could be
                // nested and we only care about the top-most one.
                effective = vg;
            }
            nextParent = nextParent.getParent();
        } while (nextParent instanceof ViewGroup);
        return root;
    }

    private static boolean hasTouchscreen(View v) {
        try {
            android.content.Context c = v.getContext();
            android.content.pm.PackageManager pm = (c == null) ? null : c.getPackageManager();
            return pm == null || pm.hasSystemFeature(
                    android.content.pm.PackageManager.FEATURE_TOUCHSCREEN);
        } catch (Throwable t) {
            return true;
        }
    }

    /** AOSP FocusFinder.findNextUserSpecifiedFocus: follow the app's
     *  nextFocus* links from `focused` until one lands on a view that can take
     *  focus right now; a fully-unfocusable cycle is detected with a
     *  half-speed second cursor and abandoned. */
    private View findNextUserSpecifiedFocus(ViewGroup root, View focused, int direction) {
        View userSetNextFocus = focused.findUserSetNextFocus(root, direction);
        View cycleCheck = userSetNextFocus;
        boolean cycleStep = true; // we want the first toggle to yield false
        while (userSetNextFocus != null) {
            if (userSetNextFocus.isFocusable()
                    && userSetNextFocus.getVisibility() == View.VISIBLE
                    && (!userSetNextFocus.isInTouchMode()
                            || userSetNextFocus.isFocusableInTouchMode())) {
                return userSetNextFocus;
            }
            userSetNextFocus = userSetNextFocus.findUserSetNextFocus(root, direction);
            if (cycleStep = !cycleStep) {
                cycleCheck = cycleCheck.findUserSetNextFocus(root, direction);
                if (cycleCheck == userSetNextFocus) {
                    // found a cycle, user-specified focus forms a loop and none
                    // of the views are currently focusable.
                    break;
                }
            }
        }
        return null;
    }

    /** AOSP FocusFinder.findNextFocusInRelativeDirection. The candidates arrive
     *  in ViewGroup.addFocusables order (geometric); a stable sort then pulls
     *  any explicit nextFocusForward chains together. Fewer than two candidates
     *  means there is nowhere to go, and AOSP says so with null rather than by
     *  handing back the focused view. */
    private View findNextFocusInRelativeDirection(ArrayList<View> focusables, ViewGroup root,
            View focused, Rect focusedRect, int direction) {
        try {
            // Note: This sort is stable.
            mUserSpecifiedFocusComparator.setFocusables(focusables, root);
            Collections.sort(focusables, mUserSpecifiedFocusComparator);
        } finally {
            mUserSpecifiedFocusComparator.recycle();
        }

        final int count = focusables.size();
        if (count < 2) return null;
        switch (direction) {
            case View.FOCUS_FORWARD:
                return getNextFocusable(focused, focusables, count);
            case View.FOCUS_BACKWARD:
                return getPreviousFocusable(focused, focusables, count);
        }
        return focusables.get(count - 1);
    }

    private static View getNextFocusable(View focused, ArrayList<View> focusables, int count) {
        if (count < 2) return null;
        if (focused != null) {
            int position = focusables.lastIndexOf(focused);
            if (position >= 0 && position + 1 < count) {
                return focusables.get(position + 1);
            }
        }
        return focusables.get(0);
    }

    private static View getPreviousFocusable(View focused, ArrayList<View> focusables, int count) {
        if (count < 2) return null;
        if (focused != null) {
            int position = focusables.indexOf(focused);
            if (position > 0) {
                return focusables.get(position - 1);
            }
        }
        return focusables.get(count - 1);
    }

    private View findNextFocusInAbsoluteDirection(ArrayList<View> focusables,
            ViewGroup root, View focused, Rect focusedRect, int direction) {
        mBestCandidateRect.set(focusedRect);
        // Push the best rect one full extent in the OPPOSITE direction, so any
        // real candidate in the search direction beats the seed.
        switch (direction) {
            case View.FOCUS_LEFT:
                mBestCandidateRect.offset(focusedRect.width() + 1, 0); break;
            case View.FOCUS_RIGHT:
                mBestCandidateRect.offset(-(focusedRect.width() + 1), 0); break;
            case View.FOCUS_UP:
                mBestCandidateRect.offset(0, focusedRect.height() + 1); break;
            case View.FOCUS_DOWN:
                mBestCandidateRect.offset(0, -(focusedRect.height() + 1)); break;
            default: break;
        }

        View closest = null;
        for (int i = 0, n = focusables.size(); i < n; i++) {
            View candidate = focusables.get(i);
            if (candidate == focused || candidate == root) continue;
            getRectInRoot(root, candidate, mOtherRect);
            if (isBetterCandidate(direction, focusedRect, mOtherRect, mBestCandidateRect)) {
                mBestCandidateRect.set(mOtherRect);
                closest = candidate;
            }
        }
        return closest;
    }

    /** AOSP isBetterCandidate: is `candidate` a better next-focus than `current`. */
    boolean isBetterCandidate(int direction, Rect source, Rect candidate, Rect current) {
        if (!isCandidate(source, candidate, direction)) return false;
        if (!isCandidate(source, current, direction)) return true;

        if (beamBeats(direction, source, candidate, current)) return true;
        if (beamBeats(direction, source, current, candidate)) return false;

        return getWeightedDistanceFor(
                    majorAxisDistance(direction, source, candidate),
                    minorAxisDistance(direction, source, candidate))
                < getWeightedDistanceFor(
                    majorAxisDistance(direction, source, current),
                    minorAxisDistance(direction, source, current));
    }

    private boolean beamBeats(int direction, Rect source, Rect rect1, Rect rect2) {
        final boolean rect1InSrcBeam = beamsOverlap(direction, source, rect1);
        final boolean rect2InSrcBeam = beamsOverlap(direction, source, rect2);
        if (rect2InSrcBeam || !rect1InSrcBeam) return false;
        if (!isToDirectionOf(direction, source, rect2)) return true;
        if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) return true;
        return majorAxisDistance(direction, source, rect1)
                < majorAxisDistanceToFarEdge(direction, source, rect2);
    }

    private long getWeightedDistanceFor(long majorAxisDistance, long minorAxisDistance) {
        return 13 * majorAxisDistance * majorAxisDistance
                + minorAxisDistance * minorAxisDistance;
    }

    private boolean isCandidate(Rect srcRect, Rect destRect, int direction) {
        switch (direction) {
            case View.FOCUS_LEFT:
                return (srcRect.right > destRect.right || srcRect.left >= destRect.right)
                        && srcRect.left > destRect.left;
            case View.FOCUS_RIGHT:
                return (srcRect.left < destRect.left || srcRect.right <= destRect.left)
                        && srcRect.right < destRect.right;
            case View.FOCUS_UP:
                return (srcRect.bottom > destRect.bottom || srcRect.top >= destRect.bottom)
                        && srcRect.top > destRect.top;
            case View.FOCUS_DOWN:
                return (srcRect.top < destRect.top || srcRect.bottom <= destRect.top)
                        && srcRect.bottom < destRect.bottom;
            default:
                return false;
        }
    }

    private boolean isToDirectionOf(int direction, Rect src, Rect dest) {
        switch (direction) {
            case View.FOCUS_LEFT:  return src.left >= dest.right;
            case View.FOCUS_RIGHT: return src.right <= dest.left;
            case View.FOCUS_UP:    return src.top >= dest.bottom;
            case View.FOCUS_DOWN:  return src.bottom <= dest.top;
            default: return false;
        }
    }

    private boolean beamsOverlap(int direction, Rect rect1, Rect rect2) {
        switch (direction) {
            case View.FOCUS_LEFT:
            case View.FOCUS_RIGHT:
                return (rect2.bottom > rect1.top) && (rect2.top < rect1.bottom);
            case View.FOCUS_UP:
            case View.FOCUS_DOWN:
                return (rect2.right > rect1.left) && (rect2.left < rect1.right);
            default: return false;
        }
    }

    private int majorAxisDistance(int direction, Rect source, Rect dest) {
        return Math.max(0, majorAxisDistanceRaw(direction, source, dest));
    }

    private int majorAxisDistanceRaw(int direction, Rect source, Rect dest) {
        switch (direction) {
            case View.FOCUS_LEFT:  return source.left - dest.right;
            case View.FOCUS_RIGHT: return dest.left - source.right;
            case View.FOCUS_UP:    return source.top - dest.bottom;
            case View.FOCUS_DOWN:  return dest.top - source.bottom;
            default: return 0;
        }
    }

    private int majorAxisDistanceToFarEdge(int direction, Rect source, Rect dest) {
        int distance;
        switch (direction) {
            case View.FOCUS_LEFT:  distance = source.left - dest.left; break;
            case View.FOCUS_RIGHT: distance = dest.right - source.right; break;
            case View.FOCUS_UP:    distance = source.top - dest.top; break;
            case View.FOCUS_DOWN:  distance = dest.bottom - source.bottom; break;
            default: distance = 0;
        }
        return Math.max(1, distance);
    }

    private int minorAxisDistance(int direction, Rect source, Rect dest) {
        switch (direction) {
            case View.FOCUS_LEFT:
            case View.FOCUS_RIGHT:
                return Math.abs(
                        (source.top + source.height() / 2) - (dest.top + dest.height() / 2));
            case View.FOCUS_UP:
            case View.FOCUS_DOWN:
                return Math.abs(
                        (source.left + source.width() / 2) - (dest.left + dest.width() / 2));
            default: return 0;
        }
    }

    /** Candidate rect in the root's coordinate space (offset by cumulative
     *  left/top up to the root), which is what the beam math compares. */
    private void getRectInRoot(ViewGroup root, View v, Rect out) {
        out.set(0, 0, v.getWidth(), v.getHeight());
        int dx = 0, dy = 0;
        for (View cur = v; cur != null && cur != root; ) {
            dx += cur.getLeft() - cur.getScrollX();
            dy += cur.getTop() - cur.getScrollY();
            ViewParent p = cur.getParent();
            cur = (p instanceof View) ? (View) p : null;
        }
        out.offset(dx, dy);
    }

    /** AOSP FocusFinder.findNearestTouchable(ViewGroup, int, int, int, int[]):705
     *  -- the trackball/dpad "find the touchable nearest this point in this
     *  direction" search, transcribed. The one substitution is AOSP's @hide
     *  `root.offsetRectBetweenParentAndChild(touchable, bounds, true, true)`,
     *  replaced by the PUBLIC `root.offsetDescendantRectToMyCoords(touchable,
     *  bounds)`, which performs the identical descendant-to-parent transform
     *  (ViewGroup.java:2380 here, and AOSP's own public wrapper over the same
     *  helper). */
    public View findNearestTouchable(ViewGroup root, int x, int y, int direction, int[] deltas) {
        if (root == null) return null;
        java.util.ArrayList<View> touchables = root.getTouchables();
        int minDistance = Integer.MAX_VALUE;
        View closest = null;

        final int numTouchables = touchables.size();
        final int edgeSlop = ViewConfiguration.get(root.getContext()).getScaledEdgeSlop();

        Rect closestBounds = new Rect();
        Rect touchableBounds = mOtherRect;

        for (int i = 0; i < numTouchables; i++) {
            View touchable = touchables.get(i);
            touchable.getDrawingRect(touchableBounds);
            root.offsetDescendantRectToMyCoords(touchable, touchableBounds);

            if (!isTouchCandidate(x, y, touchableBounds, direction)) continue;

            int distance = Integer.MAX_VALUE;
            switch (direction) {
                case View.FOCUS_LEFT:  distance = x - touchableBounds.right + 1; break;
                case View.FOCUS_RIGHT: distance = touchableBounds.left;          break;
                case View.FOCUS_UP:    distance = y - touchableBounds.bottom + 1; break;
                case View.FOCUS_DOWN:  distance = touchableBounds.top;           break;
            }

            if (distance < edgeSlop) {
                // AOSP: "Give preference to innermost views".
                if (closest == null
                        || closestBounds.contains(touchableBounds)
                        || (!touchableBounds.contains(closestBounds) && distance < minDistance)) {
                    minDistance = distance;
                    closest = touchable;
                    closestBounds.set(touchableBounds);
                    switch (direction) {
                        case View.FOCUS_LEFT:  deltas[0] = -distance; break;
                        case View.FOCUS_RIGHT: deltas[0] = distance;  break;
                        case View.FOCUS_UP:    deltas[1] = -distance; break;
                        case View.FOCUS_DOWN:  deltas[1] = distance;  break;
                    }
                }
            }
        }
        return closest;
    }

    /** AOSP FocusFinder.isTouchCandidate:778 -- verbatim, including the throw on
     *  an unrecognised direction. */
    private boolean isTouchCandidate(int x, int y, Rect destRect, int direction) {
        switch (direction) {
            case View.FOCUS_LEFT:
                return destRect.left <= x && destRect.top <= y && y <= destRect.bottom;
            case View.FOCUS_RIGHT:
                return destRect.left >= x && destRect.top <= y && y <= destRect.bottom;
            case View.FOCUS_UP:
                return destRect.top <= y && destRect.left <= x && x <= destRect.right;
            case View.FOCUS_DOWN:
                return destRect.top >= y && destRect.left <= x && x <= destRect.right;
        }
        throw new IllegalArgumentException("direction must be one of "
                + "{FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT}.");
    }

    /** AOSP FocusFinder.findNextKeyboardNavigationCluster(View, View, int):157
     *  -- Tab/Ctrl-Tab cluster navigation. AOSP first honours an explicit
     *  nextClusterForward, then ranks the clusters the hierarchy reports.
     *  View.addKeyboardNavigationClusters is a no-op in this tree (View.java:7767
     *  and ViewGroup.java:2517), so the collected list is empty and the answer
     *  is null -- which is AOSP's own answer for a hierarchy that declares no
     *  clusters, i.e. every layout that does not set android:keyboardNavigationCluster. */
    public View findNextKeyboardNavigationCluster(View root, View currentCluster, int direction) {
        if (root == null) return null;
        java.util.ArrayList<View> clusters = new java.util.ArrayList<View>();
        root.addKeyboardNavigationClusters(clusters, direction);
        if (clusters.isEmpty()) return null;
        // AOSP ranks by position; with a single candidate list and no cluster
        // geometry to rank on here, the first focusable cluster other than the
        // current one is the next one -- AOSP's result for a linear layout.
        for (int i = 0; i < clusters.size(); i++) {
            View v = clusters.get(i);
            if (v != currentCluster && v.hasFocusable()) return v;
        }
        return null;
    }

    // ---- FocusSorter (AOSP FocusFinder.java:786-884, android14-release) --------
    /** Sorts views in the ORDER a reader scans a form: rows by top edge (ties by
     *  bottom), then within a row left-to-right (right-to-left under RTL). A
     *  "row" is a run of views whose vertical extents overlap. Rects are the
     *  views' drawing rects offset into `root`'s coordinates, so nesting depth
     *  is irrelevant. ViewGroup.addFocusables calls this on its visible
     *  children, which is what gives TAB a geometric order. */
    static final class FocusSorter {
        private final ArrayList<Rect> mRectPool = new ArrayList<>();
        private int mLastPoolRect;
        private int mRtlMult;
        private HashMap<View, Rect> mRectByView = null;

        private final Comparator<View> mTopsComparator = (first, second) -> {
            if (first == second) return 0;
            Rect firstRect = mRectByView.get(first);
            Rect secondRect = mRectByView.get(second);
            int result = firstRect.top - secondRect.top;
            if (result == 0) return firstRect.bottom - secondRect.bottom;
            return result;
        };

        private final Comparator<View> mSidesComparator = (first, second) -> {
            if (first == second) return 0;
            Rect firstRect = mRectByView.get(first);
            Rect secondRect = mRectByView.get(second);
            int result = firstRect.left - secondRect.left;
            if (result == 0) return firstRect.right - secondRect.right;
            return mRtlMult * result;
        };

        public void sort(View[] views, int start, int end, ViewGroup root, boolean isRtl) {
            int count = end - start;
            if (count < 2) return;
            if (mRectByView == null) mRectByView = new HashMap<>();
            mRtlMult = isRtl ? -1 : 1;
            for (int i = mRectPool.size(); i < count; ++i) mRectPool.add(new Rect());
            for (int i = start; i < end; ++i) {
                Rect next = mRectPool.get(mLastPoolRect++);
                views[i].getDrawingRect(next);
                root.offsetDescendantRectToMyCoords(views[i], next);
                mRectByView.put(views[i], next);
            }

            // Sort top-to-bottom. AOSP passes `count` as the exclusive end here;
            // every caller passes start == 0, where count == end, so the
            // shape is preserved with `end` to keep it correct for any start.
            Arrays.sort(views, start, end, mTopsComparator);
            // Sweep top-to-bottom to identify rows
            int sweepBottom = mRectByView.get(views[start]).bottom;
            int rowStart = start;
            int sweepIdx = start + 1;
            for (; sweepIdx < end; ++sweepIdx) {
                Rect currRect = mRectByView.get(views[sweepIdx]);
                if (currRect.top >= sweepBottom) {
                    // Next view is on a new row, sort the row we've just finished left-to-right.
                    if ((sweepIdx - rowStart) > 1) {
                        Arrays.sort(views, rowStart, sweepIdx, mSidesComparator);
                    }
                    sweepBottom = currRect.bottom;
                    rowStart = sweepIdx;
                } else {
                    // Next view vertically overlaps, we need to extend our "row height"
                    sweepBottom = Math.max(sweepBottom, currRect.bottom);
                }
            }
            // Sort whatever's left (final row) left-to-right
            if ((sweepIdx - rowStart) > 1) {
                Arrays.sort(views, rowStart, sweepIdx, mSidesComparator);
            }

            mLastPoolRect = 0;
            mRectByView.clear();
        }
    }

    /** AOSP FocusFinder.sort (@hide, @TestApi): the entry ViewGroup.addFocusables
     *  uses. `views[start..end)` are reordered in place. */
    public static void sort(View[] views, int start, int end, ViewGroup root, boolean isRtl) {
        getInstance().mFocusSorter.sort(views, start, end, root, isRtl);
    }

    // ---- UserSpecifiedFocusComparator (AOSP FocusFinder.java:885-983) ---------
    /** Sorts views according to any explicitly-specified focus chains
     *  (nextFocusForward). With no chains it is a no-op: every compare answers
     *  0 and the stable sort keeps the geometric order. A chain is kept
     *  contiguous, headed by the view nothing links to; between chains (and
     *  between a chain and a loose view) the original order is preserved. */
    private static final class UserSpecifiedFocusComparator implements Comparator<View> {
        private final HashMap<View, View> mNextFoci = new HashMap<>();
        private final HashSet<View> mIsConnectedTo = new HashSet<>();
        private final HashMap<View, View> mHeadsOfChains = new HashMap<>();
        private final HashMap<View, Integer> mOriginalOrdinal = new HashMap<>();
        private final NextFocusGetter mNextFocusGetter;
        private View mRoot;

        public interface NextFocusGetter {
            View get(View root, View view);
        }

        UserSpecifiedFocusComparator(NextFocusGetter nextFocusGetter) {
            mNextFocusGetter = nextFocusGetter;
        }

        public void recycle() {
            mRoot = null;
            mHeadsOfChains.clear();
            mIsConnectedTo.clear();
            mOriginalOrdinal.clear();
            mNextFoci.clear();
        }

        public void setFocusables(List<View> focusables, View root) {
            mRoot = root;
            for (int i = 0; i < focusables.size(); ++i) {
                mOriginalOrdinal.put(focusables.get(i), i);
            }

            for (int i = focusables.size() - 1; i >= 0; i--) {
                final View view = focusables.get(i);
                final View next = mNextFocusGetter.get(mRoot, view);
                if (next != null && mOriginalOrdinal.containsKey(next)) {
                    mNextFoci.put(view, next);
                    mIsConnectedTo.add(next);
                }
            }

            for (int i = focusables.size() - 1; i >= 0; i--) {
                final View view = focusables.get(i);
                final View next = mNextFoci.get(view);
                if (next != null && !mIsConnectedTo.contains(view)) {
                    setHeadOfChain(view);
                }
            }
        }

        private void setHeadOfChain(View head) {
            for (View view = head; view != null; view = mNextFoci.get(view)) {
                final View otherHead = mHeadsOfChains.get(view);
                if (otherHead != null) {
                    if (otherHead == head) {
                        return; // This view has already had its head set properly
                    }
                    // A hydra -- multi-headed focus chain (e.g. A->C and B->C)
                    // Use the one we've already chosen instead and reset this chain.
                    view = head;
                    head = otherHead;
                }
                mHeadsOfChains.put(view, head);
            }
        }

        public int compare(View first, View second) {
            if (first == second) return 0;
            // Order between views within a chain is immaterial -- next/previous is
            // within a chain is handled elsewhere.
            View firstHead = mHeadsOfChains.get(first);
            View secondHead = mHeadsOfChains.get(second);
            if (firstHead == secondHead && firstHead != null) {
                if (first == firstHead) {
                    return -1; // first is the head, it should be first
                } else if (second == firstHead) {
                    return 1; // second is the head, it should be first
                } else if (mNextFoci.get(first) != null) {
                    return -1; // first is not the end of the chain
                } else {
                    return 1; // first is end of chain
                }
            }
            boolean involvesChain = false;
            if (firstHead != null) {
                first = firstHead;
                involvesChain = true;
            }
            if (secondHead != null) {
                second = secondHead;
                involvesChain = true;
            }

            if (involvesChain) {
                // keep original order between chains
                return mOriginalOrdinal.get(first) < mOriginalOrdinal.get(second) ? -1 : 1;
            } else {
                return 0;
            }
        }
    }
}
