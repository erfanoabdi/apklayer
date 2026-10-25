package com.google.android.gms.safetynet;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.List;

// com.google.android.gms.safetynet.SafetyNetClient -- the (legacy, deprecated)
// SafetyNet attestation/Safe-Browsing/Verify-Apps client. On a de-Googled device
// there is no SafetyNet backend, and per PROJECT POLICY we NEVER forge an
// attestation/reCAPTCHA token. Every attest/verify path therefore FAILS its Task
// with an ApiException(Status(SERVICE_MISSING)); enable/list paths resolve with
// honest empty/negative results. Apps then take their "attestation unavailable"
// branch instead of crashing on a missing class. Signatures mirror the published
// SafetyNetClient (which is itself a GoogleApi<...> subclass; we expose only the
// public method surface apps call).
public class SafetyNetClient {

    SafetyNetClient() {}

    private static Status serviceMissing() {
        return new Status(CommonStatusCodes.SERVICE_MISSING, "SafetyNet unavailable");
    }

    private static <T> Task<T> fail() {
        // Honest negative: there is no SafetyNet service to talk to.
        return Tasks.forException(new ApiException(serviceMissing()));
    }

    // attest(nonce, apiKey) -> NEVER forged; fails cleanly.
    public Task<SafetyNetApi.AttestationResponse> attest(byte[] nonce, String apiKey) {
        return fail();
    }

    // verifyWithRecaptcha(siteKey) -> NEVER forged; fails cleanly.
    public Task<SafetyNetApi.RecaptchaTokenResponse> verifyWithRecaptcha(String siteKey) {
        return fail();
    }

    // Verify Apps: enabling is a no-op that reports "not enabled" honestly.
    public Task<SafetyNetApi.VerifyAppsUserResponse> isVerifyAppsEnabled() {
        return Tasks.forResult(new SafetyNetApi.VerifyAppsUserResponse(false));
    }

    public Task<SafetyNetApi.VerifyAppsUserResponse> enableVerifyApps() {
        // We do not run Google's app-verification; report "not enabled".
        return Tasks.forResult(new SafetyNetApi.VerifyAppsUserResponse(false));
    }

    public Task<SafetyNetApi.HarmfulAppsResponse> listHarmfulApps() {
        // No scanner; honestly report no harmful apps.
        return Tasks.forResult(new SafetyNetApi.HarmfulAppsResponse(
                new Status(CommonStatusCodes.SUCCESS)));
    }

    // Safe Browsing.
    public Task<SafetyNetApi.SafeBrowsingResponse> lookupUri(String uri, String apiKey,
                                                             int... threatTypes) {
        return fail();
    }

    public Task<Boolean> initSafeBrowsing(List<Integer> threatTypes, String apiKey) {
        // No Safe Browsing backend to initialize.
        return Tasks.forResult(Boolean.FALSE);
    }

    public Task<Void> shutdownSafeBrowsing() {
        return Tasks.forResult(null);
    }
}
