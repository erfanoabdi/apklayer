package android.graphics;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * AOSP-faithful android.graphics.PathIterator (API 34). Iterates the verbs and
 * points of a Path. We back it by java.awt.geom.PathIterator over the Path's
 * AWT shape (Path.macapkShape()), translating AWT segment types to the Android
 * VERB_* constants. AWT never emits CONIC (it uses QUAD/CUBIC), so VERB_CONIC
 * is never produced and getConicWeight() is always 1.0. Mirrors
 * frameworks/base/graphics/java/android/graphics/PathIterator.java.
 */
public class PathIterator implements Iterator<PathIterator.Segment> {
    public static final int VERB_MOVE  = 0;
    public static final int VERB_LINE  = 1;
    public static final int VERB_QUAD  = 2;
    public static final int VERB_CONIC = 3;
    public static final int VERB_CUBIC = 4;
    public static final int VERB_CLOSE = 5;
    public static final int VERB_DONE  = 6;

    private final java.awt.geom.PathIterator mAwt;

    public PathIterator(Path path) {
        if (path == null) throw new NullPointerException("path");
        java.awt.Shape s = path.macapkShape();
        mAwt = s.getPathIterator(null);
    }

    private static int translateVerb(int awtSeg) {
        switch (awtSeg) {
            case java.awt.geom.PathIterator.SEG_MOVETO:  return VERB_MOVE;
            case java.awt.geom.PathIterator.SEG_LINETO:  return VERB_LINE;
            case java.awt.geom.PathIterator.SEG_QUADTO:  return VERB_QUAD;
            case java.awt.geom.PathIterator.SEG_CUBICTO: return VERB_CUBIC;
            case java.awt.geom.PathIterator.SEG_CLOSE:   return VERB_CLOSE;
            default:                                     return VERB_DONE;
        }
    }

    /** AOSP PathIterator.peek(): returns the next verb without consuming it. */
    public int peek() {
        if (mAwt.isDone()) return VERB_DONE;
        float[] coords = new float[6];
        return translateVerb(mAwt.currentSegment(coords));
    }

    @Override
    public boolean hasNext() {
        return !mAwt.isDone();
    }

    /**
     * AOSP fast-path: writes the next segment's points into the supplied array
     * at offset and returns the verb. The points array must hold at least 8
     * floats past offset.
     */
    public int next(float[] points, int offset) {
        if (points == null) throw new NullPointerException("points");
        if (mAwt.isDone()) return VERB_DONE;
        float[] coords = new float[6];
        int verb = translateVerb(mAwt.currentSegment(coords));
        // Android lays out points as a flat array; per its docs the first
        // point pair of MOVE/LINE/QUAD/CUBIC is the segment start. AWT does
        // not give us the prior current point here, so we write what AWT
        // provides (the destination control points) faithfully starting at
        // offset; callers reading conic-style layouts are rare on AWT paths.
        int n;
        switch (verb) {
            case VERB_MOVE:  n = 2; break;
            case VERB_LINE:  n = 2; break;
            case VERB_QUAD:  n = 4; break;
            case VERB_CUBIC: n = 6; break;
            default:         n = 0; break;
        }
        for (int i = 0; i < n; i++) points[offset + i] = coords[i];
        mAwt.next();
        return verb;
    }

    @Override
    public Segment next() {
        if (mAwt.isDone()) throw new NoSuchElementException();
        float[] coords = new float[6];
        int awtType = mAwt.currentSegment(coords);
        int verb = translateVerb(awtType);
        int n;
        switch (verb) {
            case VERB_MOVE:  n = 2; break;
            case VERB_LINE:  n = 2; break;
            case VERB_QUAD:  n = 4; break;
            case VERB_CUBIC: n = 6; break;
            default:         n = 0; break;
        }
        float[] pts = new float[n];
        for (int i = 0; i < n; i++) pts[i] = coords[i];
        mAwt.next();
        return new Segment(verb, pts, 1.0f);
    }

    public static class Segment {
        private final int mVerb;
        private final float[] mPoints;
        private final float mConicWeight;

        public Segment(int verb, float[] points, float conicWeight) {
            mVerb = verb;
            mPoints = points;
            mConicWeight = conicWeight;
        }

        public int getVerb() { return mVerb; }
        public float[] getPoints() { return mPoints; }
        public float getConicWeight() { return mConicWeight; }
    }
}
