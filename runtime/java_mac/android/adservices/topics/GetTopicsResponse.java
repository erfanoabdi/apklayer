package android.adservices.topics;

// android.adservices.topics -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class GetTopicsResponse {
    private java.util.List<android.adservices.topics.Topic> m_getTopics = java.util.Collections.emptyList();
    private java.util.List<android.adservices.topics.EncryptedTopic> m_getEncryptedTopics = java.util.Collections.emptyList();
    private GetTopicsResponse() { }
    public java.util.List<android.adservices.topics.Topic> getTopics() { return m_getTopics; }
    public java.util.List<android.adservices.topics.EncryptedTopic> getEncryptedTopics() { return m_getEncryptedTopics; }
    public static final class Builder {
        public Builder() { }
        public GetTopicsResponse build() { return new GetTopicsResponse(); }
    }
}
