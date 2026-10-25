package android.icu.text;

/** Formats relative times ("in 3 days", "yesterday", "3 days ago"). Was
 *  previously a 17-line stub that dropped "ago"/"in", never said "yesterday"/
 *  "tomorrow"/"today"/"now", and never singularized ("1 days" instead of
 *  "1 day") -- visibly wrong in any activity feed / notification-timestamp
 *  UI, which is exactly what apps use this class for.
 *
 *  ICU4J's real implementation (external/icu/android_icu4j/src/main/java/
 *  android/icu/text/RelativeDateTimeFormatter.java, fetched from
 *  android.googlesource.com/platform/external/icu +/refs/heads/main) is
 *  CLDR resource-bundle driven per locale and plural category. Porting that
 *  (and the CLDR data it reads) is out of scope for a self-contained fix, so
 *  this implements the well-established default English wording only --
 *  the same English CLDR "fields/relative" and "fields/relativeTime" values
 *  every other ICU-compatible implementation uses for en, and the table
 *  every other locale falls back to via CLDR root-locale inheritance when a
 *  locale does not override it.
 *
 *  Direction/unit legality mirrors AOSP exactly (both throw
 *  IllegalArgumentException, verified against the fetched source at the
 *  cited lines):
 *    - format(quantity, direction, unit): direction MUST be LAST or NEXT
 *      (RelativeDateTimeFormatter.java:645-647).
 *    - format(direction, AbsoluteUnit.NOW): direction MUST be PLAIN
 *      (RelativeDateTimeFormatter.java:797-799).
 *  LAST_2/NEXT_2 ("day before yesterday" / "week after next") are genuinely
 *  standard English idiom for DAY and WEEK; for every other unit AOSP's own
 *  javadoc says the method "may return null to signal that no formatted
 *  string is available" for those two directions, so this returns null
 *  there too rather than inventing wording.
 */
public final class RelativeDateTimeFormatter {
    public enum Direction { LAST_2, LAST, THIS, NEXT, NEXT_2, PLAIN }
    public enum RelativeUnit { SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, QUARTERS, YEARS }
    public enum AbsoluteUnit { SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, DAY, WEEK, MONTH, QUARTER, YEAR, NOW }

    private RelativeDateTimeFormatter() {}
    public static RelativeDateTimeFormatter getInstance() { return new RelativeDateTimeFormatter(); }
    public static RelativeDateTimeFormatter getInstance(java.util.Locale locale) { return new RelativeDateTimeFormatter(); }
    public static RelativeDateTimeFormatter getInstance(android.icu.util.ULocale locale) { return new RelativeDateTimeFormatter(); }

    /** English CLDR fields/relativeTime unit noun, singular form; regular
     *  plurals for all eight (append "s") match English CLDR's plural noun
     *  for every one of them. */
    private static String noun(RelativeUnit unit) {
        switch (unit) {
            case SECONDS:  return "second";
            case MINUTES:  return "minute";
            case HOURS:    return "hour";
            case DAYS:     return "day";
            case WEEKS:    return "week";
            case MONTHS:   return "month";
            case QUARTERS: return "quarter";
            case YEARS:    return "year";
            default:       return unit.name().toLowerCase(java.util.Locale.US);
        }
    }

    private static String weekday(AbsoluteUnit unit) {
        switch (unit) {
            case SUNDAY:    return "Sunday";
            case MONDAY:    return "Monday";
            case TUESDAY:   return "Tuesday";
            case WEDNESDAY: return "Wednesday";
            case THURSDAY:  return "Thursday";
            case FRIDAY:    return "Friday";
            case SATURDAY:  return "Saturday";
            default:        return null;
        }
    }

    /** "5" for a whole number, else a plain decimal ("5.5") -- AOSP formats
     *  quantity through the caller's NumberFormat; without one, the closest
     *  honest default is "don't show a spurious .0". */
    private static String formatQuantity(double quantity) {
        if (quantity == Math.rint(quantity) && !Double.isInfinite(quantity)) {
            return Long.toString((long) quantity);
        }
        return String.valueOf(quantity);
    }

    /** AOSP RelativeDateTimeFormatter.java:645-647 (formatImpl): "direction
     *  must be NEXT or LAST" -- any other value throws. */
    public String format(double quantity, Direction direction, RelativeUnit unit) {
        if (direction != Direction.LAST && direction != Direction.NEXT) {
            throw new IllegalArgumentException("direction must be NEXT or LAST");
        }
        // English "one" plural category: exactly 1 (not 1.5, not -1).
        boolean isOne = quantity == 1.0;
        String n = noun(unit) + (isOne ? "" : "s");
        String qty = formatQuantity(quantity);
        return (direction == Direction.NEXT) ? ("in " + qty + " " + n) : (qty + " " + n + " ago");
    }

    /** AOSP RelativeDateTimeFormatter.java:797-799 (formatAbsoluteImpl):
     *  NOW can only accept direction PLAIN -- any other value throws. */
    public String format(Direction direction, AbsoluteUnit unit) {
        if (unit == AbsoluteUnit.NOW) {
            if (direction != Direction.PLAIN) {
                throw new IllegalArgumentException("NOW can only accept direction PLAIN.");
            }
            return "now";
        }

        String wd = weekday(unit);
        if (wd != null) {
            switch (direction) {
                case LAST: return "last " + wd;
                case THIS: return "this " + wd;
                case NEXT: return "next " + wd;
                case PLAIN: return wd;
                // English has no standard "the Sunday before last" idiom;
                // AOSP's own javadoc allows null here for exactly this case.
                default: return null;
            }
        }

        switch (unit) {
            case DAY:
                switch (direction) {
                    case LAST_2: return "the day before yesterday";
                    case LAST:   return "yesterday";
                    case THIS:   return "today";
                    case NEXT:   return "tomorrow";
                    case NEXT_2: return "the day after tomorrow";
                    case PLAIN:  return "day";
                }
                break;
            case WEEK:
                switch (direction) {
                    case LAST_2: return "the week before last";
                    case LAST:   return "last week";
                    case THIS:   return "this week";
                    case NEXT:   return "next week";
                    case NEXT_2: return "the week after next";
                    case PLAIN:  return "week";
                }
                break;
            case MONTH:
                switch (direction) {
                    case LAST:  return "last month";
                    case THIS:  return "this month";
                    case NEXT:  return "next month";
                    case PLAIN: return "month";
                    default:    return null; // LAST_2/NEXT_2: no standard English idiom
                }
            case QUARTER:
                switch (direction) {
                    case LAST:  return "last quarter";
                    case THIS:  return "this quarter";
                    case NEXT:  return "next quarter";
                    case PLAIN: return "quarter";
                    default:    return null;
                }
            case YEAR:
                switch (direction) {
                    case LAST:  return "last year";
                    case THIS:  return "this year";
                    case NEXT:  return "next year";
                    case PLAIN: return "year";
                    default:    return null;
                }
        }
        return null;
    }
}
