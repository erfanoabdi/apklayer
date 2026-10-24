// com.google.android.gms.ads.AdError
//
// Mirrors the modern Google Mobile Ads SDK public API
// (developers.google.com/android/reference/com/google/android/gms/ads/AdError).
// Mac.apk ships AdMob as a no-op "no ad available" surface: the app's
// ad SDK <clinit> never runs (this shadow is loaded parent-first), and
// every load fails with the honest NO_FILL result a real device returns
// when no ad backend is reachable. No ad is shown; no impression or
// click is ever fabricated -- same policy as the legacy com.google.ads.*
// shims.
package com.google.android.gms.ads;

public class AdError {
    // Error-code constants from the GMS Ads reference. We only ever use
    // ERROR_CODE_NO_FILL for our honest "no ad backend" outcome.
    public static final int ERROR_CODE_INTERNAL_ERROR = 0;
    public static final int ERROR_CODE_INVALID_REQUEST = 1;
    public static final int ERROR_CODE_NETWORK_ERROR = 2;
    public static final int ERROR_CODE_NO_FILL = 3;

    private final int code;
    private final String message;
    private final String domain;

    public AdError(int code, String message, String domain) {
        this.code = code;
        this.message = message == null ? "" : message;
        this.domain = domain == null ? "" : domain;
    }

    public int getCode() { return code; }

    public String getMessage() { return message; }

    public String getDomain() { return domain; }

    public AdError getCause() { return null; }

    @Override public String toString() {
        return "{Code=" + code + ", Message=" + message + ", Domain=" + domain + "}";
    }
}
