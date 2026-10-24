package android.icu.text;

import android.icu.util.ULocale;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * android.icu.text.TimeZoneNames (API 24), the repackaged ICU4J
 * com.ibm.icu.text.TimeZoneNames. API surface mirrored from ICU4J main
 * (icu4j/main/core/src/main/java/com/ibm/icu/text/TimeZoneNames.java,
 * fetched 2026-09-29): the NameType enum, getInstance(ULocale|Locale),
 * getTZDBInstance, the abstract metazone/zone name lookups, the final
 * getDisplayName(tzID, type, date) that falls back from the zone name to the
 * metazone name, getExemplarLocationName, find, MatchInfo, getDisplayNames and
 * the Factory.
 *
 * WHY IT EXISTS. It was absent, so any app that asks for a zone's city name
 * died with NoClassDefFoundError at the call. Measured 2026-09-29 on
 * com.best.deskclock 2.31: its city model calls
 * TimeZoneNames.getInstance(locale).getExemplarLocationName(tzId) while
 * saving the selected cities, from DeskClock.onPause/onStop, so every
 * "add a clock" flow threw there and the city list never reached the
 * world-clock page (owner hand test: "Cannot add a new clock").
 *
 * BACKING. ICU's CLDR-backed TimeZoneNamesImpl is not in this runtime; the
 * host JDK's CLDR provider answers the zone display names instead
 * (java.util.TimeZone.getDisplayName for standard/daylight, java.time's
 * generic 'v' pattern for the generic names), and the exemplar location uses
 * ICU's own fallback algorithm, TimeZoneNamesImpl.getDefaultExemplarLocationName
 * (icu4j impl/TimeZoneNamesImpl.java:999-1011): null for an id with no
 * canonical country, else the last path segment with '_' -> ' '. That is the
 * name CLDR carries for the large majority of English exemplar cities; the
 * CLDR-specific overrides (e.g. localized city names) are the documented gap.
 * Metazones are not modelled: the metazone lookups answer the empty set / null,
 * which is the value ICU itself returns for a zone with no metazone mapping,
 * and getDisplayName reaches the zone name first, so it still answers.
 */
public abstract class TimeZoneNames implements java.io.Serializable {
    private static final long serialVersionUID = -9180227029248969153L;

    public enum NameType {
        LONG_GENERIC,
        LONG_STANDARD,
        LONG_DAYLIGHT,
        SHORT_GENERIC,
        SHORT_STANDARD,
        SHORT_DAYLIGHT,
        EXEMPLAR_LOCATION,
    }

    protected TimeZoneNames() {}

    public static TimeZoneNames getInstance(ULocale locale) {
        return new HostTimeZoneNames(locale != null ? locale.toLocale() : Locale.getDefault());
    }

    public static TimeZoneNames getInstance(Locale locale) {
        return new HostTimeZoneNames(locale != null ? locale : Locale.getDefault());
    }

    /** ICU: names from the TZ database (abbreviations only). */
    public static TimeZoneNames getTZDBInstance(ULocale locale) {
        return getInstance(locale);
    }

    public abstract Set<String> getAvailableMetaZoneIDs();

    public abstract Set<String> getAvailableMetaZoneIDs(String tzID);

    public abstract String getMetaZoneID(String tzID, long date);

    public abstract String getReferenceZoneID(String mzID, String region);

    public abstract String getMetaZoneDisplayName(String mzID, NameType type);

    /** ICU4J TimeZoneNames.java:278-285, verbatim. */
    public final String getDisplayName(String tzID, NameType type, long date) {
        String name = getTimeZoneDisplayName(tzID, type);
        if (name == null) {
            String mzID = getMetaZoneID(tzID, date);
            name = getMetaZoneDisplayName(mzID, type);
        }
        return name;
    }

    public abstract String getTimeZoneDisplayName(String tzID, NameType type);

    public String getExemplarLocationName(String tzID) {
        return defaultExemplarLocationName(tzID);
    }

    public Collection<MatchInfo> find(CharSequence text, int start, EnumSet<NameType> types) {
        throw new UnsupportedOperationException(
                "The method is not implemented in TimeZoneNames base class.");
    }

    public void loadAllDisplayNames() {}

    /** ICU4J TimeZoneNames.java:445-466, verbatim. */
    public void getDisplayNames(String tzID, NameType[] types, long date, String[] dest, int destOffset) {
        if (tzID == null || tzID.length() == 0) {
            return;
        }
        String mzID = null;
        for (int i = 0; i < types.length; ++i) {
            NameType type = types[i];
            String name = getTimeZoneDisplayName(tzID, type);
            if (name == null) {
                if (mzID == null) {
                    mzID = getMetaZoneID(tzID, date);
                }
                name = getMetaZoneDisplayName(mzID, type);
            }
            dest[destOffset + i] = name;
        }
    }

    public static class MatchInfo {
        private final NameType mNameType;
        private final String mTzID;
        private final String mMzID;
        private final int mMatchLength;

        public MatchInfo(NameType nameType, String tzID, String mzID, int matchLength) {
            if (nameType == null) throw new IllegalArgumentException("nameType is null");
            if (tzID == null && mzID == null)
                throw new IllegalArgumentException("Either tzID or mzID must be available");
            if (matchLength <= 0) throw new IllegalArgumentException("matchLength must be positive value");
            mNameType = nameType;
            mTzID = tzID;
            mMzID = mzID;
            mMatchLength = matchLength;
        }

        public String tzID() { return mTzID; }
        public String mzID() { return mMzID; }
        public NameType nameType() { return mNameType; }
        public int matchLength() { return mMatchLength; }
    }

    public abstract static class Factory {
        protected Factory() {}
        public abstract TimeZoneNames getTimeZoneNames(ULocale locale);
    }

    /** ICU TimeZoneNamesImpl.getDefaultExemplarLocationName (impl/TimeZoneNamesImpl.java
     *  :999-1011): null when the zone has no canonical country, else the part after
     *  the last '/', with '_' as ' '. ZoneMeta.getCanonicalCountry is null for the
     *  non-location zones (Etc/*, SystemV/*, bare ids such as "UTC" or "EST5EDT"),
     *  which is what the prefix and '/' tests below stand in for. */
    static String defaultExemplarLocationName(String tzID) {
        if (tzID == null || tzID.length() == 0) return null;
        if (tzID.startsWith("Etc/") || tzID.startsWith("SystemV/") || tzID.indexOf('/') < 0) return null;
        int sep = tzID.lastIndexOf('/');
        if (sep > 0 && sep + 1 < tzID.length()) {
            return tzID.substring(sep + 1).replace('_', ' ');
        }
        return null;
    }

    /** The concrete instance getInstance hands out, answered from the host JDK's CLDR data. */
    static final class HostTimeZoneNames extends TimeZoneNames {
        private static final long serialVersionUID = 1L;
        private final Locale mLocale;

        HostTimeZoneNames(Locale locale) { mLocale = locale; }

        @Override public Set<String> getAvailableMetaZoneIDs() { return Collections.emptySet(); }
        @Override public Set<String> getAvailableMetaZoneIDs(String tzID) { return Collections.emptySet(); }
        @Override public String getMetaZoneID(String tzID, long date) { return null; }
        @Override public String getReferenceZoneID(String mzID, String region) { return null; }
        @Override public String getMetaZoneDisplayName(String mzID, NameType type) { return null; }

        @Override public String getTimeZoneDisplayName(String tzID, NameType type) {
            if (tzID == null || tzID.isEmpty() || type == null) return null;
            if (type == NameType.EXEMPLAR_LOCATION) return getExemplarLocationName(tzID);
            java.util.TimeZone tz = java.util.TimeZone.getTimeZone(tzID);
            // getTimeZone answers GMT for an unknown id; ICU answers null.
            if (!tzID.equals(tz.getID())) return null;
            try {
                switch (type) {
                    case LONG_STANDARD:  return tz.getDisplayName(false, java.util.TimeZone.LONG, mLocale);
                    case LONG_DAYLIGHT:  return tz.useDaylightTime()
                            ? tz.getDisplayName(true, java.util.TimeZone.LONG, mLocale) : null;
                    case SHORT_STANDARD: return tz.getDisplayName(false, java.util.TimeZone.SHORT, mLocale);
                    case SHORT_DAYLIGHT: return tz.useDaylightTime()
                            ? tz.getDisplayName(true, java.util.TimeZone.SHORT, mLocale) : null;
                    case LONG_GENERIC:
                    case SHORT_GENERIC: {
                        java.time.ZoneId zid = java.time.ZoneId.of(tzID);
                        String pattern = (type == NameType.LONG_GENERIC) ? "vvvv" : "v";
                        return java.time.format.DateTimeFormatter.ofPattern(pattern, mLocale)
                                .format(java.time.ZonedDateTime.now(zid));
                    }
                    default: return null;
                }
            } catch (RuntimeException e) {
                return null;
            }
        }
    }
}
