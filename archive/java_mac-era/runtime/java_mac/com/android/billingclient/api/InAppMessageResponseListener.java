package com.android.billingclient.api;

// com.android.billingclient.api.InAppMessageResponseListener -- delivered to
// BillingClient.showInAppMessages. On a de-Googled device we always report
// NO_ACTION_NEEDED (no Play in-app message exists to surface).
public interface InAppMessageResponseListener {
    void onInAppMessageResponse(InAppMessageResult inAppMessageResult);
}
