package com.google.android.play.core.integrity;

// com.google.android.play.core.integrity.PrepareIntegrityTokenRequest -- the
// one-time warm-up request passed to StandardIntegrityManager
// .prepareIntegrityToken(). We honour the published builder shape so apps
// compile; since preparation always fails (no backend) the contents are unused.
// Mirrors the published Play Integrity PrepareIntegrityTokenRequest + Builder.
public abstract class PrepareIntegrityTokenRequest {

    public abstract Long cloudProjectNumber();

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Long cloudProjectNumber;

        Builder() {}

        public Builder setCloudProjectNumber(long cloudProjectNumber) {
            this.cloudProjectNumber = Long.valueOf(cloudProjectNumber);
            return this;
        }

        public PrepareIntegrityTokenRequest build() {
            final Long cpn = cloudProjectNumber;
            return new PrepareIntegrityTokenRequest() {
                @Override public Long cloudProjectNumber() { return cpn; }
            };
        }
    }
}
