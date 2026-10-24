package android.icu.util;

import java.util.Locale;

/**
 * ICU's Calendar. Android exposes ICU4J publicly; apps call
 * Calendar.getInstance(locale).getType() to learn the calendar system
 * ("gregorian", "japanese", ...) for the locale.
 *
 * Backed by java.util.Calendar (which shares CLDR-derived locale data on
 * OpenJDK), so getInstance / field access match for the ordinary Gregorian
 * cases these apps use. getType returns the ICU calendar-keyword string
 * derived from the JDK calendar class.
 *
 * Source: https://developer.android.com/reference/android/icu/util/Calendar
 */
public class Calendar {

    public static final int ERA = java.util.Calendar.ERA;
    public static final int YEAR = java.util.Calendar.YEAR;
    public static final int MONTH = java.util.Calendar.MONTH;
    public static final int DATE = java.util.Calendar.DATE;
    public static final int DAY_OF_MONTH = java.util.Calendar.DAY_OF_MONTH;
    public static final int DAY_OF_WEEK = java.util.Calendar.DAY_OF_WEEK;
    public static final int HOUR = java.util.Calendar.HOUR;
    public static final int HOUR_OF_DAY = java.util.Calendar.HOUR_OF_DAY;
    public static final int MINUTE = java.util.Calendar.MINUTE;
    public static final int SECOND = java.util.Calendar.SECOND;
    public static final int MILLISECOND = java.util.Calendar.MILLISECOND;
    // The rest of ICU's field numbering (android.icu.util.Calendar). 0-16 equal
    // java.util.Calendar's; 17-21 are ICU-only fields.
    public static final int WEEK_OF_YEAR = java.util.Calendar.WEEK_OF_YEAR;
    public static final int WEEK_OF_MONTH = java.util.Calendar.WEEK_OF_MONTH;
    public static final int DAY_OF_YEAR = java.util.Calendar.DAY_OF_YEAR;
    public static final int DAY_OF_WEEK_IN_MONTH = java.util.Calendar.DAY_OF_WEEK_IN_MONTH;
    public static final int AM_PM = java.util.Calendar.AM_PM;
    public static final int ZONE_OFFSET = java.util.Calendar.ZONE_OFFSET;
    public static final int DST_OFFSET = java.util.Calendar.DST_OFFSET;
    public static final int YEAR_WOY = 17;
    public static final int DOW_LOCAL = 18;
    public static final int EXTENDED_YEAR = 19;
    public static final int JULIAN_DAY = 20;
    public static final int MILLISECONDS_IN_DAY = 21;

    private final java.util.Calendar mCal;

    protected Calendar(java.util.Calendar backing) { mCal = backing; }

    public static Calendar getInstance() {
        return new Calendar(java.util.Calendar.getInstance());
    }
    public static Calendar getInstance(Locale locale) {
        return new Calendar(java.util.Calendar.getInstance(locale));
    }
    public static Calendar getInstance(ULocale locale) {
        return new Calendar(java.util.Calendar.getInstance(
                locale != null ? locale.toLocale() : Locale.getDefault()));
    }
    public static Calendar getInstance(TimeZone zone, Locale locale) {
        java.util.Calendar c = java.util.Calendar.getInstance(locale);
        return new Calendar(c);
    }

    /** ICU calendar-keyword string derived from the JDK calendar class. */
    public String getType() {
        String cls = mCal.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (cls.contains("japanese")) return "japanese";
        if (cls.contains("buddhist")) return "buddhist";
        return "gregorian";
    }

    /** ICU: the calendar's zone, as an android.icu.util.TimeZone. */
    public TimeZone getTimeZone() {
        return new TimeZone(mCal.getTimeZone());
    }

    public void setTimeZone(TimeZone value) {
        if (value != null) mCal.setTimeZone((java.util.TimeZone) value.unwrap().clone());
    }

    /**
     * ICU: the "calendar" keyword values for a locale -- the region's preferred
     * calendars first (CLDR calendarPreferenceData), then, unless commonlyUsed,
     * every other calendar type, in LEGACY spelling ("gregorian"). ICU answers
     * from its own data; the calendars this layer can actually build are the
     * JDK's (java.util.Calendar.getAvailableCalendarTypes(): gregory, buddhist,
     * japanese), so the list is those, with the locale's own default (which the
     * JDK also takes from CLDR: th_TH -> buddhist) first. Advertising a calendar
     * we cannot construct would only fail later, inside a formatter.
     */
    public static final String[] getKeywordValuesForLocale(String key, ULocale locale,
                                                           boolean commonlyUsed) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        Locale base = ULocale.unwrap(locale).stripExtensions();
        out.add(ULocale.toLegacyType("ca", java.util.Calendar.getInstance(base).getCalendarType()));
        if (!commonlyUsed) {
            for (String t : java.util.Calendar.getAvailableCalendarTypes()) {
                out.add(ULocale.toLegacyType("ca", t));
            }
        }
        return out.toArray(new String[0]);
    }

    public final java.util.Date getTime() { return mCal.getTime(); }
    public final void setTime(java.util.Date date) { mCal.setTime(date); }
    public long getTimeInMillis() { return mCal.getTimeInMillis(); }
    public void setTimeInMillis(long millis) { mCal.setTimeInMillis(millis); }
    public int get(int field) { return mCal.get(field); }
    public void set(int field, int value) { mCal.set(field, value); }
    public void add(int field, int amount) { mCal.add(field, amount); }
    public final int getFirstDayOfWeek() { return mCal.getFirstDayOfWeek(); }
    public int getMinimum(int field) { return mCal.getMinimum(field); }
    public int getMaximum(int field) { return mCal.getMaximum(field); }
    public java.util.Calendar toJavaCalendar() { return mCal; }

    /** Bridge for android.icu.text.* (which plumbs through java.util.Calendar) --
     *  the mirror of toJavaCalendar. Not part of ICU's surface; the ctor is
     *  protected so a cross-package wrap needs this, exactly as ULocale.unwrap
     *  exists for java.util.Locale. */
    public static Calendar wrap(java.util.Calendar c) {
        return (c != null) ? new Calendar(c) : null;
    }
}
