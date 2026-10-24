package android.webkit;

import java.io.OutputStream;
import java.util.concurrent.Executor;

// android.webkit.TracingController (AOSP API-28).
// Entry point to start/stop WebView devtools tracing. Abstract in AOSP. No
// engine yet, so start/stop are no-ops and stop reports "nothing was tracing".
// Ref: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/TracingController.java
public abstract class TracingController {

    public static TracingController getInstance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        static final TracingController INSTANCE = new EmptyController();
    }

    public abstract boolean isTracing();

    public abstract void start(TracingConfig tracingConfig);

    public abstract boolean stop(OutputStream outputStream, Executor executor);

    private static final class EmptyController extends TracingController {
        @Override public boolean isTracing() { return false; }
        @Override public void start(TracingConfig tracingConfig) {}
        @Override public boolean stop(OutputStream outputStream, Executor executor) {
            // AOSP returns false when no tracing session is active.
            return false;
        }
    }
}
