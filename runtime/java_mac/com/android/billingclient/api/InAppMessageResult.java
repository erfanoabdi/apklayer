package com.android.billingclient.api;

// com.android.billingclient.api.InAppMessageResult -- delivered to
// InAppMessageResponseListener after BillingClient.showInAppMessages. On a
// de-Googled device there is never a Play in-app message to surface, so we
// always report NO_ACTION_NEEDED with a null purchase token.
public final class InAppMessageResult {

    // com.android.billingclient.api.InAppMessageResult.InAppMessageResponseCode
    public @interface InAppMessageResponseCode {
        int NO_ACTION_NEEDED = 0;
        int SUBSCRIPTION_STATUS_UPDATED = 1;
    }

    private final int responseCode;
    private final String purchaseToken;

    InAppMessageResult(int responseCode, String purchaseToken) {
        this.responseCode = responseCode;
        this.purchaseToken = purchaseToken;
    }

    public int getResponseCode() { return responseCode; }
    public String getPurchaseToken() { return purchaseToken; }
}
