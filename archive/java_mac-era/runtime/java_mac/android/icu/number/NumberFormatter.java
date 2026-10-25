package android.icu.number;

import android.icu.util.ULocale;

import java.util.Locale;

/**
 * Entry point for ICU's fluent number-formatting API (API 30+).
 *
 * Android exposes ICU4J publicly, so apps call this directly rather than going
 * through java.text. Backed here by java.text.DecimalFormat, which shares the
 * same CLDR-derived locale data on OpenJDK -- so formatted output matches for
 * the ordinary decimal/grouping/precision cases these apps use. The places it
 * cannot match (measure units with full unit-width spelling, compact notation)
 * are documented at the call site rather than silently producing wrong text.
 *
 * Source: https://developer.android.com/reference/android/icu/number/NumberFormatter
 */
public final class NumberFormatter {

    private NumberFormatter() { }

    public enum UnitWidth { NARROW, SHORT, FULL_NAME, ISO_CODE, HIDDEN }

    public enum SignDisplay { AUTO, ALWAYS, NEVER, ACCOUNTING, ACCOUNTING_ALWAYS, EXCEPT_ZERO }

    public enum DecimalSeparatorDisplay { AUTO, ALWAYS }

    public enum GroupingStrategy { OFF, MIN2, AUTO, ON_ALIGNED, THOUSANDS }

    /** Start unlocalized; a locale must be supplied before formatting. */
    public static UnlocalizedNumberFormatter with() {
        return new UnlocalizedNumberFormatter();
    }

    public static LocalizedNumberFormatter withLocale(Locale locale) {
        return new LocalizedNumberFormatter(locale);
    }

    public static LocalizedNumberFormatter withLocale(ULocale locale) {
        return new LocalizedNumberFormatter(locale != null ? locale.toLocale()
                                                           : Locale.getDefault());
    }
}
