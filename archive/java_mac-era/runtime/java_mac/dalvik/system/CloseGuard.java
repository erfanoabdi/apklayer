package dalvik.system;
/** Resource-leak sentinel. AOSP uses it to warn when a closeable is GC'd still
 *  open; Mac.apk has no such reporter, so it is inert but the API resolves. */
public final class CloseGuard {
    private static volatile boolean ENABLED = false;
    private CloseGuard() {}
    public static CloseGuard get() { return new CloseGuard(); }
    public static void setEnabled(boolean enabled) { ENABLED = enabled; }
    public static boolean isEnabled() { return ENABLED; }
    public void open(String closer) {}
    public void close() {}
    public void warnIfOpen() {}
    public interface Reporter { void report(String message, Throwable allocationSite); }
    public static void setReporter(Reporter reporter) {}
}
