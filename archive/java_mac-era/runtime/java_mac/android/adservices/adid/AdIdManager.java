package android.adservices.adid;

import android.content.Context;
import android.os.OutcomeReceiver;
import java.util.concurrent.Executor;

// android.adservices.adid -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// manager reports the service as unavailable: async calls invoke
// OutcomeReceiver.onError (the two status/list reads return the
// DISABLED/empty value), so a compliant ad SDK disables that path. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class AdIdManager {
    private AdIdManager() { }
    public static AdIdManager get(Context context) { return new AdIdManager(); }

    private static void fail(Executor executor,
            OutcomeReceiver<?, Exception> receiver) {
        Runnable r = () -> receiver.onError(
                new IllegalStateException("AdServices is not available on this device"));
        if (executor != null) { try { executor.execute(r); return; } catch (Throwable ignored) { } }
        r.run();
    }
    public void getAdId(Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
}
