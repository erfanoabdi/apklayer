package dalvik.system;
/** Detects disallowed blocking calls (disk/network) on a thread. Mac.apk uses a
 *  permissive policy (nothing is penalized). */
public final class BlockGuard {
    public interface Policy {
        void onWriteToDisk();
        void onReadFromDisk();
        void onNetwork();
        int getPolicyMask();
    }
    public static final Policy LAX_POLICY = new Policy() {
        @Override public void onWriteToDisk() {}
        @Override public void onReadFromDisk() {}
        @Override public void onNetwork() {}
        @Override public int getPolicyMask() { return 0; }
    };
    private static final ThreadLocal<Policy> threadPolicy = new ThreadLocal<Policy>() {
        @Override protected Policy initialValue() { return LAX_POLICY; }
    };
    private BlockGuard() {}
    public static Policy getThreadPolicy() { return threadPolicy.get(); }
    public static void setThreadPolicy(Policy policy) { threadPolicy.set(policy != null ? policy : LAX_POLICY); }
}
