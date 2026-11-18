package apklayer.host;

import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.INetworkStatsService;
import android.net.INetworkStatsSession;
import android.net.NetworkStats;
import android.net.NetworkStatsHistory;
import android.net.NetworkTemplate;
import android.os.SystemClock;

/** Network usage is unavailable on hosts without Android's network accounting. */
final class HostNetworkStats {
    private HostNetworkStats() {}

    static NetworkStatsManager manager(Context context) {
        return new NetworkStatsManager(context, SERVICE);
    }

    private static final INetworkStatsSession SESSION = new INetworkStatsSession.Default() {
        @Override public NetworkStats getDeviceSummaryForNetwork(NetworkTemplate template, long start, long end) { return empty(); }
        @Override public NetworkStats getSummaryForNetwork(NetworkTemplate template, long start, long end) { return empty(); }
        @Override public NetworkStats getSummaryForAllUid(NetworkTemplate template, long start, long end, boolean tags) { return empty(); }
        @Override public NetworkStats getTaggedSummaryForAllUid(NetworkTemplate template, long start, long end) { return empty(); }
        @Override public NetworkStatsHistory getHistoryForNetwork(NetworkTemplate template, int fields) { return history(); }
        @Override public NetworkStatsHistory getHistoryIntervalForNetwork(NetworkTemplate template, int fields, long start, long end) { return history(); }
        @Override public NetworkStatsHistory getHistoryForUid(NetworkTemplate template, int uid, int set, int tag, int fields) { return history(); }
        @Override public NetworkStatsHistory getHistoryIntervalForUid(NetworkTemplate template, int uid, int set, int tag, int fields, long start, long end) { return history(); }
        @Override public int[] getRelevantUids() { return new int[0]; }
    };

    private static final INetworkStatsService SERVICE = new INetworkStatsService.Default() {
        @Override public INetworkStatsSession openSession() { return SESSION; }
        @Override public INetworkStatsSession openSessionForUsageStats(int flags, String packageName) { return SESSION; }
        @Override public NetworkStats getUidStatsForTransport(int transport) { return empty(); }
    };

    private static NetworkStats empty() { return new NetworkStats(SystemClock.elapsedRealtime(), 0); }
    private static NetworkStatsHistory history() { return new NetworkStatsHistory(60 * 60 * 1000L, 0); }
}
