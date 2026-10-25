package com.google.android.gms.safetynet;

import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.Status;

import java.util.Collections;
import java.util.List;

// com.google.android.gms.safetynet.SafetyNetApi -- the namespace type holding the
// SafetyNet response wrapper classes (the API itself is invoked via
// SafetyNetClient). On real GMS the responses extend Result and carry the signed
// attestation/recaptcha token. Per project policy we NEVER forge a token, so our
// SafetyNetClient fails its Tasks and these response types are never produced;
// they exist only so app code that references them (field/catch types) compiles.
// Member signatures mirror the published SafetyNetApi nested types.
public final class SafetyNetApi {
    private SafetyNetApi() {}

    // SafetyNetApi.AttestationResponse -- carries the JWS attestation statement.
    public static class AttestationResponse implements Result {
        private final Status status;
        AttestationResponse(Status status) { this.status = status; }
        @Override public Status getStatus() { return status; }
        // Never produced by us (we do not forge). Returns null defensively.
        public String getJwsResult() { return null; }
    }

    // SafetyNetApi.RecaptchaTokenResponse -- carries the reCAPTCHA user-response
    // token.
    public static class RecaptchaTokenResponse implements Result {
        private final Status status;
        RecaptchaTokenResponse(Status status) { this.status = status; }
        @Override public Status getStatus() { return status; }
        // Never produced by us. Returns null defensively.
        public String getTokenResult() { return null; }
    }

    // SafetyNetApi.SafeBrowsingResponse -- carries detected URL threats. We have
    // no Safe Browsing backend, so the metadata is empty.
    public static class SafeBrowsingResponse implements Result {
        private final Status status;
        SafeBrowsingResponse(Status status) { this.status = status; }
        @Override public Status getStatus() { return status; }
        public List<HarmfulAppsData> getDetectedThreats() {
            return Collections.emptyList();
        }
        public long getLastTimeUpdateRequestMs() { return 0L; }
        public byte[] getMetadata() { return new byte[0]; }
    }

    // SafetyNetApi.HarmfulAppsResponse -- result of listHarmfulApps. Empty: no
    // backend, and we report no harmful apps (honest for a controlled host).
    public static class HarmfulAppsResponse implements Result {
        private final Status status;
        HarmfulAppsResponse(Status status) { this.status = status; }
        @Override public Status getStatus() { return status; }
        public List<HarmfulAppsData> getHarmfulAppsList() {
            return Collections.emptyList();
        }
        public int getHoursSinceLastScanWithHarmfulApp() { return 0; }
        public long getLastScanTimeMs() { return 0L; }
    }

    // SafetyNetApi.VerifyAppsUserResponse -- result of isVerifyAppsEnabled /
    // enableVerifyApps. We do not run Google's Verify Apps, so isVerifyAppsEnabled
    // is always false (honest for a controlled host).
    public static class VerifyAppsUserResponse implements Result {
        private final boolean enabled;
        VerifyAppsUserResponse(boolean enabled) { this.enabled = enabled; }
        @Override public Status getStatus() { return Status.SUCCESS; }
        public boolean isVerifyAppsEnabled() { return enabled; }
    }

    // SafetyNetApi.HarmfulAppsData -- one entry in a harmful-apps list. Never
    // produced by us; the type exists so consumers compile.
    public static class HarmfulAppsData {
        public String apkPackageName;
        public byte[] apkSha256;
        public int apkCategory;
        HarmfulAppsData() {}
    }
}
