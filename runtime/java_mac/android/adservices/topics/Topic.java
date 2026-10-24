package android.adservices.topics;

// android.adservices.topics -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class Topic {
    private long m_getModelVersion = 0L;
    private long m_getTaxonomyVersion = 0L;
    private int m_getTopicId = 0;
    private Topic() { }
    public Topic(long taxonomyVersion, long modelVersion, int topicId) { }
    public long getModelVersion() { return m_getModelVersion; }
    public long getTaxonomyVersion() { return m_getTaxonomyVersion; }
    public int getTopicId() { return m_getTopicId; }
}
