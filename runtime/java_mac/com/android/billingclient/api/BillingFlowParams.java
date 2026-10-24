package com.android.billingclient.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// com.android.billingclient.api.BillingFlowParams -- the request passed to
// BillingClient.launchBillingFlow. We retain the caller's product/offer list
// and account identifiers faithfully so a well-formed flow request builds
// without error; the flow itself returns BILLING_UNAVAILABLE on a de-Googled
// device (no Play Store purchase UI exists).
public final class BillingFlowParams {

    // Deprecated proration-mode annotation namespace, kept for source parity
    // with apps compiled against older Play Billing. The integer constants
    // mirror the modern ReplacementMode values.
    public @interface ProrationMode {
        int UNKNOWN_SUBSCRIPTION_UPGRADE_DOWNGRADE_POLICY = 0;
        int IMMEDIATE_WITH_TIME_PRORATION = 1;
        int IMMEDIATE_AND_CHARGE_PRORATED_PRICE = 2;
        int IMMEDIATE_WITHOUT_PRORATION = 3;
        int DEFERRED = 4;
        int IMMEDIATE_AND_CHARGE_FULL_PRICE = 5;
    }

    private final List<ProductDetailsParams> productDetailsParamsList;
    private final SubscriptionUpdateParams subscriptionUpdateParams;
    private final String obfuscatedAccountId;
    private final String obfuscatedProfileId;
    private final boolean isOfferPersonalized;

    private BillingFlowParams(Builder b) {
        this.productDetailsParamsList = b.productDetailsParamsList;
        this.subscriptionUpdateParams = b.subscriptionUpdateParams;
        this.obfuscatedAccountId = b.obfuscatedAccountId;
        this.obfuscatedProfileId = b.obfuscatedProfileId;
        this.isOfferPersonalized = b.isOfferPersonalized;
    }

    // These accessors are not part of the documented public surface in every
    // release, but harmlessly expose the retained request for our own shim and
    // for apps reflecting over the params.
    public List<ProductDetailsParams> getProductDetailsParamsList() {
        return productDetailsParamsList;
    }
    public SubscriptionUpdateParams getSubscriptionUpdateParams() {
        return subscriptionUpdateParams;
    }
    public String getObfuscatedAccountId() { return obfuscatedAccountId; }
    public String getObfuscatedProfileId() { return obfuscatedProfileId; }
    public boolean isOfferPersonalized() { return isOfferPersonalized; }

    public static Builder newBuilder() { return new Builder(); }

    public static final class Builder {
        private List<ProductDetailsParams> productDetailsParamsList =
                new ArrayList<ProductDetailsParams>();
        private SubscriptionUpdateParams subscriptionUpdateParams;
        private String obfuscatedAccountId;
        private String obfuscatedProfileId;
        private boolean isOfferPersonalized;

        Builder() {}

        public Builder setProductDetailsParamsList(
                List<ProductDetailsParams> productDetailsParamsList) {
            this.productDetailsParamsList = productDetailsParamsList == null
                    ? new ArrayList<ProductDetailsParams>()
                    : new ArrayList<ProductDetailsParams>(productDetailsParamsList);
            return this;
        }

        public Builder setSubscriptionUpdateParams(
                SubscriptionUpdateParams subscriptionUpdateParams) {
            this.subscriptionUpdateParams = subscriptionUpdateParams;
            return this;
        }

        public Builder setObfuscatedAccountId(String obfuscatedAccountId) {
            this.obfuscatedAccountId = obfuscatedAccountId;
            return this;
        }

        public Builder setObfuscatedProfileId(String obfuscatedProfileId) {
            this.obfuscatedProfileId = obfuscatedProfileId;
            return this;
        }

        public Builder setIsOfferPersonalized(boolean isOfferPersonalized) {
            this.isOfferPersonalized = isOfferPersonalized;
            return this;
        }

        public BillingFlowParams build() {
            this.productDetailsParamsList = Collections.unmodifiableList(
                    new ArrayList<ProductDetailsParams>(productDetailsParamsList));
            return new BillingFlowParams(this);
        }
    }

    // com.android.billingclient.api.BillingFlowParams.ProductDetailsParams --
    // pairs a ProductDetails with the offer token (for subscriptions) the user
    // is purchasing.
    public static final class ProductDetailsParams {
        private final ProductDetails productDetails;
        private final String offerToken;

        private ProductDetailsParams(ProductDetails productDetails, String offerToken) {
            this.productDetails = productDetails;
            this.offerToken = offerToken;
        }

        public ProductDetails getProductDetails() { return productDetails; }
        public String getOfferToken() { return offerToken; }

        public static Builder newBuilder() { return new Builder(); }

        public static final class Builder {
            private ProductDetails productDetails;
            private String offerToken;

            Builder() {}

            public Builder setProductDetails(ProductDetails productDetails) {
                this.productDetails = productDetails;
                return this;
            }

            public Builder setOfferToken(String offerToken) {
                this.offerToken = offerToken;
                return this;
            }

            public ProductDetailsParams build() {
                if (productDetails == null) {
                    throw new IllegalArgumentException(
                            "ProductDetails is required for constructing ProductDetailsParams.");
                }
                return new ProductDetailsParams(productDetails, offerToken);
            }
        }
    }

    // com.android.billingclient.api.BillingFlowParams.SubscriptionUpdateParams --
    // describes an upgrade/downgrade from an existing subscription.
    public static final class SubscriptionUpdateParams {

        // ReplacementMode constants per the published API (with the historical
        // value 4 reserved/skipped to 5 for CHARGE_FULL_PRICE).
        public @interface ReplacementMode {
            int UNKNOWN_REPLACEMENT_MODE = 0;
            int WITH_TIME_PRORATION = 1;
            int CHARGE_PRORATED_PRICE = 2;
            int WITHOUT_PRORATION = 3;
            int CHARGE_FULL_PRICE = 5;
            int DEFERRED = 6;
        }

        private final String oldPurchaseToken;
        private final String originalExternalTransactionId;
        private final int subscriptionReplacementMode;

        private SubscriptionUpdateParams(Builder b) {
            this.oldPurchaseToken = b.oldPurchaseToken;
            this.originalExternalTransactionId = b.originalExternalTransactionId;
            this.subscriptionReplacementMode = b.subscriptionReplacementMode;
        }

        public String getOldPurchaseToken() { return oldPurchaseToken; }
        public String getOriginalExternalTransactionId() {
            return originalExternalTransactionId;
        }
        public int getSubscriptionReplacementMode() {
            return subscriptionReplacementMode;
        }

        public static Builder newBuilder() { return new Builder(); }

        public static final class Builder {
            private String oldPurchaseToken;
            private String originalExternalTransactionId;
            private int subscriptionReplacementMode =
                    ReplacementMode.UNKNOWN_REPLACEMENT_MODE;

            Builder() {}

            public Builder setOldPurchaseToken(String purchaseToken) {
                this.oldPurchaseToken = purchaseToken;
                return this;
            }

            public Builder setOriginalExternalTransactionId(String externalTransactionId) {
                this.originalExternalTransactionId = externalTransactionId;
                return this;
            }

            public Builder setSubscriptionReplacementMode(int subscriptionReplacementMode) {
                this.subscriptionReplacementMode = subscriptionReplacementMode;
                return this;
            }

            public SubscriptionUpdateParams build() {
                return new SubscriptionUpdateParams(this);
            }
        }
    }
}
