// android.icu.text.SimpleDateFormat -- concrete pattern-driven DateFormat. ICU4J:
//   developer.android.com/reference/android/icu/text/SimpleDateFormat
//   public class SimpleDateFormat extends DateFormat
//
// Apps construct this from a pattern like "yyyy-MM-dd HH:mm". The JDK's
// java.text.SimpleDateFormat parses the same pattern letters, so we extend our
// android.icu.text.DateFormat front and forward to a java.text.SimpleDateFormat.
package android.icu.text;

import android.icu.util.ULocale;

import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class SimpleDateFormat extends DateFormat {
    private java.text.SimpleDateFormat d;   // not final: clone() installs the copy's own formatter

    public SimpleDateFormat() {
        this.d = new java.text.SimpleDateFormat();
    }
    public SimpleDateFormat(String pattern) {
        this.d = new java.text.SimpleDateFormat(pattern);
    }
    public SimpleDateFormat(String pattern, Locale loc) {
        this.d = new java.text.SimpleDateFormat(pattern,
            (loc != null) ? loc : Locale.getDefault());
    }
    public SimpleDateFormat(String pattern, ULocale loc) {
        this.d = new java.text.SimpleDateFormat(pattern, ULocale.unwrap(loc));
    }
    /** DateFormat's style factories: the JDK's own locale SimpleDateFormat, wrapped (ICU returns a SimpleDateFormat). */
    SimpleDateFormat(java.text.SimpleDateFormat jdk) {
        this.d = jdk;
    }
    public SimpleDateFormat(String pattern, DateFormatSymbols formatSymbols) {
        this.d = new java.text.SimpleDateFormat(pattern, formatSymbols.unwrap());
    }

    public void applyPattern(String pattern) { d.applyPattern(pattern); }
    public String toPattern() { return d.toPattern(); }
    public void applyLocalizedPattern(String pattern) { d.applyLocalizedPattern(pattern); }
    public String toLocalizedPattern() { return d.toLocalizedPattern(); }
    public Date get2DigitYearStart() { return d.get2DigitYearStart(); }
    public void set2DigitYearStart(Date startDate) { d.set2DigitYearStart(startDate); }

    @Override protected java.text.DateFormat format0() { return d; }

    /** ICU's SimpleDateFormat.clone() is deep; Format.clone() is shallow and would share `d` (zone, calendar, pattern). */
    @Override public Object clone() {
        SimpleDateFormat c = (SimpleDateFormat) super.clone();
        c.d = (java.text.SimpleDateFormat) d.clone();
        return c;
    }
    @Override public StringBuffer format(Date date, StringBuffer s, FieldPosition p) { return d.format(date, s, p); }
    @Override public StringBuffer format(Object o, StringBuffer s, FieldPosition p) { return d.format(o, s, p); }
    @Override public Object parseObject(String src, ParsePosition pos) { return d.parseObject(src, pos); }
    @Override public Date parse(String text) throws java.text.ParseException { return d.parse(text); }
    @Override public Date parse(String text, ParsePosition pos) { return d.parse(text, pos); }
    // setTimeZone/getTimeZone/setCalendar/getCalendar are NOT overridden here any
    // more: DateFormat now implements the ICU-typed (android.icu.util.*) versions
    // over format0(), which is `d`. Overriding them with java.util types was what
    // gave the base class the wrong descriptors.
    @Override public void setLenient(boolean lenient) { d.setLenient(lenient); }
    @Override public boolean isLenient() { return d.isLenient(); }
}
