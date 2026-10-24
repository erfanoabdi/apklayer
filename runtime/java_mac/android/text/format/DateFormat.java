// android.text.format.DateFormat -- AOSP helper that hands back
// java.text.DateFormat instances honoring the user's locale / 12-24h
// preference. AOSP:
//   frameworks/base/core/java/android/text/format/DateFormat.java
//
// F-Droid's AppDetails (and other date-bearing screens) call
// DateFormat.getDateFormat(context) to render "last updated" / "added"
// dates while binding. The previous bare stub had none of these statics, so
// the bind threw NoSuchMethodError and aborted the detail RecyclerView's
// layout pass. We don't model the per-user 12/24h system setting, so we
// return the JDK locale-default formatters -- the same observable type
// (java.text.DateFormat) the APK expects, with locale-correct output.
package android.text.format;

import android.content.Context;

public class DateFormat {
    public DateFormat() {}

    /** AOSP: a short date formatter for the user's locale. */
    public static java.text.DateFormat getDateFormat(Context context) {
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT);
    }

    /** AOSP: a medium-length date formatter (e.g. "Jan 3, 2020"). */
    public static java.text.DateFormat getMediumDateFormat(Context context) {
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
    }

    /** AOSP: a long date formatter for the user's locale. */
    public static java.text.DateFormat getLongDateFormat(Context context) {
        return java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG);
    }

    /** AOSP: a time formatter honoring the 12/24h setting. We default to the
     *  JDK locale time format (no system 12/24h preference modeled). */
    public static java.text.DateFormat getTimeFormat(Context context) {
        return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT);
    }

    /** AOSP: true if the user prefers 24-hour time. No per-user system setting
     *  on Mac.apk; derive from the host locale's time pattern (whether it uses
     *  'H'/'k' 24-hour fields rather than 'h'/'K' + an am/pm marker) so the
     *  answer matches what getTimeFormat would render. */
    public static boolean is24HourFormat(Context context) {
        return is24HourLocale(java.util.Locale.getDefault());
    }
    public static boolean is24HourFormat(Context context, int userHandle) {
        return is24HourFormat(context);
    }
    private static boolean is24HourLocale(java.util.Locale locale) {
        java.text.DateFormat df =
            java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, locale);
        if (df instanceof java.text.SimpleDateFormat) {
            String p = ((java.text.SimpleDateFormat) df).toPattern();
            return p.indexOf('H') >= 0 || p.indexOf('k') >= 0;
        }
        return false;
    }

    /** AOSP getBestDateTimePattern: the best localized pattern matching a
     *  skeleton (e.g. "yMMMd"). java.time can synthesize a locale pattern from
     *  the skeleton's fields; we map the common case (a skeleton that is itself
     *  a valid pattern is returned as-is, which is what most callers pass). */
    public static String getBestDateTimePattern(java.util.Locale locale, String skeleton) {
        if (skeleton == null) return "";
        // AOSP (android14 DateFormat.java:267-275): DateTimePatternGenerator
        // .getInstance(uLocale).getBestPattern(skeleton, ...), then
        // getCompatibleEnglishPattern (:724-735), which turns CLDR 42's U+202F
        // before the AM/PM marker back into a plain space for en / en-US. We
        // used to hand the SKELETON back, so "hma" formatted as "126PM"
        // (com.best.deskclock's city list, 2026-09-29).
        java.util.Locale loc = locale != null ? locale : java.util.Locale.getDefault();
        String host = android.icu.text.DateTimePatternGenerator.macapkHostBestPattern(skeleton, loc);
        if (host != null) {
            String country = loc.getCountry();
            if ("en".equals(loc.getLanguage()) && (country == null || country.isEmpty() || "US".equals(country)))
                host = host.replace('\u202f', ' ');
            return host;
        }
        try {
            // java.time exposes a best-pattern resolver via the locale's
            // FormatStyle chain is not skeleton-aware; for the common skeletons
            // the field letters ARE a usable SimpleDateFormat pattern, so we
            // return the skeleton (AOSP-compatible for "yMd"/"Hm"/"jmm" forms
            // after normalizing the locale-neutral 'j' hour to the locale's
            // preferred hour field).
            boolean h24 = is24HourLocale(locale != null ? locale : java.util.Locale.getDefault());
            return skeleton.replace('j', h24 ? 'H' : 'h');
        } catch (Exception e) {
            return skeleton;
        }
    }

    /** AOSP format(CharSequence inFormat, long inTimeInMillis). */
    public static CharSequence format(CharSequence inFormat, long inTimeInMillis) {
        return format(inFormat, new java.util.Date(inTimeInMillis));
    }
    public static CharSequence format(CharSequence inFormat, java.util.Date inDate) {
        if (inFormat == null) return "";
        try {
            java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat(inFormat.toString(), java.util.Locale.getDefault());
            return sdf.format(inDate != null ? inDate : new java.util.Date());
        } catch (Exception e) {
            return inFormat.toString();
        }
    }
    public static CharSequence format(CharSequence inFormat, java.util.Calendar inDate) {
        long millis = (inDate != null) ? inDate.getTimeInMillis() : System.currentTimeMillis();
        return format(inFormat, millis);
    }

    public static char[] getDateFormatOrder(android.content.Context context) { return new char[]{'M', 'd', 'y'}; }
}

