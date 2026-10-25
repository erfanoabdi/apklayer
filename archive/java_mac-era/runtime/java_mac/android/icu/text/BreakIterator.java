// android.icu.text.BreakIterator -- ICU4J's boundary analysis class (a verbatim
// repackage of com.ibm.icu.text.BreakIterator). Published API:
//   developer.android.com/reference/android/icu/text/BreakIterator
//   public abstract class BreakIterator implements Cloneable
//
// Used for word / line / sentence / character boundary detection in text
// selection, search, and many i18n libraries. The JDK ships an equivalent
// boundary analyzer (java.text.BreakIterator) whose word/line/sentence/
// character instances produce boundaries that match ICU's for the Latin and
// common-script cases apps actually exercise. So android.icu.text.BreakIterator
// is implemented as a faithful abstract front whose factory methods return a
// concrete subclass delegating to java.text.BreakIterator -- no ICU4J bundle.
//
// Faithfulness notes: the iteration contract (first/last/next/previous/current/
// next(int)/following/preceding/isBoundary, DONE == -1) is identical between
// ICU4J and the JDK, so the delegate's results flow through unchanged.
package android.icu.text;

import android.icu.util.ULocale;

import java.text.CharacterIterator;
import java.util.Locale;

public abstract class BreakIterator implements Cloneable {
    public static final int DONE = -1;

    public static final int KIND_CHARACTER = 0;
    public static final int KIND_WORD = 1;
    public static final int KIND_LINE = 2;
    public static final int KIND_SENTENCE = 3;
    public static final int KIND_TITLE = 4;

    protected BreakIterator() {}

    public abstract int first();
    public abstract int last();
    public abstract int next(int n);
    public abstract int next();
    public abstract int previous();
    public abstract int following(int offset);
    public abstract int current();
    // ICU4J (AOSP external/icu android_icu4j .../android/icu/text/BreakIterator.java
    // :491-526): setText(String) and setText(CharSequence) are CONCRETE and both
    // funnel into the one abstract setText(CharacterIterator):
    //     public void setText(String newText)
    //     {   setText(new StringCharacterIterator(newText)); }
    //     public void setText(CharSequence newText) {
    //         setText(new CSCharacterIterator(newText)); }
    //     public abstract void setText(CharacterIterator newText);
    // The CharSequence overload (API 29) was missing here, so any app passing a
    // CharSequence died with NoSuchMethodError (Signal: StringUtil
    // .getGraphemeCount / GraphemeClusterLimitFilter, an InputFilter on its
    // text fields). setText(String) was abstract, which also broke an app's
    // own BreakIterator subclass that (like ICU's) only overrides the
    // CharacterIterator form.
    public void setText(String newText) {
        setText(new java.text.StringCharacterIterator(newText));
    }
    public void setText(CharSequence newText) {
        setText(new android.icu.impl.CSCharacterIterator(newText));
    }
    public abstract void setText(CharacterIterator newText);

    // Rule-status API (ICU4J :376-461). The constants and the base-class
    // answers below are ICU's own (BreakIterator.getRuleStatus() is 0 and
    // getRuleStatusVec() is {0}). On Android the instances apps get are
    // RuleBasedBreakIterator, which OVERRIDES both with the status tag of the
    // rule that ended the segment (RuleBasedBreakIterator.java :593 / :628), so
    // JdkBreakIterator below overrides them too instead of answering 0 for
    // every boundary -- see JdkBreakIterator.getRuleStatus.
    public static final int WORD_NONE           = 0;
    public static final int WORD_NONE_LIMIT     = 100;
    public static final int WORD_NUMBER         = 100;
    public static final int WORD_NUMBER_LIMIT   = 200;
    public static final int WORD_LETTER         = 200;
    public static final int WORD_LETTER_LIMIT   = 300;
    public static final int WORD_KANA           = 300;
    public static final int WORD_KANA_LIMIT     = 400;
    public static final int WORD_IDEO           = 400;
    public static final int WORD_IDEO_LIMIT     = 500;

    public int getRuleStatus() {
        return 0;
    }

    public int getRuleStatusVec(int[] fillInArray) {
        if (fillInArray != null && fillInArray.length > 0) {
            fillInArray[0] = 0;
        }
        return 1;
    }
    public abstract CharacterIterator getText();

    // Concrete methods with portable default impls (ICU provides these too).
    public int preceding(int offset) {
        // ICU's contract: the last boundary strictly before offset.
        following(offset);
        // following() left us at/after offset; step back.
        int prev = current();
        while (prev >= offset && prev != DONE) {
            prev = previous();
        }
        return prev;
    }

    public boolean isBoundary(int offset) {
        if (offset == 0) return true;
        return following(offset - 1) == offset;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    // ---- Factory methods (no-arg, Locale, ULocale for each kind) ----------
    public static BreakIterator getWordInstance() {
        return wrap(java.text.BreakIterator.getWordInstance(), KIND_WORD);
    }
    public static BreakIterator getWordInstance(Locale where) {
        return wrap(java.text.BreakIterator.getWordInstance(loc(where)), KIND_WORD);
    }
    public static BreakIterator getWordInstance(ULocale where) {
        return wrap(java.text.BreakIterator.getWordInstance(ULocale.unwrap(where)), KIND_WORD);
    }

    public static BreakIterator getLineInstance() {
        return wrap(java.text.BreakIterator.getLineInstance(), KIND_LINE);
    }
    public static BreakIterator getLineInstance(Locale where) {
        return wrap(java.text.BreakIterator.getLineInstance(loc(where)), KIND_LINE);
    }
    public static BreakIterator getLineInstance(ULocale where) {
        return wrap(java.text.BreakIterator.getLineInstance(ULocale.unwrap(where)), KIND_LINE);
    }

    public static BreakIterator getSentenceInstance() {
        return wrap(java.text.BreakIterator.getSentenceInstance(), KIND_SENTENCE);
    }
    public static BreakIterator getSentenceInstance(Locale where) {
        return wrap(java.text.BreakIterator.getSentenceInstance(loc(where)), KIND_SENTENCE);
    }
    public static BreakIterator getSentenceInstance(ULocale where) {
        return wrap(java.text.BreakIterator.getSentenceInstance(ULocale.unwrap(where)), KIND_SENTENCE);
    }

    public static BreakIterator getCharacterInstance() {
        return wrap(java.text.BreakIterator.getCharacterInstance(), KIND_CHARACTER);
    }
    public static BreakIterator getCharacterInstance(Locale where) {
        return wrap(java.text.BreakIterator.getCharacterInstance(loc(where)), KIND_CHARACTER);
    }
    public static BreakIterator getCharacterInstance(ULocale where) {
        return wrap(java.text.BreakIterator.getCharacterInstance(ULocale.unwrap(where)), KIND_CHARACTER);
    }

    // ICU has a dedicated title-case iterator; the JDK doesn't, so we map it
    // to the word iterator (title-casing walks word boundaries anyway).
    public static BreakIterator getTitleInstance() {
        return wrap(java.text.BreakIterator.getWordInstance(), KIND_TITLE);
    }
    public static BreakIterator getTitleInstance(Locale where) {
        return wrap(java.text.BreakIterator.getWordInstance(loc(where)), KIND_TITLE);
    }
    public static BreakIterator getTitleInstance(ULocale where) {
        return wrap(java.text.BreakIterator.getWordInstance(ULocale.unwrap(where)), KIND_TITLE);
    }

    private static Locale loc(Locale l) { return (l != null) ? l : Locale.getDefault(); }

    private static BreakIterator wrap(java.text.BreakIterator d, int kind) {
        return new JdkBreakIterator(d, kind);
    }

    // Concrete delegate. DONE maps 1:1 (java.text.BreakIterator.DONE == -1).
    //
    // `kind` exists for ONE reason: the JDK iterator has no rule-status concept,
    // and ICU's status tag is a function of which segment just ended, so the
    // tag has to be derived from the segment and the kind. See getRuleStatus.
    private static final class JdkBreakIterator extends BreakIterator {
        private final java.text.BreakIterator d;
        private final int kind;
        JdkBreakIterator(java.text.BreakIterator d, int kind) { this.d = d; this.kind = kind; }
        @Override public int first() { return d.first(); }
        @Override public int last() { return d.last(); }
        @Override public int next(int n) { return d.next(n); }
        @Override public int next() { return d.next(); }
        @Override public int previous() { return d.previous(); }
        @Override public int following(int offset) { return d.following(offset); }
        @Override public int preceding(int offset) { return d.preceding(offset); }
        @Override public int current() { return d.current(); }
        @Override public boolean isBoundary(int offset) { return d.isBoundary(offset); }
        @Override public void setText(String newText) { d.setText(newText); }
        @Override public void setText(CharacterIterator newText) { d.setText(newText); }
        @Override public CharacterIterator getText() { return d.getText(); }
        @Override public Object clone() {
            return new JdkBreakIterator((java.text.BreakIterator) d.clone(), kind);
        }

        // ICU RuleBasedBreakIterator.getRuleStatus() (AOSP external/icu android_icu4j
        // .../RuleBasedBreakIterator.java :593): "the status from the break rule that
        // determined the boundary at the current iteration position", 0 at first().
        // The tags are in the rule sources (icu4c/source/data/brkitr/rules/):
        //   word.txt:101-107  $Numeric {100}; $ALetterPlus, $HangulSyllable,
        //                     $Hebrew_Letter {200}; $Katakana, $Hiragana, $Ideographic
        //                     {400}. The comment at :102 says it: "rule status set based
        //                     on the word's final base character" (a trailing
        //                     $ExFm* = Extend/Format/ZWJ run does not count) -- that
        //                     is exactly what is computed here.
        //   line.txt:126-135  {100} only on a segment that ends in a hard break
        //                     ($LB4Breaks = BK CR LF NL, and CR LF); anything else,
        //                     including the end of the text, is 0.
        //   sent.txt:83       {100} only for a sentence with NO STerm/ATerm that ends at
        //                     a separator or the end of the text; a terminated one is 0.
        //   char.txt, title.txt  carry no status tags: always 0.
        // Measured against ICU4J 69.1 on a randomised corpus: docs/icu-breakiterator-
        // charsequence_2026-10-01.md ("rule status") and debug/icubreak_gate.sh B8.
        @Override public int getRuleStatus() {
            CharacterIterator ci = d.getText();
            int cur = d.current();
            if (ci == null || kind == KIND_CHARACTER || kind == KIND_TITLE) return 0;
            ci = (CharacterIterator) ci.clone();   // never move the iterator d is using
            int begin = ci.getBeginIndex();
            if (cur <= begin || cur > ci.getEndIndex()) return 0;
            if (kind == KIND_LINE) return lineStatus(ci, cur);
            // The segment that ENDED at cur: [prev, cur).
            int prev = ((java.text.BreakIterator) d.clone()).preceding(cur);
            if (prev == DONE || prev < begin) prev = begin;
            return kind == KIND_WORD ? wordStatus(ci, prev, cur) : sentenceStatus(ci, prev, cur);
        }

        // ICU getRuleStatusVec: the number of status values (always 1 here: one
        // tag per segment) with the values copied up to the array length.
        @Override public int getRuleStatusVec(int[] fillInArray) {
            int status = getRuleStatus();
            if (fillInArray != null && fillInArray.length > 0) fillInArray[0] = status;
            return 1;
        }

        private static int codePointBefore(CharacterIterator ci, int begin, int end) {
            char lo = ci.setIndex(end - 1);
            if (Character.isLowSurrogate(lo) && end - 2 >= begin) {
                char hi = ci.setIndex(end - 2);
                if (Character.isHighSurrogate(hi)) return Character.toCodePoint(hi, lo);
            }
            return lo;
        }

        // Word_Break = Extend | Format | ZWJ: the "$ExFm*" run the word rules attach
        // after a base character (Mn, Me, Mc, Cf, and the emoji skin-tone modifiers).
        private static boolean isExtendOrFormat(int cp) {
            switch (Character.getType(cp)) {
                case Character.NON_SPACING_MARK:
                case Character.ENCLOSING_MARK:
                case Character.COMBINING_SPACING_MARK:
                case Character.FORMAT:
                    return true;
                default:
                    return cp >= 0x1F3FB && cp <= 0x1F3FF;
            }
        }

        private static int wordStatus(CharacterIterator ci, int prev, int cur) {
            int end = cur;
            boolean trailing = false;
            while (end > prev) {
                int cp = codePointBefore(ci, prev, end);
                end -= Character.charCount(cp);
                if (isExtendOrFormat(cp)) { trailing = true; continue; }   // not the final BASE character
                if (Character.isDigit(cp)) return WORD_NUMBER;
                // word.txt:103 `$HangulSyllable {200};` is the one status rule WITHOUT a
                // trailing $ExFm*, so a syllable followed by an Extend/Format char ends
                // in a state ICU tags 0 (measured on ICU4J 69.1).
                if (trailing && cp >= 0xAC00 && cp <= 0xD7A3) return WORD_NONE;
                Character.UnicodeScript sc = Character.UnicodeScript.of(cp);
                if (sc == Character.UnicodeScript.HIRAGANA || sc == Character.UnicodeScript.KATAKANA
                        || Character.isIdeographic(cp)) return WORD_IDEO;
                if (Character.isAlphabetic(cp)) return WORD_LETTER;
                return WORD_NONE;
            }
            return WORD_NONE;
        }

        private static int lineStatus(CharacterIterator ci, int cur) {
            switch (ci.setIndex(cur - 1)) {
                case '\n': case '\r': case 0x0B: case 0x0C: case 0x85: case 0x2028: case 0x2029:
                    return 100;                         // UBRK_LINE_HARD
                default:
                    return 0;                           // UBRK_LINE_SOFT
            }
        }

        // sent.txt: `($STermEx | $ATermEx) $CloseEx* $SpEx* ($Sep | $CR | $LF)?` accepts with
        // status 0 (a sentence ENDED by a terminator); the {100} rule is for one that
        // ends at a separator or the end of the text without that shape ("ab", "3.5",
        // "e.g. x"). So: walk back over Sep / Sp / Close / Extend / Format and ask whether a
        // sentence terminator is what is left.
        private static int sentenceStatus(CharacterIterator ci, int prev, int cur) {
            int end = cur;
            while (end > prev) {
                int cp = codePointBefore(ci, prev, end);
                end -= Character.charCount(cp);
                if (isSentenceTerminator(cp)) return 0;          // UBRK_SENTENCE_TERM
                if (isSentenceSep(cp) || Character.isWhitespace(cp) || Character.isSpaceChar(cp)
                        || isSentenceClose(cp) || isExtendOrFormat(cp)) continue;
                return 100;                                      // UBRK_SENTENCE_SEP
            }
            return 100;
        }

        private static boolean isSentenceSep(int cp) {            // Sentence_Break = Sep | CR | LF
            return cp == '\n' || cp == '\r' || cp == 0x85 || cp == 0x2028 || cp == 0x2029;
        }

        private static boolean isSentenceClose(int cp) {          // Sentence_Break = Close
            switch (Character.getType(cp)) {
                case Character.START_PUNCTUATION:
                case Character.END_PUNCTUATION:
                case Character.INITIAL_QUOTE_PUNCTUATION:
                case Character.FINAL_QUOTE_PUNCTUATION:
                    return true;
                default:
                    return cp == '"' || cp == '\'' || cp == 0xFF02 || cp == 0xFF07;
            }
        }

        // Sentence_Break = STerm | ATerm (Unicode UCD auxiliary/SentenceBreakProperty.txt,
        // merged into [lo, hi] pairs); the set sent.txt's {100} rule excludes.
        private static final int[] STERM_ATERM = {
        0x0021, 0x0021, 0x002E, 0x002E, 0x003F, 0x003F, 0x0589, 0x0589, 0x061D, 0x061F,
        0x06D4, 0x06D4, 0x0700, 0x0702, 0x07F9, 0x07F9, 0x0837, 0x0837, 0x0839, 0x0839,
        0x083D, 0x083E, 0x0964, 0x0965, 0x104A, 0x104B, 0x1362, 0x1362, 0x1367, 0x1368,
        0x166E, 0x166E, 0x1735, 0x1736, 0x17D4, 0x17D5, 0x1803, 0x1803, 0x1809, 0x1809,
        0x1944, 0x1945, 0x1AA8, 0x1AAB, 0x1B4E, 0x1B4F, 0x1B5A, 0x1B5B, 0x1B5E, 0x1B5F,
        0x1B7D, 0x1B7F, 0x1C3B, 0x1C3C, 0x1C7E, 0x1C7F, 0x2024, 0x2024, 0x203C, 0x203D,
        0x2047, 0x2049, 0x2CF9, 0x2CFB, 0x2E2E, 0x2E2E, 0x2E3C, 0x2E3C, 0x2E53, 0x2E54,
        0x2E60, 0x2E61, 0x3002, 0x3002, 0xA4FF, 0xA4FF, 0xA60E, 0xA60F, 0xA6F3, 0xA6F3,
        0xA6F7, 0xA6F7, 0xA876, 0xA877, 0xA8CE, 0xA8CF, 0xA92F, 0xA92F, 0xA9C8, 0xA9C9,
        0xAA5D, 0xAA5F, 0xAAF0, 0xAAF1, 0xABEB, 0xABEB, 0xFE12, 0xFE12, 0xFE15, 0xFE16,
        0xFE52, 0xFE52, 0xFE56, 0xFE57, 0xFF01, 0xFF01, 0xFF0E, 0xFF0E, 0xFF1F, 0xFF1F,
        0xFF61, 0xFF61, 0x10A56, 0x10A57, 0x10F55, 0x10F59, 0x10F86, 0x10F89, 0x11047, 0x11048,
        0x110BE, 0x110C1, 0x11141, 0x11143, 0x111C5, 0x111C6, 0x111CD, 0x111CD, 0x111DE, 0x111DF,
        0x11238, 0x11239, 0x1123B, 0x1123C, 0x112A9, 0x112A9, 0x113D4, 0x113D5, 0x1144B, 0x1144C,
        0x115C2, 0x115C3, 0x115C9, 0x115D7, 0x11641, 0x11642, 0x1173C, 0x1173E, 0x11944, 0x11944,
        0x11946, 0x11946, 0x11A42, 0x11A43, 0x11A9B, 0x11A9C, 0x11C41, 0x11C42, 0x11EF7, 0x11EF8,
        0x11F43, 0x11F44, 0x16A6E, 0x16A6F, 0x16AF5, 0x16AF5, 0x16B37, 0x16B38, 0x16B44, 0x16B44,
        0x16D6E, 0x16D6F, 0x16E98, 0x16E98, 0x1BC9F, 0x1BC9F, 0x1DA88, 0x1DA88,
        };

        private static boolean isSentenceTerminator(int cp) {
            int lo = 0, hi = STERM_ATERM.length / 2 - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (cp < STERM_ATERM[2 * mid]) hi = mid - 1;
                else if (cp > STERM_ATERM[2 * mid + 1]) lo = mid + 1;
                else return true;
            }
            return false;
        }
    }
}
