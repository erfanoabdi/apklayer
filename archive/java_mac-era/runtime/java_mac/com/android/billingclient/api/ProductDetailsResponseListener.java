package com.android.billingclient.api;

import java.util.List;

// com.android.billingclient.api.ProductDetailsResponseListener -- callback for
// BillingClient.queryProductDetailsAsync. The v6 SDK passes a List, v7.1+ added
// a QueryProductDetailsResult overload; we keep the List-based contract that all
// shipping apps were compiled against.
public interface ProductDetailsResponseListener {
    void onProductDetailsResponse(BillingResult billingResult,
                                  List<ProductDetails> productDetailsList);
}
