package com.android.billingclient.api;

// com.android.billingclient.api.QueryPurchasesParams -- the v5+ request passed
// to BillingClient.queryPurchasesAsync. We retain the requested product type
// faithfully; the query returns an empty purchase list on a de-Googled device
// (the user owns nothing through a Play Store that is structurally absent).
public final class QueryPurchasesParams {
    private final String productType;

    private QueryPurchasesParams(String productType) {
        this.productType = productType;
    }

    public String getProductType() { return productType; }

    public static Builder newBuilder() { return new Builder(); }

    public static final class Builder {
        private String productType;

        Builder() {}

        public Builder setProductType(String productType) {
            this.productType = productType;
            return this;
        }

        public QueryPurchasesParams build() {
            if (productType == null) {
                throw new IllegalArgumentException("Product type must be provided.");
            }
            return new QueryPurchasesParams(productType);
        }
    }
}
