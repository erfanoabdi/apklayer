package com.android.billingclient.api;

// com.android.billingclient.api.BillingConfigResponseListener -- callback for
// BillingClient.getBillingConfigAsync. getBillingConfig (country code) requires
// the Play Store backend, so we return BILLING_UNAVAILABLE with a null config.
public interface BillingConfigResponseListener {
    void onBillingConfigResponse(BillingResult billingResult, BillingConfig billingConfig);
}
