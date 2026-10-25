package com.android.billingclient.api;

// com.android.billingclient.api.AcknowledgePurchaseResponseListener -- callback
// for BillingClient.acknowledgePurchase.
public interface AcknowledgePurchaseResponseListener {
    void onAcknowledgePurchaseResponse(BillingResult billingResult);
}
