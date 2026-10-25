// android.text.TextDirectionHeuristics -- the static heuristics that decide
// whether a run of text is RTL. Ported from AOSP
// frameworks/base/core/java/android/text/TextDirectionHeuristics.java
// (FirstStrong = Unicode Bidirectional Algorithm steps P2/P3; AnyStrong;
// the isolate-skipping rules; the DIRECTIONALITY_UNDEFINED fallback ranges).
//
// WHY THE REAL ALGORITHM AND NOT A CONSTANT: until v1.0.4xx every constant
// here was `new SimpleHeuristic(<fixed boolean>)` whose isRtl() ignored the
// text it was handed, so FIRSTSTRONG_LTR -- the heuristic AOSP's TextView /
// BidiFormatter pipeline defaults to -- answered "LTR" for Arabic/Hebrew/
// Farsi content and mixed-direction strings laid out scrambled with no
// exception thrown. Nothing about macOS forces that: the Unicode Bidi_Class
// property this needs is in the JDK itself (Character.getDirectionality
// reads the same UCD data ICU does), so the honest implementation is a
// straight port.
package android.text;

import android.view.View;

import java.nio.CharBuffer;

public class TextDirectionHeuristics {

    /** Always decides that the direction is left to right. */
    public static final TextDirectionHeuristic LTR =
        new TextDirectionHeuristicInternal(null /* no algorithm */, false);

    /** Always decides that the direction is right to left. */
    public static final TextDirectionHeuristic RTL =
        new TextDirectionHeuristicInternal(null /* no algorithm */, true);

    /** First strong directional character wins, falling back to LTR. This is
     *  the default behavior of the Unicode Bidirectional Algorithm. */
    public static final TextDirectionHeuristic FIRSTSTRONG_LTR =
        new TextDirectionHeuristicInternal(FirstStrong.INSTANCE, false);

    /** First strong directional character wins, falling back to RTL. */
    public static final TextDirectionHeuristic FIRSTSTRONG_RTL =
        new TextDirectionHeuristicInternal(FirstStrong.INSTANCE, true);

    /** RTL if the text contains any strong RTL non-format character,
     *  falling back to LTR. */
    public static final TextDirectionHeuristic ANYRTL_LTR =
        new TextDirectionHeuristicInternal(AnyStrong.INSTANCE_RTL, false);

    /** Force the paragraph direction to the Locale direction, falling back
     *  to LTR. */
    public static final TextDirectionHeuristic LOCALE = TextDirectionHeuristicLocale.INSTANCE;

    /** State constants for taking care about true / false / unknown. */
    private static final int STATE_TRUE = 0;
    private static final int STATE_FALSE = 1;
    private static final int STATE_UNKNOWN = 2;

    /* Returns STATE_TRUE for strong RTL characters, STATE_FALSE for strong LTR
     * characters, and STATE_UNKNOWN for everything else. The DIRECTIONALITY_
     * UNDEFINED arm matters because a JDK whose UCD tables predate a codepoint
     * still has to answer for it the way the derived Bidi_Class table does:
     * http://www.unicode.org/Public/UCD/latest/ucd/extracted/DerivedBidiClass.txt */
    private static int isRtlCodePoint(int codePoint) {
        switch (Character.getDirectionality(codePoint)) {
            case Character.DIRECTIONALITY_LEFT_TO_RIGHT:
                return STATE_FALSE;
            case Character.DIRECTIONALITY_RIGHT_TO_LEFT:
            case Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
                return STATE_TRUE;
            case Character.DIRECTIONALITY_UNDEFINED:
                if ((0x0590 <= codePoint && codePoint <= 0x08FF) ||
                        (0xFB1D <= codePoint && codePoint <= 0xFDCF) ||
                        (0xFDF0 <= codePoint && codePoint <= 0xFDFF) ||
                        (0xFE70 <= codePoint && codePoint <= 0xFEFF) ||
                        (0x10800 <= codePoint && codePoint <= 0x10FFF) ||
                        (0x1E800 <= codePoint && codePoint <= 0x1EFFF)) {
                    // Unassigned RTL character
                    return STATE_TRUE;
                } else if (
                        // Potentially-unassigned Default_Ignorable. Ranges are from
                        // unassigned characters that have Unicode property
                        // Other_Default_Ignorable_Code_Point plus some enlargening to
                        // cover bidi isolates and simplify checks.
                        (0x2065 <= codePoint && codePoint <= 0x2069) ||
                        (0xFFF0 <= codePoint && codePoint <= 0xFFF8) ||
                        (0xE0000 <= codePoint && codePoint <= 0xE0FFF) ||
                        // Non-character
                        (0xFDD0 <= codePoint && codePoint <= 0xFDEF) ||
                        ((codePoint & 0xFFFE) == 0xFFFE) ||
                        // Currency symbol
                        (0x20A0 <= codePoint && codePoint <= 0x20CF) ||
                        // Unpaired surrogate
                        (0xD800 <= codePoint && codePoint <= 0xDFFF)) {
                    return STATE_UNKNOWN;
                } else {
                    // Unassigned LTR character
                    return STATE_FALSE;
                }
            default:
                return STATE_UNKNOWN;
        }
    }

    /** Computes the text direction based on an algorithm. Subclasses implement
     *  defaultIsRtl() to handle the cases where the algorithm cannot determine
     *  the direction from the text alone. */
    private static abstract class TextDirectionHeuristicImpl implements TextDirectionHeuristic {
        private final TextDirectionAlgorithm mAlgorithm;

        TextDirectionHeuristicImpl(TextDirectionAlgorithm algorithm) {
            mAlgorithm = algorithm;
        }

        /** Return true if the default text direction is rtl. */
        protected abstract boolean defaultIsRtl();

        @Override
        public boolean isRtl(char[] array, int start, int count) {
            return isRtl(CharBuffer.wrap(array), start, count);
        }

        @Override
        public boolean isRtl(CharSequence cs, int start, int count) {
            if (cs == null || start < 0 || count < 0 || cs.length() - count < start) {
                throw new IllegalArgumentException();
            }
            if (mAlgorithm == null) {
                return defaultIsRtl();
            }
            return doCheck(cs, start, count);
        }

        private boolean doCheck(CharSequence cs, int start, int count) {
            switch (mAlgorithm.checkRtl(cs, start, count)) {
                case STATE_TRUE:
                    return true;
                case STATE_FALSE:
                    return false;
                default:
                    return defaultIsRtl();
            }
        }
    }

    private static class TextDirectionHeuristicInternal extends TextDirectionHeuristicImpl {
        private final boolean mDefaultIsRtl;

        private TextDirectionHeuristicInternal(TextDirectionAlgorithm algorithm,
                boolean defaultIsRtl) {
            super(algorithm);
            mDefaultIsRtl = defaultIsRtl;
        }

        @Override
        protected boolean defaultIsRtl() {
            return mDefaultIsRtl;
        }
    }

    /** Interface for an algorithm to guess the direction of a paragraph of text. */
    private interface TextDirectionAlgorithm {
        /** Returns whether the range of text is RTL according to the algorithm. */
        int checkRtl(CharSequence cs, int start, int count);
    }

    /** Uses the first strong directional character to determine the paragraph
     *  direction: the standard Unicode Bidirectional Algorithm (steps P2 and
     *  P3), except that UNKNOWN is returned when no strong character is found
     *  so the caller's per-constant default applies. */
    private static class FirstStrong implements TextDirectionAlgorithm {
        @Override
        public int checkRtl(CharSequence cs, int start, int count) {
            int result = STATE_UNKNOWN;
            int openIsolateCount = 0;
            for (int cp, i = start, end = start + count;
                    i < end && result == STATE_UNKNOWN;
                    i += Character.charCount(cp)) {
                cp = Character.codePointAt(cs, i);
                if (0x2066 <= cp && cp <= 0x2068) { // Opening isolates
                    openIsolateCount += 1;
                } else if (cp == 0x2069) { // POP DIRECTIONAL ISOLATE (PDI)
                    if (openIsolateCount > 0) openIsolateCount -= 1;
                } else if (openIsolateCount == 0) {
                    // Only consider the characters outside isolate pairs
                    result = isRtlCodePoint(cp);
                }
            }
            return result;
        }

        private FirstStrong() {
        }

        static final FirstStrong INSTANCE = new FirstStrong();
    }

    /** Uses the presence of any strong directional character of the type given
     *  to the constructor. Characters inside isolate pairs are skipped. */
    private static class AnyStrong implements TextDirectionAlgorithm {
        private final boolean mLookForRtl;

        @Override
        public int checkRtl(CharSequence cs, int start, int count) {
            boolean haveUnlookedFor = false;
            int openIsolateCount = 0;
            for (int cp, i = start, end = start + count; i < end; i += Character.charCount(cp)) {
                cp = Character.codePointAt(cs, i);
                if (0x2066 <= cp && cp <= 0x2068) { // Opening isolates
                    openIsolateCount += 1;
                } else if (cp == 0x2069) { // POP DIRECTIONAL ISOLATE (PDI)
                    if (openIsolateCount > 0) openIsolateCount -= 1;
                } else if (openIsolateCount == 0) {
                    // Only consider the characters outside isolate pairs
                    switch (isRtlCodePoint(cp)) {
                        case STATE_TRUE:
                            if (mLookForRtl) {
                                return STATE_TRUE;
                            }
                            haveUnlookedFor = true;
                            break;
                        case STATE_FALSE:
                            if (!mLookForRtl) {
                                return STATE_FALSE;
                            }
                            haveUnlookedFor = true;
                            break;
                        default:
                            break;
                    }
                }
            }
            if (haveUnlookedFor) {
                return mLookForRtl ? STATE_FALSE : STATE_TRUE;
            }
            return STATE_UNKNOWN;
        }

        private AnyStrong(boolean lookForRtl) {
            this.mLookForRtl = lookForRtl;
        }

        static final AnyStrong INSTANCE_RTL = new AnyStrong(true);
        static final AnyStrong INSTANCE_LTR = new AnyStrong(false);
    }

    /** Uses the Locale direction to force the direction of a paragraph. AOSP
     *  reads it through TextUtils.getLayoutDirectionFromLocale, which is also
     *  what View.resolveLayoutDirection's LAYOUT_DIRECTION_LOCALE branch calls,
     *  so every locale-driven direction decision in the tree agrees. */
    private static class TextDirectionHeuristicLocale extends TextDirectionHeuristicImpl {

        TextDirectionHeuristicLocale() {
            super(null);
        }

        @Override
        protected boolean defaultIsRtl() {
            final int dir = TextUtils.getLayoutDirectionFromLocale(java.util.Locale.getDefault());
            return dir == View.LAYOUT_DIRECTION_RTL;
        }

        static final TextDirectionHeuristicLocale INSTANCE = new TextDirectionHeuristicLocale();
    }
}
