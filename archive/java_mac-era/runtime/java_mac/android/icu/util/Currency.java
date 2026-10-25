package android.icu.util;
import java.util.Locale;
/** ICU currency handle. Backs onto java.util.Currency (the host JVM's data) so
 *  code/symbol/fraction-digits are real.
 *
 *  AOSP: `public class Currency extends MeasureUnit` -- ICU models a currency as a
 *  measure unit (type "currency", subtype = the ISO code) so it can go through
 *  MeasureFormat. The old note here said it was kept standalone "to avoid
 *  coupling"; that broke every use of a Currency where a MeasureUnit is expected.
 *  android.icu.util.Currency (android-30 android.jar). */
public class Currency extends MeasureUnit {
    public static final int SYMBOL_NAME = 0, LONG_NAME = 1, PLURAL_LONG_NAME = 2, NARROW_SYMBOL_NAME = 3;
    private final java.util.Currency delegate;
    protected Currency(java.util.Currency d) {
        // ICU's own identity for a currency unit: type "currency", subtype = code.
        super("currency", (d != null) ? d.getCurrencyCode() : "XXX");
        delegate = d;
    }
    public static Currency getInstance(String theISOCode) { return new Currency(java.util.Currency.getInstance(theISOCode)); }
    public static Currency getInstance(Locale locale) { return new Currency(java.util.Currency.getInstance(locale)); }
    public static Currency getInstance(ULocale locale) { return getInstance(locale != null ? locale.toLocale() : Locale.getDefault()); }
    public String getCurrencyCode() { return delegate.getCurrencyCode(); }
    public String getSymbol() { return delegate.getSymbol(); }
    public String getSymbol(Locale loc) { return delegate.getSymbol(loc); }
    public String getSymbol(ULocale uloc) { return delegate.getSymbol(uloc != null ? uloc.toLocale() : Locale.getDefault()); }
    public int getDefaultFractionDigits() { return delegate.getDefaultFractionDigits(); }
    public int getNumericCode() { return delegate.getNumericCode(); }
    public String getName(Locale locale, int nameStyle, boolean[] isChoiceFormat) { return delegate.getDisplayName(locale); }
    public String getName(ULocale locale, int nameStyle, boolean[] isChoiceFormat) { return delegate.getDisplayName(); }
    @Override public String toString() { return delegate.getCurrencyCode(); }

    /** Bridge to/from the host java.util.Currency the icu NumberFormat delegates use. */
    public java.util.Currency toJavaUtil() { return delegate; }
    public static Currency wrap(java.util.Currency c) { return c == null ? null : new Currency(c); }
}

