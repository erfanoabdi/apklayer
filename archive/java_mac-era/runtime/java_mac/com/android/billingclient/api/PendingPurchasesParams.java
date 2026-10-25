package com.android.billingclient.api;

// com.android.billingclient.api.PendingPurchasesParams -- passed to
// BillingClient.Builder.enablePendingPurchases(PendingPurchasesParams) in v6+.
// It only records which pending-purchase product categories the app opts into;
// it has no backend effect on a de-Googled device but must build cleanly so
// billing initialization succeeds.
public final class PendingPurchasesParams {
    private final boolean enableOneTimeProducts;
    private final boolean enablePrepaidPlans;

    private PendingPurchasesParams(boolean enableOneTimeProducts,
                                   boolean enablePrepaidPlans) {
        this.enableOneTimeProducts = enableOneTimeProducts;
        this.enablePrepaidPlans = enablePrepaidPlans;
    }

    public boolean isOneTimeProductsEnabled() { return enableOneTimeProducts; }
    public boolean isPrepaidPlansEnabled() { return enablePrepaidPlans; }

    public static Builder newBuilder() { return new Builder(); }

    public static final class Builder {
        private boolean enableOneTimeProducts;
        private boolean enablePrepaidPlans;

        Builder() {}

        public Builder enableOneTimeProducts() {
            this.enableOneTimeProducts = true;
            return this;
        }

        public Builder enablePrepaidPlans() {
            this.enablePrepaidPlans = true;
            return this;
        }

        public PendingPurchasesParams build() {
            return new PendingPurchasesParams(enableOneTimeProducts, enablePrepaidPlans);
        }
    }
}
