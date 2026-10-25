// android.graphics.Region -- 2D region for clipping ops.
//
// v1.0.433: re-backed by java.awt.geom.Area for TRUE (non-rectangular,
// multi-rect) region math. The prior impl tracked only a bounding Rect, so
// op(DIFFERENCE) was a no-op and op(XOR) collapsed to UNION -- Compose
// clip-to-Path and custom-View region clipping silently computed the wrong
// (too-large) region. Area gives correct set algebra + unlocks the missing
// setPath / getBoundaryPath / quickReject / translate / op-overload surface
// (each previously a NoSuchMethodError). Mirrors
// frameworks/base/graphics/java/android/graphics/Region.java.
//
// v1.0.923: the SET ALGEBRA was right and the SHAPE OF THE ANSWER was not.
// A real Region is an SkRegion: an ordered list of horizontal BANDS, each
// holding disjoint x-runs, and that decomposition is what RegionIterator hands
// out, what toString() prints, and what a clip walker consumes. An Area knows
// only contours, so a 100x100 ring answered "two rects: the hole, then the
// outer square" -- the HOLE reported as a POSITIVE rect, which is not merely a
// different decomposition of the same set, it is the wrong set. See
// macapkBands() below. Also fixed here: empty is not "rectangular", set(Rect)
// reports whether the result is non-empty, quickReject/quickContains are
// BOUNDS tests (an exact test rejects a rect lying in a hole and silently
// drops the drawing), toString is SkRegion's, and Region is Parcelable.
package android.graphics;

import java.awt.geom.Area;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

public class Region implements android.os.Parcelable {
    public enum Op {
        DIFFERENCE(0),
        INTERSECT(1),
        UNION(2),
        XOR(3),
        REVERSE_DIFFERENCE(4),
        REPLACE(5);

        public final int nativeInt;
        Op(int v) { this.nativeInt = v; }
    }

    private Area area = new Area();

    public Region() {}
    public Region(Region src) { if (src != null) area = new Area(src.area); }
    public Region(Rect r) { if (r != null) setRect(r.left, r.top, r.right, r.bottom); }
    public Region(int left, int top, int right, int bottom) { setRect(left, top, right, bottom); }

    private void setRect(int l, int t, int r, int b) {
        area = new Area(new Rectangle2D.Float(l, t, Math.max(0, r - l), Math.max(0, b - t)));
    }

    public boolean isEmpty()   { return area.isEmpty(); }
    /** SkRegion::isRect() is "the run head is the RECT sentinel", which an
     *  EMPTY region never is -- its run head is the empty sentinel. Area's
     *  isRectangular() counts an empty shape as rectangular, so an empty region
     *  answered isRect()=1 and any `if (clip.isRect())` fast path took the
     *  wrong branch on an empty clip. */
    public boolean isRect()    { return !area.isEmpty() && area.isRectangular(); }
    public boolean isComplex() { return !area.isEmpty() && !area.isRectangular(); }

    public Rect getBounds() { Rect r = new Rect(); getBounds(r); return r; }
    public boolean getBounds(Rect r) {
        if (r == null) return false;
        java.awt.Rectangle b = area.getBounds();
        r.set(b.x, b.y, b.x + b.width, b.y + b.height);
        return !area.isEmpty();
    }
    public Path getBoundaryPath() { Path p = new Path(); p.macapkAppendShape(area); return p; }
    public boolean getBoundaryPath(Path path) {
        if (path != null) { path.reset(); path.macapkAppendShape(area); }
        return !area.isEmpty();
    }

    public void setEmpty() { area = new Area(); }

    // SkRegion's setters all return "is the result non-empty" (SkRegion::setRect
    // is `return !this->isEmpty();`). Returning an unconditional true made
    // set(emptyRect) look like it had produced something.
    public boolean set(Region r) { if (r != null) { area = new Area(r.area); return !area.isEmpty(); } return false; }
    public boolean set(Rect r)   { if (r != null) { setRect(r.left, r.top, r.right, r.bottom); return !area.isEmpty(); } return false; }
    public boolean set(int l, int t, int r, int b) { setRect(l, t, r, b); return !area.isEmpty(); }
    public boolean setPath(Path path, Region clip) {
        if (path == null) { setEmpty(); return false; }
        Area a = new Area(path.macapkShape());
        if (clip != null) a.intersect(clip.area);
        area = a;
        return !area.isEmpty();
    }

    public boolean contains(int x, int y) { return area.contains(x, y); }
    /** SkRegion::quickContains is only ever true for a RECT region -- it reads
     *  `fRunHead == kRectRunHeadPtr && fBounds.contains(...)`. It is a cheap
     *  conservative test, so a complex region always answers false even when
     *  the rect really is inside it. */
    public boolean quickContains(int l, int t, int r, int b) {
        if (!(l < r && t < b) || !isRect()) return false;
        java.awt.Rectangle bounds = area.getBounds();
        return bounds.x <= l && bounds.y <= t
            && bounds.x + bounds.width >= r && bounds.y + bounds.height >= b;
    }
    public boolean quickContains(Rect r) { return r != null && quickContains(r.left, r.top, r.right, r.bottom); }
    /** ⚠ quickReject is a BOUNDS test, not an exact one: SkRegion::quickReject
     *  is `isEmpty() || rect.isEmpty() || !SkIRect::Intersects(fBounds, rect)`.
     *  Testing the actual geometry is "more accurate" and WRONG -- a rect
     *  sitting in the hole of a ring is not rejected on a device, and rejecting
     *  it makes the caller skip drawing that a real Android would have drawn
     *  (and then clipped). Getting a quick-reject too eager is invisible in
     *  every log and shows up as missing content. */
    public boolean quickReject(int l, int t, int r, int b) {
        if (area.isEmpty() || !(l < r && t < b)) return true;
        java.awt.Rectangle bounds = area.getBounds();
        return !(bounds.x < r && l < bounds.x + bounds.width
              && bounds.y < b && t < bounds.y + bounds.height);
    }
    public boolean quickReject(Rect r) { return r == null || quickReject(r.left, r.top, r.right, r.bottom); }
    public boolean quickReject(Region rgn) {
        if (rgn == null || area.isEmpty() || rgn.area.isEmpty()) return true;
        java.awt.Rectangle b = rgn.area.getBounds();
        return quickReject(b.x, b.y, b.x + b.width, b.y + b.height);
    }

    public void translate(int dx, int dy) {
        area.transform(AffineTransform.getTranslateInstance(dx, dy));
    }
    public void translate(int dx, int dy, Region dst) {
        if (dst == null) { translate(dx, dy); return; }
        dst.area = new Area(area);
        dst.area.transform(AffineTransform.getTranslateInstance(dx, dy));
    }
    public void scale(float scale) { scale(scale, null); }
    public void scale(float scale, Region dst) {
        Area a = new Area(area);
        a.transform(AffineTransform.getScaleInstance(scale, scale));
        if (dst == null) area = a; else dst.area = a;
    }

    public boolean op(Rect r, Op op) { return op(new Region(r), op); }
    public boolean op(int l, int t, int r, int b, Op op) { return op(new Region(l, t, r, b), op); }
    public boolean op(Rect rect, Region region, Op op) { set(rect); return op(region, op); }
    public boolean op(Region region1, Region region2, Op op) { set(region1); return op(region2, op); }
    public boolean op(Region other, Op op) {
        if (other == null) return !area.isEmpty();
        switch (op) {
            case DIFFERENCE:         area.subtract(other.area); break;
            case INTERSECT:          area.intersect(other.area); break;
            case UNION:              area.add(other.area); break;
            case XOR:                area.exclusiveOr(other.area); break;
            case REVERSE_DIFFERENCE: { Area t = new Area(other.area); t.subtract(area); area = t; break; }
            case REPLACE:            area = new Area(other.area); break;
        }
        return !area.isEmpty();
    }
    public boolean union(Rect r) { return op(r, Op.UNION); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Region)) return false;
        return area.equals(((Region) o).area);
    }
    @Override public int hashCode() { return area.getBounds().hashCode(); }

    /** SkRegion::toString: "SkRegion()" when empty, otherwise "SkRegion(" plus
     *  one "(l,t,r,b)" per rect in ITERATION order, then ")". Object's default
     *  toString leaked an identity hash instead, so anything logging a clip
     *  region printed an address. */
    @Override public String toString() {
        StringBuilder sb = new StringBuilder(32).append("SkRegion(");
        for (Rect r : macapkBands()) {
            sb.append('(').append(r.left).append(',').append(r.top).append(',')
              .append(r.right).append(',').append(r.bottom).append(')');
        }
        return sb.append(')').toString();
    }

    // ---------------------------------------------------------------------
    // Parcelable. AOSP declares `public class Region implements Parcelable`;
    // see Rect.java's note for why the `implements` is a class-load-time issue
    // for any app that stores a Region through a Parcelable-typed reference,
    // not merely a missing parcel path. The wire form here is our own (a rect
    // count followed by the band rects) rather than Skia's binary blob -- both
    // ends of any parcel inside Mac.apk are this class.
    // ---------------------------------------------------------------------
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel out, int flags) {
        List<Rect> rects = macapkBands();
        out.writeInt(rects.size());
        for (Rect r : rects) {
            out.writeInt(r.left); out.writeInt(r.top);
            out.writeInt(r.right); out.writeInt(r.bottom);
        }
    }
    public static final android.os.Parcelable.Creator<Region> CREATOR =
        new android.os.Parcelable.Creator<Region>() {
            @Override public Region createFromParcel(android.os.Parcel in) {
                Region rgn = new Region();
                int n = in.readInt();
                for (int i = 0; i < n; i++) {
                    rgn.op(new Rect(in.readInt(), in.readInt(), in.readInt(), in.readInt()),
                           Op.UNION);
                }
                return rgn;
            }
            @Override public Region[] newArray(int size) { return new Region[size]; }
        };

    /** Internal: the AWT area (Canvas.clipRegion / consumers). */
    public java.awt.geom.Area macapkArea() { return area; }

    // =====================================================================
    // THE BAND DECOMPOSITION -- what an SkRegion actually IS
    // =====================================================================
    //
    // SkRegion stores a region as an ordered list of horizontal BANDS; within
    // a band, a list of disjoint x-runs. RegionIterator hands those out
    // top-to-bottom, left-to-right, and toString() prints them. A 100x100
    // square with a 20,20..80,80 hole is FOUR rects:
    //     (0,0,100,20) (0,20,20,80) (80,20,100,80) (0,80,100,100)
    // and never "the hole" -- an Area-contour walk emitted the hole as a
    // positive rect, which is the wrong SET, not just a different partition.
    //
    // Reconstructing the bands from an Area: a Region's coordinates are
    // integers (SkRegion cannot hold anything else), and every region built
    // from rects and rect ops is RECTILINEAR, so the distinct x and y
    // coordinates of the Area's outline form a grid on which coverage is
    // constant per cell. Sample each cell's centre, merge horizontally into
    // runs, then merge vertically adjacent bands with identical runs. That is
    // exact for every rectilinear region.
    //
    // A NON-rectilinear region (only reachable via setPath with a diagonal or
    // curved path) has no such grid: a device scan-converts it to integer
    // scanlines, which can be thousands of bands. Rather than pay that or
    // return a wrong grid, those fall back to the per-contour bounding boxes
    // this class used to return for everything. Documented rather than hidden:
    // it is the one shape of region whose iteration is still approximate.
    List<Rect> macapkBands() {
        final List<Rect> out = new ArrayList<>();
        if (area.isEmpty()) return out;

        final java.util.TreeSet<Integer> xsSet = new java.util.TreeSet<>();
        final java.util.TreeSet<Integer> ysSet = new java.util.TreeSet<>();
        if (!collectGrid(xsSet, ysSet)) return contourBoxes();
        if (xsSet.size() < 2 || ysSet.size() < 2) return out;

        final int[] xs = toArray(xsSet);
        final int[] ys = toArray(ysSet);

        // runsPerBand[j] = the covered x-runs of band [ys[j], ys[j+1]).
        int[] prev = null;
        final List<int[]> bandRuns = new ArrayList<>();
        final List<int[]> bandSpan = new ArrayList<>();
        for (int j = 0; j + 1 < ys.length; j++) {
            final double midY = (ys[j] + (double) ys[j + 1]) * 0.5;
            final List<Integer> runs = new ArrayList<>();
            int runStart = Integer.MIN_VALUE;
            for (int k = 0; k + 1 < xs.length; k++) {
                final double midX = (xs[k] + (double) xs[k + 1]) * 0.5;
                final boolean in = area.contains(midX, midY);
                if (in && runStart == Integer.MIN_VALUE) runStart = xs[k];
                if (!in && runStart != Integer.MIN_VALUE) {
                    runs.add(runStart); runs.add(xs[k]); runStart = Integer.MIN_VALUE;
                }
            }
            if (runStart != Integer.MIN_VALUE) { runs.add(runStart); runs.add(xs[xs.length - 1]); }
            final int[] r = new int[runs.size()];
            for (int i = 0; i < r.length; i++) r[i] = runs.get(i);
            if (r.length == 0) { prev = null; continue; }
            if (prev != null && java.util.Arrays.equals(prev, r)) {
                bandSpan.get(bandSpan.size() - 1)[1] = ys[j + 1];   // extend the band down
            } else {
                bandRuns.add(r);
                bandSpan.add(new int[] { ys[j], ys[j + 1] });
            }
            prev = r;
        }

        for (int j = 0; j < bandRuns.size(); j++) {
            final int[] r = bandRuns.get(j);
            final int[] span = bandSpan.get(j);
            for (int i = 0; i + 1 < r.length; i += 2) {
                out.add(new Rect(r[i], span[0], r[i + 1], span[1]));
            }
        }
        return out;
    }

    /** Gather the distinct integer x and y coordinates of the outline.
     *  Returns false when the outline is not rectilinear (a curve, or a line
     *  that is neither horizontal nor vertical), in which case no such grid
     *  exists. Coordinates are rounded because a Region is integral by
     *  definition and Area math can leave a representation epsilon behind. */
    private boolean collectGrid(java.util.TreeSet<Integer> xs, java.util.TreeSet<Integer> ys) {
        final double[] c = new double[6];
        double curX = 0, curY = 0, startX = 0, startY = 0;
        boolean have = false;
        for (PathIterator it = area.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO:
                    curX = startX = c[0]; curY = startY = c[1];
                    xs.add((int) Math.round(curX)); ys.add((int) Math.round(curY));
                    have = true;
                    break;
                case PathIterator.SEG_LINETO:
                    if (!have) return false;
                    if (!nearlyEqual(c[0], curX) && !nearlyEqual(c[1], curY)) return false;  // diagonal
                    curX = c[0]; curY = c[1];
                    xs.add((int) Math.round(curX)); ys.add((int) Math.round(curY));
                    break;
                case PathIterator.SEG_CLOSE:
                    if (have && !nearlyEqual(startX, curX) && !nearlyEqual(startY, curY)) return false;
                    curX = startX; curY = startY;
                    break;
                default:
                    return false;   // QUADTO / CUBICTO: not rectilinear
            }
        }
        return have;
    }
    private static boolean nearlyEqual(double a, double b) { return Math.abs(a - b) < 1e-6; }
    private static int[] toArray(java.util.TreeSet<Integer> s) {
        final int[] a = new int[s.size()];
        int i = 0;
        for (Integer v : s) a[i++] = v;
        return a;
    }

    /** Fallback for a non-rectilinear region: one integer bounding box per
     *  closed contour. Approximate, and the behaviour this class had for every
     *  region before the band decomposition existed. */
    private List<Rect> contourBoxes() {
        final List<Rect> out = new ArrayList<>();
        final double[] c = new double[6];
        double minX = 0, minY = 0, maxX = 0, maxY = 0;
        boolean have = false;
        for (PathIterator it = area.getPathIterator(null); !it.isDone(); it.next()) {
            final int type = it.currentSegment(c);
            final int n;
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    if (have) emitBox(out, minX, minY, maxX, maxY);
                    minX = maxX = c[0]; minY = maxY = c[1]; have = true;
                    continue;
                case PathIterator.SEG_LINETO:  n = 1; break;
                case PathIterator.SEG_QUADTO:  n = 2; break;
                case PathIterator.SEG_CUBICTO: n = 3; break;
                default:
                    if (have) { emitBox(out, minX, minY, maxX, maxY); have = false; }
                    continue;
            }
            for (int i = 0; i < n; i++) {
                minX = Math.min(minX, c[i * 2]); maxX = Math.max(maxX, c[i * 2]);
                minY = Math.min(minY, c[i * 2 + 1]); maxY = Math.max(maxY, c[i * 2 + 1]);
            }
        }
        if (have) emitBox(out, minX, minY, maxX, maxY);
        return out;
    }
    private static void emitBox(List<Rect> out, double minX, double minY, double maxX, double maxY) {
        final int l = (int) Math.floor(minX), t = (int) Math.floor(minY);
        final int r = (int) Math.ceil(maxX),  b = (int) Math.ceil(maxY);
        if (r > l && b > t) out.add(new Rect(l, t, r, b));
    }
}
