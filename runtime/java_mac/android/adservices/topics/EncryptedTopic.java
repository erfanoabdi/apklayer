package android.adservices.topics;

// android.adservices.topics -- Privacy Sandbox (API 33+).
// Mac.apk is a de-Googled device: the AdServices module is absent, so this
// value never leaves the app process. See [[gms-absent-device-contract]]; the class exists so an ad SDK's
// class-load/verify succeeds, and the honest runtime answer is "not present".

public final class EncryptedTopic {
    private byte[] m_getEncapsulatedKey = new byte[0];
    private byte[] m_getEncryptedTopic = new byte[0];
    private String m_getKeyIdentifier = "";
    private EncryptedTopic() { }
    public EncryptedTopic(byte[] encryptedTopic, String keyIdentifier, byte[] encapsulatedKey) { }
    public byte[] getEncapsulatedKey() { return m_getEncapsulatedKey; }
    public byte[] getEncryptedTopic() { return m_getEncryptedTopic; }
    public String getKeyIdentifier() { return m_getKeyIdentifier; }
    @Override public String toString() { return "EncryptedTopic{}"; }
}
