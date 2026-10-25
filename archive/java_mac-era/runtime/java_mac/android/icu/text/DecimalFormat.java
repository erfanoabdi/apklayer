// android.icu.text.DecimalFormat -- concrete pattern-driven NumberFormat. ICU4J:
//   developer.android.com/reference/android/icu/text/DecimalFormat
//   public class DecimalFormat extends NumberFormat
//
// Apps construct this directly from a pattern string (e.g. "#,##0.00"). The
// JDK's java.text.DecimalFormat parses the same pattern grammar, so we extend
// our android.icu.text.NumberFormat front and forward everything to a
// java.text.DecimalFormat. applyPattern / toPattern round-trip identically for
// the patterns apps use.
package android.icu.text;

import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Locale;

public class DecimalFormat extends NumberFormat {
    private java.text.DecimalFormat d;   // not final: clone() installs the copy's own formatter

    public DecimalFormat() {
        this.d = new java.text.DecimalFormat();
    }
    public DecimalFormat(String pattern) {
        this.d = new java.text.DecimalFormat(pattern);
    }
    public DecimalFormat(String pattern, DecimalFormatSymbols symbols) {
        this.d = new java.text.DecimalFormat(pattern, symbols.unwrap());
    }
    /** NumberFormat's factories: the JDK's own locale DecimalFormat, wrapped (ICU returns a DecimalFormat). */
    DecimalFormat(java.text.DecimalFormat jdk) {
        this.d = jdk;
    }

    // ---- ICU-only settings the JDK formatter has no equivalent for ------------
    // ICU stores them as properties (-1 = unset); the JDK DecimalFormat has
    // neither significant digits nor a sign-always-shown flag, so they are
    // applied per call in prep() on a configured clone. With neither set the
    // formatter is the untouched JDK path.
    private int mMinSig = -1;
    private int mMaxSig = -1;
    private boolean mSignAlwaysShown;

    /** ICU: true when either significant-digit bound is set. */
    public synchronized boolean areSignificantDigitsUsed() {
        return mMinSig != -1 || mMaxSig != -1;
    }

    /** ICU DecimalFormat.setSignificantDigitsUsed: true installs ICU's legacy defaults 1..6 when unset. */
    public synchronized void setSignificantDigitsUsed(boolean useSignificantDigits) {
        if (useSignificantDigits) {
            if (mMinSig != -1 || mMaxSig != -1) return;
            mMinSig = 1;
            mMaxSig = 6;
        } else {
            mMinSig = -1;
            mMaxSig = -1;
        }
    }

    /** ICU: the effective minimum (1 when unset, ICU's documented default). */
    public synchronized int getMinimumSignificantDigits() {
        return (mMinSig != -1) ? mMinSig : 1;
    }

    /** ICU: the effective maximum (6 when unset, ICU's documented default). */
    public synchronized int getMaximumSignificantDigits() {
        return (mMaxSig != -1) ? mMaxSig : 6;
    }

    /** ICU: raising the minimum above the maximum raises the maximum too. */
    public synchronized void setMinimumSignificantDigits(int value) {
        if (mMaxSig >= 0 && mMaxSig < value) mMaxSig = value;
        mMinSig = value;
    }

    /** ICU: lowering the maximum below the minimum lowers the minimum too. */
    public synchronized void setMaximumSignificantDigits(int value) {
        if (mMinSig >= 0 && mMinSig > value) mMinSig = value;
        mMaxSig = value;
    }

    public synchronized boolean isSignAlwaysShown() { return mSignAlwaysShown; }

    /** ICU: show the plus sign on positive numbers and zero (UNUM_SIGN_ALWAYS). */
    public synchronized void setSignAlwaysShown(boolean value) { mSignAlwaysShown = value; }

    private boolean icuSettingsActive() {
        // CompactDecimalFormat swaps format0() for its own renderer; these settings
        // apply to the plain pattern formatter only.
        return (mMinSig != -1 || mMaxSig != -1 || mSignAlwaysShown) && format0() == d;
    }

    /**
     * -> {java.text.DecimalFormat to use, value to hand it}. Significant digits:
     * round to the maximum (BigDecimal.round with the formatter's rounding mode;
     * BigDecimal.valueOf keeps a double's shortest decimal form, as ICU's
     * DoubleToStringConverter does), then show at least the minimum, which is
     * the fraction-digit window [min - (e+1), max - (e+1)] for a leading digit at
     * 10^e. Sign always shown: the positive affixes become the negative ones with
     * the minus sign replaced by the plus sign.
     */
    private Object[] prep(Object number) {
        java.text.DecimalFormat f = (java.text.DecimalFormat) d.clone();
        Object value = number;
        if ((mMinSig != -1 || mMaxSig != -1) && number instanceof Number) {
            java.math.BigDecimal bd = toBigDecimal((Number) number);
            if (bd != null) {
                int minSig = Math.max(1, (mMinSig != -1) ? mMinSig : 1);
                int maxSig = (mMaxSig != -1) ? Math.max(mMaxSig, minSig) : -1;
                if (maxSig > 0) bd = bd.round(new java.math.MathContext(maxSig, d.getRoundingMode()));
                int e = (bd.signum() == 0) ? 0 : bd.precision() - bd.scale() - 1;
                int minFrac = Math.max(0, minSig - (e + 1));
                int maxFrac = (maxSig > 0) ? Math.max(minFrac, maxSig - (e + 1)) : Math.max(minFrac, 340);
                f.setMinimumFractionDigits(minFrac);
                f.setMaximumFractionDigits(maxFrac);
                value = bd;
            }
        }
        if (mSignAlwaysShown) {
            String minus = String.valueOf(f.getDecimalFormatSymbols().getMinusSign());
            String plus = String.valueOf(new DecimalFormatSymbols(f.getDecimalFormatSymbols()).getPlusSign());
            String np = f.getNegativePrefix(), ns = f.getNegativeSuffix();
            if (np.contains(minus)) f.setPositivePrefix(np.replace(minus, plus));
            else if (ns.contains(minus)) f.setPositiveSuffix(ns.replace(minus, plus));
            else f.setPositivePrefix(plus + f.getPositivePrefix());
        }
        return new Object[] {f, value};
    }

    private static java.math.BigDecimal toBigDecimal(Number n) {
        if (n instanceof java.math.BigDecimal) return (java.math.BigDecimal) n;
        if (n instanceof java.math.BigInteger) return new java.math.BigDecimal((java.math.BigInteger) n);
        if (n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte) {
            return java.math.BigDecimal.valueOf(n.longValue());
        }
        double v = n.doubleValue();
        if (Double.isNaN(v) || Double.isInfinite(v)) return null;
        return java.math.BigDecimal.valueOf(v);
    }

    @Override
    String formatPlain(Object number) {
        if (!icuSettingsActive()) return super.formatPlain(number);
        Object[] pv = prep(number);
        return ((java.text.DecimalFormat) pv[0]).format(pv[1]);
    }

    @Override
    java.text.AttributedCharacterIterator jdkCharacterIterator(Object obj) {
        if (!icuSettingsActive()) return super.jdkCharacterIterator(obj);
        Object[] pv = prep(obj);
        return ((java.text.DecimalFormat) pv[0]).formatToCharacterIterator(pv[1]);
    }

    public void applyPattern(String pattern) { d.applyPattern(pattern); }
    public void applyLocalizedPattern(String pattern) { d.applyLocalizedPattern(pattern); }
    public String toPattern() { return d.toPattern(); }
    public String toLocalizedPattern() { return d.toLocalizedPattern(); }

    public DecimalFormatSymbols getDecimalFormatSymbols() {
        return new DecimalFormatSymbols(d.getDecimalFormatSymbols());
    }
    public void setDecimalFormatSymbols(DecimalFormatSymbols newSymbols) {
        d.setDecimalFormatSymbols(newSymbols.unwrap());
    }

    public void setGroupingSize(int newValue) { d.setGroupingSize(newValue); }
    public int getGroupingSize() { return d.getGroupingSize(); }
    public void setMultiplier(int newValue) { d.setMultiplier(newValue); }
    public int getMultiplier() { return d.getMultiplier(); }
    public void setPositivePrefix(String s) { d.setPositivePrefix(s); }
    public String getPositivePrefix() { return d.getPositivePrefix(); }
    public void setNegativePrefix(String s) { d.setNegativePrefix(s); }
    public String getNegativePrefix() { return d.getNegativePrefix(); }
    public void setPositiveSuffix(String s) { d.setPositiveSuffix(s); }
    public String getPositiveSuffix() { return d.getPositiveSuffix(); }
    public void setNegativeSuffix(String s) { d.setNegativeSuffix(s); }
    public String getNegativeSuffix() { return d.getNegativeSuffix(); }

    @Override protected java.text.NumberFormat format0() { return d; }

    /**
     * ICU's DecimalFormat.clone() is DEEP (the clone owns its own settings); java.text.Format.clone() is
     * a shallow Object.clone(), which would leave the copy sharing this formatter's `d`, so a caller that
     * mutates a clone (MeasureFormat.getNumberFormat() hands one out) would mutate the original. Keep
     * super.clone() (subclass state, the ICU-only sig-digit / sign fields) and give the copy its own `d`.
     */
    @Override public synchronized Object clone() {
        DecimalFormat c = (DecimalFormat) super.clone();
        c.d = (java.text.DecimalFormat) d.clone();
        return c;
    }

    @Override public StringBuffer format(double n, StringBuffer s, FieldPosition p) {
        if (icuSettingsActive()) { Object[] pv = prep(n); return ((java.text.DecimalFormat) pv[0]).format(pv[1], s, p); }
        return d.format(n, s, p);
    }
    @Override public StringBuffer format(long n, StringBuffer s, FieldPosition p) {
        if (icuSettingsActive()) { Object[] pv = prep(n); return ((java.text.DecimalFormat) pv[0]).format(pv[1], s, p); }
        return d.format(n, s, p);
    }
    @Override public StringBuffer format(Object n, StringBuffer s, FieldPosition p) {
        if (icuSettingsActive()) { Object[] pv = prep(n); return ((java.text.DecimalFormat) pv[0]).format(pv[1], s, p); }
        return d.format(n, s, p);
    }
    @Override public Object parseObject(String src, ParsePosition pos) { return d.parseObject(src, pos); }
    @Override public Number parse(String text) throws java.text.ParseException { return d.parse(text); }
    @Override public Number parse(String text, ParsePosition pos) { return d.parse(text, pos); }
    @Override public void setMaximumFractionDigits(int v) { d.setMaximumFractionDigits(v); }
    @Override public void setMinimumFractionDigits(int v) { d.setMinimumFractionDigits(v); }
    @Override public void setMaximumIntegerDigits(int v) { d.setMaximumIntegerDigits(v); }
    @Override public void setMinimumIntegerDigits(int v) { d.setMinimumIntegerDigits(v); }
    @Override public int getMaximumFractionDigits() { return d.getMaximumFractionDigits(); }
    @Override public int getMinimumFractionDigits() { return d.getMinimumFractionDigits(); }
    @Override public int getMaximumIntegerDigits() { return d.getMaximumIntegerDigits(); }
    @Override public int getMinimumIntegerDigits() { return d.getMinimumIntegerDigits(); }
    @Override public void setGroupingUsed(boolean v) { d.setGroupingUsed(v); }
    @Override public boolean isGroupingUsed() { return d.isGroupingUsed(); }
    @Override public void setParseIntegerOnly(boolean v) { d.setParseIntegerOnly(v); }
    @Override public boolean isParseIntegerOnly() { return d.isParseIntegerOnly(); }
    @Override public void setRoundingMode(java.math.RoundingMode m) { d.setRoundingMode(m); }
    @Override public void setCurrency(android.icu.util.Currency c) { d.setCurrency(c == null ? null : c.toJavaUtil()); }
    @Override public android.icu.util.Currency getCurrency() { return android.icu.util.Currency.wrap(d.getCurrency()); }
}
