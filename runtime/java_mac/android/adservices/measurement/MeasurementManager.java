package android.adservices.measurement;

import android.content.Context;
import android.os.OutcomeReceiver;
import java.util.concurrent.Executor;

// android.adservices.measurement -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// manager reports the service as unavailable: async calls invoke
// OutcomeReceiver.onError (the two status/list reads return the
// DISABLED/empty value), so a compliant ad SDK disables that path. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class MeasurementManager {
    private MeasurementManager() { }
    public static MeasurementManager get(Context context) { return new MeasurementManager(); }

    private static void fail(Executor executor,
            OutcomeReceiver<?, Exception> receiver) {
        Runnable r = () -> receiver.onError(
                new IllegalStateException("AdServices is not available on this device"));
        if (executor != null) { try { executor.execute(r); return; } catch (Throwable ignored) { } }
        r.run();
    }
    public void deleteRegistrations(android.adservices.measurement.DeletionRequest p0, Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
    public void getMeasurementApiStatus(Executor executor,
            OutcomeReceiver<Integer, Exception> receiver) {
        // MEASUREMENT_API_STATE_DISABLED == 0: the API exists but is off.
        Runnable r = () -> receiver.onResult(0);
        if (executor != null) { try { executor.execute(r); return; } catch (Throwable ignored) { } }
        r.run();
    }
    public void registerSource(android.net.Uri p0, android.view.InputEvent p1, Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
    public void registerTrigger(android.net.Uri p0, Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
    public void registerWebSource(android.adservices.measurement.WebSourceRegistrationRequest p0, Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
    public void registerWebTrigger(android.adservices.measurement.WebTriggerRegistrationRequest p0, Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
}
