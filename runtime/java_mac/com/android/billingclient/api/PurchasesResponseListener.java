package com.android.billingclient.api;

import java.util.List;

// com.android.billingclient.api.PurchasesResponseListener -- callback for
// BillingClient.queryPurchasesAsync. On a de-Googled device the user owns
// nothing, so we always return an empty list with a clean result.
public interface PurchasesResponseListener {
    void onQueryPurchasesResponse(BillingResult billingResult, List<Purchase> purchases);
}
