package android.app.usage;
/** A queryable set of network-usage buckets. Mac.apk collects no network stats,
 *  so there are no buckets. API 23. */
public final class NetworkStats implements AutoCloseable {
    NetworkStats() {}
    public boolean hasNextBucket() { return false; }
    public boolean getNextBucket(Bucket bucketOut) { return false; }
    @Override public void close() {}
    public static class Bucket {
        public static final int STATE_ALL = -1, STATE_DEFAULT = 1, STATE_FOREGROUND = 2;
        public static final int UID_ALL = -1, UID_REMOVED = -4, UID_TETHERING = -5;
        public static final int TAG_NONE = 0;
        public static final int METERED_ALL = -1, METERED_NO = 1, METERED_YES = 2;
        public static final int ROAMING_ALL = -1, ROAMING_NO = 1, ROAMING_YES = 2;
        public static final int DEFAULT_NETWORK_ALL = -1, DEFAULT_NETWORK_NO = 1, DEFAULT_NETWORK_YES = 2;
        public Bucket() {}
        public int getUid() { return UID_ALL; }
        public int getTag() { return TAG_NONE; }
        public int getState() { return STATE_ALL; }
        public int getMetered() { return METERED_ALL; }
        public int getRoaming() { return ROAMING_ALL; }
        public int getDefaultNetworkStatus() { return DEFAULT_NETWORK_ALL; }
        public long getStartTimeStamp() { return 0L; }
        public long getEndTimeStamp() { return 0L; }
        public long getRxBytes() { return 0L; }
        public long getTxBytes() { return 0L; }
        public long getRxPackets() { return 0L; }
        public long getTxPackets() { return 0L; }
    }
}
