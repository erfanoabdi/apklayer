package com.android.billingclient.api;

import java.util.List;

// com.android.billingclient.api.PurchasesUpdatedListener -- registered on the
// BillingClient.Builder; real Play Billing invokes it after a purchase flow.
// We never run a real flow (no Play Store), so this is only ever invoked with a
// failure result + null list from launchBillingFlow's degrade path.
public interface PurchasesUpdatedListener {
    void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases);
}
