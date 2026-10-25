package android.util;
/** Binary system event log. Mac.apk swallows events (no eventlog device). */
public class EventLog {
    private EventLog() {}
    public static int writeEvent(int tag, int value) { return 0; }
    public static int writeEvent(int tag, long value) { return 0; }
    public static int writeEvent(int tag, float value) { return 0; }
    public static int writeEvent(int tag, String str) { return 0; }
    public static int writeEvent(int tag, Object... list) { return 0; }
    public static void readEvents(int[] tags, java.util.Collection<Event> output) {}

    /**
     * Look up a tag by name. AOSP reads /system/etc/event-log-tags; that file
     * has not been app-readable since API 16, and AOSP's own implementation
     * returns -1 for every name once the read fails. -1 is therefore the
     * ACCURATE answer here, not a placeholder: it is what a modern device
     * returns for a name it cannot resolve.
     *
     * @return the tag code, or -1 if the tag is unknown.
     */
    public static int getTagCode(String name) { return -1; }

    /**
     * The inverse of {@link #getTagCode}. Null for an unknown tag, which is
     * what AOSP answers when the tag file is unavailable.
     */
    public static String getTagName(int tag) { return null; }
    public static final class Event {
        public int getTag() { return 0; }
        public int getProcessId() { return 0; }
        public int getThreadId() { return 0; }
        public long getTimeNanos() { return 0L; }
        public Object getData() { return null; }
    }
}
