package android.adservices.topics;

import android.adservices.topics.GetTopicsRequest;
import android.adservices.topics.GetTopicsResponse;
import android.content.Context;
import android.os.OutcomeReceiver;
import java.util.concurrent.Executor;

// android.adservices.topics -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// manager reports the service as unavailable: async calls invoke
// OutcomeReceiver.onError (the two status/list reads return the
// DISABLED/empty value), so a compliant ad SDK disables that path. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class TopicsManager {
    private TopicsManager() { }
    public static TopicsManager get(Context context) { return new TopicsManager(); }

    private static void fail(Executor executor,
            OutcomeReceiver<?, Exception> receiver) {
        Runnable r = () -> receiver.onError(
                new IllegalStateException("AdServices is not available on this device"));
        if (executor != null) { try { executor.execute(r); return; } catch (Throwable ignored) { } }
        r.run();
    }
    public void getTopics(android.adservices.topics.GetTopicsRequest request,
            Executor executor, OutcomeReceiver<android.adservices.topics.GetTopicsResponse, Exception> receiver) {
        // No topics on a device without the AdServices module: deliver an empty
        // response (the faithful "observed no topics" answer), not an error.
        android.adservices.topics.GetTopicsResponse empty =
                new android.adservices.topics.GetTopicsResponse.Builder().build();
        Runnable r = () -> receiver.onResult(empty);
        if (executor != null) { try { executor.execute(r); return; } catch (Throwable ignored) { } }
        r.run();
    }
}
