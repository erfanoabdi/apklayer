// android.icu.text.NumberFormat -- ICU4J number formatting (repackage of
// com.ibm.icu.text.NumberFormat). Published API:
//   developer.android.com/reference/android/icu/text/NumberFormat
//   public abstract class NumberFormat extends UFormat
//
// i18n number / currency / percent / scientific formatting. The JDK's
// java.text.NumberFormat produces the same locale output for the
// number/currency/percent/integer styles apps use, so we provide a faithful
// abstract front whose factory methods return a concrete delegate over
// java.text.NumberFormat -- no ICU4J bundle.
//
// UFormat is ICU's abstract base. The note that used to sit here -- "on Mac.apk
// it does not exist as a separate type" -- was the reason this extended
// java.text.Format directly, and it was wrong: android.icu.text.UFormat IS a
// public type in the platform (`public abstract class android.icu.text.UFormat
// extends java.text.Format`, android-30 android.jar), so an app casting a
// NumberFormat to UFormat got a ClassCastException. UFormat now exists here and
// this sits under it, as AOSP has it.
package android.icu.text;

import android.icu.util.ULocale;

import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Locale;

public abstract class NumberFormat extends UFormat {
    public static final int NUMBERSTYLE = 0;
    public static final int CURRENCYSTYLE = 1;
    public static final int PERCENTSTYLE = 2;
    public static final int SCIENTIFICSTYLE = 3;
    public static final int INTEGERSTYLE = 4;
    public static final int ISOCURRENCYSTYLE = 5;
    public static final int PLURALCURRENCYSTYLE = 6;
    public static final int ACCOUNTINGCURRENCYSTYLE = 7;
    public static final int CASHCURRENCYSTYLE = 8;
    public static final int STANDARDCURRENCYSTYLE = 9;

    protected NumberFormat() {}

    public static NumberFormat getInstance() {
        return wrap(java.text.NumberFormat.getInstance());
    }
    public static NumberFormat getInstance(Locale loc) {
        return wrap(java.text.NumberFormat.getInstance(nz(loc)));
    }
    public static NumberFormat getInstance(ULocale loc) {
        return wrap(java.text.NumberFormat.getInstance(ULocale.unwrap(loc)));
    }
    public static NumberFormat getInstance(Locale loc, int style) {
        return wrap(byStyle(nz(loc), style));
    }
    public static NumberFormat getInstance(ULocale loc, int style) {
        return wrap(byStyle(ULocale.unwrap(loc), style));
    }

    public static NumberFormat getNumberInstance() {
        return wrap(java.text.NumberFormat.getNumberInstance());
    }
    public static NumberFormat getNumberInstance(Locale loc) {
        return wrap(java.text.NumberFormat.getNumberInstance(nz(loc)));
    }
    public static NumberFormat getNumberInstance(ULocale loc) {
        return wrap(java.text.NumberFormat.getNumberInstance(ULocale.unwrap(loc)));
    }

    public static NumberFormat getIntegerInstance() {
        return wrap(java.text.NumberFormat.getIntegerInstance());
    }
    public static NumberFormat getIntegerInstance(Locale loc) {
        return wrap(java.text.NumberFormat.getIntegerInstance(nz(loc)));
    }
    public static NumberFormat getIntegerInstance(ULocale loc) {
        return wrap(java.text.NumberFormat.getIntegerInstance(ULocale.unwrap(loc)));
    }

    public static NumberFormat getCurrencyInstance() {
        return wrap(java.text.NumberFormat.getCurrencyInstance());
    }
    public static NumberFormat getCurrencyInstance(Locale loc) {
        return wrap(java.text.NumberFormat.getCurrencyInstance(nz(loc)));
    }
    public static NumberFormat getCurrencyInstance(ULocale loc) {
        return wrap(java.text.NumberFormat.getCurrencyInstance(ULocale.unwrap(loc)));
    }

    public static NumberFormat getPercentInstance() {
        return wrap(java.text.NumberFormat.getPercentInstance());
    }
    public static NumberFormat getPercentInstance(Locale loc) {
        return wrap(java.text.NumberFormat.getPercentInstance(nz(loc)));
    }
    public static NumberFormat getPercentInstance(ULocale loc) {
        return wrap(java.text.NumberFormat.getPercentInstance(ULocale.unwrap(loc)));
    }

    // The JDK has no scientific NumberFormat factory; ICU does. Fall back to a
    // plain number instance (the common consumer just wants formatted output).
    public static NumberFormat getScientificInstance() {
        return wrap(java.text.NumberFormat.getNumberInstance());
    }
    public static NumberFormat getScientificInstance(Locale loc) {
        return wrap(java.text.NumberFormat.getNumberInstance(nz(loc)));
    }
    public static NumberFormat getScientificInstance(ULocale loc) {
        return wrap(java.text.NumberFormat.getNumberInstance(ULocale.unwrap(loc)));
    }

    private static Locale nz(Locale l) { return (l != null) ? l : Locale.getDefault(); }

    private static java.text.NumberFormat byStyle(Locale loc, int style) {
        switch (style) {
            case CURRENCYSTYLE:
            case ISOCURRENCYSTYLE:
            case PLURALCURRENCYSTYLE:
            case ACCOUNTINGCURRENCYSTYLE:
            case CASHCURRENCYSTYLE:
            case STANDARDCURRENCYSTYLE:
                return java.text.NumberFormat.getCurrencyInstance(loc);
            case PERCENTSTYLE:
                return java.text.NumberFormat.getPercentInstance(loc);
            case INTEGERSTYLE:
                return java.text.NumberFormat.getIntegerInstance(loc);
            case NUMBERSTYLE:
            case SCIENTIFICSTYLE:
            default:
                return java.text.NumberFormat.getNumberInstance(loc);
        }
    }

    // ICU's factories hand back a DecimalFormat for every pattern-based style
    // (NumberFormat.createInstance: `new DecimalFormat(pattern, symbols, style)`),
    // and callers rely on it: Hermes' PlatformNumberFormatterICU applies
    // significant digits, signDisplay and the currency symbol only
    // `if (mNumberFormat instanceof android.icu.text.DecimalFormat)`. The JDK's
    // factories likewise return a java.text.DecimalFormat, so wrap that AS our
    // DecimalFormat. MACAPK_ICU_PLATFORM_TYPES=0 restores the old opaque wrapper.
    static NumberFormat wrap(java.text.NumberFormat d) {
        if (com.macapk.icu.HostIcu.PLATFORM_TYPES && d instanceof java.text.DecimalFormat) {
            return new DecimalFormat((java.text.DecimalFormat) d);
        }
        return new JdkNumberFormat(d);
    }

    // ---- instance API (abstract front, mirrors ICU) ----------------------
    public final String format(double number) { return formatPlain(number); }
    public final String format(long number) { return formatPlain(number); }
    public final String format(java.math.BigInteger number) { return formatPlain(number); }
    public final String format(java.math.BigDecimal number) { return formatPlain(number); }

    // The one seam every format(x) overload goes through. java.text.Format's
    // format(Object) reaches the same java.text.DecimalFormat.format(double|long,
    // StringBuffer, FieldPosition) the primitive overloads do, so the default is
    // output-identical to the old direct calls; DecimalFormat overrides it to
    // apply ICU's significant-digit / sign-always-shown settings.
    String formatPlain(Object number) { return format0().format(number); }

    /**
     * ICU NumberFormat.setRoundingMode(int): takes a java.math.BigDecimal.ROUND_*
     * constant (Hermes: setRoundingMode(BigDecimal.ROUND_HALF_UP)). The int
     * values are RoundingMode's ordinals; java.text.NumberFormat throws
     * UnsupportedOperationException for a subclass that does not round, as
     * ICU's base does.
     */
    public void setRoundingMode(int roundingMode) {
        if (roundingMode < 0 || roundingMode > 7) {
            throw new IllegalArgumentException("Invalid rounding mode: " + roundingMode);
        }
        format0().setRoundingMode(java.math.RoundingMode.valueOf(roundingMode));
    }

    public int getRoundingMode() {
        return format0().getRoundingMode().ordinal();
    }

    /**
     * ICU's NumberFormat formatToCharacterIterator marks each part with an
     * android.icu.text.NumberFormat.Field (Hermes' formatToParts compares the
     * keys with ==). The JDK marks the same parts with java.text's Field
     * constants of the same names, so translate them. This class did not
     * override the method before, i.e. it returned java.text.Format's
     * attribute-less iterator; MACAPK_ICU_PLATFORM_TYPES=0 keeps that.
     */
    @Override
    public java.text.AttributedCharacterIterator formatToCharacterIterator(Object obj) {
        if (!com.macapk.icu.HostIcu.PLATFORM_TYPES) return super.formatToCharacterIterator(obj);
        java.text.NumberFormat f = format0();
        // A delegate that is not a stock java.text formatter (CompactDecimalFormat's
        // overriding subclass) renders differently from its own iterator path.
        if (!f.getClass().getName().startsWith("java.text.")) return super.formatToCharacterIterator(obj);
        return Field.translate(jdkCharacterIterator(obj));
    }

    java.text.AttributedCharacterIterator jdkCharacterIterator(Object obj) {
        return format0().formatToCharacterIterator(obj);
    }

    /** ICU NumberFormat.Field (the names are ICU's, which java.text's share). */
    public static class Field extends java.text.Format.Field {
        private static final long serialVersionUID = 1L;

        public static final Field SIGN = new Field("sign");
        public static final Field INTEGER = new Field("integer");
        public static final Field FRACTION = new Field("fraction");
        public static final Field EXPONENT = new Field("exponent");
        public static final Field EXPONENT_SIGN = new Field("exponent sign");
        public static final Field EXPONENT_SYMBOL = new Field("exponent symbol");
        public static final Field DECIMAL_SEPARATOR = new Field("decimal separator");
        public static final Field GROUPING_SEPARATOR = new Field("grouping separator");
        public static final Field PERCENT = new Field("percent");
        public static final Field PERMILLE = new Field("per mille");
        public static final Field CURRENCY = new Field("currency");
        public static final Field MEASURE_UNIT = new Field("measure unit");
        public static final Field COMPACT = new Field("compact");

        protected Field(String fieldName) {
            super(fieldName);
        }

        private static final java.util.Map<java.text.AttributedCharacterIterator.Attribute,
                java.text.AttributedCharacterIterator.Attribute> FROM_JDK = new java.util.HashMap<>();
        static {
            FROM_JDK.put(java.text.NumberFormat.Field.SIGN, SIGN);
            FROM_JDK.put(java.text.NumberFormat.Field.INTEGER, INTEGER);
            FROM_JDK.put(java.text.NumberFormat.Field.FRACTION, FRACTION);
            FROM_JDK.put(java.text.NumberFormat.Field.EXPONENT, EXPONENT);
            FROM_JDK.put(java.text.NumberFormat.Field.EXPONENT_SIGN, EXPONENT_SIGN);
            FROM_JDK.put(java.text.NumberFormat.Field.EXPONENT_SYMBOL, EXPONENT_SYMBOL);
            FROM_JDK.put(java.text.NumberFormat.Field.DECIMAL_SEPARATOR, DECIMAL_SEPARATOR);
            FROM_JDK.put(java.text.NumberFormat.Field.GROUPING_SEPARATOR, GROUPING_SEPARATOR);
            FROM_JDK.put(java.text.NumberFormat.Field.PERCENT, PERCENT);
            FROM_JDK.put(java.text.NumberFormat.Field.PERMILLE, PERMILLE);
            FROM_JDK.put(java.text.NumberFormat.Field.CURRENCY, CURRENCY);
        }

        static java.text.AttributedCharacterIterator translate(java.text.AttributedCharacterIterator it) {
            return UFormat.translateAttributes(it, FROM_JDK);
        }
    }

    public abstract StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos);
    public abstract StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos);

    public abstract Number parse(String text) throws java.text.ParseException;
    public abstract Number parse(String text, ParsePosition parsePosition);

    public abstract void setMaximumFractionDigits(int newValue);
    public abstract void setMinimumFractionDigits(int newValue);
    public abstract void setMaximumIntegerDigits(int newValue);
    public abstract void setMinimumIntegerDigits(int newValue);
    public abstract int getMaximumFractionDigits();
    public abstract int getMinimumFractionDigits();
    public abstract int getMaximumIntegerDigits();
    public abstract int getMinimumIntegerDigits();
    public abstract void setGroupingUsed(boolean newValue);
    public abstract boolean isGroupingUsed();
    public abstract void setParseIntegerOnly(boolean value);
    public abstract boolean isParseIntegerOnly();
    public abstract void setRoundingMode(java.math.RoundingMode roundingMode);
    public abstract void setCurrency(android.icu.util.Currency currency);
    public abstract android.icu.util.Currency getCurrency();

    // Used by the final format(...) helpers above. The delegate subclass
    // overrides this to expose its java.text.NumberFormat.
    protected abstract java.text.NumberFormat format0();

    private static final class JdkNumberFormat extends NumberFormat {
        private final java.text.NumberFormat d;
        JdkNumberFormat(java.text.NumberFormat d) { this.d = d; }
        @Override protected java.text.NumberFormat format0() { return d; }
        @Override public StringBuffer format(double n, StringBuffer s, FieldPosition p) { return d.format(n, s, p); }
        @Override public StringBuffer format(long n, StringBuffer s, FieldPosition p) { return d.format(n, s, p); }
        @Override public StringBuffer format(Object n, StringBuffer s, FieldPosition p) { return d.format(n, s, p); }
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
}
