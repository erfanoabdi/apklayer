package android.icu.text;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Generates a locale-appropriate date/time pattern from a "skeleton"
 * (e.g. "yMMMd" -> "MMM d, y" in en-US). Apps use getDefaultHourCycle() to pick
 * 12- vs 24-hour display and getBestPattern(skeleton) to format.
 *
 * getDefaultHourCycle is derived from the locale's own short time format via
 * java.text.SimpleDateFormat: if the pattern contains 'H'/'k' it is a 24-hour
 * locale, else 12-hour. That is the observable fact apps read, and it matches
 * what a device reports for the same locale.
 *
 * Source: https://developer.android.com/reference/android/icu/text/DateTimePatternGenerator
 */
public class DateTimePatternGenerator {

    private final Locale mLocale;

    private DateTimePatternGenerator(Locale locale) { mLocale = locale; }

    public static DateTimePatternGenerator getInstance() {
        return new DateTimePatternGenerator(Locale.getDefault());
    }
    public static DateTimePatternGenerator getInstance(Locale locale) {
        return new DateTimePatternGenerator(locale != null ? locale : Locale.getDefault());
    }
    public static DateTimePatternGenerator getInstance(android.icu.util.ULocale locale) {
        return new DateTimePatternGenerator(
                locale != null ? locale.toLocale() : Locale.getDefault());
    }

    public DateFormat.HourCycle getDefaultHourCycle() {
        try {
            java.text.DateFormat df = java.text.DateFormat.getTimeInstance(
                    java.text.DateFormat.SHORT, mLocale);
            if (df instanceof SimpleDateFormat) {
                String p = ((SimpleDateFormat) df).toPattern();
                if (p.indexOf('H') >= 0) return DateFormat.HourCycle.HOUR_CYCLE_23;
                if (p.indexOf('k') >= 0) return DateFormat.HourCycle.HOUR_CYCLE_24;
                if (p.indexOf('K') >= 0) return DateFormat.HourCycle.HOUR_CYCLE_11;
            }
        } catch (Throwable ignored) { }
        return DateFormat.HourCycle.HOUR_CYCLE_12;
    }

    /**
     * Best-effort skeleton -> pattern. ICU's full skeleton algebra is large;
     * apps overwhelmingly pass a handful of common skeletons, mapped here, and
     * anything else returns the skeleton unchanged (ICU's documented fallback
     * when it cannot improve on the input).
     */
    public String getBestPattern(String skeleton) {
        if (skeleton == null) return "";
        String host = macapkHostBestPattern(skeleton, mLocale);
        if (host != null) return host;
        boolean h24 = getDefaultHourCycle() == DateFormat.HourCycle.HOUR_CYCLE_23
                || getDefaultHourCycle() == DateFormat.HourCycle.HOUR_CYCLE_24;
        switch (skeleton) {
            case "yMMMd":  return "MMM d, y";
            case "yMMMMd": return "MMMM d, y";
            case "yMd":    return "M/d/y";
            case "MMMd":   return "MMM d";
            case "Hm":     return "HH:mm";
            case "hm":     return h24 ? "HH:mm" : "h:mm a";
            case "jm":     return h24 ? "HH:mm" : "h:mm a";
            case "Hms":    return "HH:mm:ss";
            case "hms":    return h24 ? "HH:mm:ss" : "h:mm:ss a";
            default:       return skeleton;
        }
    }

    /** MACAPK_SKELETON_HOST, default ON (2026-09-29): resolve a skeleton through
     *  the host JDK's CLDR data instead of the hand table / "return the skeleton"
     *  fallback. =0 restores the old answers exactly. */
    static final boolean SKELETON_HOST = !"0".equals(System.getenv("MACAPK_SKELETON_HOST"));

    /** Canonical field order of java.time's requestedTemplate
     *  (DateTimeFormatterBuilder.getLocalizedDateTimePattern(String, Chronology,
     *  Locale), JDK 19+: "G{0,5}y*Q{0,5}M{0,5}w*E{0,5}d{0,2}B{0,5}[hHjC]{0,2}
     *  m{0,2}s{0,2}[vz]{0,4}"). */
    private static final String TEMPLATE_ORDER = "GyQMwEdBhmsv";

    /** ICU's getBestPattern answered from the host: the same CLDR availableFormats
     *  data ICU reads, through java.time's skeleton matcher
     *  (DateTimeFormatterBuilder.getLocalizedDateTimePattern, JDK 19+). That
     *  matcher is EXACT where ICU's is fuzzy: it takes one width per field in a
     *  fixed order and rejects "hmm", "jmm" or "EEEEMMMMd" outright, and it does
     *  not stretch a matched pattern to the requested width. So the skeleton is
     *  first reduced to the CLDR key widths (numeric fields 1, M 1 or 3, E 1,
     *  Q 1 or 3, v 1 or 4), queried, and the answer's fields are then widened
     *  back the way ICU's adjustFieldTypes does: a TEXT field (M/L >= 3, E, G,
     *  Q >= 3, v, z) takes the requested width, an hour / minute / second field
     *  keeps the pattern's own width (MATCH_NO_OPTIONS), any other numeric field
     *  keeps the wider of the two ("hm" -> "h:mm a", "hhmm" -> "h:mm a",
     *  "yMMdd" -> "MM/dd/y"). The AM/PM letter is
     *  dropped from the query because 'h' already implies it in CLDR's
     *  patterns. Returns null when a field cannot be expressed (S, A, D, W...),
     *  and the caller keeps its previous answer. */
    public static String macapkHostBestPattern(String skeleton, Locale locale) {
        if (!SKELETON_HOST || skeleton == null || skeleton.isEmpty()) return null;
        Locale loc = locale != null ? locale : Locale.getDefault();
        try {
            int[] want = new int[128];           // requested width per canonical field
            char hourLetter = 0;
            for (int i = 0; i < skeleton.length(); i++) {
                char f = canonicalField(skeleton.charAt(i));
                if (f == 0) return null;
                if (f == '-') continue;          // a / b: implied by h
                char c = skeleton.charAt(i);
                if (f == 'h') {
                    if (c == 'j' || c == 'C') c = preferredHour(loc);
                    else if (c == 'K') c = 'h';
                    else if (c == 'k') c = 'H';
                    hourLetter = c;
                }
                want[f]++;
            }
            StringBuilder tmpl = new StringBuilder();
            for (int i = 0; i < TEMPLATE_ORDER.length(); i++) {
                char f = TEMPLATE_ORDER.charAt(i);
                int n = want[f];
                if (n == 0) continue;
                char letter = (f == 'h') ? hourLetter : f;
                int qn;
                switch (f) {
                    case 'M': case 'Q': qn = n >= 3 ? 3 : 1; break;
                    case 'v': qn = n >= 4 ? 4 : 1; break;
                    case 'G': qn = Math.min(n, 5); break;
                    default:  qn = 1;
                }
                for (int k = 0; k < qn; k++) tmpl.append(letter);
            }
            if (tmpl.length() == 0) return null;
            String pattern = java.time.format.DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                    tmpl.toString(), java.time.chrono.IsoChronology.INSTANCE, loc);
            return adjustFieldWidths(pattern, want);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The requestedTemplate field a skeleton letter belongs to; '-' = ignored, 0 = unsupported. */
    private static char canonicalField(char c) {
        switch (c) {
            case 'G': return 'G';
            case 'y': case 'u': case 'Y': return 'y';
            case 'Q': case 'q': return 'Q';
            case 'M': case 'L': return 'M';
            case 'w': return 'w';
            case 'E': case 'c': case 'e': return 'E';
            case 'd': return 'd';
            case 'B': return 'B';
            case 'h': case 'H': case 'K': case 'k': case 'j': case 'C': return 'h';
            case 'm': return 'm';
            case 's': return 's';
            case 'v': case 'z': return 'v';
            case 'a': case 'b': return '-';
            default: return 0;
        }
    }

    private static char preferredHour(Locale loc) {
        DateFormat.HourCycle hc = getInstance(loc).getDefaultHourCycle();
        return (hc == DateFormat.HourCycle.HOUR_CYCLE_23 || hc == DateFormat.HourCycle.HOUR_CYCLE_24) ? 'H' : 'h';
    }

    /** ICU adjustFieldTypes, reduced to what the host query needs (see above). */
    static String adjustFieldWidths(String pattern, int[] want) {
        StringBuilder out = new StringBuilder(pattern.length() + 4);
        boolean quoted = false;
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            if (c == '\'') { quoted = !quoted; out.append(c); i++; continue; }
            if (quoted || !((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) { out.append(c); i++; continue; }
            int j = i;
            while (j < pattern.length() && pattern.charAt(j) == c) j++;
            int have = j - i;
            char f = canonicalField(c);
            int req = (f > 0 && f != '-') ? want[f] : 0;
            int len = have;
            if (req > 0) {
                boolean text = f == 'E' || f == 'G' || f == 'v'
                        || ((f == 'M' || f == 'Q') && (req >= 3 || have >= 3));
                // Hour / minute / second keep the PATTERN's own width: ICU
                // adjustFieldTypes (icu4j DateTimePatternGenerator.java, `adjFieldLen =
                // fieldBuilder.length()` when `type == HOUR && (options &
                // MATCH_HOUR_FIELD_LENGTH) == 0`, likewise MINUTE / SECOND) and AOSP's
                // DateFormat.getBestDateTimePattern passes MATCH_NO_OPTIONS
                // (android14 DateFormat.java:272), so en_US "hhmm" is "h:mm a", not
                // "hh:mm a". (Text-input lane review, 2026-09-29.)
                final boolean timeField = f == 'h' || f == 'm' || f == 's';
                len = text ? req : timeField ? have : Math.max(have, req);
            }
            for (int k = 0; k < len; k++) out.append(c);
            i = j;
        }
        return out.toString();
    }
}
