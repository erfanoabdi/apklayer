package android.adservices.measurement;

// android.adservices.measurement -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class DeletionRequest {
    private DeletionRequest() { }
    public static final class Builder {
        public Builder() { }
        public Builder setDeletionMode(int value) { return this; }
        public Builder setDomainUris(java.util.List<android.net.Uri> value) { return this; }
        public Builder setEnd(java.time.Instant value) { return this; }
        public Builder setMatchBehavior(int value) { return this; }
        public Builder setOriginUris(java.util.List<android.net.Uri> value) { return this; }
        public Builder setStart(java.time.Instant value) { return this; }
        public DeletionRequest build() { return new DeletionRequest(); }
    }
}
