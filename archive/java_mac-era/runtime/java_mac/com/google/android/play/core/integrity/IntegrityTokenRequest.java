package com.google.android.play.core.integrity;

// com.google.android.play.core.integrity.IntegrityTokenRequest -- the request
// bag passed to IntegrityManager.requestIntegrityToken(). We honour the
// published builder shape so apps compile and configure it normally; since the
// request always fails (no backend, never forged) the contents are unused.
// Mirrors the published Play Integrity IntegrityTokenRequest + Builder.
public abstract class IntegrityTokenRequest {

    public abstract String nonce();

    public abstract Long cloudProjectNumber();

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String nonce;
        private Long cloudProjectNumber;

        Builder() {}

        public Builder setNonce(String nonce) {
            this.nonce = nonce;
            return this;
        }

        public Builder setCloudProjectNumber(long cloudProjectNumber) {
            this.cloudProjectNumber = Long.valueOf(cloudProjectNumber);
            return this;
        }

        public IntegrityTokenRequest build() {
            final String n = nonce;
            final Long cpn = cloudProjectNumber;
            return new IntegrityTokenRequest() {
                @Override public String nonce() { return n; }
                @Override public Long cloudProjectNumber() { return cpn; }
            };
        }
    }
}
