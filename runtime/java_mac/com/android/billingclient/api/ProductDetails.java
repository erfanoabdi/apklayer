package com.android.billingclient.api;

import java.util.Collections;
import java.util.List;

// com.android.billingclient.api.ProductDetails -- the v5+ replacement for
// SkuDetails. Returned by queryProductDetailsAsync. On a de-Googled device the
// Play catalog is unreachable so we never construct one (the listener gets
// BILLING_UNAVAILABLE + an empty list). The full nested getter surface ships so
// apps that hold ProductDetails references in their pricing UI / type signatures
// resolve every method. We do NOT fabricate a price.
public final class ProductDetails {
    private final String productId;
    private final String productType;
    private final String title;
    private final String name;
    private final String description;
    private final OneTimePurchaseOfferDetails oneTimeOffer;
    private final List<SubscriptionOfferDetails> subscriptionOffers;

    ProductDetails(String productId, String productType, String title, String name,
                   String description, OneTimePurchaseOfferDetails oneTimeOffer,
                   List<SubscriptionOfferDetails> subscriptionOffers) {
        this.productId = productId;
        this.productType = productType;
        this.title = title;
        this.name = name;
        this.description = description;
        this.oneTimeOffer = oneTimeOffer;
        this.subscriptionOffers = subscriptionOffers;
    }

    public String getProductId() { return productId; }
    public String getProductType() { return productType; }
    public String getTitle() { return title; }
    public String getName() { return name; }
    public String getDescription() { return description; }

    public OneTimePurchaseOfferDetails getOneTimePurchaseOfferDetails() { return oneTimeOffer; }

    public List<SubscriptionOfferDetails> getSubscriptionOfferDetails() {
        return subscriptionOffers;
    }

    @Override public String toString() {
        return "ProductDetails{productId='" + productId + "', productType='" + productType + "'}";
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductDetails)) return false;
        ProductDetails p = (ProductDetails) o;
        return productId != null && productId.equals(p.productId)
                && productType != null && productType.equals(p.productType);
    }

    @Override public int hashCode() {
        return (productId == null ? 0 : productId.hashCode());
    }

    // ---- nested: one-time purchase offer ----------------------------------
    public static final class OneTimePurchaseOfferDetails {
        private final String formattedPrice;
        private final long priceAmountMicros;
        private final String priceCurrencyCode;

        OneTimePurchaseOfferDetails(String formattedPrice, long priceAmountMicros,
                                    String priceCurrencyCode) {
            this.formattedPrice = formattedPrice;
            this.priceAmountMicros = priceAmountMicros;
            this.priceCurrencyCode = priceCurrencyCode;
        }

        public String getFormattedPrice() { return formattedPrice; }
        public long getPriceAmountMicros() { return priceAmountMicros; }
        public String getPriceCurrencyCode() { return priceCurrencyCode; }
    }

    // ---- nested: subscription offer ---------------------------------------
    public static final class SubscriptionOfferDetails {
        private final String basePlanId;
        private final String offerId;
        private final String offerToken;
        private final List<String> offerTags;
        private final PricingPhases pricingPhases;

        SubscriptionOfferDetails(String basePlanId, String offerId, String offerToken,
                                 List<String> offerTags, PricingPhases pricingPhases) {
            this.basePlanId = basePlanId;
            this.offerId = offerId;
            this.offerToken = offerToken;
            this.offerTags = offerTags == null ? Collections.<String>emptyList() : offerTags;
            this.pricingPhases = pricingPhases;
        }

        public String getBasePlanId() { return basePlanId; }
        public String getOfferId() { return offerId; }
        public String getOfferToken() { return offerToken; }
        public List<String> getOfferTags() { return offerTags; }
        public PricingPhases getPricingPhases() { return pricingPhases; }
    }

    // ---- nested: pricing phases wrapper -----------------------------------
    public static final class PricingPhases {
        private final List<PricingPhase> pricingPhaseList;

        PricingPhases(List<PricingPhase> pricingPhaseList) {
            this.pricingPhaseList = pricingPhaseList == null
                    ? Collections.<PricingPhase>emptyList() : pricingPhaseList;
        }

        public List<PricingPhase> getPricingPhaseList() { return pricingPhaseList; }
    }

    // ---- nested: single pricing phase -------------------------------------
    public static final class PricingPhase {
        // com.android.billingclient.api.ProductDetails.RecurrenceMode constants
        // (these live as int constants on the SDK; exposed via getRecurrenceMode()).
        public static final int INFINITE_RECURRING = 1;
        public static final int FINITE_RECURRING = 2;
        public static final int NON_RECURRING = 3;

        private final String formattedPrice;
        private final long priceAmountMicros;
        private final String priceCurrencyCode;
        private final String billingPeriod;
        private final int billingCycleCount;
        private final int recurrenceMode;

        PricingPhase(String formattedPrice, long priceAmountMicros, String priceCurrencyCode,
                     String billingPeriod, int billingCycleCount, int recurrenceMode) {
            this.formattedPrice = formattedPrice;
            this.priceAmountMicros = priceAmountMicros;
            this.priceCurrencyCode = priceCurrencyCode;
            this.billingPeriod = billingPeriod;
            this.billingCycleCount = billingCycleCount;
            this.recurrenceMode = recurrenceMode;
        }

        public String getFormattedPrice() { return formattedPrice; }
        public long getPriceAmountMicros() { return priceAmountMicros; }
        public String getPriceCurrencyCode() { return priceCurrencyCode; }
        public String getBillingPeriod() { return billingPeriod; }
        public int getBillingCycleCount() { return billingCycleCount; }
        public int getRecurrenceMode() { return recurrenceMode; }
    }
}
