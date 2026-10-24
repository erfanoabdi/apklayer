package com.android.billingclient.api;

// com.android.billingclient.api.ConsumeResponseListener -- callback for
// BillingClient.consumeAsync.
public interface ConsumeResponseListener {
    void onConsumeResponse(BillingResult billingResult, String purchaseToken);
}
