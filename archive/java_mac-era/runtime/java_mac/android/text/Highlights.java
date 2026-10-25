// android.text.Highlights (API 34) -- an immutable list of (Paint, ranges)
// pairs a TextView draws underneath its text: search-result highlights, an
// IME's composing-region tint, a "find in page" match set. Was absent, so
// TextView.setHighlights(...) had no argument type to name and any app that
// built one hit NoClassDefFoundError.
//
// Mirrors frameworks/base/core/java/android/text/Highlights.java exactly: the
// class is a pair of parallel lists (paints, and an int[] of start/end pairs
// per paint) built through Highlights.Builder, and the three getters are
// index-aligned across both.
package android.text;

import android.graphics.Paint;

import java.util.ArrayList;
import java.util.List;

public class Highlights {

    private final List<Paint> mPaints;
    private final List<int[]> mRanges;

    private Highlights(List<Paint> paints, List<int[]> ranges) {
        mPaints = paints;
        mRanges = ranges;
    }

    /** AOSP Highlights.getSize(): the number of (paint, ranges) pairs. */
    public int getSize() {
        return mPaints.size();
    }

    /** AOSP Highlights.getPaint(int): the paint of the i-th highlight. */
    public Paint getPaint(int index) {
        return mPaints.get(index);
    }

    /**
     * AOSP Highlights.getRanges(int): the i-th highlight's ranges, as a FLAT
     * int[] of {start0, end0, start1, end1, ...} -- not a list of pairs. Its
     * length is therefore always even.
     */
    public int[] getRanges(int index) {
        return mRanges.get(index);
    }

    /** AOSP Highlights.Builder. */
    public static final class Builder {
        private final List<Paint> mPaints = new ArrayList<Paint>();
        private final List<int[]> mRanges = new ArrayList<int[]>();

        public Builder() {
        }

        /**
         * AOSP Highlights.Builder.addRange(Paint, int, int) -- one range for
         * one paint. AOSP throws IllegalArgumentException when start > end.
         */
        public Builder addRange(Paint paint, int start, int end) {
            if (paint == null) throw new NullPointerException("paint must not be null");
            if (start > end) {
                throw new IllegalArgumentException("start must not be larger than end");
            }
            mPaints.add(paint);
            mRanges.add(new int[] { start, end });
            return this;
        }

        /**
         * AOSP Highlights.Builder.addRanges(Paint, int...) -- several ranges
         * sharing one paint, given as the same flat start/end array
         * getRanges() returns. AOSP rejects an odd-length array and a range
         * whose start exceeds its end.
         */
        public Builder addRanges(Paint paint, int... ranges) {
            if (paint == null) throw new NullPointerException("paint must not be null");
            if (ranges == null) throw new NullPointerException("ranges must not be null");
            if (ranges.length % 2 == 1) {
                throw new IllegalArgumentException(
                        "Flatten ranges must have even numbered elements");
            }
            for (int i = 0; i < ranges.length / 2; ++i) {
                int start = ranges[i * 2];
                int end = ranges[i * 2 + 1];
                if (start > end) {
                    throw new IllegalArgumentException(
                            "Reverse range found in the ranges: " + start + ", " + end);
                }
            }
            mPaints.add(paint);
            mRanges.add(ranges.clone());
            return this;
        }

        /** AOSP Highlights.Builder.build(). */
        public Highlights build() {
            return new Highlights(new ArrayList<Paint>(mPaints), new ArrayList<int[]>(mRanges));
        }
    }
}
