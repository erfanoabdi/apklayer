package com.google.firebase.functions;

// com.google.firebase.functions.HttpsCallableOptions -- per-callable options
// (currently just whether to send Firebase App Check / auth limited-use tokens).
// Recorded faithfully; there is no backend to apply them against.
//
// Signatures mirror the published Firebase Functions Android reference.
public class HttpsCallableOptions {

    private final boolean limitedUseAppCheckTokens;

    private HttpsCallableOptions(boolean limitedUseAppCheckTokens) {
        this.limitedUseAppCheckTokens = limitedUseAppCheckTokens;
    }

    public boolean getLimitedUseAppCheckTokens() { return limitedUseAppCheckTokens; }

    public static class Builder {
        private boolean limitedUseAppCheckTokens = false;

        public Builder() {}

        public Builder setLimitedUseAppCheckTokens(boolean limitedUse) {
            this.limitedUseAppCheckTokens = limitedUse;
            return this;
        }

        public boolean getLimitedUseAppCheckTokens() { return limitedUseAppCheckTokens; }

        public HttpsCallableOptions build() {
            return new HttpsCallableOptions(limitedUseAppCheckTokens);
        }
    }
}
