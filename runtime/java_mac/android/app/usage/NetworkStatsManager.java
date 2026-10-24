package android.app.usage;
/** Queries historical network usage (API 23). Mac.apk keeps none, so queries
 *  return an empty NetworkStats. */
public class NetworkStatsManager {
    public NetworkStatsManager() {}
    public NetworkStats querySummary(int networkType, String subscriberId, long startTime, long endTime) { return new NetworkStats(); }
    public NetworkStats queryDetails(int networkType, String subscriberId, long startTime, long endTime) { return new NetworkStats(); }
    public NetworkStats.Bucket querySummaryForDevice(int networkType, String subscriberId, long startTime, long endTime) { return new NetworkStats.Bucket(); }
    public NetworkStats.Bucket querySummaryForUser(int networkType, String subscriberId, long startTime, long endTime) { return new NetworkStats.Bucket(); }
}
