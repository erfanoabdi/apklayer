// com.google.android.gms.ads.LoadAdError
//
// GMS Ads reference: the error passed to AdListener.onAdFailedToLoad and
// the *AdLoadCallback.onAdFailedToLoad callbacks. Mac.apk always reports
// ERROR_CODE_NO_FILL with the SDK's mediation domain -- the honest result
// a device with no reachable ad backend returns.
package com.google.android.gms.ads;

public class LoadAdError extends AdError {
    // The mediation domain the real SDK uses for SDK-side errors.
    public static final String DOMAIN = "com.google.android.gms.ads";

    public LoadAdError(int code, String message, String domain) {
        super(code, message, domain);
    }

    public LoadAdError(int code, String message, String domain,
                       AdError cause, ResponseInfo responseInfo) {
        super(code, message, domain);
    }

    public ResponseInfo getResponseInfo() { return null; }

    // Convenience factory for our no-backend "no fill" outcome.
    public static LoadAdError noFill() {
        return new LoadAdError(AdError.ERROR_CODE_NO_FILL,
                "No ad config.", DOMAIN);
    }
}
