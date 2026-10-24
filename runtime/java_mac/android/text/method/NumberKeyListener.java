// android.text.method.NumberKeyListener -- AOSP base for key listeners that
// accept only a fixed set of characters (digits, dialer keys, dates). AOSP:
//   frameworks/base/core/java/android/text/method/NumberKeyListener.java
//   public abstract class NumberKeyListener extends BaseKeyListener
//       implements InputFilter
// Subclasses provide getAcceptedChars(); filter() drops every character not in
// that set (returning null to accept unchanged, "" to reject a single char, or
// a stripped copy otherwise) -- exactly the AOSP behavior, which is the part
// apps actually observe (a numeric EditText silently ignores letters).
//
// Promoted from the v1.0.92 empty stub to the real abstract base so
// DigitsKeyListener can extend it faithfully.
package android.text.method;

import android.text.InputFilter;
import android.text.Spanned;
import android.text.SpannableStringBuilder;

public abstract class NumberKeyListener extends BaseKeyListener
        implements InputFilter {

    protected abstract char[] getAcceptedChars();

    // ---------------------------------------------------------------------
    // AOSP NumberKeyListener's package-private locale helpers
    // (NumberKeyListener.java:160-245), used by DateKeyListener /
    // TimeKeyListener / DateTimeKeyListener to widen their accepted-character
    // set with whatever SEPARATORS the requested locale's date/time pattern
    // actually uses (a locale that writes "2024\u5e743\u6708" needs the CJK
    // characters accepted, not just '/' and '-').
    //
    // Each returns FALSE on the first thing it cannot handle, and AOSP's
    // callers then fall back to the fixed CHARACTERS table -- that "all or
    // nothing" shape is deliberate and reproduced: a half-built character set
    // would silently reject valid input.
    // ---------------------------------------------------------------------

    private static final char SINGLE_QUOTE = '\'';

    /** AOSP NumberKeyListener.DATE_TIME_FORMAT_SYMBOLS -- every character that
     *  is a FIELD in a date/time pattern, so anything here that is not in the
     *  caller's symbolsToIgnore means the pattern used a field the caller did
     *  not expect. */
    private static final String DATE_TIME_FORMAT_SYMBOLS =
            "GyYuUrQqMLlwWdDFgEecabBhHKkjJCmsSAzZOvVXx";

    static boolean addFormatCharsFromSkeleton(java.util.Collection<Character> collection,
            java.util.Locale locale, String skeleton, String symbolsToIgnore) {
        if (locale == null) {
            return false;
        }
        final String pattern =
                android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton);
        if (pattern == null) return false;
        boolean outsideQuotes = true;
        for (int i = 0; i < pattern.length(); i++) {
            final char ch = pattern.charAt(i);
            if (Character.isSurrogate(ch)) { // characters outside BMP are not supported.
                return false;
            } else if (ch == SINGLE_QUOTE) {
                outsideQuotes = !outsideQuotes;
                // A single quote counts as a literal only when it follows
                // another single quote.
                if (i == 0 || pattern.charAt(i - 1) != SINGLE_QUOTE) {
                    continue;
                }
            }

            if (outsideQuotes) {
                if (symbolsToIgnore.indexOf(ch) != -1) {
                    // Skip expected pattern characters.
                    continue;
                } else if (DATE_TIME_FORMAT_SYMBOLS.indexOf(ch) != -1) {
                    // An unexpected symbol is seen. We have failed.
                    return false;
                }
            }
            // Either inside quotes, or a non-pattern character outside them:
            // a real character of the formatted date.
            collection.add(Character.valueOf(ch));
        }
        return true;
    }

    static boolean addFormatCharsFromSkeletons(java.util.Collection<Character> collection,
            java.util.Locale locale, String[] skeletons, String symbolsToIgnore) {
        for (int i = 0; i < skeletons.length; i++) {
            if (!addFormatCharsFromSkeleton(collection, locale, skeletons[i], symbolsToIgnore)) {
                return false;
            }
        }
        return true;
    }

    static boolean addAmPmChars(java.util.Collection<Character> collection,
            java.util.Locale locale) {
        if (locale == null) {
            return false;
        }
        final String[] amPm =
                java.text.DateFormatSymbols.getInstance(locale).getAmPmStrings();
        for (int i = 0; i < amPm.length; i++) {
            for (int j = 0; j < amPm[i].length(); j++) {
                final char ch = amPm[i].charAt(j);
                if (Character.isBmpCodePoint(ch)) {
                    collection.add(Character.valueOf(ch));
                } else { // We do not support non-BMP characters.
                    return false;
                }
            }
        }
        return true;
    }

    /** AOSP NumberKeyListener.addDigits: the locale's own decimal digits. */
    static boolean addDigits(java.util.Collection<Character> collection,
            java.util.Locale locale) {
        // AOSP reads the locale's DecimalFormatSymbols zero digit and adds the
        // ten code points starting there, which is what makes a locale using
        // Eastern Arabic numerals accept its own digits.
        char zero = '0';
        if (locale != null) {
            try {
                zero = new java.text.DecimalFormatSymbols(locale).getZeroDigit();
            } catch (Throwable ignored) { /* keep ASCII */ }
        }
        for (int i = 0; i < 10; i++) {
            char ch = (char) (zero + i);
            if (!Character.isBmpCodePoint(ch)) return false;
            collection.add(Character.valueOf(ch));
        }
        return true;
    }

    /** Flatten a Collection&lt;Character&gt; into the sorted char[] the
     *  accepted-character set is stored as. AOSP's collectionToArray. */
    static char[] collectionToArray(java.util.Collection<Character> chars) {
        final char[] result = new char[chars.size()];
        int i = 0;
        for (Character ch : chars) {
            result[i++] = ch.charValue();
        }
        java.util.Arrays.sort(result);
        return result;
    }

    /**
     * AOSP NumberKeyListener.lookup(KeyEvent, Spannable)
     * (NumberKeyListener.java:52-54):
     *     return event.getMatch(getAcceptedChars(), getMetaState(content, event));
     * -- which character this key would produce, restricted to the characters
     * this listener accepts. Subclasses (DigitsKeyListener, DateKeyListener,
     * ...) inherit it and onKeyDown calls it.
     */
    protected int lookup(android.view.KeyEvent event, android.text.Spannable content) {
        return event.getMatch(getAcceptedChars(), getMetaState(content, event));
    }

    /** STATIC, as in AOSP ("protected static boolean ok(char[] accept, char
     *  c)", frameworks/base android14-release core/java/android/text/method/
     *  NumberKeyListener.java:93) and the SDK-34 android.jar. It was an
     *  instance method here, so an app subclass calling ok(...) (compiled as
     *  invokestatic) threw IncompatibleClassChangeError. Found by
     *  debug/apishape_gate.sh (KNOWN_OPEN, struck 2026-09-26). */
    protected static boolean ok(char[] accept, char c) {
        for (int i = accept.length - 1; i >= 0; i--) {
            if (accept[i] == c) return true;
        }
        return false;
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end,
                               Spanned dest, int dstart, int dend) {
        char[] accept = getAcceptedChars();
        int i;
        for (i = start; i < end; i++) {
            if (!ok(accept, source.charAt(i))) break;
        }
        if (i == end) {
            // Everything accepted: keep as-is.
            return null;
        }
        if (end - start == 1) {
            // Reject the single bad character.
            return "";
        }
        SpannableStringBuilder filtered =
            new SpannableStringBuilder(source, start, end);
        i -= start;
        end -= start;
        for (int j = end - 1; j >= i; j--) {
            if (!ok(accept, source.charAt(j + start))) {
                filtered.delete(j, j + 1);
            }
        }
        return filtered;
    }
}
