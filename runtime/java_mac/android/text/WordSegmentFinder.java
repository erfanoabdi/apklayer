// android.text.WordSegmentFinder (API 34) -- a SegmentFinder whose segments are
// WORDS, with whitespace belonging to no segment at all. Handwriting gestures
// ("select this word", "delete this word") and Layout.getRangeForRect take one.
// Was absent, so the class was a NoClassDefFoundError.
//
// Mirrors frameworks/base/core/java/android/text/WordSegmentFinder.java. AOSP
// drives it with android.text.method.WordIterator (@hide, and itself a thin
// wrapper over android.icu.text.BreakIterator.getWordInstance(locale)); this
// tree has no WordIterator, so the icu word BreakIterator is used directly --
// the same UAX#29 word boundaries, one indirection fewer.
//
// The four whitespace-skipping loops are AOSP's, verbatim, including the
// asymmetry that is easy to get wrong: a START boundary looks at the character
// AT the boundary while an END boundary looks at the one BEFORE it.
package android.text;

import android.icu.text.BreakIterator;
import android.icu.util.ULocale;

public class WordSegmentFinder extends SegmentFinder {

    private final CharSequence mText;
    private final BreakIterator mWordIterator;

    public WordSegmentFinder(CharSequence text, ULocale locale) {
        if (text == null) throw new NullPointerException("text must not be null");
        if (locale == null) throw new NullPointerException("locale must not be null");
        mText = text;
        mWordIterator = BreakIterator.getWordInstance(locale.toLocale());
        mWordIterator.setText(text.toString());
    }

    @Override
    public int previousStartBoundary(int offset) {
        int boundary = offset;
        do {
            boundary = prevBoundary(boundary);
            if (boundary == BreakIterator.DONE) {
                return DONE;
            }
        } while (boundary < mText.length() && Character.isWhitespace(mText.charAt(boundary)));
        return boundary;
    }

    @Override
    public int previousEndBoundary(int offset) {
        int boundary = offset;
        do {
            boundary = prevBoundary(boundary);
            if (boundary == BreakIterator.DONE || boundary == 0) {
                return DONE;
            }
        } while (Character.isWhitespace(mText.charAt(boundary - 1)));
        return boundary;
    }

    @Override
    public int nextStartBoundary(int offset) {
        int boundary = offset;
        do {
            boundary = nextBoundary(boundary);
            if (boundary == BreakIterator.DONE || boundary == mText.length()) {
                return DONE;
            }
        } while (Character.isWhitespace(mText.charAt(boundary)));
        return boundary;
    }

    @Override
    public int nextEndBoundary(int offset) {
        int boundary = offset;
        do {
            boundary = nextBoundary(boundary);
            if (boundary == BreakIterator.DONE) {
                return DONE;
            }
        } while (boundary > 0 && Character.isWhitespace(mText.charAt(boundary - 1)));
        return boundary;
    }

    /** WordIterator.prevBoundary(offset) == BreakIterator.preceding(offset). */
    private int prevBoundary(int offset) {
        if (offset <= 0) return BreakIterator.DONE;
        return mWordIterator.preceding(offset);
    }

    /** WordIterator.nextBoundary(offset) == BreakIterator.following(offset). */
    private int nextBoundary(int offset) {
        if (offset >= mText.length()) return BreakIterator.DONE;
        return mWordIterator.following(offset);
    }
}
