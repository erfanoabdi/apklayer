package android.credentials;

import android.content.Context;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.util.Log;
import java.util.concurrent.Executor;

// android.credentials.CredentialManager -- the Credential Manager entry point
// (API 34: passkeys / password / federated sign-in aggregation).
//
// Mac.apk has ONE credential provider: Google sign-in (GetGoogleIdOption /
// GetSignInWithGoogleOption), answered from the central Google account by
// com.macapk.gms.GoogleSignInBroker, as GMS's provider answers on a phone
// (MACAPK_GMS_SIGNIN=0 removes it). For everything else -- passwords,
// passkeys, a request with no Google option -- there is no provider, and the
// AOSP-faithful answer is "no credential available": get/prepare/create
// deliver their typed *Exception via OutcomeReceiver.onError, and clear succeeds
// trivially (there is no provider state to clear). Apps handle onError(
// GetCredentialException TYPE_NO_CREDENTIAL) by falling back to their own login
// UI -- exactly what happens on a real device with no providers enabled.
// See [[gms-absent-device-contract]].
//
// Source: frameworks/base/core/java/android/credentials/CredentialManager.java
public final class CredentialManager {

    private static final String TAG = "CredentialManager";

    public CredentialManager() { }

    public void getCredential(Context context, GetCredentialRequest request,
            CancellationSignal cancellationSignal, Executor executor,
            OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback) {
        // AOSP CredentialManager.getCredential: an already-cancelled signal does
        // nothing (no request, no callback).
        if (cancellationSignal != null && cancellationSignal.isCanceled()) {
            Log.w(TAG, "getCredential already canceled");
            return;
        }
        // The one provider Mac.apk has: Google sign-in for a GetGoogleIdOption /
        // GetSignInWithGoogleOption, answered from the central Google account the
        // way GMS's provider answers on a phone (com.macapk.gms.GoogleSignInBroker).
        // Every other option (passwords, passkeys) still has no provider.
        // MACAPK_GMS_SIGNIN=0 -> false -> the pre-provider no-credential answer.
        // The signal reaches the provider so cancelling it closes the chooser (AOSP:
        // cancellationSignal.setRemote(cancelRemote) -> the service ends the session).
        if (com.macapk.gms.GoogleSignInBroker.startCredentialRequest(context, request,
                cancellationSignal, executor, callback)) {
            return;
        }
        failGet(executor, callback);
    }

    public void getCredential(Context context,
            PrepareGetCredentialResponse.PendingGetCredentialHandle pendingGetCredentialHandle,
            CancellationSignal cancellationSignal, Executor executor,
            OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback) {
        // AOSP: an already-cancelled signal does nothing (the same check, ahead of
        // pendingGetCredentialHandle.show(...)).
        if (cancellationSignal != null && cancellationSignal.isCanceled()) {
            Log.w(TAG, "getCredential already canceled");
            return;
        }
        // AOSP: pendingGetCredentialHandle.show(...) -- the UI prepared for the
        // request. Ours carries the request into the same provider hand-off.
        GetCredentialRequest prepared = pendingGetCredentialHandle != null
                ? pendingGetCredentialHandle.mRequest : null;
        if (prepared != null && com.macapk.gms.GoogleSignInBroker.startCredentialRequest(
                context, prepared, cancellationSignal, executor, callback)) {
            return;
        }
        failGet(executor, callback);
    }

    public void prepareGetCredential(GetCredentialRequest request,
            CancellationSignal cancellationSignal, Executor executor,
            OutcomeReceiver<PrepareGetCredentialResponse, GetCredentialException> callback) {
        // prepare itself succeeds; it reports (via the response flags) that a
        // later getCredential would surface nothing. That matches a device with
        // providers present-but-empty and avoids a spurious prepare-time error.
        PrepareGetCredentialResponse resp = new PrepareGetCredentialResponse(request,
                com.macapk.gms.GoogleSignInBroker.wouldOffer(request));
        post(executor, () -> callback.onResult(resp));
    }

    public void createCredential(Context context, CreateCredentialRequest request,
            CancellationSignal cancellationSignal, Executor executor,
            OutcomeReceiver<CreateCredentialResponse, CreateCredentialException> callback) {
        post(executor, () -> callback.onError(new CreateCredentialException(
                CreateCredentialException.TYPE_NO_CREATE_OPTIONS,
                "No credential provider available on this device")));
    }

    public void clearCredentialState(ClearCredentialStateRequest request,
            CancellationSignal cancellationSignal, Executor executor,
            OutcomeReceiver<Void, ClearCredentialStateException> callback) {
        // No provider keeps credential state, so there is nothing to wipe -- except
        // that GMS's Google provider turns automatic sign-in OFF for the app, so a
        // logout is not undone by the next One Tap auto-select
        // (MACAPK_GMS_SIGNIN=0: untouched).
        com.macapk.gms.GoogleSignInBroker.onClearCredentialState();
        post(executor, () -> callback.onResult(null));
    }

    // --- provider registry ------------------------------------------------
    // AOSP forwards these three to the credential manager system service.
    // Mac.apk has no such service and no credential provider is ever enabled,
    // so:
    //   isEnabledCredentialProviderService  -> false. This is the honest
    //     answer, not a stub: the set of enabled providers is genuinely
    //     empty, which is also what a de-Googled device reports.
    //   register/unregisterCredentialDescription -> recorded in a
    //     process-local set. AOSP's registry exists only so the system picker
    //     can route a get-request to a provider without waking it; we never
    //     surface a credential (see the header), so a registration is inert
    //     by construction. Accepting it silently is what the device does on
    //     the success path; throwing here would be a failure the API does not
    //     document. Nothing reads the set back -- there is no public getter --
    //     it exists so unregister has something coherent to remove.
    private static final java.util.Set<CredentialDescription> sDescriptions =
            java.util.Collections.synchronizedSet(new java.util.HashSet<CredentialDescription>());

    public boolean isEnabledCredentialProviderService(android.content.ComponentName component) {
        return false;
    }

    public void registerCredentialDescription(RegisterCredentialDescriptionRequest request) {
        if (request != null) sDescriptions.addAll(request.getCredentialDescriptions());
    }

    public void unregisterCredentialDescription(UnregisterCredentialDescriptionRequest request) {
        if (request != null) sDescriptions.removeAll(request.getCredentialDescriptions());
    }

    private static void failGet(Executor executor,
            OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback) {
        post(executor, () -> callback.onError(new GetCredentialException(
                GetCredentialException.TYPE_NO_CREDENTIAL,
                "No credential available on this device")));
    }

    private static void post(Executor executor, Runnable r) {
        if (executor != null) { try { executor.execute(r); return; } catch (Throwable ignored) { } }
        r.run();
    }
}
