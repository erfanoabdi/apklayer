// android.icu.util.TimeZone -- ICU4J's TimeZone (the platform android.icu.* one,
// API 24+). Published API: developer.android.com/reference/android/icu/util/TimeZone
//
// On Mac.apk every time-zone operation resolves through the JDK's
// java.util.TimeZone, so this is a thin concrete wrapper that round-trips to a
// java.util.TimeZone delegate (mirroring how android.icu.util.ULocale wraps
// java.util.Locale). It was a NoClassDefFoundError blocker: duolingo's
// LaunchActivity.onCreate references it (via its startup/critical-path init),
// so the referencing class failed to load and onCreate aborted before adding
// its splash fragment -> blank screen. ICU TimeZone is abstract in ICU4J; we
// make this concrete so the static factories can vend a working instance and
// the abstract operations delegate to java.util.TimeZone.
package android.icu.util;

import java.util.Date;

public class TimeZone implements java.io.Serializable, Cloneable, java.io.Externalizable {
    private static final long serialVersionUID = 1L;

    // Display-name style constants (ICU values).
    public static final int SHORT = 0;
    public static final int LONG = 1;
    public static final int SHORT_GENERIC = 2;
    public static final int LONG_GENERIC = 3;
    public static final int SHORT_GMT = 4;
    public static final int LONG_GMT = 5;
    public static final int SHORT_COMMONLY_USED = 6;
    public static final int GENERIC_LOCATION = 7;

    // TimeZone implementation type constants.
    public static final int TIMEZONE_ICU = 0;
    public static final int TIMEZONE_JDK = 1;

    public static final String UNKNOWN_ZONE_ID = "Etc/Unknown";

    /** GMT (UTC) zone -- a frozen shared instance, like ICU's GMT_ZONE. */
    public static final TimeZone GMT_ZONE =
            new TimeZone(java.util.TimeZone.getTimeZone("GMT")).freezeShared();
    /** The "unknown" zone (GMT with id Etc/Unknown), like ICU's UNKNOWN_ZONE. */
    public static final TimeZone UNKNOWN_ZONE = makeUnknown();

    private java.util.TimeZone delegate;
    private boolean frozen;

    private static TimeZone makeUnknown() {
        java.util.TimeZone u = java.util.TimeZone.getTimeZone("GMT");
        u.setID(UNKNOWN_ZONE_ID);
        return new TimeZone(u).freezeShared();
    }
    private TimeZone freezeShared() { this.frozen = true; return this; }

    /** Public no-arg ctor (ICU TimeZone has one for serialization/subclassing). */
    public TimeZone() { this.delegate = java.util.TimeZone.getDefault(); }
    /** Internal: wrap a java.util.TimeZone. */
    public TimeZone(java.util.TimeZone tz) { this.delegate = (tz != null) ? tz : java.util.TimeZone.getDefault(); }

    java.util.TimeZone unwrap() { return delegate; }

    // ---- static factories ----
    public static TimeZone getDefault() { return new TimeZone(java.util.TimeZone.getDefault()); }
    public static synchronized void setDefault(TimeZone tz) {
        if (tz != null && tz.delegate != null) java.util.TimeZone.setDefault(tz.delegate);
    }
    public static TimeZone getTimeZone(String id) {
        return new TimeZone(java.util.TimeZone.getTimeZone(id));
    }
    public static TimeZone getTimeZone(String id, int type) { return getTimeZone(id); }
    public static TimeZone getFrozenTimeZone(String id) { return getTimeZone(id).freezeShared(); }
    public static TimeZone getTimeZone(java.time.ZoneId zoneId) {
        return new TimeZone(java.util.TimeZone.getTimeZone(zoneId));
    }

    public static String[] getAvailableIDs() { return java.util.TimeZone.getAvailableIDs(); }
    public static String[] getAvailableIDs(int rawOffset) { return java.util.TimeZone.getAvailableIDs(rawOffset); }
    public static String[] getAvailableIDs(String region) { return java.util.TimeZone.getAvailableIDs(); }
    public static int countEquivalentIDs(String id) { return 1; }
    public static String getEquivalentID(String id, int index) { return id; }
    public static String getCanonicalID(String id) { return id; }
    public static String getCanonicalID(String id, boolean[] isSystemID) {
        if (isSystemID != null && isSystemID.length > 0) isSystemID[0] = true;
        return id;
    }
    public static String getRegion(String id) { return "001"; }
    public static String getIDForWindowsID(String winID, String region) { return winID; }
    public static String getWindowsID(String id) { return id; }

    private static int defaultType = TIMEZONE_ICU;
    public static synchronized int getDefaultTimeZoneType() { return defaultType; }
    public static synchronized void setDefaultTimeZoneType(int type) { defaultType = type; }

    // ---- instance accessors (delegate to java.util.TimeZone) ----
    public String getID() { return delegate.getID(); }
    public void setID(String id) {
        if (frozen) throw new UnsupportedOperationException("frozen TimeZone");
        delegate.setID(id);
    }
    public int getRawOffset() { return delegate.getRawOffset(); }
    public void setRawOffset(int offsetMillis) {
        if (frozen) throw new UnsupportedOperationException("frozen TimeZone");
        delegate.setRawOffset(offsetMillis);
    }
    public int getOffset(long date) { return delegate.getOffset(date); }
    public int getOffset(int era, int year, int month, int day, int dayOfWeek, int milliseconds) {
        return delegate.getOffset(era, year, month, day, dayOfWeek, milliseconds);
    }
    /** ICU's getOffset(date, local, offsets[2]) -> fills [rawOffset, dstOffset]. */
    public void getOffset(long date, boolean local, int[] offsets) {
        int raw = delegate.getRawOffset();
        int total = delegate.getOffset(date);
        if (offsets != null && offsets.length >= 2) { offsets[0] = raw; offsets[1] = total - raw; }
    }
    public int getDSTSavings() { return delegate.getDSTSavings(); }
    public boolean useDaylightTime() { return delegate.useDaylightTime(); }
    public boolean observesDaylightTime() { return delegate.observesDaylightTime(); }
    public boolean inDaylightTime(Date date) { return delegate.inDaylightTime(date); }
    public boolean hasSameRules(TimeZone other) {
        return other != null && delegate.hasSameRules(other.delegate);
    }

    public String getDisplayName() { return delegate.getDisplayName(); }
    public String getDisplayName(java.util.Locale locale) { return delegate.getDisplayName(locale); }
    public String getDisplayName(ULocale locale) { return delegate.getDisplayName(locale != null ? locale.toLocale() : java.util.Locale.getDefault()); }
    public String getDisplayName(boolean daylight, int style) {
        return delegate.getDisplayName(daylight, style == LONG || style == LONG_GENERIC || style == LONG_GMT
                ? java.util.TimeZone.LONG : java.util.TimeZone.SHORT);
    }
    public String getDisplayName(boolean daylight, int style, java.util.Locale locale) {
        return delegate.getDisplayName(daylight, style == LONG || style == LONG_GENERIC || style == LONG_GMT
                ? java.util.TimeZone.LONG : java.util.TimeZone.SHORT, locale);
    }
    public String getDisplayName(boolean daylight, int style, ULocale locale) {
        return getDisplayName(daylight, style, locale != null ? locale.toLocale() : java.util.Locale.getDefault());
    }

    public java.time.ZoneId toZoneId() { return delegate.toZoneId(); }

    // ---- freeze / clone (ICU Freezable) ----
    public boolean isFrozen() { return frozen; }
    public TimeZone freeze() { this.frozen = true; return this; }
    public TimeZone cloneAsThawed() {
        TimeZone t = new TimeZone((java.util.TimeZone) delegate.clone());
        t.frozen = false;
        return t;
    }
    @Override public Object clone() {
        if (frozen) return this;   // ICU: a frozen clone returns itself
        return new TimeZone((java.util.TimeZone) delegate.clone());
    }

    @Override public boolean equals(Object o) {
        return (o instanceof TimeZone) && delegate.equals(((TimeZone) o).delegate);
    }
    @Override public int hashCode() { return delegate.hashCode(); }
    @Override public String toString() { return "android.icu.util.TimeZone[" + delegate.getID() + "]"; }

    // Externalizable (ICU declares it); we don't persist, so these are inert.
    @Override public void writeExternal(java.io.ObjectOutput out) {}
    @Override public void readExternal(java.io.ObjectInput in) {}
}
