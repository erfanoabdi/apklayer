// android.icu.text.DateFormatSymbols -- locale date/time symbol set (month
// names, weekday names, am/pm, era) for SimpleDateFormat. ICU4J:
//   developer.android.com/reference/android/icu/text/DateFormatSymbols
//   public class DateFormatSymbols implements Serializable, Cloneable
//
// Faithful delegate over java.text.DateFormatSymbols, which carries the same
// localized name arrays.
package android.icu.text;

import android.icu.util.ULocale;

import java.util.Locale;

public class DateFormatSymbols implements java.io.Serializable, Cloneable {
    private static final long serialVersionUID = 1L;

    private final java.text.DateFormatSymbols d;

    public DateFormatSymbols() {
        this.d = new java.text.DateFormatSymbols();
    }
    public DateFormatSymbols(Locale locale) {
        this.d = new java.text.DateFormatSymbols(
            (locale != null) ? locale : Locale.getDefault());
    }
    public DateFormatSymbols(ULocale locale) {
        this.d = new java.text.DateFormatSymbols(ULocale.unwrap(locale));
    }
    DateFormatSymbols(java.text.DateFormatSymbols delegate) {
        this.d = delegate;
    }

    public static DateFormatSymbols getInstance() {
        return new DateFormatSymbols(java.text.DateFormatSymbols.getInstance());
    }
    public static DateFormatSymbols getInstance(Locale locale) {
        return new DateFormatSymbols(java.text.DateFormatSymbols.getInstance(
            (locale != null) ? locale : Locale.getDefault()));
    }
    public static DateFormatSymbols getInstance(ULocale locale) {
        return new DateFormatSymbols(java.text.DateFormatSymbols.getInstance(ULocale.unwrap(locale)));
    }

    public String[] getEras() { return d.getEras(); }
    public void setEras(String[] newEras) { d.setEras(newEras); }
    public String[] getMonths() { return d.getMonths(); }
    public void setMonths(String[] newMonths) { d.setMonths(newMonths); }
    public String[] getShortMonths() { return d.getShortMonths(); }
    public void setShortMonths(String[] newShortMonths) { d.setShortMonths(newShortMonths); }
    public String[] getWeekdays() { return d.getWeekdays(); }
    public void setWeekdays(String[] newWeekdays) { d.setWeekdays(newWeekdays); }
    public String[] getShortWeekdays() { return d.getShortWeekdays(); }
    public void setShortWeekdays(String[] newShortWeekdays) { d.setShortWeekdays(newShortWeekdays); }
    public String[] getAmPmStrings() { return d.getAmPmStrings(); }
    public void setAmPmStrings(String[] newAmpms) { d.setAmPmStrings(newAmpms); }

    java.text.DateFormatSymbols unwrap() { return d; }

    @Override public Object clone() {
        return new DateFormatSymbols((java.text.DateFormatSymbols) d.clone());
    }
    @Override public boolean equals(Object o) {
        return (o instanceof DateFormatSymbols) && ((DateFormatSymbols) o).d.equals(d);
    }
    @Override public int hashCode() { return d.hashCode(); }
}
