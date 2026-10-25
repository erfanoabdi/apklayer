package android.adservices.adselection;

import android.content.Context;
import android.os.OutcomeReceiver;
import java.util.concurrent.Executor;

// android.adservices.adselection -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// manager reports the service as unavailable: async calls invoke
// OutcomeReceiver.onError (the two status/list reads return the
// DISABLED/empty value), so a compliant ad SDK disables that path. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class AdSelectionManager {
    private AdSelectionManager() { }
    public static AdSelectionManager get(Context context) { return new AdSelectionManager(); }

    private static void fail(Executor executor,
            OutcomeReceiver<?, Exception> receiver) {
        Runnable r = () -> receiver.onError(
                new IllegalStateException("AdServices is not available on this device"));
        if (executor != null) { try { executor.execute(r); return; } catch (Throwable ignored) { } }
        r.run();
    }
    public void selectAds(android.adservices.adselection.AdSelectionConfig config,
            Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
    public void selectAds(android.adservices.adselection.AdSelectionFromOutcomesConfig config,
            Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
    public void reportImpression(android.adservices.adselection.ReportImpressionRequest p0, Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
    public void reportEvent(android.adservices.adselection.ReportEventRequest p0, Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
    public void updateAdCounterHistogram(android.adservices.adselection.UpdateAdCounterHistogramRequest p0, Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
    public void getAdSelectionData(android.adservices.adselection.GetAdSelectionDataRequest p0, Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
    public void persistAdSelectionResult(android.adservices.adselection.PersistAdSelectionResultRequest p0, Executor executor, OutcomeReceiver<?, Exception> receiver) { fail(executor, receiver); }
}
