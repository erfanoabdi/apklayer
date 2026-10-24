package android.telephony.mbms;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
/** Describes one eMBMS (cell-broadcast multicast) service. No MBMS middleware
 *  on Mac.apk, so an app never receives a populated instance; the accessors
 *  return empty. API 28.
 *  Source: frameworks/base/telephony/java/android/telephony/mbms/ServiceInfo.java */
public class ServiceInfo {
    protected ServiceInfo() { }
    public CharSequence getNameForLocale(Locale locale) { return ""; }
    public Set<Locale> getNamedContentLocales() { return Collections.emptySet(); }

    // API-36 surface added 2026-08-24. Mac.apk has no MBMS middleware, so an
    // app never receives a populated instance -- but AOSP's own accessors are
    // @NonNull for the collections and the id/class name, so the honest answer
    // is "empty", not null, and the session window is the epoch rather than an
    // invented broadcast schedule.
    // Source: frameworks/base/telephony/java/android/telephony/mbms/ServiceInfo.java

    /** AOSP is @NonNull; a service that was never announced has no locales. */
    public List<Locale> getLocales() { return Collections.emptyList(); }

    /** AOSP is @NonNull; "" means the middleware never named a class. */
    public String getServiceClassName() { return ""; }

    /** AOSP is @NonNull; "" means the middleware never announced an id. */
    public String getServiceId() { return ""; }

    /** AOSP is @NonNull; the epoch marks "no session was ever announced". */
    public Date getSessionStartTime() { return new Date(0L); }

    /** AOSP is @NonNull; see {@link #getSessionStartTime()}. */
    public Date getSessionEndTime() { return new Date(0L); }
}
