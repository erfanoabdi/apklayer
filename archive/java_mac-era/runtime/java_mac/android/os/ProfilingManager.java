package android.os;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
/** On-device profiling requests (system traces / heap dumps). API 35. Mac.apk
 *  has no profiler; requests are accepted and simply never produce a result, and
 *  registration is a no-op. This matches a device where profiling is disabled.
 *  Source: frameworks/base/core/java/android/os/ProfilingManager.java */
public final class ProfilingManager {
    public ProfilingManager() { }
    public void requestProfiling(int profilingType, Bundle parameters, String tag,
            CancellationSignal cancellationSignal, Executor executor,
            Consumer<ProfilingResult> listener) { }
    public void registerForAllProfilingResults(Executor executor,
            Consumer<ProfilingResult> listener) { }
    public void unregisterForAllProfilingResults(Consumer<ProfilingResult> listener) { }
}
