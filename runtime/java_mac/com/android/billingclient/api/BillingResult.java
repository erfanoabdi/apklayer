package com.android.billingclient.api;

// com.android.billingclient.api.BillingResult -- the response object returned
// by every Play Billing call (synchronously from launchBillingFlow /
// isFeatureSupported, and inside every listener callback). Carries a response
// code (one of BillingClient.BillingResponseCode) plus a human-readable debug
// message. Mac.apk runs as a de-Googled device with no Play Store service, so
// every result we hand back is a clean BILLING_UNAVAILABLE / SERVICE_DISCONNECTED
// -- never a fabricated OK with a fake purchase. We still ship the full type so
// paid apps can read getResponseCode() and reach their degrade path.
public final class BillingResult {
    private final int responseCode;
    private final String debugMessage;

    private BillingResult(int responseCode, String debugMessage) {
        this.responseCode = responseCode;
        this.debugMessage = debugMessage == null ? "" : debugMessage;
    }

    public int getResponseCode() { return responseCode; }
    public String getDebugMessage() { return debugMessage; }

    @Override public String toString() {
        return "BillingResult{responseCode=" + responseCode
                + ", debugMessage='" + debugMessage + "'}";
    }

    public static Builder newBuilder() { return new Builder(); }

    public static final class Builder {
        private int responseCode;
        private String debugMessage = "";

        Builder() {}

        public Builder setResponseCode(int responseCode) {
            this.responseCode = responseCode;
            return this;
        }
        public Builder setDebugMessage(String debugMessage) {
            this.debugMessage = debugMessage == null ? "" : debugMessage;
            return this;
        }
        public BillingResult build() {
            return new BillingResult(responseCode, debugMessage);
        }
    }
}
