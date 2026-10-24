// android.icu.text.NumberingSystem -- a numbering system (digits + radix).
// Published API:
//   developer.android.com/reference/android/icu/text/NumberingSystem
//   public class NumberingSystem
//
// Hermes' Intl resolves a numberingSystem option with getInstanceByName(name)
// (null -> RangeError "Invalid numbering system"), validates a -u-nu- value
// against getAvailableNames(), and reports the locale default with
// getInstance(ULocale).getName(). ICU answers all three from its
// numberingSystems.txt + each locale's NumberElements/default.
//
// The formatters on this layer are the JDK's (java.text.DecimalFormat), which
// honour a -u-nu- keyword for exactly the numeric systems whose digits are a
// run of ten consecutive BMP code points; algorithmic systems (roman, hebr,
// ...) and supplementary-plane digits (adlm, ...) are not formattable there.
// So the AVAILABLE set is computed by asking the host: every numeric system
// CLDR defines (the candidate names below, icu4c/source/data/misc/
// numberingSystems.txt entries with algorithmic:int{0}) whose zero digit the
// JDK's DecimalFormatSymbols actually adopts for "und-u-nu-<name>". A system
// the formatters would silently render in Latin digits is not advertised --
// that is the honest answer, and the one a caller validating against it needs.
// The locale default is read the same way: the zero digit of the locale's own
// DecimalFormatSymbols (CLDR NumberElements/default, as the JDK resolves it).
package android.icu.text;

import android.icu.util.ULocale;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class NumberingSystem {
    // icu4c/source/data/misc/numberingSystems.txt: every algorithmic:int{0} entry.
    private static final String[] CLDR_NUMERIC = {
        "adlm", "ahom", "arab", "arabext", "bali", "beng", "bhks", "brah", "cakm", "cham",
        "deva", "diak", "fullwide", "gara", "gong", "gonm", "gujr", "gukh", "guru", "hanidec",
        "hmng", "hmnp", "java", "kali", "kawi", "khmr", "knda", "krai", "lana", "lanatham",
        "laoo", "latn", "lepc", "limb", "mathbold", "mathdbl", "mathmono", "mathsanb",
        "mathsans", "mlym", "modi", "mong", "mroo", "mtei", "mymr", "mymrepka", "mymrpao",
        "mymrshan", "mymrtlng", "nagm", "newa", "nkoo", "olck", "onao", "orya", "osma",
        "outlined", "rohg", "saur", "segment", "shrd", "sind", "sinh", "sora", "sund", "sunu",
        "takr", "talu", "tamldec", "telu", "thai", "tibt", "tirh", "tnsa", "tols", "vaii",
        "wara", "wcho",
    };
    // ICU NumberingSystem.OTHER_NS_KEYWORDS: keyword values that name a CATEGORY,
    // resolved through the locale's data rather than by name.
    private static final String[] OTHER_NS_KEYWORDS = {"native", "traditional", "finance"};

    public static final NumberingSystem LATIN = new NumberingSystem("latn", 10, false, "0123456789");

    private final String name;
    private final int radix;
    private final boolean algorithmic;
    private final String desc;

    /** ICU: the default (Latin decimal) system. */
    public NumberingSystem() {
        this("latn", 10, false, "0123456789");
    }

    private NumberingSystem(String name, int radix, boolean algorithmic, String desc) {
        this.name = name;
        this.radix = radix;
        this.algorithmic = algorithmic;
        this.desc = desc;
    }

    /** name -> digits, for the systems the host formatters honour (built once). */
    private static volatile Map<String, String> sHonoured;

    private static Map<String, String> honoured() {
        Map<String, String> m = sHonoured;
        if (m == null) {
            TreeMap<String, String> t = new TreeMap<>();
            for (String n : CLDR_NUMERIC) {
                char zero;
                try {
                    zero = java.text.DecimalFormatSymbols.getInstance(
                            Locale.forLanguageTag("und-u-nu-" + n)).getZeroDigit();
                } catch (RuntimeException e) {
                    continue;
                }
                if (zero == '0' && !"latn".equals(n)) continue;   // fell back to Latin: not honoured
                StringBuilder d = new StringBuilder(10);
                for (int i = 0; i < 10; i++) d.append((char) (zero + i));
                t.put(n, d.toString());
            }
            m = java.util.Collections.unmodifiableMap(t);
            sHonoured = m;
        }
        return m;
    }

    /** ICU: a system by name, or null when there is no such (honourable) system. */
    public static NumberingSystem getInstanceByName(String name) {
        if (name == null) return null;
        String d = honoured().get(name);
        return (d == null) ? null : new NumberingSystem(name, 10, false, d);
    }

    public static String[] getAvailableNames() {
        return honoured().keySet().toArray(new String[0]);
    }

    public static NumberingSystem getInstance() {
        return getInstance(ULocale.getDefault(ULocale.Category.FORMAT));
    }

    public static NumberingSystem getInstance(Locale inLocale) {
        return getInstance(ULocale.forLocale(inLocale));
    }

    /**
     * ICU: the "numbers" keyword if it names a system, else the locale's
     * default (ICU NumberingSystem.getInstance(ULocale): a category keyword --
     * native / traditional / finance -- or an unknown name falls back to the
     * locale data).
     */
    public static NumberingSystem getInstance(ULocale locale) {
        if (locale == null) locale = ULocale.getDefault(ULocale.Category.FORMAT);
        String kw = locale.getKeywordValue("numbers");
        if (kw != null) {
            boolean category = false;
            for (String k : OTHER_NS_KEYWORDS) if (k.equals(kw)) category = true;
            if (!category && !"default".equals(kw)) {
                NumberingSystem ns = getInstanceByName(kw);
                if (ns != null) return ns;
            }
        }
        char zero;
        try {
            zero = java.text.DecimalFormatSymbols.getInstance(
                    ULocale.unwrap(locale).stripExtensions()).getZeroDigit();
        } catch (RuntimeException e) {
            return new NumberingSystem();
        }
        if (zero == '0') return new NumberingSystem();
        for (Map.Entry<String, String> e : honoured().entrySet()) {
            if (e.getValue().charAt(0) == zero) {
                return new NumberingSystem(e.getKey(), 10, false, e.getValue());
            }
        }
        return new NumberingSystem();
    }

    /** ICU: an unnamed system from a radix and description (e.g. 10, false, "0123456789"). */
    public static NumberingSystem getInstance(int radix, boolean isAlgorithmic, String desc) {
        if (radix < 2) throw new IllegalArgumentException("Invalid radix for numbering system");
        if (!isAlgorithmic && (desc == null || desc.codePointCount(0, desc.length()) != radix)) {
            throw new IllegalArgumentException("Numbering system's radix and digit string are inconsistent");
        }
        return new NumberingSystem("", radix, isAlgorithmic, desc);
    }

    public int getRadix() { return radix; }
    public String getDescription() { return desc; }
    public String getName() { return name; }
    public boolean isAlgorithmic() { return algorithmic; }
}
