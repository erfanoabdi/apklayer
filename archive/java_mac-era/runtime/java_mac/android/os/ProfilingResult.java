package android.os;
/** The result of a requestProfiling call. API 35. Mac.apk runs no profiler, so
 *  no result is ever delivered; the type exists for the Consumer<ProfilingResult>
 *  callbacks apps register.
 *  Source: frameworks/base/core/java/android/os/ProfilingResult.java */
public final class ProfilingResult {
    public static final int ERROR_UNKNOWN = 0;
    ProfilingResult() { }
    public int getErrorCode() { return ERROR_UNKNOWN; }
    public String getErrorMessage() { return "profiling not supported"; }
    public String getResultFilePath() { return null; }
    public String getTag() { return null; }
}
