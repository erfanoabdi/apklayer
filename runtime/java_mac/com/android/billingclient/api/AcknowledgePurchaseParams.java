package com.android.billingclient.api;

// com.android.billingclient.api.AcknowledgePurchaseParams -- the request passed
// to BillingClient.acknowledgePurchase. We retain the purchase token faithfully;
// the acknowledgement itself returns BILLING_UNAVAILABLE on a de-Googled device
// (there is no Play Store purchase to acknowledge).
public final class AcknowledgePurchaseParams {
    private final String purchaseToken;

    private AcknowledgePurchaseParams(String purchaseToken) {
        this.purchaseToken = purchaseToken;
    }

    public String getPurchaseToken() { return purchaseToken; }

    public static Builder newBuilder() { return new Builder(); }

    public static final class Builder {
        private String purchaseToken;

        Builder() {}

        public Builder setPurchaseToken(String purchaseToken) {
            this.purchaseToken = purchaseToken;
            return this;
        }

        public AcknowledgePurchaseParams build() {
            if (purchaseToken == null) {
                throw new IllegalArgumentException("Purchase token must be set");
            }
            return new AcknowledgePurchaseParams(purchaseToken);
        }
    }
}
