// android.icu.text.DateFormat -- ICU4J date/time formatting (repackage of
// com.ibm.icu.text.DateFormat). Published API:
//   developer.android.com/reference/android/icu/text/DateFormat
//   public abstract class DateFormat extends UFormat
//
// The JDK's java.text.DateFormat produces equivalent locale output for the
// SHORT/MEDIUM/LONG/FULL styles apps use, so this is a faithful abstract front
// whose factory methods return a concrete delegate over java.text.DateFormat.
//
// ICU's style constants (FULL=0, LONG=1, MEDIUM=2, SHORT=3) match
// java.text.DateFormat's exactly, so they pass through unchanged.
package android.icu.text;

import android.icu.util.ULocale;

import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Date;
import java.util.Locale;

// AOSP: `public abstract class DateFormat extends UFormat` (UFormat extends
// java.text.Format). Skipping the UFormat link breaks an app's cast to it.
public abstract class DateFormat extends UFormat {
    public static final int FULL = 0;
    public static final int LONG = 1;
    public static final int MEDIUM = 2;
    public static final int SHORT = 3;
    public static final int DEFAULT = MEDIUM;

    protected DateFormat() {}

    public static DateFormat getDateInstance() {
        return wrap(java.text.DateFormat.getDateInstance());
    }
    public static DateFormat getDateInstance(int style) {
        return wrap(java.text.DateFormat.getDateInstance(style));
    }
    public static DateFormat getDateInstance(int style, Locale loc) {
        return wrap(java.text.DateFormat.getDateInstance(style, nz(loc)));
    }
    public static DateFormat getDateInstance(int style, ULocale loc) {
        return wrap(java.text.DateFormat.getDateInstance(style, ULocale.unwrap(loc)));
    }

    public static DateFormat getTimeInstance() {
        return wrap(java.text.DateFormat.getTimeInstance());
    }
    public static DateFormat getTimeInstance(int style) {
        return wrap(java.text.DateFormat.getTimeInstance(style));
    }
    public static DateFormat getTimeInstance(int style, Locale loc) {
        return wrap(java.text.DateFormat.getTimeInstance(style, nz(loc)));
    }
    public static DateFormat getTimeInstance(int style, ULocale loc) {
        return wrap(java.text.DateFormat.getTimeInstance(style, ULocale.unwrap(loc)));
    }

    public static DateFormat getDateTimeInstance() {
        return wrap(java.text.DateFormat.getDateTimeInstance());
    }
    public static DateFormat getDateTimeInstance(int dateStyle, int timeStyle) {
        return wrap(java.text.DateFormat.getDateTimeInstance(dateStyle, timeStyle));
    }
    public static DateFormat getDateTimeInstance(int dateStyle, int timeStyle, Locale loc) {
        return wrap(java.text.DateFormat.getDateTimeInstance(dateStyle, timeStyle, nz(loc)));
    }
    public static DateFormat getDateTimeInstance(int dateStyle, int timeStyle, ULocale loc) {
        return wrap(java.text.DateFormat.getDateTimeInstance(dateStyle, timeStyle, ULocale.unwrap(loc)));
    }

    public static DateFormat getInstance() {
        return wrap(java.text.DateFormat.getInstance());
    }

    private static Locale nz(Locale l) { return (l != null) ? l : Locale.getDefault(); }

    // ICU's style factories (DateFormat.get{Date,Time,DateTime}Instance) return
    // a SimpleDateFormat, and callers cast: Hermes'
    // PlatformDateTimeFormatterICU.getDefaultHourCycle reads
    // ((android.icu.text.SimpleDateFormat) DateFormat.getTimeInstance(FULL, loc)).toPattern()
    // and answers h24 on ClassCastException -- so an opaque wrapper silently gave
    // every locale a 24-hour cycle. The JDK's factories return a
    // java.text.SimpleDateFormat; wrap that AS our SimpleDateFormat.
    // MACAPK_ICU_PLATFORM_TYPES=0 restores the old opaque wrapper.
    static DateFormat wrap(java.text.DateFormat d) {
        if (com.macapk.icu.HostIcu.PLATFORM_TYPES && d instanceof java.text.SimpleDateFormat) {
            return new SimpleDateFormat((java.text.SimpleDateFormat) d);
        }
        return new JdkDateFormat(d);
    }

    /**
     * ICU marks each formatted field with an android.icu.text.DateFormat.Field
     * (Hermes' formatToParts compares with ==). The JDK's SimpleDateFormat marks
     * the same pattern letters with java.text.DateFormat.Field constants of the
     * same names, so translate them. This class did not override the method
     * before (java.text.Format's attribute-less iterator);
     * MACAPK_ICU_PLATFORM_TYPES=0 keeps that.
     */
    @Override
    public java.text.AttributedCharacterIterator formatToCharacterIterator(Object obj) {
        if (!com.macapk.icu.HostIcu.PLATFORM_TYPES) return super.formatToCharacterIterator(obj);
        return UFormat.translateAttributes(format0().formatToCharacterIterator(obj), Field.FROM_JDK);
    }

    /** ICU DateFormat.Field: a calendar field's attribute, with ICU's names and calendar-field numbers. */
    public static class Field extends java.text.Format.Field {
        private static final long serialVersionUID = 1L;
        private static final java.util.Map<Integer, Field> CAL_FIELD_MAP = new java.util.HashMap<>();

        public static final Field AM_PM = new Field("am pm", android.icu.util.Calendar.AM_PM);
        public static final Field DAY_OF_MONTH = new Field("day of month", android.icu.util.Calendar.DAY_OF_MONTH);
        public static final Field DAY_OF_WEEK = new Field("day of week", android.icu.util.Calendar.DAY_OF_WEEK);
        public static final Field DAY_OF_WEEK_IN_MONTH =
                new Field("day of week in month", android.icu.util.Calendar.DAY_OF_WEEK_IN_MONTH);
        public static final Field DAY_OF_YEAR = new Field("day of year", android.icu.util.Calendar.DAY_OF_YEAR);
        public static final Field ERA = new Field("era", android.icu.util.Calendar.ERA);
        public static final Field HOUR_OF_DAY0 = new Field("hour of day", android.icu.util.Calendar.HOUR_OF_DAY);
        public static final Field HOUR_OF_DAY1 = new Field("hour of day 1", -1);
        public static final Field HOUR0 = new Field("hour", android.icu.util.Calendar.HOUR);
        public static final Field HOUR1 = new Field("hour 1", -1);
        public static final Field MILLISECOND = new Field("millisecond", android.icu.util.Calendar.MILLISECOND);
        public static final Field MINUTE = new Field("minute", android.icu.util.Calendar.MINUTE);
        public static final Field MONTH = new Field("month", android.icu.util.Calendar.MONTH);
        public static final Field SECOND = new Field("second", android.icu.util.Calendar.SECOND);
        public static final Field TIME_ZONE = new Field("time zone", -1);
        public static final Field WEEK_OF_MONTH = new Field("week of month", android.icu.util.Calendar.WEEK_OF_MONTH);
        public static final Field WEEK_OF_YEAR = new Field("week of year", android.icu.util.Calendar.WEEK_OF_YEAR);
        public static final Field YEAR = new Field("year", android.icu.util.Calendar.YEAR);
        public static final Field DOW_LOCAL = new Field("local day of week", android.icu.util.Calendar.DOW_LOCAL);
        public static final Field EXTENDED_YEAR = new Field("extended year", android.icu.util.Calendar.EXTENDED_YEAR);
        public static final Field JULIAN_DAY = new Field("Julian day", android.icu.util.Calendar.JULIAN_DAY);
        public static final Field MILLISECONDS_IN_DAY =
                new Field("milliseconds in day", android.icu.util.Calendar.MILLISECONDS_IN_DAY);
        public static final Field YEAR_WOY = new Field("year for week of year", android.icu.util.Calendar.YEAR_WOY);
        public static final Field QUARTER = new Field("quarter", -1);
        public static final Field AM_PM_MIDNIGHT_NOON = new Field("am/pm/midnight/noon", -1);
        public static final Field FLEXIBLE_DAY_PERIOD = new Field("flexible day period", -1);

        private final int calendarField;

        protected Field(String name, int calendarField) {
            super(name);
            this.calendarField = calendarField;
            if (calendarField != -1 && !CAL_FIELD_MAP.containsKey(calendarField)) {
                CAL_FIELD_MAP.put(calendarField, this);
            }
        }

        public int getCalendarField() { return calendarField; }

        public static DateFormat.Field ofCalendarField(int calendarField) {
            DateFormat.Field f = CAL_FIELD_MAP.get(calendarField);
            if (f == null) throw new IllegalArgumentException("Unknown Calendar field " + calendarField);
            return f;
        }

        static final java.util.Map<java.text.AttributedCharacterIterator.Attribute,
                java.text.AttributedCharacterIterator.Attribute> FROM_JDK = new java.util.HashMap<>();
        static {
            FROM_JDK.put(java.text.DateFormat.Field.AM_PM, AM_PM);
            FROM_JDK.put(java.text.DateFormat.Field.DAY_OF_MONTH, DAY_OF_MONTH);
            FROM_JDK.put(java.text.DateFormat.Field.DAY_OF_WEEK, DAY_OF_WEEK);
            FROM_JDK.put(java.text.DateFormat.Field.DAY_OF_WEEK_IN_MONTH, DAY_OF_WEEK_IN_MONTH);
            FROM_JDK.put(java.text.DateFormat.Field.DAY_OF_YEAR, DAY_OF_YEAR);
            FROM_JDK.put(java.text.DateFormat.Field.ERA, ERA);
            FROM_JDK.put(java.text.DateFormat.Field.HOUR_OF_DAY0, HOUR_OF_DAY0);
            FROM_JDK.put(java.text.DateFormat.Field.HOUR_OF_DAY1, HOUR_OF_DAY1);
            FROM_JDK.put(java.text.DateFormat.Field.HOUR0, HOUR0);
            FROM_JDK.put(java.text.DateFormat.Field.HOUR1, HOUR1);
            FROM_JDK.put(java.text.DateFormat.Field.MILLISECOND, MILLISECOND);
            FROM_JDK.put(java.text.DateFormat.Field.MINUTE, MINUTE);
            FROM_JDK.put(java.text.DateFormat.Field.MONTH, MONTH);
            FROM_JDK.put(java.text.DateFormat.Field.SECOND, SECOND);
            FROM_JDK.put(java.text.DateFormat.Field.TIME_ZONE, TIME_ZONE);
            FROM_JDK.put(java.text.DateFormat.Field.WEEK_OF_MONTH, WEEK_OF_MONTH);
            FROM_JDK.put(java.text.DateFormat.Field.WEEK_OF_YEAR, WEEK_OF_YEAR);
            FROM_JDK.put(java.text.DateFormat.Field.YEAR, YEAR);
        }
    }

    public final String format(Date date) { return format0().format(date); }
    public abstract StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition);
    public abstract Date parse(String text) throws java.text.ParseException;
    public abstract Date parse(String text, ParsePosition pos);
    // ICU types all four of these with android.icu.util.{TimeZone,Calendar}, NOT
    // the java.util ones, so an app doing
    //   df.setTimeZone(android.icu.util.TimeZone.getTimeZone(id))
    // hit NoSuchMethodError against a java.util-typed method (11 of the corpus
    // apps make that exact call). The setter half was landed first; the GETTER
    // half is landed here -- Java has no return-type overloading, so it needed the
    // abstract declarations to go and the two subclasses' overrides to go with
    // them. They are now concrete and implemented over format0(), which every
    // subclass already supplies, so there is nothing left to keep in lockstep.
    // android.icu.text.DateFormat (android-30 android.jar):
    //   public void setTimeZone(android.icu.util.TimeZone);
    //   public android.icu.util.TimeZone getTimeZone();
    //   public void setCalendar(android.icu.util.Calendar);
    //   public android.icu.util.Calendar getCalendar();
    public void setTimeZone(android.icu.util.TimeZone zone) {
        format0().setTimeZone(toJdkTimeZone(zone));
    }
    public android.icu.util.TimeZone getTimeZone() {
        return new android.icu.util.TimeZone(format0().getTimeZone());
    }

    // android.icu.util.TimeZone keeps its java.util.TimeZone delegate
    // package-private, so convert through the public identity ICU exposes: the
    // zone ID, plus the raw offset, which is the one property an app can change
    // on a thawed clone (TimeZone.setRawOffset) and that a lookup by ID alone
    // would silently reset to the tzdata default.
    static java.util.TimeZone toJdkTimeZone(android.icu.util.TimeZone zone) {
        if (zone == null) return null;
        java.util.TimeZone jdk = java.util.TimeZone.getTimeZone(zone.getID());
        if (jdk.getRawOffset() != zone.getRawOffset()) {
            jdk = (java.util.TimeZone) jdk.clone();
            jdk.setRawOffset(zone.getRawOffset());
        }
        return jdk;
    }
    public void setCalendar(android.icu.util.Calendar newCalendar) {
        if (newCalendar != null) format0().setCalendar(newCalendar.toJavaCalendar());
    }
    public android.icu.util.Calendar getCalendar() {
        return android.icu.util.Calendar.wrap(format0().getCalendar());
    }
    public abstract void setLenient(boolean lenient);
    public abstract boolean isLenient();

    protected abstract java.text.DateFormat format0();

    private static final class JdkDateFormat extends DateFormat {
        private final java.text.DateFormat d;
        JdkDateFormat(java.text.DateFormat d) { this.d = d; }
        @Override protected java.text.DateFormat format0() { return d; }
        @Override public StringBuffer format(Date date, StringBuffer s, FieldPosition p) { return d.format(date, s, p); }
        @Override public StringBuffer format(Object o, StringBuffer s, FieldPosition p) { return d.format(o, s, p); }
        @Override public Object parseObject(String src, ParsePosition pos) { return d.parseObject(src, pos); }
        @Override public Date parse(String text) throws java.text.ParseException { return d.parse(text); }
        @Override public Date parse(String text, ParsePosition pos) { return d.parse(text, pos); }
        @Override public void setLenient(boolean lenient) { d.setLenient(lenient); }
        @Override public boolean isLenient() { return d.isLenient(); }
    }

    /**
     * The locale's hour cycle: 11/12 (with-or-without a 0 for midnight) or
     * 23/24. Apps read getDefaultHourCycle().ordinal() (or compare to
     * HOUR_CYCLE_24H) to decide 12- vs 24-hour time display.
     * Source: frameworks/base ... android/icu/text/DateFormat.java (HourCycle)
     */
    public enum HourCycle {
        HOUR_CYCLE_11,   // h11: 0-11
        HOUR_CYCLE_12,   // h12: 1-12
        HOUR_CYCLE_23,   // h23: 0-23
        HOUR_CYCLE_24;   // h24: 1-24
    }

    // ICU DateFormat.getInstanceForSkeleton(String skeleton, ULocale locale):
    //     DateTimePatternGenerator generator = DateTimePatternGenerator.getInstance(locale);
    //     final String bestPattern = generator.getBestPattern(skeleton);
    //     return new SimpleDateFormat(bestPattern, locale);
    // and getPatternInstance(...) is its older name. The (Calendar, ...) form
    // first sets the locale's calendar keyword to cal.getType() and finally
    // format.setCalendar(cal). These two used to answer getDateInstance() for
    // ANY skeleton; MACAPK_ICU_PLATFORM_TYPES=0 keeps that answer.
    public static final DateFormat getInstanceForSkeleton(String skeleton) {
        return getPatternInstance(skeleton, ULocale.getDefault(ULocale.Category.FORMAT));
    }
    public static final DateFormat getInstanceForSkeleton(String skeleton, java.util.Locale locale) {
        return getPatternInstance(skeleton, ULocale.forLocale(nz(locale)));
    }
    public static final DateFormat getInstanceForSkeleton(String skeleton, ULocale locale) {
        if (!com.macapk.icu.HostIcu.PLATFORM_TYPES) return getDateInstance();
        if (locale == null) locale = ULocale.getDefault(ULocale.Category.FORMAT);
        String bestPattern = DateTimePatternGenerator.getInstance(locale).getBestPattern(skeleton);
        return new SimpleDateFormat(bestPattern, locale);
    }
    public static final DateFormat getInstanceForSkeleton(android.icu.util.Calendar cal, String skeleton,
                                                          java.util.Locale locale) {
        return getPatternInstance(cal, skeleton, ULocale.forLocale(nz(locale)));
    }
    public static final DateFormat getInstanceForSkeleton(android.icu.util.Calendar cal, String skeleton,
                                                          ULocale locale) {
        if (!com.macapk.icu.HostIcu.PLATFORM_TYPES) return getDateInstance();
        if (locale == null) locale = ULocale.getDefault(ULocale.Category.FORMAT);
        if (cal != null) locale = locale.setKeywordValue("calendar", cal.getType());
        String bestPattern = DateTimePatternGenerator.getInstance(locale).getBestPattern(skeleton);
        SimpleDateFormat format = new SimpleDateFormat(bestPattern, locale);
        format.setCalendar(cal);
        return format;
    }
    public static final DateFormat getPatternInstance(String skeleton) {
        return getInstanceForSkeleton(skeleton);
    }
    public static final DateFormat getPatternInstance(String skeleton, java.util.Locale locale) {
        return getInstanceForSkeleton(skeleton, locale);
    }
    public static final DateFormat getPatternInstance(String skeleton, ULocale locale) {
        return getInstanceForSkeleton(skeleton, locale);
    }
    public static final DateFormat getPatternInstance(android.icu.util.Calendar cal, String skeleton,
                                                      java.util.Locale locale) {
        return getInstanceForSkeleton(cal, skeleton, locale);
    }
    public static final DateFormat getPatternInstance(android.icu.util.Calendar cal, String skeleton,
                                                      ULocale locale) {
        return getInstanceForSkeleton(cal, skeleton, locale);
    }
    public void setContext(android.icu.text.DisplayContext context) { }
}

