package android.text;

/**
 * Character-property helpers Android historically exposed for BiDi and CJK text.
 * Referenced by 10 of 37 corpus apps (older text-layout code).
 *
 * getMirror returns the BiDi mirror of a bracket-like character; getEastAsianWidth
 * classifies a char's East-Asian width (used to decide monospace column width).
 * Both are pure character-property lookups, implemented for real here from the
 * documented Unicode ranges rather than stubbed.
 *
 * Source: frameworks/base/core/java/android/text/AndroidCharacter.java
 */
public class AndroidCharacter {

    public static final int EAST_ASIAN_WIDTH_NEUTRAL = 0;
    public static final int EAST_ASIAN_WIDTH_AMBIGUOUS = 1;
    public static final int EAST_ASIAN_WIDTH_HALF_WIDTH = 2;
    public static final int EAST_ASIAN_WIDTH_FULL_WIDTH = 3;
    public static final int EAST_ASIAN_WIDTH_NARROW = 4;
    public static final int EAST_ASIAN_WIDTH_WIDE = 5;

    /** BiDi mirror: paired brackets/relational operators swap; others unchanged. */
    public static char getMirror(char c) {
        switch (c) {
            case '(': return ')';   case ')': return '(';
            case '[': return ']';   case ']': return '[';
            case '{': return '}';   case '}': return '{';
            case '<': return '>';   case '>': return '<';
            case '‹': return '›'; case '›': return '‹'; // single angle quotes
            case '«': return '»'; case '»': return '«'; // guillemets
            default: return c;
        }
    }

    public static void getMirror(char[] chars, int start, int count) {
        for (int i = start; i < start + count && i < chars.length; i++) {
            chars[i] = getMirror(chars[i]);
        }
    }

    /** East-Asian width class from the Unicode Wide/Fullwidth ranges. */
    public static int getEastAsianWidth(char c) {
        if ((c >= 0x1100 && c <= 0x115F)   // Hangul Jamo
                || (c >= 0x2E80 && c <= 0x303E)   // CJK Radicals .. Kangxi
                || (c >= 0x3041 && c <= 0x33FF)   // Hiragana .. CJK symbols
                || (c >= 0x3400 && c <= 0x4DBF)   // CJK Ext A
                || (c >= 0x4E00 && c <= 0x9FFF)   // CJK Unified
                || (c >= 0xA000 && c <= 0xA4CF)   // Yi
                || (c >= 0xAC00 && c <= 0xD7A3)   // Hangul Syllables
                || (c >= 0xF900 && c <= 0xFAFF)   // CJK Compatibility Ideographs
                || (c >= 0xFE30 && c <= 0xFE4F)) { // CJK Compatibility Forms
            return EAST_ASIAN_WIDTH_WIDE;
        }
        if (c >= 0xFF01 && c <= 0xFF60) return EAST_ASIAN_WIDTH_FULL_WIDTH; // Fullwidth forms
        if (c >= 0xFF61 && c <= 0xFFDC) return EAST_ASIAN_WIDTH_HALF_WIDTH; // Halfwidth forms
        if (c < 0x80) return EAST_ASIAN_WIDTH_NARROW;
        return EAST_ASIAN_WIDTH_NEUTRAL;
    }

    public static int getEastAsianWidth(char[] chars, int start, int count) {
        int result = EAST_ASIAN_WIDTH_NEUTRAL;
        for (int i = start; i < start + count && i < chars.length; i++) {
            result = getEastAsianWidth(chars[i]);
        }
        return result;
    }

    /**
     * AOSP AndroidCharacter.getDirectionalities(char[] src, byte[] dest, int
     * count) -- a native bulk form of Character.getDirectionality(), filling
     * dest[i] with the Unicode bidi class of src[i] for i in [0, count). The
     * byte values are the java.lang.Character.DIRECTIONALITY_* constants, which
     * ARE the ICU/Unicode bidi class numbers AOSP's native implementation
     * returns, so delegating is exact rather than approximate.
     *
     * AOSP throws when either array is too short; reproduced.
     */
    public static void getDirectionalities(char[] src, byte[] dest, int count) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (count < 0 || src.length < count || dest.length < count) {
            throw new ArrayIndexOutOfBoundsException();
        }
        for (int i = 0; i < count; i++) {
            dest[i] = Character.getDirectionality(src[i]);
        }
    }

    /**
     * AOSP AndroidCharacter.getEastAsianWidths(char[] src, int start, int
     * count, byte[] dest) -- the bulk form of getEastAsianWidth(char), writing
     * dest[i] for i in [0, count) from src[start + i].
     *
     * Note the argument shape, which is easy to get backwards: the WINDOW
     * applies to `src` and `dest` is filled from index 0.
     */
    public static void getEastAsianWidths(char[] src, int start, int count, byte[] dest) {
        if (src == null || dest == null) {
            throw new NullPointerException();
        }
        if (start < 0 || count < 0 || start + count > src.length || dest.length < count) {
            throw new ArrayIndexOutOfBoundsException();
        }
        for (int i = 0; i < count; i++) {
            dest[i] = (byte) getEastAsianWidth(src[start + i]);
        }
    }

    public static boolean mirror(char[] text, int start, int count) {
        boolean changed = false;
        for (int i = start; i < start + count && i < text.length; i++) {
            char m = getMirror(text[i]);
            if (m != text[i]) { text[i] = m; changed = true; }
        }
        return changed;
    }
}
