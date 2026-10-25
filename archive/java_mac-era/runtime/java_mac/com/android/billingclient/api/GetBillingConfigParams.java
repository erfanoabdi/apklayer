package com.android.billingclient.api;

// com.android.billingclient.api.GetBillingConfigParams -- the request passed to
// BillingClient.getBillingConfigAsync. It carries no caller data of consequence
// for our shim, but must build cleanly. The async call returns
// BILLING_UNAVAILABLE with a null config on a de-Googled device.
public final class GetBillingConfigParams {

    private GetBillingConfigParams() {}

    public static Builder newBuilder() { return new Builder(); }

    public static final class Builder {
        Builder() {}

        public GetBillingConfigParams build() {
            return new GetBillingConfigParams();
        }
    }
}
