package com.android.billingclient.api;

import java.util.List;

// com.android.billingclient.api.PurchaseHistoryResponseListener -- callback for
// the (deprecated but still widely-referenced) queryPurchaseHistoryAsync.
public interface PurchaseHistoryResponseListener {
    void onPurchaseHistoryResponse(BillingResult billingResult,
                                   List<PurchaseHistoryRecord> purchaseHistoryRecordList);
}
