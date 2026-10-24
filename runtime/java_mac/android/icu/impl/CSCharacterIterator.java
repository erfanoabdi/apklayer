// android.icu.impl.CSCharacterIterator -- a java.text.CharacterIterator over a
// CharSequence. Ported from AOSP external/icu
//   android_icu4j/src/main/java/android/icu/impl/CSCharacterIterator.java
// (ICU license, (C) 2016 and later: Unicode, Inc. and others; a verbatim
// repackage of com.ibm.icu.impl.CSCharacterIterator).
//
// WHY it exists here: android.icu.text.BreakIterator.setText(CharSequence)
// (API 29) is defined by ICU4J as `setText(new CSCharacterIterator(newText))`.
// We had no such overload, so every app that hands a CharSequence to an ICU
// BreakIterator died with NoSuchMethodError -- Signal's StringUtil
// .getGraphemeCount and GraphemeClusterLimitFilter (the InputFilter on its
// profile-name / about / group-name fields) both do. The iterator reads the
// sequence LIVE (no copy), exactly like ICU: the caller must not mutate it
// while the BreakIterator holds it.
package android.icu.impl;

import java.text.CharacterIterator;

public class CSCharacterIterator implements CharacterIterator {

    private int index;
    private CharSequence seq;

    public CSCharacterIterator(CharSequence text) {
        if (text == null) {
            throw new NullPointerException();
        }
        seq = text;
        index = 0;
    }

    @Override
    public char first() {
        index = 0;
        return current();
    }

    @Override
    public char last() {
        index = seq.length();
        return previous();
    }

    @Override
    public char current() {
        if (index == seq.length()) {
            return DONE;
        }
        return seq.charAt(index);
    }

    @Override
    public char next() {
        if (index < seq.length()) {
            ++index;
        }
        return current();
    }

    @Override
    public char previous() {
        if (index == 0) {
            return DONE;
        }
        --index;
        return current();
    }

    @Override
    public char setIndex(int position) {
        if (position < 0 || position > seq.length()) {
            throw new IllegalArgumentException();
        }
        index = position;
        return current();
    }

    @Override
    public int getBeginIndex() {
        return 0;
    }

    @Override
    public int getEndIndex() {
        return seq.length();
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public Object clone() {
        CSCharacterIterator copy = new CSCharacterIterator(seq);
        copy.setIndex(index);
        return copy;
    }
}
