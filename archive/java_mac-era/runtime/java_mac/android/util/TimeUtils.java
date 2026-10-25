// android.util.TimeUtils -- the four PUBLIC members of AOSP's
// frameworks/base/core/java/android/util/TimeUtils.java. (AOSP's file is much
// larger; everything else in it is @hide and would not be reachable from an
// app anyway.)
//
// HOW THIS IS BACKED, and why (the host-first decision ladder in CLAUDE.md):
// AOSP answers the country/zone questions from libcore's TimeZoneFinder +
// ZoneInfoDb, which read /apex/com.android.tzdata. macOS ships the SAME
// upstream IANA tzdata at /usr/share/zoneinfo -- including the country->zone
// table (`zone.tab`) and the release stamp (`+VERSION`) -- and the JDK's own
// java.time/java.util.TimeZone rules come from the same upstream release. So
// rung 1 applies: route to the host rather than vendor a copy of the table.
//
// Verified on this machine 2026-08-24:
//   /usr/share/zoneinfo/+VERSION   -> "2026c"
//   /usr/share/zoneinfo/zone.tab   -> 418 country/zone rows
// macOS does NOT ship zone1970.tab (checked; absent), so zone.tab is the
// primary and zone1970.tab is only a fallback for hosts that have it. Both
// have the same first-two-columns shape (CC[,CC...] <coords> <zoneid>), so one
// parser reads either.
//
// KNOWN DIVERGENCE, stated rather than papered over: AOSP's list is CURATED
// (its own javadoc says so -- it drops contentious/obsolete mappings and
// filters by MIN_USE_DATE_OF_TIMEZONE, 2019-01-01). Reading raw tzdata gives
// the uncurated set, so getTimeZoneIdsForCountryCode may return a couple of
// extra legacy ids for a handful of countries. That is a superset, not a
// wrong answer, and it beats returning null for every country.
package android.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public class TimeUtils {
    /** AOSP has an implicit public default constructor here. */
    public TimeUtils() {}

    private static final String[] ZONEINFO_DIRS = {
        "/usr/share/zoneinfo",
        "/var/db/timezone/zoneinfo",   // macOS keeps a second copy here
    };

    // Lazily-built, then immutable: country code (lower case) -> zone ids, in
    // the order tzdata lists them (which puts the country's principal zone
    // first, matching what a picker wants).
    private static volatile Map<String, List<String>> sCountryZones;
    private static volatile String sTzdbVersion;

    /**
     * Tries to return a time zone that would have had the specified offset
     * and DST value at the specified moment in the specified country.
     * Returns null if no suitable zone could be found.
     *
     * AOSP biases the search toward the default zone, so that a device already
     * sitting in a matching zone keeps it instead of jumping to an equivalent
     * one. Mirrored here: the default zone is tested first when it belongs to
     * the country.
     */
    public static TimeZone getTimeZone(int offset, boolean dst, long when, String country) {
        if (country == null) {
            return null;
        }
        List<String> ids = getTimeZoneIdsForCountryCode(country);
        if (ids == null || ids.isEmpty()) {
            return null;
        }

        final String biasId = TimeZone.getDefault().getID();
        if (ids.contains(biasId) && matches(biasId, offset, dst, when)) {
            return TimeZone.getTimeZone(biasId);
        }
        for (String id : ids) {
            if (matches(id, offset, dst, when)) {
                return TimeZone.getTimeZone(id);
            }
        }
        return null;
    }

    private static boolean matches(String id, int offset, boolean dst, long when) {
        TimeZone tz = TimeZone.getTimeZone(id);
        // getTimeZone answers GMT for an unknown id; reject that so a bad row
        // in zone.tab cannot masquerade as a match.
        if (!id.equals(tz.getID())) {
            return false;
        }
        if (tz.getOffset(when) != offset) {
            return false;
        }
        return tz.inDaylightTime(new java.util.Date(when)) == dst;
    }

    /**
     * The IANA tzdata release the zone rules come from, e.g. "2026c".
     * AOSP reads it from ZoneInfoDb; we read the host's own stamp.
     */
    public static String getTimeZoneDatabaseVersion() {
        String v = sTzdbVersion;
        if (v != null) {
            return v;
        }
        for (String dir : ZONEINFO_DIRS) {
            File f = new File(dir, "+VERSION");
            if (!f.isFile()) continue;
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line = r.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    v = line.trim();
                    break;
                }
            } catch (IOException ignored) {
                // fall through to the next candidate
            }
        }
        if (v == null) {
            // Never throw and never return null: AOSP's contract is a
            // non-null String, and a caller that logs it must not NPE just
            // because a host laid its tzdata out differently.
            v = "unknown";
        }
        sTzdbVersion = v;
        return v;
    }

    /**
     * Time zone IDs known to be associated with a country, or null if the
     * country code is unrecognized.
     *
     * @param countryCode ISO 3166-1 alpha-2, as from {@link Locale#getCountry()}
     */
    public static List<String> getTimeZoneIdsForCountryCode(String countryCode) {
        if (countryCode == null) {
            throw new NullPointerException("countryCode == null");
        }
        List<String> ids = countryZones().get(countryCode.toLowerCase(Locale.ROOT));
        return ids == null ? null : Collections.unmodifiableList(ids);
    }

    private static Map<String, List<String>> countryZones() {
        Map<String, List<String>> m = sCountryZones;
        if (m != null) {
            return m;
        }
        synchronized (TimeUtils.class) {
            if (sCountryZones != null) {
                return sCountryZones;
            }
            Map<String, LinkedHashSet<String>> acc = new HashMap<>();
            boolean any = false;
            for (String dir : ZONEINFO_DIRS) {
                // zone1970.tab is the modern file; zone.tab is the one macOS
                // actually ships. Read whichever exists, first hit wins.
                any |= parseZoneTab(new File(dir, "zone1970.tab"), acc);
                any |= parseZoneTab(new File(dir, "zone.tab"), acc);
                if (any) break;
            }
            Map<String, List<String>> out = new HashMap<>();
            for (Map.Entry<String, LinkedHashSet<String>> e : acc.entrySet()) {
                out.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            sCountryZones = out;
            return out;
        }
    }

    /**
     * tzdata's zone.tab / zone1970.tab format: tab-separated, '#' comments,
     * column 0 is a comma-separated list of ISO-3166 codes, column 2 is the
     * zone id. (zone.tab's column 0 is always a single code; zone1970.tab's
     * can list several, which is why we split on ',' either way.)
     */
    private static boolean parseZoneTab(File f, Map<String, LinkedHashSet<String>> acc) {
        if (!f.isFile()) {
            return false;
        }
        boolean read = false;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] cols = line.split("\t");
                if (cols.length < 3) continue;
                String zoneId = cols[2].trim();
                if (zoneId.isEmpty()) continue;
                for (String cc : cols[0].split(",")) {
                    cc = cc.trim().toLowerCase(Locale.ROOT);
                    if (cc.isEmpty()) continue;
                    LinkedHashSet<String> set = acc.get(cc);
                    if (set == null) {
                        set = new LinkedHashSet<>();
                        acc.put(cc, set);
                    }
                    set.add(zoneId);
                }
                read = true;
            }
        } catch (IOException ignored) {
            // A host without a readable table simply answers null per country,
            // which is the documented "unrecognized" result.
        }
        return read;
    }

    /**
     * Is {@code reference} inside the [start, end] window, where the window may
     * wrap around midnight? Transcribed from AOSP verbatim (the boolean shape
     * is deliberately AOSP's, including the inclusive-endpoint behaviour that
     * falls out of using isBefore/isAfter rather than compareTo).
     *
     * AOSP: TimeUtils.java, isTimeBetween(LocalTime,LocalTime,LocalTime).
     */
    public static boolean isTimeBetween(LocalTime reference, LocalTime start, LocalTime end) {
        //    ////////E----+-----S////////
        if ((reference.isBefore(start) && reference.isAfter(end)
                //    -----+----S//////////E------
                || (reference.isBefore(end) && reference.isBefore(start) && start.isBefore(end))
                //    ---------S//////////E---+---
                || (reference.isAfter(end) && reference.isAfter(start)) && start.isBefore(end))) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * A set view of every zone id tzdata associates with some country. Not part
     * of the public API; used by our own diagnostics.
     * @hide
     */
    public static Set<String> macapkKnownCountryCodes() {
        return Collections.unmodifiableSet(countryZones().keySet());
    }
}
