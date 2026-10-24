// android.icu.text.DecimalFormatSymbols -- the locale-specific symbol set
// (decimal separator, grouping separator, currency sign, etc.) a DecimalFormat
// uses. ICU4J:
//   developer.android.com/reference/android/icu/text/DecimalFormatSymbols
//   public class DecimalFormatSymbols implements Cloneable, Serializable
//
// (v1.0.92 shipped a bare empty stub here; any accessor threw NoSuchMethodError.
// This is now a faithful delegate over java.text.DecimalFormatSymbols, which
// carries the identical symbol set for the locales apps exercise.)
package android.icu.text;

import android.icu.util.ULocale;

import java.util.Locale;

public class DecimalFormatSymbols implements Cloneable, java.io.Serializable {
    private static final long serialVersionUID = 1L;

    // ICU's default digit characters, used as the fallback when a digit-string
    // array cannot be represented as ten single-UTF-16-char digits (ICU's
    // DEF_DIGIT_CHARS_ARRAY).
    private static final char[] DEF_DIGIT_CHARS_ARRAY =
        {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

    private final java.text.DecimalFormatSymbols d;

    // ICU 58+ models the digits as ten STRINGS (a numbering system's digit can
    // be a surrogate pair, e.g. the SMP math-alphanumeric digit sets), with the
    // older char[] digits / zeroDigit kept in sync as a lossy view. java.text's
    // symbol set only carries zeroDigit, so we hold the string form here and
    // keep the delegate's zeroDigit synchronized in both directions.
    private String[] digitStrings;
    private char[] digits;

    public DecimalFormatSymbols() {
        this.d = new java.text.DecimalFormatSymbols();
        initDigitsFromZeroDigit();
    }
    public DecimalFormatSymbols(Locale locale) {
        this.d = new java.text.DecimalFormatSymbols(
            (locale != null) ? locale : Locale.getDefault());
        initDigitsFromZeroDigit();
    }
    public DecimalFormatSymbols(ULocale locale) {
        this.d = new java.text.DecimalFormatSymbols(ULocale.unwrap(locale));
        initDigitsFromZeroDigit();
    }
    DecimalFormatSymbols(java.text.DecimalFormatSymbols delegate) {
        this.d = delegate;
        initDigitsFromZeroDigit();
    }

    // Derive digits 0-9 from the delegate's zero digit by incrementing the code
    // point one by one. This is exactly what ICU's setZeroDigit() does ("Always
    // propagate to digits 1-9 for JDK and ICU4C consistency") and what
    // java.text.DecimalFormat itself assumes when it formats a digit as
    // zeroDigit + value, so the two stay consistent for every numbering system
    // java.text can express (Latin, Arabic-Indic, Devanagari, ... are all
    // contiguous runs starting at their zero digit).
    private void initDigitsFromZeroDigit() {
        char zero = d.getZeroDigit();
        String[] ds = new String[10];
        char[] dc = new char[10];
        for (int i = 0; i < 10; i++) {
            char c = (char) (zero + i);
            ds[i] = String.valueOf(c);
            dc[i] = c;
        }
        this.digitStrings = ds;
        this.digits = dc;
    }

    public static DecimalFormatSymbols getInstance() {
        return new DecimalFormatSymbols(java.text.DecimalFormatSymbols.getInstance());
    }
    public static DecimalFormatSymbols getInstance(Locale locale) {
        return new DecimalFormatSymbols(java.text.DecimalFormatSymbols.getInstance(
            (locale != null) ? locale : Locale.getDefault()));
    }
    public static DecimalFormatSymbols getInstance(ULocale locale) {
        return new DecimalFormatSymbols(
            java.text.DecimalFormatSymbols.getInstance(ULocale.unwrap(locale)));
    }

    public char getDecimalSeparator() { return d.getDecimalSeparator(); }
    public void setDecimalSeparator(char c) { d.setDecimalSeparator(c); }
    public char getGroupingSeparator() { return d.getGroupingSeparator(); }
    public void setGroupingSeparator(char c) { d.setGroupingSeparator(c); }
    public char getPatternSeparator() { return d.getPatternSeparator(); }
    public void setPatternSeparator(char c) { d.setPatternSeparator(c); }
    public char getPercent() { return d.getPercent(); }
    public void setPercent(char c) { d.setPercent(c); }
    public char getPerMill() { return d.getPerMill(); }
    public void setPerMill(char c) { d.setPerMill(c); }
    public char getDigit() { return d.getDigit(); }
    public void setDigit(char c) { d.setDigit(c); }
    public char getZeroDigit() { return d.getZeroDigit(); }
    // ICU: "Note: This method propagates digit 1 to digit 9 by incrementing
    // code point one by one", keeping digitStrings and digits in sync.
    public void setZeroDigit(char c) { d.setZeroDigit(c); initDigitsFromZeroDigit(); }

    /** ICU DecimalFormatSymbols.getDigits(): the ten digit CHARACTERS, 0 through 9. */
    public char[] getDigits() { return digits.clone(); }

    /**
     * ICU DecimalFormatSymbols.getDigitStrings(): the ten digit STRINGS, 0
     * through 9. Since ICU 58 this is the preferred accessor and getDigits() /
     * getZeroDigit() are documented as discouraged, so modern formatting code
     * reads its digit glyphs from here.
     */
    public String[] getDigitStrings() { return digitStrings.clone(); }

    /**
     * ICU DecimalFormatSymbols.setDigitStrings(String[]). Validation and the
     * char[]/zeroDigit fallback mirror ICU exactly: the array must be non-null
     * and exactly 10 long with no null element, and when any digit is not a
     * single UTF-16 code unit the char-based views fall back to the default
     * '0'-'9' (ICU: "When the input array of digit strings contains any strings
     * represented by multiple Java chars, then getDigits() will return the
     * default digits and getZeroDigit() will return the default zero digit").
     */
    public void setDigitStrings(String[] digitStrings) {
        if (digitStrings == null) {
            throw new NullPointerException("The input digit string array is null");
        }
        if (digitStrings.length != 10) {
            throw new IllegalArgumentException("Number of digit strings is not 10");
        }
        String[] tmpDigitStrings = new String[10];
        char[] tmpDigits = new char[10];
        for (int i = 0; i < 10; i++) {
            String digitStr = digitStrings[i];
            if (digitStr == null) {
                throw new IllegalArgumentException(
                    "The input digit string array contains a null element");
            }
            tmpDigitStrings[i] = digitStr;
            if (tmpDigits != null) {
                if (digitStr.length() == 1) {
                    tmpDigits[i] = digitStr.charAt(0);
                } else {
                    // Zero-length, or a surrogate pair / multi-code-point digit:
                    // not representable as a char, so the whole char[] view is
                    // invalidated (ICU sets tmpDigits = null here).
                    tmpDigits = null;
                }
            }
        }
        this.digitStrings = tmpDigitStrings;
        if (tmpDigits == null) {
            this.digits = DEF_DIGIT_CHARS_ARRAY.clone();
        } else {
            this.digits = tmpDigits;
        }
        // Push the zero digit down to the java.text delegate so the symbols we
        // hand a DecimalFormat actually format with the requested numbering
        // system, rather than silently keeping the old one. ICU likewise sets
        // zeroDigit = tmpDigits[0] here. Caveat: java.text renders digit i as
        // zeroDigit + i, so a NON-contiguous custom digit set round-trips
        // correctly through getDigitStrings() but formats from digits[0]
        // onward; ICU's own codePointZero == -1 path (format straight from the
        // string array) has no java.text equivalent to delegate to.
        d.setZeroDigit(this.digits[0]);
    }
    // ICU's plus sign. java.text.DecimalFormatSymbols does not expose CLDR's
    // plusSign, so this holds ICU's own fallback: DecimalFormatSymbols.setPlusSignString
    // keeps a one-char string as the char and otherwise uses DEF_PLUS_SIGN = '+'.
    // CLDR's plusSign is "+" everywhere except the bidi locales, where it is a
    // two-char string (a direction mark + '+'), for which ICU's getPlusSign()
    // therefore ALSO answers '+'. Only getPlusSignString() differs there.
    private String plusString = "+";
    public char getPlusSign() { return plusString.length() == 1 ? plusString.charAt(0) : '+'; }
    public void setPlusSign(char plus) { plusString = String.valueOf(plus); }
    public String getPlusSignString() { return plusString; }
    public void setPlusSignString(String plusSignString) {
        if (plusSignString == null) throw new NullPointerException("The input plus sign is null");
        plusString = plusSignString;
    }
    public String getMinusSignString() { return String.valueOf(d.getMinusSign()); }
    public char getMinusSign() { return d.getMinusSign(); }
    public void setMinusSign(char c) { d.setMinusSign(c); }
    public char getMonetaryDecimalSeparator() { return d.getMonetaryDecimalSeparator(); }
    public void setMonetaryDecimalSeparator(char c) { d.setMonetaryDecimalSeparator(c); }
    public String getInfinity() { return d.getInfinity(); }
    public void setInfinity(String s) { d.setInfinity(s); }
    public String getNaN() { return d.getNaN(); }
    public void setNaN(String s) { d.setNaN(s); }
    public String getCurrencySymbol() { return d.getCurrencySymbol(); }
    public void setCurrencySymbol(String s) { d.setCurrencySymbol(s); }
    public String getInternationalCurrencySymbol() { return d.getInternationalCurrencySymbol(); }
    public void setInternationalCurrencySymbol(String s) { d.setInternationalCurrencySymbol(s); }

    // Internal: hand the underlying JDK symbols to a delegating DecimalFormat.
    java.text.DecimalFormatSymbols unwrap() { return d; }

    @Override
    public Object clone() {
        DecimalFormatSymbols copy =
            new DecimalFormatSymbols((java.text.DecimalFormatSymbols) d.clone());
        // Carry the digit arrays over verbatim: a set that came from
        // setDigitStrings() is not necessarily re-derivable from zeroDigit.
        copy.digitStrings = digitStrings.clone();
        copy.digits = digits.clone();
        return copy;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof DecimalFormatSymbols)) return false;
        DecimalFormatSymbols other = (DecimalFormatSymbols) o;
        // ICU's equals() compares the digit strings, which is the only piece of
        // state the java.text delegate cannot represent on its own.
        return other.d.equals(d)
            && java.util.Arrays.equals(other.digitStrings, digitStrings);
    }
    @Override public int hashCode() { return d.hashCode(); }
}
