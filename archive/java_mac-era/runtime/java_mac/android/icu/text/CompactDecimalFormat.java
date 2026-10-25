package android.icu.text;

/** Formats numbers compactly ("1.2K", "3 million"). Was previously a pure
 *  passthrough to plain DecimalFormat -- format(1200) returned "1200", not
 *  "1.2K" -- which reads visibly wrong in any follower/view-count UI a real
 *  device would compact (the motivating case for this fix).
 *
 *  ICU4J's real implementation (external/icu/android_icu4j/src/main/java/
 *  android/icu/text/CompactDecimalFormat.java) is CLDR resource-bundle
 *  driven: per-locale compact patterns keyed by magnitude AND plural
 *  category. Porting that (and the CLDR data it reads) is out of scope for
 *  a self-contained fix, so this implements only the documented DEFAULT
 *  behaviour, which is locale-independent enough to be useful everywhere:
 *    - "By default, there are 2 significant digits" (CompactDecimalFormat's
 *      own javadoc, same file, lines ~39-41 as fetched from
 *      android.googlesource.com/platform/external/icu +/refs/heads/main).
 *    - SHORT suffixes K/M/B/T and LONG words "thousand"/"million"/
 *      "billion"/"trillion" are CLDR's en decimalFormatLength[@type=short|
 *      long] values -- the ones every other locale falls back to via CLDR's
 *      own root-locale inheritance when a locale doesn't override them, and
 *      the same set used by every ICU-compatible implementation for en.
 *  Every locale is served this English table rather than silently staying a
 *  no-op for non-English locales: an approximately-right compacted number
 *  beats a suspiciously-uncompacted one in a UI that visibly expects "1.2K".
 */
public class CompactDecimalFormat extends DecimalFormat {
    public enum CompactStyle { SHORT, LONG }

    // Largest tier first: index 0 = trillion .. index 3 = thousand. Values
    // below 1000 are never compacted (CLDR defines no pattern under 10^3).
    private static final long[] TIER = { 1_000_000_000_000L, 1_000_000_000L, 1_000_000L, 1_000L };
    private static final String[] SHORT_SUFFIX = { "T", "B", "M", "K" };
    private static final String[] LONG_SUFFIX  = { " trillion", " billion", " million", " thousand" };

    private static final java.text.DecimalFormat PLAIN = new java.text.DecimalFormat("#,##0.###");

    private final CompactStyle style;

    protected CompactDecimalFormat() { this(CompactStyle.SHORT); }
    private CompactDecimalFormat(CompactStyle style) {
        super();
        this.style = (style != null) ? style : CompactStyle.SHORT;
    }

    public static CompactDecimalFormat getInstance(java.util.Locale locale, CompactStyle style) {
        return new CompactDecimalFormat(style);
    }
    public static CompactDecimalFormat getInstance(android.icu.util.ULocale locale, CompactStyle style) {
        return new CompactDecimalFormat(style);
    }

    // android.icu.text.NumberFormat#format(double)/#format(long) are FINAL
    // and call format0().format(number) directly -- they do NOT go through
    // format(double, StringBuffer, FieldPosition), so overriding that
    // 3-arg overload (as plain DecimalFormat does) would never fire for the
    // common `cdf.format(1200)` call apps actually make. The one seam that
    // intercepts every overload is format0() itself: replace the plain
    // java.text.DecimalFormat delegate DecimalFormat's constructor set up
    // with one that renders compact.
    @Override
    protected java.text.NumberFormat format0() { return compactDelegate; }

    private final java.text.NumberFormat compactDelegate = new java.text.DecimalFormat() {
        @Override
        public StringBuffer format(double number, StringBuffer toAppendTo, java.text.FieldPosition pos) {
            return toAppendTo.append(compact(number));
        }
        @Override
        public StringBuffer format(long number, StringBuffer toAppendTo, java.text.FieldPosition pos) {
            return toAppendTo.append(compact((double) number));
        }
    };

    private String compact(double number) {
        boolean neg = number < 0;
        double abs = Math.abs(number);

        int i = -1;
        for (int t = 0; t < TIER.length; t++) {
            if (abs >= TIER[t]) { i = t; break; }
        }
        if (i < 0) {
            return (neg ? "-" : "") + PLAIN.format(abs);
        }

        java.math.BigDecimal rounded = round2sig(abs / (double) TIER[i]);
        // Rounding to 2 significant digits can carry into the next tier up
        // (999,999 -> "1M", not "1000K"): re-divide against the bigger tier
        // whenever the rounded value reaches 1000 in the current one.
        while (rounded.doubleValue() >= 1000 && i > 0) {
            i--;
            rounded = round2sig(abs / (double) TIER[i]);
        }
        String digits = rounded.stripTrailingZeros().toPlainString();
        String suffix = (style == CompactStyle.LONG) ? LONG_SUFFIX[i] : SHORT_SUFFIX[i];
        return (neg ? "-" : "") + digits + suffix;
    }

    /** BigDecimal.valueOf (not `new BigDecimal(double)`) so the source is the
     *  double's own canonical decimal text, not its binary floating-point
     *  imprecision -- avoids e.g. 1.2 rounding from 1.19999999999999996. */
    private static java.math.BigDecimal round2sig(double v) {
        return java.math.BigDecimal.valueOf(v)
            .round(new java.math.MathContext(2, java.math.RoundingMode.HALF_UP));
    }
}
