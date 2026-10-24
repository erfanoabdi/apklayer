package com.android.billingclient.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// com.android.billingclient.api.QueryProductDetailsParams -- the v5+ request for
// queryProductDetailsAsync. We retain the caller's product list faithfully (so a
// well-formed request builds without error) even though the query itself returns
// BILLING_UNAVAILABLE on a de-Googled device.
public final class QueryProductDetailsParams {
    private final List<Product> productList;

    private QueryProductDetailsParams(List<Product> productList) {
        this.productList = productList;
    }

    public List<Product> getProductList() { return productList; }

    public static Builder newBuilder() { return new Builder(); }

    public static final class Builder {
        private List<Product> productList = new ArrayList<Product>();

        Builder() {}

        public Builder setProductList(List<Product> productList) {
            this.productList = productList == null
                    ? new ArrayList<Product>() : new ArrayList<Product>(productList);
            return this;
        }

        public QueryProductDetailsParams build() {
            return new QueryProductDetailsParams(
                    Collections.unmodifiableList(new ArrayList<Product>(productList)));
        }
    }

    // com.android.billingclient.api.QueryProductDetailsParams.Product -- one
    // (productId, productType) pair to query.
    public static final class Product {
        private final String productId;
        private final String productType;

        private Product(String productId, String productType) {
            this.productId = productId;
            this.productType = productType;
        }

        public String zza() { return productId; }     // R8-stable accessor parity
        public String getProductId() { return productId; }
        public String getProductType() { return productType; }

        public static Builder newBuilder() { return new Builder(); }

        public static final class Builder {
            private String productId;
            private String productType;

            Builder() {}

            public Builder setProductId(String productId) {
                this.productId = productId;
                return this;
            }
            public Builder setProductType(String productType) {
                this.productType = productType;
                return this;
            }
            public Product build() {
                if (productId == null) {
                    throw new IllegalArgumentException("Product id must be provided.");
                }
                if (productType == null) {
                    throw new IllegalArgumentException("Product type must be provided.");
                }
                return new Product(productId, productType);
            }
        }
    }
}
