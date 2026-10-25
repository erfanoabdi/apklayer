package com.android.billingclient.api;

// com.android.billingclient.api.ConsumeParams -- the request passed to
// BillingClient.consumeAsync. We retain the purchase token faithfully; the
// consume itself returns BILLING_UNAVAILABLE on a de-Googled device (there is
// no Play Store purchase to consume).
public final class ConsumeParams {
    private final String purchaseToken;

    private ConsumeParams(String purchaseToken) {
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

        public ConsumeParams build() {
            if (purchaseToken == null) {
                throw new IllegalArgumentException("Purchase token must be set");
            }
            return new ConsumeParams(purchaseToken);
        }
    }
}
