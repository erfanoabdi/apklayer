package android.os;

// apklayer patch: minimal ThreadLocalWorkSource (upstream tracks battery-stats UIDs).
public class ThreadLocalWorkSource {
    public static long setUid(long uid) { return 0; }
    public static int getUid() { return 0; }
    public static void restore(long state) {}
    public static void set(long uid) {}
}
