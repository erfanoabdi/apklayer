package com.android.billingclient.api;

import java.util.List;

// com.android.billingclient.api.SkuDetailsResponseListener -- callback for the
// legacy (pre-v5) querySkuDetailsAsync. Apps compiled against Billing v4 still
// reference it; we return an empty list cleanly.
public interface SkuDetailsResponseListener {
    void onSkuDetailsResponse(BillingResult billingResult, List<SkuDetails> skuDetailsList);
}
