package android.text;
import java.text.BreakIterator;
/** A SegmentFinder whose segments are grapheme clusters, backed by ICU's
 *  character BreakIterator (which is what AOSP uses under the hood). Real
 *  behavior: each segment is one user-perceived character. API 34.
 *  Source: frameworks/base/graphics/java/android/text/GraphemeClusterSegmentFinder.java */
public class GraphemeClusterSegmentFinder extends SegmentFinder {
    private final BreakIterator mIterator;
    public GraphemeClusterSegmentFinder(CharSequence text, TextPaint paint) {
        mIterator = BreakIterator.getCharacterInstance();
        mIterator.setText(text == null ? "" : text.toString());
    }
    @Override public int previousStartBoundary(int offset) {
        int r = mIterator.preceding(offset); return r == BreakIterator.DONE ? DONE : r;
    }
    @Override public int previousEndBoundary(int offset) {
        int r = mIterator.preceding(offset); return r == BreakIterator.DONE ? DONE : r;
    }
    @Override public int nextStartBoundary(int offset) {
        int r = mIterator.following(offset); return r == BreakIterator.DONE ? DONE : r;
    }
    @Override public int nextEndBoundary(int offset) {
        int r = mIterator.following(offset); return r == BreakIterator.DONE ? DONE : r;
    }
}
