// android.app.usage.UsageStatsManager
//
// AOSP's app-usage-statistics service. Apps fetch it via getSystemService(
// USAGE_STATS_SERVICE) to query per-app usage and the current app-standby
// bucket. Mac.apk collects no usage stats, so queries return empty and the
// standby bucket is ACTIVE -- the honest "no usage history, app is active"
// result. Mirrors
// frameworks/base/core/java/android/app/usage/UsageStatsManager.java.
package android.app.usage;

import android.content.Context;

public final class UsageStatsManager {

    public static final int INTERVAL_DAILY   = 0;
    public static final int INTERVAL_WEEKLY  = 1;
    public static final int INTERVAL_MONTHLY = 2;
    public static final int INTERVAL_YEARLY  = 3;
    public static final int INTERVAL_BEST    = 4;

    public static final int STANDBY_BUCKET_ACTIVE        = 10;
    public static final int STANDBY_BUCKET_WORKING_SET   = 20;
    public static final int STANDBY_BUCKET_FREQUENT      = 30;
    public static final int STANDBY_BUCKET_RARE          = 40;
    public static final int STANDBY_BUCKET_RESTRICTED    = 45;

    public UsageStatsManager() {}
    public UsageStatsManager(Context context) {}

    /** AOSP queryUsageStats(int, long, long): "@return A list of UsageStats"
     *  -- the ELEMENT TYPE is UsageStats, not Object, which matters for an app
     *  that iterates the result with a typed loop variable. android.app.usage
     *  .UsageStats exists in this tree now, so the signature says so. Never
     *  null: an empty list is what a device returns when the caller lacks Usage
     *  Access, and AppOpsManager.checkOp(OPSTR_GET_USAGE_STATS) here reports
     *  MODE_DEFAULT for exactly that reason. */
    public java.util.List<UsageStats> queryUsageStats(int intervalType, long beginTime,
            long endTime) {
        return new java.util.ArrayList<UsageStats>();
    }

    /** AOSP queryAndAggregateUsageStats(long, long): "@return A map keyed by
     *  package name" of merged UsageStats. Never null. */
    public java.util.Map<String, UsageStats> queryAndAggregateUsageStats(long beginTime,
            long endTime) {
        return new java.util.HashMap<String, UsageStats>();
    }

    // ================================================================
    // AOSP API-36 surface completion.
    // AOSP ref: frameworks/base/core/java/android/app/usage/
    // UsageStatsManager.java (android16-release). Mac.apk records no usage
    // history at all, so every query returns AOSP's empty result -- the same
    // answer a device gives an app that has not been granted Usage Access, and
    // a state apps already branch on (see the AppOpsManager note about
    // OPSTR_GET_USAGE_STATS reporting MODE_DEFAULT rather than MODE_ALLOWED,
    // which is what tells an app to send the user to Settings instead of
    // concluding the device has no history).
    // ================================================================

    /** AOSP queryEvents(long beginTime, long endTime): "@return A UsageEvents
     *  object", never null -- an empty cursor rather than null, so the caller's
     *  while(hasNextEvent()) loop simply does not execute. */
    public UsageEvents queryEvents(long beginTime, long endTime) {
        return new UsageEvents(new java.util.ArrayList<UsageEvents.Event>());
    }

    /** AOSP queryEventsForSelf(long, long) (API 28): "Like queryEvents, but
     *  only returns events for the calling package" and, crucially, requires NO
     *  Usage Access permission. Same empty result: nothing records this app's
     *  events either. */
    public UsageEvents queryEventsForSelf(long beginTime, long endTime) {
        return new UsageEvents(new java.util.ArrayList<UsageEvents.Event>());
    }

    /** AOSP queryEvents(UsageEventsQuery) (API 35): the filtered form. AOSP
     *  requires a non-null query. */
    public UsageEvents queryEvents(UsageEventsQuery query) {
        if (query == null) throw new NullPointerException("query cannot be null");
        return new UsageEvents(new java.util.ArrayList<UsageEvents.Event>());
    }

    /** AOSP queryEventStats(int intervalType, long beginTime, long endTime)
     *  (API 28): per-event-type aggregates. Never null. */
    public java.util.List<EventStats> queryEventStats(int intervalType, long beginTime,
            long endTime) {
        return new java.util.ArrayList<EventStats>();
    }

    /** AOSP queryConfigurations(int intervalType, long beginTime, long endTime):
     *  per-device-Configuration aggregates. Never null. */
    public java.util.List<ConfigurationStats> queryConfigurations(int intervalType,
            long beginTime, long endTime) {
        return new java.util.ArrayList<ConfigurationStats>();
    }
    public int getAppStandbyBucket() { return STANDBY_BUCKET_ACTIVE; }
    public boolean isAppInactive(String packageName) { return false; }

    /** AOSP UsageStatsManager.EXTRA_EVENT_CATEGORY / EXTRA_EVENT_ACTION
     *  (API 36): the keys under which a USER_INTERACTION event's category and
     *  action travel in UsageEvents.Event.getExtras(). */
    public static final String EXTRA_EVENT_CATEGORY =
            "android.app.usage.extra.EVENT_CATEGORY";
    public static final String EXTRA_EVENT_ACTION =
            "android.app.usage.extra.EVENT_ACTION";
}
