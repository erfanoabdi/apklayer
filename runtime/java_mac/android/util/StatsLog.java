package android.util;
/** statsd write entry point. Mac.apk has no statsd, so writes are dropped. */
public final class StatsLog {
    private StatsLog() {}
    public static boolean logStart(int id) { return false; }
    public static boolean logStop(int id) { return false; }
    public static boolean logEvent(int id) { return false; }
    public static void write(StatsEvent statsEvent) {}
    public static void writeRaw(byte[] buffer, int size) {}
}
