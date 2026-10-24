package com.android.billingclient.api;

// com.android.billingclient.api.BillingConfig -- returned by getBillingConfigAsync;
// carries the user's Play Store country code. Requires the Play backend, so we
// never produce one (the listener gets BILLING_UNAVAILABLE + null). Type ships
// so apps can reference BillingConfig in their callback signature.
public final class BillingConfig {
    private final String countryCode;

    BillingConfig(String countryCode) { this.countryCode = countryCode; }

    public String getCountryCode() { return countryCode; }
}
