// android.icu.lang.UCharacter -- ICU4J's character-property and case-mapping
// class. Published API:
//   developer.android.com/reference/android/icu/lang/UCharacter
//   public final class UCharacter implements UCharacterEnums.ECharacterCategory, ...
//
// Hermes' Intl (Intl.toLocaleLowerCase / toLocaleUpperCase, i.e. JS
// String.prototype.toLocaleLowerCase) calls UCharacter.toLowerCase(ULocale,
// String) and toUpperCase(ULocale, String). ICU implements those as the full
// Unicode SpecialCasing locale-sensitive mapping (CaseMapImpl.toLower with
// getCaseLocale: tr/az dotted-i, lt soft-dot, el, nl IJ); java.lang.String's
// toLowerCase(Locale) / toUpperCase(Locale) implement the same SpecialCasing
// rules on the JDK, so the String forms delegate there.
//
// Only members whose meaning is identical on java.lang.Character are carried.
// UCharacter.getType() is deliberately NOT: ICU's general-category numbering
// (UCharacterCategory) diverges from Character.getType()'s from PRIVATE_USE on
// (ICU 17, Java 18), so a delegation would answer wrong numbers silently.
package android.icu.lang;

import android.icu.util.ULocale;

import java.util.Locale;

public final class UCharacter {
    public static final int MIN_VALUE = Character.MIN_CODE_POINT;
    public static final int MAX_VALUE = Character.MAX_CODE_POINT;
    public static final int MIN_SUPPLEMENTARY_CODE_POINT = Character.MIN_SUPPLEMENTARY_CODE_POINT;

    private UCharacter() {}

    // ---- string case mapping ------------------------------------------------
    public static String toLowerCase(String str) { return str.toLowerCase(Locale.getDefault()); }
    public static String toLowerCase(Locale locale, String str) {
        return str.toLowerCase(locale != null ? locale : Locale.getDefault());
    }
    public static String toLowerCase(ULocale locale, String str) {
        return str.toLowerCase(ULocale.unwrap(locale));
    }
    public static String toUpperCase(String str) { return str.toUpperCase(Locale.getDefault()); }
    public static String toUpperCase(Locale locale, String str) {
        return str.toUpperCase(locale != null ? locale : Locale.getDefault());
    }
    public static String toUpperCase(ULocale locale, String str) {
        return str.toUpperCase(ULocale.unwrap(locale));
    }

    // ---- code point mapping / predicates (Unicode-defined, same on the JDK) ----
    public static int toLowerCase(int ch) { return Character.toLowerCase(ch); }
    public static int toUpperCase(int ch) { return Character.toUpperCase(ch); }
    public static int toTitleCase(int ch) { return Character.toTitleCase(ch); }
    public static boolean isLetter(int ch) { return Character.isLetter(ch); }
    public static boolean isDigit(int ch) { return Character.isDigit(ch); }
    public static boolean isLetterOrDigit(int ch) { return Character.isLetterOrDigit(ch); }
    public static boolean isLowerCase(int ch) { return Character.isLowerCase(ch); }
    public static boolean isUpperCase(int ch) { return Character.isUpperCase(ch); }
    public static boolean isTitleCase(int ch) { return Character.isTitleCase(ch); }
    public static boolean isSpaceChar(int ch) { return Character.isSpaceChar(ch); }
    public static boolean isISOControl(int ch) { return Character.isISOControl(ch); }
    public static boolean isSupplementary(int ch) { return Character.isSupplementaryCodePoint(ch); }
    public static boolean isBMP(int ch) { return Character.isBmpCodePoint(ch); }
    public static boolean isValidCodePoint(int ch) { return Character.isValidCodePoint(ch); }
    public static int charCount(int ch) { return Character.charCount(ch); }
    public static int digit(int ch, int radix) { return Character.digit(ch, radix); }
    public static int digit(int ch) { return Character.digit(ch, 10); }
    public static String getName(int ch) { return Character.getName(ch); }
    public static String toString(int ch) { return new String(Character.toChars(ch)); }
}
