package com.android.billingclient.api;

// com.android.billingclient.api.BillingClientStateListener -- delivered to
// BillingClient.startConnection. Real Play Billing fires onBillingSetupFinished
// once the Play Store service binds; on a de-Googled device the bind never
// succeeds so we fire BILLING_UNAVAILABLE.
public interface BillingClientStateListener {
    void onBillingSetupFinished(BillingResult billingResult);
    void onBillingServiceDisconnected();
}
