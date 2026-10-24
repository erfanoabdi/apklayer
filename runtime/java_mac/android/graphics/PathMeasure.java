// android.graphics.PathMeasure -- walks a Path one contour at a time,
// exposing arc-length, position+tangent at a distance, a transform at a
// distance, and sub-segment extraction.
//
// Real Android backs this with Skia's SkPathMeasure, which flattens each
// contour into a sequence of line segments and integrates arc-length over
// them. We mirror that exactly with java.awt: Path.macapkShape() gives us
// the AWT Shape, and PathIterator with a flattening tolerance turns every
// curve into line segments. Each contour (SEG_MOVETO boundary) becomes a
// polyline with a cumulative arc-length table; getPosTan / getMatrix /
// getSegment all walk that polyline.
//
// Why this matters: Compose's trim-path and the Material progress
// indicators animate by calling getLength() once and then
// getSegment()/getPosTan() every frame as the progress fraction sweeps.
// A stub here freezes animated vector content; a correct impl unblocks it.
package android.graphics;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.util.ArrayList;

public class PathMeasure {
    // Matrix-flag bits, matching AOSP. POSITION adds the translate component;
    // TANGENT adds the rotate component. They can be OR'd together.
    public static final int POSITION_MATRIX_FLAG = 1;
    public static final int TANGENT_MATRIX_FLAG  = 2;

    // Flattening tolerance handed to PathIterator. Smaller = more segments =
    // better length/position accuracy at higher cost. 0.5px matches the
    // requested tolerance and is plenty for on-screen animation.
    private static final float FLATNESS = 0.5f;

    // One flattened contour: parallel arrays of point coords (xs[i],ys[i])
    // and the cumulative arc-length reaching each point (lengths[i], with
    // lengths[0] == 0). isClosed records whether the source contour was
    // explicitly closed (SEG_CLOSE) or forceClosed was requested.
    private static final class Contour {
        float[] xs;
        float[] ys;
        float[] lengths; // cumulative; lengths[count-1] == total length
        int count;       // number of points
        boolean closed;
    }

    private final ArrayList<Contour> contours = new ArrayList<>();
    private int current = 0;   // index into contours of the active contour
    private boolean forceClosed = false;

    public PathMeasure() {}

    public PathMeasure(Path path, boolean forceClosed) {
        setPath(path, forceClosed);
    }

    public void setPath(Path path, boolean forceClosed) {
        this.forceClosed = forceClosed;
        contours.clear();
        current = 0;
        if (path == null) return;
        Shape shape = path.macapkShape();
        if (shape == null) return;

        // Flatten with an identity transform; PathIterator subdivides curves
        // to within FLATNESS, so we only ever see SEG_MOVETO / SEG_LINETO /
        // SEG_CLOSE.
        PathIterator it = shape.getPathIterator((AffineTransform) null, FLATNESS);
        float[] seg = new float[6];

        ArrayList<Float> px = new ArrayList<>();
        ArrayList<Float> py = new ArrayList<>();
        float startX = 0, startY = 0; // first point of the current contour
        boolean haveContour = false;

        while (!it.isDone()) {
            int type = it.currentSegment(seg);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    // A new sub-path begins; flush whatever we accumulated.
                    flush(px, py, false);
                    px.clear(); py.clear();
                    startX = seg[0]; startY = seg[1];
                    px.add(startX); py.add(startY);
                    haveContour = true;
                    break;
                case PathIterator.SEG_LINETO:
                    if (!haveContour) {
                        // Defensive: a LINETO with no preceding MOVETO. Treat
                        // the line's start as origin so we still build a contour.
                        startX = 0; startY = 0;
                        px.add(startX); py.add(startY);
                        haveContour = true;
                    }
                    px.add(seg[0]); py.add(seg[1]);
                    break;
                case PathIterator.SEG_CLOSE:
                    // Close back to the contour's start, then flush as closed.
                    if (haveContour) {
                        px.add(startX); py.add(startY);
                        flush(px, py, true);
                        px.clear(); py.clear();
                        haveContour = false;
                    }
                    break;
                default:
                    break; // CUBICTO/QUADTO never appear after flattening.
            }
            it.next();
        }
        // Flush a trailing un-closed contour.
        flush(px, py, false);
    }

    // Build a Contour from accumulated points and append it. Skips degenerate
    // contours (fewer than 2 points -> zero length, nothing to measure).
    private void flush(ArrayList<Float> px, ArrayList<Float> py, boolean closed) {
        int n = px.size();
        if (n < 2) return;

        // If forceClosed and the contour was not explicitly closed, append the
        // closing edge back to the start so getLength() includes it (mirrors
        // SkPathMeasure's forceClosed behavior).
        boolean effClosed = closed;
        if (forceClosed && !closed) {
            float sx = px.get(0), sy = py.get(0);
            float ex = px.get(n - 1), ey = py.get(n - 1);
            if (sx != ex || sy != ey) {
                px.add(sx); py.add(sy);
                n++;
            }
            effClosed = true;
        }

        Contour c = new Contour();
        c.count = n;
        c.xs = new float[n];
        c.ys = new float[n];
        c.lengths = new float[n];
        c.closed = effClosed;
        float acc = 0;
        c.xs[0] = px.get(0); c.ys[0] = py.get(0); c.lengths[0] = 0;
        for (int i = 1; i < n; i++) {
            float x = px.get(i), y = py.get(i);
            c.xs[i] = x; c.ys[i] = y;
            float dx = x - c.xs[i - 1];
            float dy = y - c.ys[i - 1];
            acc += (float) Math.hypot(dx, dy);
            c.lengths[i] = acc;
        }
        contours.add(c);
    }

    private Contour cur() {
        if (current < 0 || current >= contours.size()) return null;
        return contours.get(current);
    }

    public float getLength() {
        Contour c = cur();
        if (c == null || c.count < 2) return 0f;
        return c.lengths[c.count - 1];
    }

    public boolean isClosed() {
        Contour c = cur();
        return c != null && c.closed;
    }

    /** SkPathMeasure::nextContour is `fContour = fIter.next(); return
     *  !!fContour;` -- so a call that returns FALSE leaves the measure with NO
     *  current contour, and getLength() answers 0 afterwards rather than
     *  keeping the last contour's length. An animation that walks contours in
     *  a while(nextContour()) loop and then reads getLength() sees the
     *  difference. Advancing past the end (rather than refusing to move) is
     *  what makes cur() go null. */
    public boolean nextContour() {
        if (current < contours.size()) current++;
        return current < contours.size();
    }

    public boolean getPosTan(float distance, float[] pos, float[] tan) {
        // AOSP validates the array lengths in JAVA, above the JNI
        // (PathMeasure.java:86-92), so a short array is an
        // ArrayIndexOutOfBoundsException and never reaches native. Both arrays
        // null is legal. This has to happen before any other work, including
        // the no-path early return, or the throw becomes conditional on state.
        if ((pos != null && pos.length < 2) || (tan != null && tan.length < 2)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        Contour c = cur();
        if (c == null || c.count < 2) return false;
        float total = c.lengths[c.count - 1];
        if (total <= 0f) {
            // Degenerate contour: report the single point with no tangent.
            if (pos != null && pos.length >= 2) { pos[0] = c.xs[0]; pos[1] = c.ys[0]; }
            if (tan != null && tan.length >= 2) { tan[0] = 0; tan[1] = 0; }
            return false;
        }
        if (distance < 0f) distance = 0f;
        if (distance > total) distance = total;

        // Binary-search the segment whose cumulative length brackets distance.
        int seg = findSegment(c, distance);
        float l0 = c.lengths[seg];
        float l1 = c.lengths[seg + 1];
        float segLen = l1 - l0;
        float t = segLen > 0f ? (distance - l0) / segLen : 0f;

        float x0 = c.xs[seg],     y0 = c.ys[seg];
        float x1 = c.xs[seg + 1], y1 = c.ys[seg + 1];
        if (pos != null && pos.length >= 2) {
            pos[0] = x0 + (x1 - x0) * t;
            pos[1] = y0 + (y1 - y0) * t;
        }
        if (tan != null && tan.length >= 2) {
            float dx = x1 - x0, dy = y1 - y0;
            float mag = (float) Math.hypot(dx, dy);
            if (mag > 0f) { tan[0] = dx / mag; tan[1] = dy / mag; }
            else          { tan[0] = 0; tan[1] = 0; }
        }
        return true;
    }

    public boolean getMatrix(float distance, Matrix matrix, int flags) {
        if (matrix == null) return false;
        float[] pos = new float[2];
        float[] tan = new float[2];
        if (!getPosTan(distance, pos, tan)) {
            matrix.reset();
            return false;
        }
        matrix.reset();
        if ((flags & TANGENT_MATRIX_FLAG) != 0) {
            // tan is a unit vector; its angle is the contour heading. AOSP
            // builds the rotate component from atan2(tangentY, tangentX).
            float degrees = (float) Math.toDegrees(Math.atan2(tan[1], tan[0]));
            matrix.preRotate(degrees);
        }
        if ((flags & POSITION_MATRIX_FLAG) != 0) {
            // postTranslate so the translation is applied after the rotation,
            // matching SkMatrix::setRSXform semantics (rotate about origin,
            // then move to the sampled position).
            matrix.postTranslate(pos[0], pos[1]);
        }
        return true;
    }

    public boolean getSegment(float startD, float stopD, Path dst,
                              boolean startWithMoveTo) {
        Contour c = cur();
        if (c == null || dst == null || c.count < 2) return false;
        float total = c.lengths[c.count - 1];
        if (total <= 0f) return false;

        if (startD < 0f) startD = 0f;
        if (stopD > total) stopD = total;
        if (startD >= stopD) return false;

        float[] pos = new float[2];

        // ⚠ startWithMoveTo=false does NOT mean "lineTo the start point". Skia
        // emits the first partial segment as a single lineTo to its END, so
        // the sampled START position never appears in dst at all -- the caller
        // wanted the segment appended to wherever dst already was. MEASURED
        // (GEO.PM.segment.noMoveTo.bounds): appending [10,40] to a dst holding
        // moveTo(200,200) gives bounds 30,0,200,200 on a device; emitting the
        // start point as a lineTo dragged the left edge out to 10.
        if (startWithMoveTo) {
            getPosAt(c, startD, pos);
            dst.moveTo(pos[0], pos[1]);
        }

        // Walk every polyline vertex strictly inside (startD, stopD) so the
        // emitted sub-path follows the original geometry, then finish at stopD.
        int startSeg = findSegment(c, startD);
        int stopSeg  = findSegment(c, stopD);
        for (int i = startSeg + 1; i <= stopSeg; i++) {
            float l = c.lengths[i];
            if (l > startD && l < stopD) {
                dst.lineTo(c.xs[i], c.ys[i]);
            }
        }
        getPosAt(c, stopD, pos);
        dst.lineTo(pos[0], pos[1]);
        return true;
    }

    // Position-only sampler shared by getSegment (avoids tangent math).
    private void getPosAt(Contour c, float distance, float[] pos) {
        if (distance < 0f) distance = 0f;
        float total = c.lengths[c.count - 1];
        if (distance > total) distance = total;
        int seg = findSegment(c, distance);
        float l0 = c.lengths[seg];
        float l1 = c.lengths[seg + 1];
        float segLen = l1 - l0;
        float t = segLen > 0f ? (distance - l0) / segLen : 0f;
        pos[0] = c.xs[seg]     + (c.xs[seg + 1] - c.xs[seg]) * t;
        pos[1] = c.ys[seg]     + (c.ys[seg + 1] - c.ys[seg]) * t;
    }

    // Return the index i such that lengths[i] <= distance <= lengths[i+1],
    // clamped to a valid segment ([0, count-2]).
    //
    // ⚠ THE TIE-BREAK AT A VERTEX IS OBSERVABLE. A distance landing exactly on
    // a polyline vertex belongs to BOTH the segment ending there and the one
    // starting there; the position is the same either way but the TANGENT is
    // not. Skia's binary search picks the segment ENDING at that distance
    // (MEASURED, GEO.PM.posTan.corner.tan: a 30-long horizontal run into a
    // vertical run answers (1,0) at d=30 on a device, and we answered (0,1)).
    // Hence the strict `<` below rather than `<=`.
    private int findSegment(Contour c, float distance) {
        int lo = 0, hi = c.count - 1;
        // Binary search for the largest i with lengths[i] < distance.
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (c.lengths[mid] < distance) lo = mid;
            else hi = mid - 1;
        }
        if (lo >= c.count - 1) lo = c.count - 2;
        if (lo < 0) lo = 0;
        return lo;
    }
}
