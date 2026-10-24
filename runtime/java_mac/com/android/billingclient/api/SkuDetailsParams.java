package com.android.billingclient.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// com.android.billingclient.api.SkuDetailsParams -- the legacy (pre-v5) request
// passed to BillingClient.querySkuDetailsAsync. Deprecated in favor of
// QueryProductDetailsParams, but still present so apps compiled against older
// Play Billing build and run. We retain the SKU list and type faithfully; the
// query returns an empty list on a de-Googled device.
public final class SkuDetailsParams {
    private final String skuType;
    private final List<String> skusList;

    private SkuDetailsParams(String skuType, List<String> skusList) {
        this.skuType = skuType;
        this.skusList = skusList;
    }

    public String getSkuType() { return skuType; }
    public List<String> getSkusList() { return skusList; }

    public static Builder newBuilder() { return new Builder(); }

    public static final class Builder {
        private String skuType;
        private List<String> skusList = new ArrayList<String>();

        Builder() {}

        public Builder setType(String type) {
            this.skuType = type;
            return this;
        }

        public Builder setSkusList(List<String> skusList) {
            this.skusList = skusList == null
                    ? new ArrayList<String>() : new ArrayList<String>(skusList);
            return this;
        }

        public SkuDetailsParams build() {
            return new SkuDetailsParams(skuType,
                    Collections.unmodifiableList(new ArrayList<String>(skusList)));
        }
    }
}
