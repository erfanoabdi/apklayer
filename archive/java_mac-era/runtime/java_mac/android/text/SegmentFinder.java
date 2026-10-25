package android.text;
/** Locates text segment boundaries (grapheme clusters, words, etc.) for
 *  selection / handwriting. Abstract base; the boundary APIs return DONE past
 *  the ends. API 34.
 *  Source: frameworks/base/core/java/android/text/SegmentFinder.java */
public abstract class SegmentFinder {
    public static final int DONE = -1;
    public SegmentFinder() { }
    public abstract int previousStartBoundary(int offset);
    public abstract int previousEndBoundary(int offset);
    public abstract int nextStartBoundary(int offset);
    public abstract int nextEndBoundary(int offset);

    /**
     * AOSP SegmentFinder.PrescribedSegmentFinder (API 34) -- a SegmentFinder
     * over an EXPLICIT list of segments, given as a flat sorted array where the
     * i-th segment is [segments[2*i], segments[2*i+1]). Handwriting and gesture
     * APIs hand one of these to Layout.getRangeForRect.
     *
     * The whole class is reproduced from AOSP because the binary-search index
     * arithmetic is the entire behaviour and is genuinely easy to get subtly
     * wrong -- in particular AOSP's handling of a DUPLICATE boundary (one
     * segment's end equal to the next one's start), which must step the index
     * in the direction of travel or the search returns the wrong segment.
     */
    public static class PrescribedSegmentFinder extends SegmentFinder {
        private final int[] mSegments;

        /**
         * @throws IllegalArgumentException if the array length is odd, the
         *         segments overlap, a segment is empty, or the segments are not
         *         sorted.
         */
        public PrescribedSegmentFinder(int[] segments) {
            checkSegmentsValid(segments);
            mSegments = segments;
        }

        @Override
        public int previousStartBoundary(int offset) {
            return findPrevious(offset, /* isStart = */ true);
        }

        @Override
        public int previousEndBoundary(int offset) {
            return findPrevious(offset, /* isStart = */ false);
        }

        @Override
        public int nextStartBoundary(int offset) {
            return findNext(offset, /* isStart = */ true);
        }

        @Override
        public int nextEndBoundary(int offset) {
            return findNext(offset, /* isStart = */ false);
        }

        private int findNext(int offset, boolean isStart) {
            if (offset < 0) return DONE;
            if (mSegments.length < 1 || offset > mSegments[mSegments.length - 1]) return DONE;

            if (offset < mSegments[0]) {
                return isStart ? mSegments[0] : mSegments[1];
            }

            int index = java.util.Arrays.binarySearch(mSegments, offset);
            if (index >= 0) {
                // mSegments may have duplicate elements (the previous segment's
                // end equal to the following segment's start). Move the index
                // forwards, since we are searching for the NEXT segment.
                if (index + 1 < mSegments.length && mSegments[index + 1] == offset) {
                    index = index + 1;
                }
                // Point the index at the first boundary larger than `offset`.
                index += 1;
            } else {
                // binarySearch returns the insertion point, which IS the first
                // boundary larger than `offset`.
                index = -(index + 1);
            }
            if (index >= mSegments.length) return DONE;

            //  +---------------------------------------+
            //  |               | isStart   | isEnd     |
            //  |---------------+-----------+-----------|
            //  | indexIsStart  | index     | index + 1 |
            //  |---------------+-----------+-----------|
            //  | indexIsEnd    | index + 1 | index     |
            //  +---------------------------------------+
            boolean indexIsStart = index % 2 == 0;
            if (isStart != indexIsStart) {
                return (index + 1 < mSegments.length) ? mSegments[index + 1] : DONE;
            }
            return mSegments[index];
        }

        private int findPrevious(int offset, boolean isStart) {
            if (mSegments.length < 1 || offset < mSegments[0]) return DONE;

            if (offset > mSegments[mSegments.length - 1]) {
                return isStart ? mSegments[mSegments.length - 2] : mSegments[mSegments.length - 1];
            }

            int index = java.util.Arrays.binarySearch(mSegments, offset);
            if (index >= 0) {
                // Duplicate boundary: move BACKWARDS here, mirroring findNext.
                if (index > 0 && mSegments[index - 1] == offset) {
                    index = index - 1;
                }
                index -= 1;
            } else {
                index = -(index + 1) - 1;
            }
            if (index < 0) return DONE;

            boolean indexIsStart = index % 2 == 0;
            if (isStart != indexIsStart) {
                return (index > 0) ? mSegments[index - 1] : DONE;
            }
            return mSegments[index];
        }

        private static void checkSegmentsValid(int[] segments) {
            if (segments == null) throw new NullPointerException("segments must not be null");
            if (segments.length % 2 != 0) {
                throw new IllegalArgumentException("the length of segments must be even");
            }
            if (segments.length == 0) return;
            int lastSegmentEnd = Integer.MIN_VALUE;
            for (int index = 0; index < segments.length; index += 2) {
                if (segments[index] < lastSegmentEnd) {
                    throw new IllegalArgumentException("segments can\'t overlap");
                }
                if (segments[index] >= segments[index + 1]) {
                    throw new IllegalArgumentException("the segment range can\'t be empty");
                }
                lastSegmentEnd = segments[index + 1];
            }
        }
    }
}
