package android.icu.number;

import android.icu.util.ULocale;

import java.util.Locale;

/**
 * A formatter with settings but no locale yet; locale(...) produces the
 * localized one that can actually format.
 * Source: https://developer.android.com/reference/android/icu/number/UnlocalizedNumberFormatter
 */
public class UnlocalizedNumberFormatter
        extends NumberFormatterSettings<UnlocalizedNumberFormatter> {

    UnlocalizedNumberFormatter() { }

    @Override UnlocalizedNumberFormatter copy() {
        UnlocalizedNumberFormatter c = new UnlocalizedNumberFormatter();
        copyInto(c);
        return c;
    }

    public LocalizedNumberFormatter locale(Locale locale) {
        LocalizedNumberFormatter f = new LocalizedNumberFormatter(locale);
        copyInto(f);
        return f;
    }

    public LocalizedNumberFormatter locale(ULocale locale) {
        return locale(locale != null ? locale.toLocale() : Locale.getDefault());
    }

    public android.icu.number.NumberFormatterSettings usage(String usage) { return this; }
}

