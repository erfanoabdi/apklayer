package android.os;

// apklayer patch: minimal Binder facade. The real userspace-binder work comes later;
// the seed only needs identity token no-ops.
public class Binder {
    public static final long clearCallingIdentity() { return 0; }
    public static final void restoreCallingIdentity(long token) {}
    public static final void flushPendingCommands() {}
    public static final void flushPendingCommands(boolean async) {}
    public static final int getCallingPid() { return Process.myPid(); }
    public static final int getCallingUid() { return Process.myUid(); }
}
