package android.os;

// apklayer patch: minimal StrictMode; policy calls become no-ops.
public final class StrictMode {
    public static void noteSlowCall(String name) {}
    public static void noteDiskRead() {}
    public static void noteDiskWrite() {}
    public static void noteCustomSlowCalls(int mask) {}
}
