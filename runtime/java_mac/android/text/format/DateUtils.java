// android.text.format.DateUtils -- relative/absolute time + date formatting.
// Mirrors the AOSP public surface that apps actually call. The Google
// Calculator's history feature (DisplayAwareMotionLayout.onMeasure ->
// relative-timestamp formatting) calls getRelativeTimeSpanString(long,long,long,
// int); the previous empty stub made that throw NoSuchMethodError inside
// onMeasure, so the keypad MotionLayout never laid out and the whole keypad
// rendered blank. We implement the real behavior (duration -> "N units ago" /
// "in N units") so the measure pass succeeds. Reference:
// frameworks/base/core/java/android/text/format/DateUtils.java.
package android.text.format;

public class DateUtils {
    public DateUtils() {}

    // AOSP time constants (public; widely referenced by callers as the
    // minResolution / duration thresholds).
    public static final long SECOND_IN_MILLIS = 1000L;
    public static final long MINUTE_IN_MILLIS = SECOND_IN_MILLIS * 60L;
    public static final long HOUR_IN_MILLIS   = MINUTE_IN_MILLIS * 60L;
    public static final long DAY_IN_MILLIS    = HOUR_IN_MILLIS * 24L;
    public static final long WEEK_IN_MILLIS   = DAY_IN_MILLIS * 7L;
    public static final long YEAR_IN_MILLIS   = WEEK_IN_MILLIS * 52L;

    // AOSP format flags for formatDateRange (values match frameworks/base/core/
    // java/android/text/format/DateUtils.java, android11-release). We declare
    // the full set, not just the ones our formatter acts on: javac inlines
    // `public static final int` field reads into the literal value at every
    // call site (JLS compile-time constant folding), so an app compiled
    // against the real Android SDK already has these exact bits baked into
    // its bytecode whether or not we declare the field. Declaring them here
    // is for OUR OWN readability when decoding a flags int, not for callers.
    public static final int FORMAT_SHOW_TIME       = 0x00001;
    public static final int FORMAT_SHOW_WEEKDAY    = 0x00002;
    public static final int FORMAT_SHOW_YEAR       = 0x00004;
    public static final int FORMAT_NO_YEAR         = 0x00008;
    public static final int FORMAT_SHOW_DATE       = 0x00010;
    public static final int FORMAT_NO_MONTH_DAY    = 0x00020;
    public static final int FORMAT_12HOUR          = 0x00040;
    public static final int FORMAT_24HOUR          = 0x00080;
    public static final int FORMAT_CAP_AMPM        = 0x00100;
    public static final int FORMAT_NO_NOON         = 0x00200;
    public static final int FORMAT_CAP_NOON        = 0x00400;
    public static final int FORMAT_NO_MIDNIGHT     = 0x00800;
    public static final int FORMAT_CAP_MIDNIGHT    = 0x01000;
    public static final int FORMAT_UTC             = 0x02000;
    public static final int FORMAT_ABBREV_TIME     = 0x04000;
    public static final int FORMAT_ABBREV_WEEKDAY  = 0x08000;
    public static final int FORMAT_ABBREV_MONTH    = 0x10000;
    public static final int FORMAT_NUMERIC_DATE    = 0x20000;
    public static final int FORMAT_ABBREV_RELATIVE = 0x40000;
    public static final int FORMAT_ABBREV_ALL      = 0x80000;
    /** AOSP DateUtils.java:103, :105 -- the two composite flags. */
    public static final int FORMAT_CAP_NOON_MIDNIGHT = (FORMAT_CAP_NOON | FORMAT_CAP_MIDNIGHT);
    public static final int FORMAT_NO_NOON_MIDNIGHT  = (FORMAT_NO_NOON | FORMAT_NO_MIDNIGHT);

    // AOSP DateUtils.java:114-127 -- the strftime-style format strings the
    // (long-deprecated) android.text.format.Time-based API took. They are
    // compile-time constants, so an app compiled against the real SDK already
    // has the literal baked in; they are declared here for the same reason the
    // FORMAT_* block above is.
    public static final String HOUR_MINUTE_24 = "%H:%M";
    public static final String MONTH_FORMAT = "%B";
    public static final String ABBREV_MONTH_FORMAT = "%b";
    public static final String NUMERIC_MONTH_FORMAT = "%m";
    public static final String MONTH_DAY_FORMAT = "%-d";
    public static final String YEAR_FORMAT = "%Y";
    public static final String YEAR_FORMAT_TWO_DIGITS = "%g";
    public static final String WEEKDAY_FORMAT = "%A";
    public static final String ABBREV_WEEKDAY_FORMAT = "%a";

    /** AOSP DateUtils.java:131, :135. Both are literally `null` on a device --
     *  vestigial @Deprecated fields whose only remaining contract is that they
     *  EXIST and read as null. Reproduced exactly rather than "helpfully"
     *  populated. */
    public static final int[] sameYearTable = null;
    public static final int[] sameMonthTable = null;

    // AOSP DateUtils.java:146-190 -- the `abbrev` argument of
    // getDayOfWeekString / getMonthString.
    public static final int LENGTH_LONG = 10;
    public static final int LENGTH_MEDIUM = 20;
    public static final int LENGTH_SHORT = 30;
    public static final int LENGTH_SHORTER = 40;
    public static final int LENGTH_SHORTEST = 50;

    /**
     * AOSP DateUtils.getDayOfWeekString(int, int) (DateUtils.java:206-224).
     * `dayOfWeek` is a Calendar.SUNDAY..Calendar.SATURDAY value (1..7).
     * AOSP maps LENGTH_LONG to ICU's WIDE, LENGTH_SHORTEST to NARROW, and
     * everything else (including LENGTH_SHORT and LENGTH_SHORTER, which carry
     * "TODO" comments in AOSP) to ABBREVIATED.
     *
     * We go through java.time's DayOfWeek.getDisplayName, whose FULL / SHORT /
     * NARROW text styles are the same CLDR widths ICU's WIDE / ABBREVIATED /
     * NARROW select, so the strings match for every locale the JDK ships.
     * An out-of-range dayOfWeek raises IndexOutOfBoundsException, as documented.
     */
    @Deprecated
    public static String getDayOfWeekString(int dayOfWeek, int abbrev) {
        if (dayOfWeek < java.util.Calendar.SUNDAY || dayOfWeek > java.util.Calendar.SATURDAY) {
            throw new IndexOutOfBoundsException("dayOfWeek: " + dayOfWeek);
        }
        // Calendar numbers Sunday..Saturday as 1..7; java.time numbers
        // Monday..Sunday as 1..7.
        java.time.DayOfWeek d = java.time.DayOfWeek.of(
                dayOfWeek == java.util.Calendar.SUNDAY ? 7 : dayOfWeek - 1);
        return d.getDisplayName(textStyleFor(abbrev), java.util.Locale.getDefault());
    }

    /**
     * AOSP DateUtils.getMonthString(int, int) (DateUtils.java:250-268).
     * `month` is a Calendar.JANUARY..Calendar.DECEMBER value (0..11).
     */
    @Deprecated
    public static String getMonthString(int month, int abbrev) {
        if (month < java.util.Calendar.JANUARY || month > java.util.Calendar.DECEMBER) {
            throw new IndexOutOfBoundsException("month: " + month);
        }
        return java.time.Month.of(month + 1)
                .getDisplayName(textStyleFor(abbrev), java.util.Locale.getDefault());
    }

    private static java.time.format.TextStyle textStyleFor(int abbrev) {
        switch (abbrev) {
            case LENGTH_LONG:     return java.time.format.TextStyle.FULL;
            case LENGTH_SHORTEST: return java.time.format.TextStyle.NARROW;
            case LENGTH_MEDIUM:
            case LENGTH_SHORT:
            case LENGTH_SHORTER:
            default:              return java.time.format.TextStyle.SHORT;
        }
    }

    /**
     * AOSP DateUtils.getAMPMString(int) (DateUtils.java:233-236):
     *     String[] amPm = ...getAmPmStrings();
     *     return amPm[ampm - Calendar.AM];
     * `ampm` is Calendar.AM (0) or Calendar.PM (1).
     */
    @Deprecated
    public static String getAMPMString(int ampm) {
        String[] amPm = java.text.DateFormatSymbols.getInstance(
                java.util.Locale.getDefault()).getAmPmStrings();
        return amPm[ampm - java.util.Calendar.AM];
    }

    /** AOSP getRelativeTimeSpanString(time, now, minResolution, flags): a
     *  human "N units ago" / "in N units" string for (time - now), where the
     *  unit is the largest of {minute,hour,day,week} that is >= minResolution
     *  (clamped so we never report a finer unit than minResolution). flags only
     *  affect abbreviation, which we render the same either way. */
    public static CharSequence getRelativeTimeSpanString(long time, long now,
                                                         long minResolution, int flags) {
        long duration = Math.abs(now - time);
        boolean past = (now >= time);
        long count;
        String unit;
        long res = Math.max(minResolution, 0L);
        if (duration < MINUTE_IN_MILLIS && res < MINUTE_IN_MILLIS) {
            count = Math.max(duration / SECOND_IN_MILLIS, 0L);
            unit = "second";
        } else if (duration < HOUR_IN_MILLIS && res < HOUR_IN_MILLIS) {
            count = duration / MINUTE_IN_MILLIS;
            unit = "minute";
        } else if (duration < DAY_IN_MILLIS && res < DAY_IN_MILLIS) {
            count = duration / HOUR_IN_MILLIS;
            unit = "hour";
        } else if (duration < WEEK_IN_MILLIS && res < WEEK_IN_MILLIS) {
            count = duration / DAY_IN_MILLIS;
            unit = "day";
        } else {
            count = Math.max(duration / WEEK_IN_MILLIS, 0L);
            unit = "week";
        }
        if (count == 0L && unit.equals("second")) return "0 seconds ago";
        String plural = (count == 1L) ? unit : unit + "s";
        return past ? (count + " " + plural + " ago")
                    : ("in " + count + " " + plural);
    }

    /** AOSP getRelativeTimeSpanString(time, now, minResolution): flags default 0. */
    public static CharSequence getRelativeTimeSpanString(long time, long now, long minResolution) {
        return getRelativeTimeSpanString(time, now, minResolution, 0);
    }

    /** AOSP getRelativeTimeSpanString(startTime): relative to "now" with a
     *  minute min-resolution. */
    public static CharSequence getRelativeTimeSpanString(long startTime) {
        return getRelativeTimeSpanString(startTime, System.currentTimeMillis(), MINUTE_IN_MILLIS, 0);
    }

    /** AOSP getRelativeDateTimeString: we approximate with the relative span
     *  (apps use this for "2 hours ago" style labels). */
    public static CharSequence getRelativeDateTimeString(android.content.Context c, long time,
            long minResolution, long transitionResolution, int flags) {
        return getRelativeTimeSpanString(time, System.currentTimeMillis(), minResolution, flags);
    }

    /** AOSP formatDateTime: a plain locale-default date/time string. */
    public static String formatDateTime(android.content.Context c, long millis, int flags) {
        return new java.util.Date(millis).toString();
    }

    public static boolean isToday(long when) {
        return Math.abs(System.currentTimeMillis() - when) < DAY_IN_MILLIS;
    }

    /** AOSP formatElapsedTime(elapsedSeconds): "MM:SS" or "H:MM:SS". */
    public static String formatElapsedTime(long elapsedSeconds) {
        long h = elapsedSeconds / 3600, m = (elapsedSeconds % 3600) / 60, s = elapsedSeconds % 60;
        if (h > 0) return String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(java.util.Locale.US, "%02d:%02d", m, s);
    }

    /**
     * AOSP DateUtils.formatElapsedTime(StringBuilder recycle, long
     * elapsedSeconds) (DateUtils.java:448-482). The StringBuilder is a
     * caller-supplied scratch buffer -- it is RESET (setLength(0)), not
     * appended to, and a null one is allocated internally. The returned String
     * is the same "H:MM:SS" / "MM:SS" shape the one-argument overload produces.
     */
    public static String formatElapsedTime(StringBuilder recycle, long elapsedSeconds) {
        String out = formatElapsedTime(elapsedSeconds);
        if (recycle != null) {
            recycle.setLength(0);
            recycle.append(out);
        }
        return out;
    }

    /**
     * AOSP DateUtils.formatSameDayTime(long then, long now, int dateStyle, int
     * timeStyle) (DateUtils.java:500-518): when `then` falls on the SAME
     * calendar day as `now`, format it as a TIME with `timeStyle`; otherwise
     * format it as a DATE with `dateStyle`. Both styles are
     * java.text.DateFormat constants (FULL / LONG / MEDIUM / SHORT).
     */
    public static CharSequence formatSameDayTime(long then, long now, int dateStyle,
            int timeStyle) {
        java.util.Calendar thenCal = new java.util.GregorianCalendar();
        thenCal.setTimeInMillis(then);
        java.util.Date thenDate = thenCal.getTime();
        java.util.Calendar nowCal = new java.util.GregorianCalendar();
        nowCal.setTimeInMillis(now);

        java.text.DateFormat f;
        if (thenCal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR)
                && thenCal.get(java.util.Calendar.MONTH) == nowCal.get(java.util.Calendar.MONTH)
                && thenCal.get(java.util.Calendar.DAY_OF_MONTH)
                        == nowCal.get(java.util.Calendar.DAY_OF_MONTH)) {
            f = java.text.DateFormat.getTimeInstance(timeStyle);
        } else {
            f = java.text.DateFormat.getDateInstance(dateStyle);
        }
        return f.format(thenDate);
    }

    /** AOSP DateUtils.getRelativeTimeSpanString(Context, long):
     *  `return getRelativeTimeSpanString(c, millis, false);` */
    public static CharSequence getRelativeTimeSpanString(android.content.Context c, long millis) {
        return getRelativeTimeSpanString(c, millis, false);
    }

    /**
     * AOSP DateUtils.formatDateRange(Context, Formatter, long, long, int)
     * -- `return formatDateRange(context, formatter, startMillis, endMillis,
     * flags, null);`
     */
    public static java.util.Formatter formatDateRange(android.content.Context context,
            java.util.Formatter formatter, long startMillis, long endMillis, int flags) {
        return formatDateRange(context, formatter, startMillis, endMillis, flags, null);
    }

    /**
     * AOSP DateUtils.formatDateRange(Context, Formatter, long, long, int,
     * String timeZone) -- appends the formatted range to the Formatter's own
     * output and returns THAT SAME Formatter (callers chain on it and read
     * formatter.out()). A null timeZone means the device's current zone.
     *
     * Note the contract that is easy to get wrong: it APPENDS, it does not
     * replace, so a Formatter that already has content keeps it.
     */
    public static java.util.Formatter formatDateRange(android.content.Context context,
            java.util.Formatter formatter, long startMillis, long endMillis, int flags,
            String timeZone) {
        if (formatter == null) throw new NullPointerException("formatter");
        java.time.ZoneId zone;
        if (timeZone != null) {
            try {
                zone = java.util.TimeZone.getTimeZone(timeZone).toZoneId();
            } catch (Throwable ignored) {
                zone = java.time.ZoneId.systemDefault();
            }
        } else if ((flags & FORMAT_UTC) != 0) {
            zone = java.time.ZoneOffset.UTC;
        } else {
            zone = java.time.ZoneId.systemDefault();
        }
        try {
            formatter.out().append(formatDateRange(context, startMillis, endMillis, flags, zone));
        } catch (java.io.IOException impossible) {
            throw new AssertionError(impossible);
        }
        return formatter;
    }

    public static CharSequence getRelativeTimeSpanString(android.content.Context context, long millis, boolean withPreposition) {
        return getRelativeTimeSpanString(millis, System.currentTimeMillis(), MINUTE_IN_MILLIS);
    }

    /** AOSP formatDateRange(Context, long, long, int): a date/time string honoring
     *  the FORMAT_* flags (frameworks/base/core/java/android/text/format/DateUtils
     *  .java, android11-release). Real AOSP builds this via a java.util.Formatter
     *  that delegates to libcore.icu.DateIntervalFormat (ICU4C, locale tables we
     *  don't carry); this renders the same flag-driven pieces -- weekday, date,
     *  time -- with java.time, whose Month/DayOfWeek display names are already
     *  locale-aware. Confirmed against a real caller: thunderbird's
     *  RelativeDateTimeFormatter.formatDate calls this with startMillis ==
     *  endMillis and each of FORMAT_SHOW_TIME alone, FORMAT_SHOW_WEEKDAY |
     *  FORMAT_ABBREV_WEEKDAY, FORMAT_SHOW_DATE | FORMAT_ABBREV_MONTH, and
     *  FORMAT_SHOW_DATE | FORMAT_SHOW_YEAR | FORMAT_NUMERIC_DATE -- the message
     *  list's "today" / "this week" / "this year" / "older" date column.
     *  <p>
     *  Known simplification: a true multi-day range (startMillis != endMillis on
     *  different days) renders each endpoint independently and joins them with a
     *  hyphen, rather than ICU's shared-field collapsing (real AOSP renders
     *  "Oct 9 - 10, 2007"; this renders "Oct 9, 2007 - Oct 10, 2007"). No known
     *  caller passes a multi-day range yet; every caller found so far uses the
     *  documented startMillis == endMillis idiom ("if the start and end time are
     *  the same, then just the start time is shown"), which this handles exactly. */
    public static String formatDateRange(android.content.Context context, long startMillis,
            long endMillis, int flags) {
        return formatDateRange(context, startMillis, endMillis, flags,
                (flags & FORMAT_UTC) != 0 ? java.time.ZoneOffset.UTC
                                          : java.time.ZoneId.systemDefault());
    }

    /** The zone-explicit body of formatDateRange, so the Formatter overloads
     *  above can honour their `timeZone` argument (and FORMAT_UTC) instead of
     *  silently using the device zone. */
    private static String formatDateRange(android.content.Context context, long startMillis,
            long endMillis, int flags, java.time.ZoneId zone) {
        // Mirrors AOSP's own reconciliation: an explicit SHOW_TIME with neither
        // 12h/24h flag falls back to the user's clock preference, not a locale
        // guess (frameworks/base DateUtils.formatDateRange, the FORMAT_SHOW_TIME
        // check immediately before the DateIntervalFormat.formatDateRange call).
        if ((flags & (FORMAT_SHOW_TIME | FORMAT_12HOUR | FORMAT_24HOUR)) == FORMAT_SHOW_TIME) {
            flags |= android.text.format.DateFormat.is24HourFormat(context)
                    ? FORMAT_24HOUR : FORMAT_12HOUR;
        }
        java.time.LocalDateTime start = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(startMillis), zone);
        java.time.LocalDateTime end = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(endMillis), zone);
        java.time.LocalDateTime now = java.time.LocalDateTime.now(zone);

        // "Normally the date is shown unless the start and end day are the same"
        // -- true regardless of FORMAT_SHOW_DATE once the two instants land on
        // different days.
        if (!start.toLocalDate().equals(end.toLocalDate())) flags |= FORMAT_SHOW_DATE;

        String startStr = formatDateRangeInstant(start, now, flags);
        if (startMillis == endMillis) return startStr;
        String endStr = formatDateRangeInstant(end, now, flags);
        return startStr.equals(endStr) ? startStr : (startStr + " - " + endStr);
    }

    /** One endpoint of formatDateRange: [weekday], [date], [time] joined with
     *  ", " in AOSP's own display order (see the class javadoc examples, e.g.
     *  "Tue, Oct 9" and "Oct 9, 8:00am"). */
    private static String formatDateRangeInstant(java.time.LocalDateTime dt,
            java.time.LocalDateTime now, int flags) {
        boolean showWeekday = (flags & FORMAT_SHOW_WEEKDAY) != 0;
        boolean showDate = (flags & FORMAT_SHOW_DATE) != 0;
        boolean showTime = (flags & FORMAT_SHOW_TIME) != 0;
        boolean abbrevAll = (flags & FORMAT_ABBREV_ALL) != 0;
        boolean abbrevWeekday = abbrevAll || (flags & FORMAT_ABBREV_WEEKDAY) != 0;
        boolean abbrevMonth = abbrevAll || (flags & FORMAT_ABBREV_MONTH) != 0;
        boolean numericDate = (flags & FORMAT_NUMERIC_DATE) != 0;
        boolean noMonthDay = (flags & FORMAT_NO_MONTH_DAY) != 0;
        boolean forceYear = (flags & FORMAT_SHOW_YEAR) != 0;
        boolean forceNoYear = (flags & FORMAT_NO_YEAR) != 0;
        boolean showYear = forceYear || (!forceNoYear && dt.getYear() != now.getYear());

        java.util.Locale locale = java.util.Locale.getDefault();
        java.util.List<String> parts = new java.util.ArrayList<String>();

        if (showWeekday) {
            parts.add(dt.getDayOfWeek().getDisplayName(
                    abbrevWeekday ? java.time.format.TextStyle.SHORT
                                  : java.time.format.TextStyle.FULL,
                    locale));
        }
        if (showDate) {
            StringBuilder date = new StringBuilder();
            if (numericDate) {
                date.append(dt.getMonthValue());
                if (!noMonthDay) date.append('/').append(dt.getDayOfMonth());
                if (showYear) date.append('/').append(dt.getYear());
            } else {
                date.append(dt.getMonth().getDisplayName(
                        abbrevMonth ? java.time.format.TextStyle.SHORT
                                    : java.time.format.TextStyle.FULL,
                        locale));
                if (!noMonthDay) date.append(' ').append(dt.getDayOfMonth());
                if (showYear) date.append(", ").append(dt.getYear());
            }
            parts.add(date.toString());
        }
        if (showTime) {
            boolean h24 = (flags & FORMAT_24HOUR) != 0;
            java.time.format.DateTimeFormatter tf = java.time.format.DateTimeFormatter.ofPattern(
                    h24 ? "H:mm" : "h:mm a", locale);
            parts.add(dt.format(tf));
        }
        if (parts.isEmpty()) {
            // AOSP's flag contract has no defined output for "show nothing" (zero
            // relevant flags on a single-day instant); fall back to a plain date
            // so callers never get back an empty label.
            parts.add(dt.toLocalDate().toString());
        }
        return String.join(", ", parts);
    }
}


