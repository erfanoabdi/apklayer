package com.android.billingclient.api;

import org.json.JSONException;
import org.json.JSONObject;

// com.android.billingclient.api.SkuDetails -- the pre-v5 product descriptor
// returned by querySkuDetailsAsync. Replaced by ProductDetails but apps compiled
// against Billing v4 still reference it. De-Googled device returns no SKUs, so
// we never produce one; the JSON-backed getter surface ships for completeness.
public class SkuDetails {
    private final String originalJson;
    private final JSONObject parsed;

    public SkuDetails(String jsonSkuDetails) throws JSONException {
        this.originalJson = jsonSkuDetails == null ? "" : jsonSkuDetails;
        this.parsed = jsonSkuDetails == null || jsonSkuDetails.isEmpty()
                ? new JSONObject() : new JSONObject(jsonSkuDetails);
    }

    public String getSku() { return parsed.optString("productId", ""); }
    public String getType() { return parsed.optString("type", ""); }
    public String getPrice() { return parsed.optString("price", ""); }
    public long getPriceAmountMicros() { return parsed.optLong("price_amount_micros", 0L); }
    public String getPriceCurrencyCode() { return parsed.optString("price_currency_code", ""); }
    public String getOriginalPrice() {
        String s = parsed.optString("original_price", "");
        return s.isEmpty() ? getPrice() : s;
    }
    public long getOriginalPriceAmountMicros() {
        long v = parsed.optLong("original_price_micros", 0L);
        return v == 0L ? getPriceAmountMicros() : v;
    }
    public String getTitle() { return parsed.optString("title", ""); }
    public String getDescription() { return parsed.optString("description", ""); }
    public String getSubscriptionPeriod() { return parsed.optString("subscriptionPeriod", ""); }
    public String getFreeTrialPeriod() { return parsed.optString("freeTrialPeriod", ""); }
    public String getIntroductoryPrice() { return parsed.optString("introductoryPrice", ""); }
    public long getIntroductoryPriceAmountMicros() {
        return parsed.optLong("introductoryPriceAmountMicros", 0L);
    }
    public String getIntroductoryPricePeriod() {
        return parsed.optString("introductoryPricePeriod", "");
    }
    public int getIntroductoryPriceCycles() {
        return parsed.optInt("introductoryPriceCycles", 0);
    }
    public String getIconUrl() { return parsed.optString("iconUrl", ""); }
    public String getOriginalJson() { return originalJson; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkuDetails)) return false;
        return originalJson.equals(((SkuDetails) o).originalJson);
    }

    @Override public int hashCode() { return originalJson.hashCode(); }

    @Override public String toString() { return "SkuDetails: " + originalJson; }
}
