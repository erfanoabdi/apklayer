package android.os;

// apklayer patch: aconfig-generated Flags stand-in; all monitored features off.
public final class Flags {
    public static final String FLAG_MAINLINE_VCN_PLATFORM_API = "android.os.os.mainline_vcn_platform_api";
    public static final String FLAG_ASYNC_TRACE_FOR_TRACK = "android.os.os.async_trace_for_track";
    public static boolean messageQueueMonitoringEnabled() { return false; }
    public static boolean perfettoSdkTracingV3() { return false; }
}
