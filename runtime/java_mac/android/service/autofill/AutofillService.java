package android.service.autofill;

import android.content.Intent;
import android.os.CancellationSignal;
import android.os.IBinder;

/**
 * Base for an autofill service (a password manager).
 *
 * PACKAGE NOTE -- applies to every class in android.service.autofill.
 * An AutofillService is bound by the system so it can fill fields in OTHER
 * apps. Mac.apk runs ONE app and binds no autofill service, so onFillRequest /
 * onSaveRequest never fire and no FillResponse is ever consumed. This is the
 * second archetype (with NotificationListenerService) of a service that cannot
 * function on a single-app host.
 *
 * What DOES have to work, and does: the entire request/response object graph is
 * ordinary immutable value objects with builders, and a password manager
 * exercises them constantly OUTSIDE the platform callback -- constructing a
 * Dataset, validating a card number, running its own tests, serialising a
 * FillResponse. Every builder in this package validates its arguments the way
 * AOSP does and every getter returns what was set, so that half is faithful
 * rather than stubbed. Nothing here fabricates a bound service, and nothing
 * here reads, writes, or transports a credential.
 *
 * ⚠ Two members elsewhere in the package are MISSING because their types do not
 * exist in this tree: FillContext.getStructure() (android.app.assist.
 * AssistStructure) and FillRequest.getInlineSuggestionsRequest()
 * (android.view.inputmethod.InlineSuggestionsRequest), plus
 * InlinePresentation's three InlinePresentationSpec members. They are reported
 * as residual gaps rather than faked with a wrong type.
 *
 * Constants transcribed verbatim from /tmp/aosp_api/android16.txt:
 *   SERVICE_INTERFACE  = "android.service.autofill.AutofillService"
 *   SERVICE_META_DATA  = "android.autofill"
 *   EXTRA_FILL_RESPONSE = "android.service.autofill.extra.FILL_RESPONSE"
 *
 * Source: frameworks/base/core/java/android/service/autofill/AutofillService.java
 */
public abstract class AutofillService extends android.app.Service {

    public static final String SERVICE_INTERFACE = "android.service.autofill.AutofillService";
    public static final String SERVICE_META_DATA = "android.autofill";
    public static final String EXTRA_FILL_RESPONSE =
            "android.service.autofill.extra.FILL_RESPONSE";

    public AutofillService() { }

    /** AOSP declares this final and returns the autofill binder. */
    @Override public final IBinder onBind(Intent intent) { return null; }

    /** Empty in AOSP too; the service overrides them. */
    public void onConnected() { }

    public void onDisconnected() { }

    public abstract void onFillRequest(FillRequest request,
            CancellationSignal cancellationSignal, FillCallback callback);

    public abstract void onSaveRequest(SaveRequest request, SaveCallback callback);

    /**
     * API 31. AOSP's default answers ERROR_UNSUPPORTED, so a service that does
     * not override it reports "I cannot tell you" rather than leaving Settings
     * waiting forever. Reproduced exactly -- a silent default would hang the
     * caller.
     */
    public void onSavedDatasetsInfoRequest(SavedDatasetsInfoCallback callback) {
        if (callback != null) {
            callback.onError(SavedDatasetsInfoCallback.ERROR_UNSUPPORTED);
        }
    }

    /** API 36. Empty in AOSP; the service overrides it. */
    public void onSessionDestroyed(FillEventHistory history) { }

    /**
     * @deprecated at API 36 in favour of onSessionDestroyed(FillEventHistory).
     * AOSP returns null when there is nothing to report; there is never
     * anything to report here, and null is a value every caller null-checks
     * because AOSP documents the method @Nullable.
     */
    @Deprecated
    public final FillEventHistory getFillEventHistory() { return null; }
}
