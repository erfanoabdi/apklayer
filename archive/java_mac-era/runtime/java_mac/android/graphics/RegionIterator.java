package android.graphics;

import java.util.List;

/**
 * AOSP-faithful android.graphics.RegionIterator. Walks the rectangles that make
 * up a Region.
 *
 * ⚠ THE DECOMPOSITION IS THE ANSWER, not an implementation detail. On a device
 * a Region is an SkRegion: an ordered list of horizontal BANDS, each holding
 * disjoint x-runs, and SkRegion::Iterator walks exactly those, top-to-bottom
 * and left-to-right. A 100x100 square with a 20,20..80,80 hole is four rects --
 * (0,0,100,20) (0,20,20,80) (80,20,100,80) (0,80,100,100) -- and a caller
 * unions them back to reconstruct the region.
 *
 * This class used to walk the backing Area's CONTOURS and emit one bounding box
 * per contour, which for that ring produced the HOLE as a positive rect
 * followed by the whole outer square: a caller that unioned them got the solid
 * square, i.e. the wrong set, with the hole filled in. The band decomposition
 * now lives in Region.macapkBands(); this class is just the cursor over it.
 *
 * Mirrors frameworks/base/graphics/java/android/graphics/RegionIterator.java.
 */
public class RegionIterator {
    private final List<Rect> mRects;
    private int mIndex = 0;

    public RegionIterator(Region region) {
        if (region == null) throw new NullPointerException("region");
        mRects = region.macapkBands();
    }

    public final boolean next(Rect r) {
        if (r == null) throw new NullPointerException("r");
        if (mIndex >= mRects.size()) return false;
        r.set(mRects.get(mIndex++));
        return true;
    }
}
