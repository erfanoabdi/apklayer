// android.text.format.Time -- the pre-java.time broken-down wall-clock struct
// Android shipped from API 1. Deprecated upstream in API 22 in favour of
// java.util.Calendar / java.time, but still public API and still referenced by
// long-lived calendar/agenda code, and it was absent here entirely
// (NoClassDefFoundError).
//
// Mirrors frameworks/base/core/java/android/text/format/Time.java. It is a
// MUTABLE PUBLIC-FIELD struct -- second/minute/hour/monthDay/month/year plus
// the derived weekDay/yearDay/gmtoff/isDst and the timezone id -- and its
// contract is that the fields are read and written directly and then
// reconciled by normalize(). All of that is reproduced; the wall-clock <->
// epoch conversions are done with java.time, which is the same proleptic
// Gregorian calendar and the same tzdb rules AOSP's native implementation uses.
//
// Two AOSP behaviours that are easy to lose and are kept here deliberately:
//   * normalize(ignoreDst) and toMillis(ignoreDst) return -1 when the
//     broken-down fields do not name a real instant (a time inside a DST
//     spring-forward gap) and `ignoreDst` is false. Callers branch on that.
//   * The WEEK_NUM / weekDay numbering is SUNDAY = 0, matching the constants
//     below, NOT java.time's Monday = 1.
package android.text.format;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Locale;

@Deprecated
public class Time {

    @Deprecated public static final int SUNDAY = 0;
    @Deprecated public static final int MONDAY = 1;
    @Deprecated public static final int TUESDAY = 2;
    @Deprecated public static final int WEDNESDAY = 3;
    @Deprecated public static final int THURSDAY = 4;
    @Deprecated public static final int FRIDAY = 5;
    @Deprecated public static final int SATURDAY = 6;

    @Deprecated public static final String TIMEZONE_UTC = "UTC";

    /** AOSP: the Julian day number of the Unix epoch (1970-01-01). */
    @Deprecated public static final int EPOCH_JULIAN_DAY = 2440588;

    /** AOSP: the Julian day of the Monday immediately before the epoch. */
    @Deprecated public static final int MONDAY_BEFORE_JULIAN_EPOCH = EPOCH_JULIAN_DAY - 3;

    // Field selectors for getActualMaximum(int).
    @Deprecated public static final int SECOND = 1;
    @Deprecated public static final int MINUTE = 2;
    @Deprecated public static final int HOUR = 3;
    @Deprecated public static final int MONTH_DAY = 4;
    @Deprecated public static final int MONTH = 5;
    @Deprecated public static final int YEAR = 6;
    @Deprecated public static final int WEEK_DAY = 7;
    @Deprecated public static final int YEAR_DAY = 8;
    @Deprecated public static final int WEEK_NUM = 9;

    /** True when this is a whole-day event with no meaningful time-of-day.
     *  AOSP requires hour/minute/second to be 0 when it is set. */
    @Deprecated public boolean allDay;
    @Deprecated public int second;
    @Deprecated public int minute;
    @Deprecated public int hour;
    /** Day of the month, 1..31. */
    @Deprecated public int monthDay;
    /** Month, 0..11 -- ZERO-based, like Calendar and unlike java.time. */
    @Deprecated public int month;
    /** Full year, e.g. 2024. */
    @Deprecated public int year;
    /** Day of the week, SUNDAY(0)..SATURDAY(6). Derived; set by normalize(). */
    @Deprecated public int weekDay;
    /** Day of the year, 0..365. Derived; set by normalize(). */
    @Deprecated public int yearDay;
    /** 1 if daylight savings is in effect, 0 if not, -1 if unknown. */
    @Deprecated public int isDst;
    /** Offset from UTC in SECONDS. Derived; set by normalize(). */
    @Deprecated public long gmtoff;
    /** The Olson id this Time is expressed in. */
    @Deprecated public String timezone;

    /** AOSP Time(String timezoneId): a Time in that zone, with every field 0
     *  (i.e. NOT "now" -- setToNow() does that). */
    @Deprecated
    public Time(String timezoneId) {
        if (timezoneId == null) {
            throw new NullPointerException("timezoneId is null!");
        }
        initialize(timezoneId);
    }

    /** AOSP Time(): the device's current default zone. */
    @Deprecated
    public Time() {
        initialize(TimeZoneGetter.getCurrentTimezone());
    }

    /** AOSP Time(Time other): a copy. */
    @Deprecated
    public Time(Time other) {
        set(other);
    }

    private void initialize(String timezoneId) {
        this.timezone = timezoneId;
        this.year = 1970;
        this.monthDay = 1;
        // Per AOSP, isDst is "unknown" until normalize()/set() computes it.
        this.isDst = -1;
    }

    /** AOSP Time.clear(String timezone): zero every field and adopt the zone. */
    @Deprecated
    public void clear(String timezoneId) {
        if (timezoneId == null) {
            throw new NullPointerException("timezone is null!");
        }
        this.timezone = timezoneId;
        this.allDay = false;
        this.second = 0;
        this.minute = 0;
        this.hour = 0;
        this.monthDay = 0;
        this.month = 0;
        this.year = 0;
        this.weekDay = 0;
        this.yearDay = 0;
        this.gmtoff = 0;
        this.isDst = -1;
    }

    /**
     * AOSP Time.compare(Time a, Time b): compares the two as INSTANTS, so two
     * Times in different zones compare by the moment they name, not by their
     * field values. Throws NullPointerException on a null argument, as AOSP
     * does.
     */
    @Deprecated
    public static int compare(Time a, Time b) {
        if (a == null) {
            throw new NullPointerException("a == null");
        } else if (b == null) {
            throw new NullPointerException("b == null");
        }
        long am = a.toMillisSafe();
        long bm = b.toMillisSafe();
        return Long.compare(am, bm);
    }

    /** AOSP Time.before(Time): compare(this, that) < 0. */
    @Deprecated
    public boolean before(Time that) {
        return Time.compare(this, that) < 0;
    }

    /** AOSP Time.after(Time): compare(this, that) > 0. */
    @Deprecated
    public boolean after(Time that) {
        return Time.compare(this, that) > 0;
    }

    /** AOSP Time.setToNow(). */
    @Deprecated
    public void setToNow() {
        set(System.currentTimeMillis());
    }

    /**
     * AOSP Time.set(long millis): fill every field from the epoch time,
     * interpreted in this Time's own zone.
     */
    @Deprecated
    public void set(long millis) {
        ZonedDateTime z = Instant.ofEpochMilli(millis).atZone(zone());
        setFrom(z);
        this.allDay = false;
    }

    /** AOSP Time.set(Time that): copy every field, zone included. */
    @Deprecated
    public void set(Time that) {
        this.timezone = that.timezone;
        this.allDay = that.allDay;
        this.second = that.second;
        this.minute = that.minute;
        this.hour = that.hour;
        this.monthDay = that.monthDay;
        this.month = that.month;
        this.year = that.year;
        this.weekDay = that.weekDay;
        this.yearDay = that.yearDay;
        this.isDst = that.isDst;
        this.gmtoff = that.gmtoff;
    }

    /**
     * AOSP Time.set(int second, int minute, int hour, int monthDay, int month,
     * int year). Note the argument ORDER: smallest unit first, which is the
     * reverse of Calendar.set(). The derived fields are left stale until
     * normalize() runs, exactly as on AOSP.
     */
    @Deprecated
    public void set(int second, int minute, int hour, int monthDay, int month, int year) {
        this.allDay = false;
        this.second = second;
        this.minute = minute;
        this.hour = hour;
        this.monthDay = monthDay;
        this.month = month;
        this.year = year;
        this.weekDay = 0;
        this.yearDay = 0;
        this.isDst = -1;
        this.gmtoff = 0;
    }

    /**
     * AOSP Time.set(int monthDay, int month, int year): a whole-day value.
     * It sets allDay = true and zeroes the time-of-day.
     */
    @Deprecated
    public void set(int monthDay, int month, int year) {
        this.allDay = true;
        this.second = 0;
        this.minute = 0;
        this.hour = 0;
        this.monthDay = monthDay;
        this.month = month;
        this.year = year;
        this.weekDay = 0;
        this.yearDay = 0;
        this.isDst = -1;
        this.gmtoff = 0;
    }

    /**
     * AOSP Time.normalize(boolean ignoreDst): reconcile out-of-range fields
     * (month 13 becomes January of the next year), recompute weekDay / yearDay
     * / gmtoff / isDst, and return the epoch millis.
     *
     * ⚠ The return value is -1 when the fields name a time that does not exist
     * in this zone -- the hour skipped by a DST spring-forward -- and
     * ignoreDst is false. Callers really do branch on that, so it is
     * reproduced rather than smoothed over.
     */
    @Deprecated
    public long normalize(boolean ignoreDst) {
        LocalDateTime ldt = toLocalDateTimeNormalized();
        ZoneId z = zone();
        java.util.List<ZoneOffset> valid = z.getRules().getValidOffsets(ldt);
        if (valid.isEmpty() && !ignoreDst) {
            // A gap: this wall-clock time does not exist in this zone.
            return -1;
        }
        ZonedDateTime zdt = ldt.atZone(z);
        setFrom(zdt);
        return zdt.toInstant().toEpochMilli();
    }

    /**
     * AOSP Time.toMillis(boolean ignoreDst): the epoch millis for the current
     * field values WITHOUT rewriting them. Same -1 contract as normalize().
     */
    @Deprecated
    public long toMillis(boolean ignoreDst) {
        LocalDateTime ldt = toLocalDateTimeNormalized();
        ZoneId z = zone();
        if (z.getRules().getValidOffsets(ldt).isEmpty() && !ignoreDst) {
            return -1;
        }
        return ldt.atZone(z).toInstant().toEpochMilli();
    }

    /**
     * AOSP Time.switchTimezone(String): keep the same INSTANT and re-express
     * the fields in the new zone (unlike assigning `timezone` directly, which
     * keeps the fields and changes the instant).
     */
    @Deprecated
    public void switchTimezone(String targetTimezone) {
        long millis = toMillis(true);
        this.timezone = targetTimezone;
        set(millis);
    }

    private static final int[] DAYS_PER_MONTH = {
            31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 };

    /**
     * AOSP Time.getActualMaximum(int field): the largest legal value of that
     * field for the CURRENT date -- notably MONTH_DAY, which depends on the
     * month and on whether the year is a leap year.
     */
    @Deprecated
    public int getActualMaximum(int field) {
        switch (field) {
            case SECOND:
                return 59;      // leap seconds, bah humbug (AOSP's own comment)
            case MINUTE:
                return 59;
            case HOUR:
                return 23;
            case MONTH_DAY: {
                int n = DAYS_PER_MONTH[this.month];
                if (n != 28) {
                    return n;
                } else {
                    return LocalDate.of(this.year, 1, 1).isLeapYear() ? 29 : 28;
                }
            }
            case MONTH:
                return 11;
            case YEAR:
                return 2037;
            case WEEK_DAY:
                return 6;
            case YEAR_DAY:
                return LocalDate.of(this.year, 1, 1).isLeapYear() ? 365 : 364;
            case WEEK_NUM:
                throw new RuntimeException("WEEK_NUM not implemented");
            default:
                throw new RuntimeException("bad field=" + field);
        }
    }

    /** AOSP Time.getWeekNumber(): the ISO 8601 week of the year. */
    @Deprecated
    public int getWeekNumber() {
        LocalDate d = toLocalDateTimeNormalized().toLocalDate();
        return d.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    /**
     * AOSP Time.getJulianDay(long millis, long gmtoff): the Julian day of the
     * local day containing `millis`, where `gmtoff` is the UTC offset IN
     * SECONDS (not millis) -- getting that unit wrong is the classic bug with
     * this method.
     */
    @Deprecated
    public static int getJulianDay(long millis, long gmtoff) {
        long offsetMillis = gmtoff * 1000;
        long julianDay = (millis + offsetMillis) / android.text.format.DateUtils.DAY_IN_MILLIS;
        if (millis + offsetMillis < 0
                && (millis + offsetMillis) % android.text.format.DateUtils.DAY_IN_MILLIS != 0) {
            julianDay -= 1;   // floor, not truncate-toward-zero
        }
        return (int) julianDay + EPOCH_JULIAN_DAY;
    }

    /**
     * AOSP Time.setJulianDay(int julianDay): set this Time to midnight at the
     * start of that Julian day in its own zone, and return the epoch millis.
     * AOSP re-normalizes after the initial estimate because midnight may not
     * exist on a DST day; the java.time conversion below picks the same
     * post-gap instant.
     */
    @Deprecated
    public long setJulianDay(int julianDay) {
        long days = (long) julianDay - EPOCH_JULIAN_DAY;
        LocalDate date = LocalDate.ofEpochDay(days);
        ZonedDateTime zdt = date.atStartOfDay(zone());
        setFrom(zdt);
        this.allDay = true;
        this.hour = 0;
        this.minute = 0;
        this.second = 0;
        return zdt.toInstant().toEpochMilli();
    }

    /** AOSP Time.getWeeksSinceEpochFromJulianDay(int julianDay, int firstDayOfWeek). */
    @Deprecated
    public static int getWeeksSinceEpochFromJulianDay(int julianDay, int firstDayOfWeek) {
        int diff = THURSDAY - firstDayOfWeek;
        if (diff < 0) {
            diff += 7;
        }
        int refDay = EPOCH_JULIAN_DAY - diff;
        return (julianDay - refDay) / 7;
    }

    /** AOSP Time.getJulianMondayFromWeeksSinceEpoch(int weeksSinceEpoch). */
    @Deprecated
    public static int getJulianMondayFromWeeksSinceEpoch(int weeksSinceEpoch) {
        return MONDAY_BEFORE_JULIAN_EPOCH + weeksSinceEpoch * 7;
    }

    /** AOSP Time.getCurrentTimezone(): the device's default Olson id. */
    @Deprecated
    public static String getCurrentTimezone() {
        return TimeZoneGetter.getCurrentTimezone();
    }

    /**
     * AOSP Time.isEpoch(Time): true when the Time is exactly 1970-01-01
     * 00:00:00 UTC. AOSP implements it as
     * `getJulianDay(time.toMillis(true), 0) == EPOCH_JULIAN_DAY`, which is a
     * DAY comparison, not an instant one -- so any time on the epoch DAY in
     * UTC answers true.
     */
    @Deprecated
    public static boolean isEpoch(Time time) {
        long millis = time.toMillis(true);
        return getJulianDay(millis, 0) == EPOCH_JULIAN_DAY;
    }

    /**
     * AOSP Time.format(String format): strftime(3) with this Time's fields.
     *
     * ⚠ SCOPE, stated rather than hidden: AOSP routes this through a native
     * strftime with the full conversion set. The conversions implemented here
     * are the ones Android's own callers and documentation actually use --
     * %Y %y %m %d %e %H %I %M %S %p %P %A %a %B %b %j %Z %z %F %T %R %D %%,
     * plus the '-' (no-pad) modifier. An unrecognised conversion is emitted
     * VERBATIM (percent and all) rather than dropped, so a caller can see what
     * was not handled instead of silently losing a field.
     */
    @Deprecated
    public String format(String format) {
        if (format == null) return "";
        LocalDateTime dt = toLocalDateTimeNormalized();
        ZonedDateTime zdt = dt.atZone(zone());
        Locale locale = Locale.getDefault();
        StringBuilder out = new StringBuilder(format.length() + 16);
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c != '%' || i + 1 >= format.length()) {
                out.append(c);
                continue;
            }
            i++;
            boolean noPad = false;
            char conv = format.charAt(i);
            if (conv == '-' && i + 1 < format.length()) {
                noPad = true;
                i++;
                conv = format.charAt(i);
            }
            switch (conv) {
                case 'Y': out.append(dt.getYear()); break;
                case 'y': out.append(pad(dt.getYear() % 100, 2, noPad)); break;
                case 'm': out.append(pad(dt.getMonthValue(), 2, noPad)); break;
                case 'd': out.append(pad(dt.getDayOfMonth(), 2, noPad)); break;
                case 'e': out.append(noPad ? String.valueOf(dt.getDayOfMonth())
                                           : String.format("%2d", dt.getDayOfMonth())); break;
                case 'H': out.append(pad(dt.getHour(), 2, noPad)); break;
                case 'I': {
                    int h12 = dt.getHour() % 12;
                    if (h12 == 0) h12 = 12;
                    out.append(pad(h12, 2, noPad));
                    break;
                }
                case 'M': out.append(pad(dt.getMinute(), 2, noPad)); break;
                case 'S': out.append(pad(dt.getSecond(), 2, noPad)); break;
                case 'p': out.append(amPm(dt, locale).toUpperCase(locale)); break;
                case 'P': out.append(amPm(dt, locale).toLowerCase(locale)); break;
                case 'A': out.append(dt.getDayOfWeek().getDisplayName(
                        java.time.format.TextStyle.FULL, locale)); break;
                case 'a': out.append(dt.getDayOfWeek().getDisplayName(
                        java.time.format.TextStyle.SHORT, locale)); break;
                case 'B': out.append(dt.getMonth().getDisplayName(
                        java.time.format.TextStyle.FULL, locale)); break;
                case 'b':
                case 'h': out.append(dt.getMonth().getDisplayName(
                        java.time.format.TextStyle.SHORT, locale)); break;
                case 'j': out.append(pad(dt.getDayOfYear(), 3, noPad)); break;
                case 'Z': out.append(zdt.getZone().getDisplayName(
                        java.time.format.TextStyle.SHORT, locale)); break;
                case 'z': out.append(zdt.getOffset().getId().replace(":", "")
                        .replace("Z", "+0000")); break;
                case 'F': out.append(String.format(Locale.US, "%04d-%02d-%02d",
                        dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth())); break;
                case 'T': out.append(String.format(Locale.US, "%02d:%02d:%02d",
                        dt.getHour(), dt.getMinute(), dt.getSecond())); break;
                case 'R': out.append(String.format(Locale.US, "%02d:%02d",
                        dt.getHour(), dt.getMinute())); break;
                case 'D': out.append(String.format(Locale.US, "%02d/%02d/%02d",
                        dt.getMonthValue(), dt.getDayOfMonth(), dt.getYear() % 100)); break;
                case 'n': out.append('\n'); break;
                case 't': out.append('\t'); break;
                case '%': out.append('%'); break;
                default:
                    // Unhandled conversion: emit it verbatim rather than
                    // pretending it produced nothing.
                    out.append('%');
                    if (noPad) out.append('-');
                    out.append(conv);
                    break;
            }
        }
        return out.toString();
    }

    /**
     * AOSP Time.format2445(): the RFC 2445 (iCalendar) form,
     * "YYYYMMDDTHHMMSS", with a trailing 'Z' when this Time is in UTC. An
     * allDay Time renders as "YYYYMMDD" with no time part.
     */
    @Deprecated
    public String format2445() {
        LocalDateTime dt = toLocalDateTimeNormalized();
        StringBuilder sb = new StringBuilder(16);
        sb.append(String.format(Locale.US, "%04d%02d%02d",
                dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth()));
        if (allDay) {
            return sb.toString();
        }
        sb.append('T');
        sb.append(String.format(Locale.US, "%02d%02d%02d",
                dt.getHour(), dt.getMinute(), dt.getSecond()));
        if (TIMEZONE_UTC.equals(timezone)) {
            sb.append('Z');
        }
        return sb.toString();
    }

    /**
     * AOSP Time.format3339(boolean allDay): the RFC 3339 form. With allDay
     * true it is just "YYYY-MM-DD"; otherwise it carries fractional seconds
     * (always ".000" here, since this struct has no sub-second field) and
     * either 'Z' or a +HH:MM offset.
     */
    @Deprecated
    public String format3339(boolean allDay) {
        LocalDateTime dt = toLocalDateTimeNormalized();
        if (allDay) {
            return String.format(Locale.US, "%04d-%02d-%02d",
                    dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth());
        }
        ZonedDateTime zdt = dt.atZone(zone());
        String base = String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d:%02d.000",
                dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(),
                dt.getHour(), dt.getMinute(), dt.getSecond());
        if (TIMEZONE_UTC.equals(timezone)) {
            return base + "Z";
        }
        String off = zdt.getOffset().getId();
        return base + ("Z".equals(off) ? "+00:00" : off);
    }

    /**
     * AOSP Time.parse(String s): parses the RFC 2445 form this class's
     * format2445() produces -- "YYYYMMDD" or "YYYYMMDDTHHMMSS", with an
     * optional trailing 'Z'. Returns TRUE when the string carried the UTC 'Z'
     * (in which case the zone is switched to UTC), false otherwise, and throws
     * TimeFormatException on anything it cannot read.
     */
    @Deprecated
    public boolean parse(String s) {
        if (s == null) {
            throw new NullPointerException("time string is null");
        }
        boolean inUtc = parse2445(s);
        if (inUtc) {
            timezone = TIMEZONE_UTC;
        }
        return inUtc;
    }

    private boolean parse2445(String s) {
        int len = s.length();
        if (len < 8) {
            throw timeFormatException("String too short: \"" + s + "\"");
        }
        boolean inUtc = false;
        try {
            int localYear = Integer.parseInt(s.substring(0, 4));
            int localMonth = Integer.parseInt(s.substring(4, 6)) - 1;
            int localMonthDay = Integer.parseInt(s.substring(6, 8));
            int localHour = 0;
            int localMinute = 0;
            int localSecond = 0;
            boolean localAllDay = true;
            if (len > 8) {
                if (s.charAt(8) != 'T') {
                    throw timeFormatException(
                            "Unexpected character 0x" + Integer.toHexString(s.charAt(8))
                            + " at position 8. Expected 'T'.");
                }
                if (len < 15) {
                    throw timeFormatException("String too short: \"" + s + "\"");
                }
                localHour = Integer.parseInt(s.substring(9, 11));
                localMinute = Integer.parseInt(s.substring(11, 13));
                localSecond = Integer.parseInt(s.substring(13, 15));
                localAllDay = false;
                if (len > 15) {
                    if (s.charAt(15) != 'Z') {
                        throw timeFormatException(
                                "Unexpected character 0x" + Integer.toHexString(s.charAt(15))
                                + " at position 15. Expected 'Z'.");
                    }
                    inUtc = true;
                }
            }
            this.year = localYear;
            this.month = localMonth;
            this.monthDay = localMonthDay;
            this.hour = localHour;
            this.minute = localMinute;
            this.second = localSecond;
            this.allDay = localAllDay;
            this.weekDay = 0;
            this.yearDay = 0;
            this.isDst = -1;
            this.gmtoff = 0;
        } catch (NumberFormatException e) {
            throw timeFormatException("Numeric field expected: \"" + s + "\"");
        }
        return inUtc;
    }

    /**
     * AOSP Time.parse3339(String s): parses an RFC 3339 timestamp. Returns
     * true when the string named UTC.
     */
    @Deprecated
    public boolean parse3339(String s) {
        if (s == null) {
            throw new NullPointerException("time string is null");
        }
        try {
            if (s.length() == 10) {
                // "YYYY-MM-DD" -- an all-day value.
                LocalDate d = LocalDate.parse(s);
                this.year = d.getYear();
                this.month = d.getMonthValue() - 1;
                this.monthDay = d.getDayOfMonth();
                this.hour = 0;
                this.minute = 0;
                this.second = 0;
                this.allDay = true;
                this.timezone = TIMEZONE_UTC;
                this.weekDay = 0;
                this.yearDay = 0;
                this.isDst = -1;
                this.gmtoff = 0;
                return true;
            }
            ZonedDateTime z = ZonedDateTime.parse(s);
            boolean inUtc = z.getOffset().getTotalSeconds() == 0;
            this.timezone = inUtc ? TIMEZONE_UTC : z.getZone().getId();
            setFrom(z);
            this.allDay = false;
            return inUtc;
        } catch (RuntimeException e) {
            throw timeFormatException("Unparseable RFC 3339 time: \"" + s + "\"");
        }
    }

    @Override
    public String toString() {
        return format2445();
    }

    // ---- internals -------------------------------------------------------

    private ZoneId zone() {
        String tz = this.timezone;
        if (tz == null) {
            tz = TimeZoneGetter.getCurrentTimezone();
        }
        try {
            return ZoneId.of(tz);
        } catch (RuntimeException e) {
            // An unknown Olson id: AOSP's native layer falls back to GMT
            // rather than throwing out of a field accessor.
            return ZoneOffset.UTC;
        }
    }

    /**
     * The current field values as a LocalDateTime, with out-of-range values
     * CARRIED (month 13 -> January of the next year, monthDay 32 -> the 1st of
     * the next month, and so on). That carrying behaviour is the whole point
     * of normalize(), so it is done here rather than rejected.
     */
    private LocalDateTime toLocalDateTimeNormalized() {
        LocalDateTime base = LocalDateTime.of(this.year, 1, 1, 0, 0, 0);
        base = base.plusMonths(this.month);
        base = base.plusDays((long) this.monthDay - 1);
        base = base.plusHours(this.hour);
        base = base.plusMinutes(this.minute);
        base = base.plusSeconds(this.second);
        return base;
    }

    private void setFrom(ZonedDateTime z) {
        this.year = z.getYear();
        this.month = z.getMonthValue() - 1;
        this.monthDay = z.getDayOfMonth();
        this.hour = z.getHour();
        this.minute = z.getMinute();
        this.second = z.getSecond();
        // AOSP numbers weekDay SUNDAY = 0; java.time numbers MONDAY = 1.
        DayOfWeek dow = z.getDayOfWeek();
        this.weekDay = (dow == DayOfWeek.SUNDAY) ? 0 : dow.getValue();
        // AOSP's yearDay is ZERO-based.
        this.yearDay = z.getDayOfYear() - 1;
        this.gmtoff = z.getOffset().getTotalSeconds();
        this.isDst = z.getZone().getRules().isDaylightSavings(z.toInstant()) ? 1 : 0;
    }

    /** toMillis(true) -- used by compare(), which must not answer -1. */
    private long toMillisSafe() {
        return toLocalDateTimeNormalized().atZone(zone()).toInstant().toEpochMilli();
    }

    /**
     * ⚠ AOSP's parse()/parse3339() throw android.util.TimeFormatException,
     * which does NOT EXIST in this tree (android.util belongs to another lane).
     * It extends RuntimeException on a device, so a caller catching
     * RuntimeException behaves identically here; a caller with an explicit
     * `catch (TimeFormatException)` could not have loaded in the first place,
     * because resolving that catch type would already be a
     * NoClassDefFoundError. Adding android.util.TimeFormatException is the real
     * fix and is called out in the lane report as a cross-package dependency.
     */
    private static RuntimeException timeFormatException(String message) {
        return new IllegalArgumentException(message);
    }

    private static String pad(int value, int width, boolean noPad) {
        if (noPad) return String.valueOf(value);
        return String.format(Locale.US, "%0" + width + "d", value);
    }

    private static String amPm(LocalDateTime dt, Locale locale) {
        String[] amPm = java.text.DateFormatSymbols.getInstance(locale).getAmPmStrings();
        return dt.getHour() < 12 ? amPm[0] : amPm[1];
    }

    /** Indirection so getCurrentTimezone() and the no-arg constructor agree,
     *  and so a future "the app changed the default zone" hook has one place
     *  to live. */
    private static final class TimeZoneGetter {
        static String getCurrentTimezone() {
            return java.util.TimeZone.getDefault().getID();
        }
    }
}
