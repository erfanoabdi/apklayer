package com.google.android.play.core.integrity;

// com.google.android.play.core.integrity.StandardIntegrityTokenRequest -- the
// per-request bag passed to StandardIntegrityTokenProvider.request(). We honour
// the published builder shape so apps compile; since the request always fails
// (no backend, never forged) the contents are unused. Mirrors the published
// Play Integrity StandardIntegrityTokenRequest + Builder.
public abstract class StandardIntegrityTokenRequest {

    public abstract String requestHash();

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String requestHash;

        Builder() {}

        public Builder setRequestHash(String requestHash) {
            this.requestHash = requestHash;
            return this;
        }

        public StandardIntegrityTokenRequest build() {
            final String rh = requestHash;
            return new StandardIntegrityTokenRequest() {
                @Override public String requestHash() { return rh; }
            };
        }
    }
}
