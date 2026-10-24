// android.icu.text.PluralRules -- CLDR plural-category selection. ICU4J:
//   developer.android.com/reference/android/icu/text/PluralRules
//   public class PluralRules implements Serializable
//
// Maps a number to a CLDR plural keyword ("one"/"few"/"many"/"other"/...) for
// the locale. The JDK has NO equivalent, so this is a REAL (not delegated)
// implementation of the CLDR cardinal plural rules for the major language
// families, sourced from the Unicode CLDR "Language Plural Rules" chart
// (unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html).
// We do not bundle the full per-locale rule table; instead we group locales by
// the well-known CLDR families so the selection is correct for the languages
// real apps localize into. Unknown locales fall back to the English rule
// (one/other), which is the most common shape.
//
// Variables follow CLDR notation: n = absolute value, i = integer part,
// v = number of visible fraction digits, f = fraction digits as an integer.
package android.icu.text;

import android.icu.util.ULocale;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class PluralRules implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    public static final String KEYWORD_ZERO = "zero";
    public static final String KEYWORD_ONE = "one";
    public static final String KEYWORD_TWO = "two";
    public static final String KEYWORD_FEW = "few";
    public static final String KEYWORD_MANY = "many";
    public static final String KEYWORD_OTHER = "other";

    public static final double NO_UNIQUE_VALUE = -0.00123456777;

    public static final int CARDINAL = 0;
    public static final int ORDINAL = 1;

    // The locale family this PluralRules instance evaluates against.
    private final int family;
    private static final int FAM_ENGLISH = 0;   // one (i==1 && v==0), other
    private static final int FAM_FRENCH = 1;     // one (i==0 || i==1), other
    private static final int FAM_OTHER_ONLY = 2; // other (CJK, Thai, etc.)
    private static final int FAM_SLAVIC = 3;     // one/few/many/other (ru/uk/...)
    private static final int FAM_POLISH = 4;     // one/few/many/other (pl)
    private static final int FAM_CZECH = 5;      // one/few/many/other (cs/sk)
    private static final int FAM_ARABIC = 6;     // zero/one/two/few/many/other
    private static final int FAM_HEBREW = 7;     // one/two/other (he)
    private static final int FAM_ROMANIAN = 8;   // one/few/other (ro)
    private static final int FAM_LITHUANIAN = 9; // one/few/many/other (lt)

    private PluralRules(int family) { this.family = family; }

    public static PluralRules forLocale(Locale locale) {
        return new PluralRules(familyFor((locale != null) ? locale : Locale.getDefault()));
    }
    public static PluralRules forLocale(ULocale locale) {
        return forLocale(ULocale.unwrap(locale));
    }
    public static PluralRules forLocale(Locale locale, int type) {
        // ORDINAL rules differ from CARDINAL, but the vast majority of app
        // usage is cardinal (getQuantityString). We evaluate cardinal; ordinal
        // callers get the cardinal family, which is correct for "other"-only
        // languages and a safe approximation elsewhere.
        return forLocale(locale);
    }
    public static PluralRules forLocale(ULocale locale, int type) {
        return forLocale(ULocale.unwrap(locale), type);
    }

    public String select(double number) {
        double n = Math.abs(number);
        long i = (long) n;
        boolean integer = (n == Math.floor(n));
        int v = integer ? 0 : fractionDigits(n);
        long f = integer ? 0 : (long) Math.round((n - i) * Math.pow(10, v));
        return selectInternal(n, i, v, f);
    }

    public String select(long number) {
        long n = Math.abs(number);
        return selectInternal(n, n, 0, 0);
    }

    public String select(int number) {
        return select((long) number);
    }

    private String selectInternal(double n, long i, int v, long f) {
        switch (family) {
            case FAM_OTHER_ONLY:
                return KEYWORD_OTHER;
            case FAM_FRENCH:
                // CLDR fr: one when i in {0,1} and v==0 (or fractional 0.x..1.x).
                return (i == 0 || i == 1) ? KEYWORD_ONE : KEYWORD_OTHER;
            case FAM_SLAVIC: {
                // CLDR ru/uk: integers only branch (v==0).
                if (v == 0) {
                    long mod10 = i % 10, mod100 = i % 100;
                    if (mod10 == 1 && mod100 != 11) return KEYWORD_ONE;
                    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return KEYWORD_FEW;
                    if (mod10 == 0 || (mod10 >= 5 && mod10 <= 9)
                        || (mod100 >= 11 && mod100 <= 14)) return KEYWORD_MANY;
                }
                return KEYWORD_OTHER;
            }
            case FAM_POLISH: {
                // CLDR pl.
                if (v == 0 && i == 1) return KEYWORD_ONE;
                if (v == 0) {
                    long mod10 = i % 10, mod100 = i % 100;
                    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return KEYWORD_FEW;
                    return KEYWORD_MANY;
                }
                return KEYWORD_OTHER;
            }
            case FAM_CZECH: {
                // CLDR cs/sk.
                if (v == 0 && i == 1) return KEYWORD_ONE;
                if (v == 0 && i >= 2 && i <= 4) return KEYWORD_FEW;
                if (v != 0) return KEYWORD_MANY;
                return KEYWORD_OTHER;
            }
            case FAM_ARABIC: {
                // CLDR ar.
                if (n == 0) return KEYWORD_ZERO;
                if (n == 1) return KEYWORD_ONE;
                if (n == 2) return KEYWORD_TWO;
                long mod100 = i % 100;
                if (mod100 >= 3 && mod100 <= 10) return KEYWORD_FEW;
                if (mod100 >= 11 && mod100 <= 99) return KEYWORD_MANY;
                return KEYWORD_OTHER;
            }
            case FAM_HEBREW: {
                // CLDR he (common/supplemental/plurals.xml, cardinal):
                //   one:   i = 1 and v = 0 or i = 0 and v != 0
                //   two:   i = 2 and v = 0
                //   other:
                if ((i == 1 && v == 0) || (i == 0 && v != 0)) return KEYWORD_ONE;
                if (i == 2 && v == 0) return KEYWORD_TWO;
                return KEYWORD_OTHER;
            }
            case FAM_ROMANIAN: {
                // CLDR ro (cardinal):
                //   one:   i = 1 and v = 0
                //   few:   v != 0 or n = 0 or n != 1 and n % 100 = 1..19
                //   other:
                // The n%100 test is only reachable with v==0 (a fractional value
                // already matched the v!=0 disjunct), and there n == i exactly,
                // so evaluating it on the integer part is equivalent and exact.
                if (i == 1 && v == 0) return KEYWORD_ONE;
                if (v != 0 || n == 0 || (n != 1 && inIntegerRange(i % 100, 1, 19)))
                    return KEYWORD_FEW;
                return KEYWORD_OTHER;
            }
            case FAM_LITHUANIAN: {
                // CLDR lt (cardinal):
                //   one:   n % 10 = 1 and n % 100 != 11..19
                //   few:   n % 10 = 2..9 and n % 100 != 11..19
                //   many:  f != 0
                //   other:
                // CLDR range membership ("x = 1..19") holds only for INTEGER x,
                // so a fractional n%100 does not match 11..19 and falls through
                // to the f!=0 "many" rule (e.g. 11.5 is many, 11 is other).
                double mod10 = n % 10, mod100 = n % 100;
                boolean teens = inIntegerRange(mod100, 11, 19);
                if (!teens && inIntegerRange(mod10, 1, 1)) return KEYWORD_ONE;
                if (!teens && inIntegerRange(mod10, 2, 9)) return KEYWORD_FEW;
                if (f != 0) return KEYWORD_MANY;
                return KEYWORD_OTHER;
            }
            case FAM_ENGLISH:
            default:
                // CLDR en: one when i==1 && v==0.
                return (i == 1 && v == 0) ? KEYWORD_ONE : KEYWORD_OTHER;
        }
    }

    /** CLDR "x = lo..hi" range membership: true only when x is an integer in
     *  [lo, hi]. A fractional value never matches a CLDR integer range. */
    private static boolean inIntegerRange(double x, long lo, long hi) {
        return x == Math.floor(x) && x >= lo && x <= hi;
    }

    public Set<String> getKeywords() {
        Set<String> s = new HashSet<>();
        s.add(KEYWORD_OTHER);
        switch (family) {
            case FAM_OTHER_ONLY:
                break;
            case FAM_FRENCH:
            case FAM_ENGLISH:
                s.add(KEYWORD_ONE);
                break;
            case FAM_SLAVIC:
            case FAM_POLISH:
            case FAM_CZECH:
            case FAM_LITHUANIAN:
                s.add(KEYWORD_ONE);
                s.add(KEYWORD_FEW);
                s.add(KEYWORD_MANY);
                break;
            case FAM_HEBREW:
                s.add(KEYWORD_ONE);
                s.add(KEYWORD_TWO);
                break;
            case FAM_ROMANIAN:
                s.add(KEYWORD_ONE);
                s.add(KEYWORD_FEW);
                break;
            case FAM_ARABIC:
                s.add(KEYWORD_ZERO);
                s.add(KEYWORD_ONE);
                s.add(KEYWORD_TWO);
                s.add(KEYWORD_FEW);
                s.add(KEYWORD_MANY);
                break;
        }
        return s;
    }

    private static int fractionDigits(double n) {
        String s = Double.toString(n);
        int dot = s.indexOf('.');
        if (dot < 0) return 0;
        // strip a trailing exponent / "0" tail introduced by Double.toString
        int end = s.length();
        while (end > dot + 2 && s.charAt(end - 1) == '0') end--;
        return end - dot - 1;
    }

    private static int familyFor(Locale locale) {
        String lang = locale.getLanguage();
        if (lang == null) return FAM_ENGLISH;
        lang = lang.toLowerCase(Locale.ROOT);
        // NOTE on the legacy two-letter codes below ("iw", "in", "mo"): ISO 639
        // renamed three languages, and java.util.Locale documents the old forms
        // as still-supported aliases -- he/iw (Hebrew), id/in (Indonesian),
        // yi/ji (Yiddish). getLanguage() passes through whatever code the caller
        // built the Locale with, and Android's own libcore has historically
        // reported the OLD codes (Hebrew is "iw" there), so guest code routinely
        // carries them. Map both spellings or the rule silently falls back to
        // English. "mo" (Moldavian) is CLDR's alias for Romanian.
        switch (lang) {
            // "other"-only: CJK, Thai, Vietnamese, Indonesian, Japanese, Korean...
            case "zh": case "ja": case "ko": case "th": case "vi": case "id":
            case "in": case "ms": case "lo": case "km": case "my":
                return FAM_OTHER_ONLY;
            case "fr": case "pt":
                return FAM_FRENCH;
            case "ru": case "uk": case "be": case "hr": case "sr": case "bs":
                return FAM_SLAVIC;
            case "pl":
                return FAM_POLISH;
            case "cs": case "sk":
                return FAM_CZECH;
            case "ar":
                return FAM_ARABIC;
            case "he": case "iw":
                return FAM_HEBREW;
            case "ro": case "mo":
                return FAM_ROMANIAN;
            case "lt":
                return FAM_LITHUANIAN;
            default:
                return FAM_ENGLISH;
        }
    }

    @Override public String toString() { return "PluralRules(family=" + family + ")"; }
}
