package android.adservices.customaudience;

import android.content.Context;
import android.os.OutcomeReceiver;
import java.util.concurrent.Executor;

// android.adservices.customaudience -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// manager reports the service as unavailable: async calls invoke
// OutcomeReceiver.onError (the two status/list reads return the
// DISABLED/empty value), so a compliant ad SDK disables that path. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class CustomAudienceManager {
    private CustomAudienceManager() { }
    public static CustomAudienceManager get(Context context) { return new CustomAudienceManager(); }

    private static void fail(Executor executor,
            OutcomeReceiver<?, Exception> receiver) {
        Runnable r = () -> receiver.onError(
                new IllegalStateException("AdServices is not available on this device"));
        if (executor != null) { try { executor.execute(r); return; } catch (Throwable ignored) { } }
        r.run();
    }
    public void fetchAndJoinCustomAudience(android.adservices.customaudience.FetchAndJoinCustomAudienceRequest p0, Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
    public void joinCustomAudience(android.adservices.customaudience.JoinCustomAudienceRequest p0, Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
    public void leaveCustomAudience(android.adservices.customaudience.LeaveCustomAudienceRequest p0, Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
}
