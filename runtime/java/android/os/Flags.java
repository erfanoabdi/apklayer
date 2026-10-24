package android.os;

// apklayer patch: aconfig-generated Flags stand-in; all monitored features off.
public final class Flags {
    public static boolean messageQueueMonitoringEnabled() { return false; }
    public static boolean perfettoSdkTracingV3() { return false; }
}
