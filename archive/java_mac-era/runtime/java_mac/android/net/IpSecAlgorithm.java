// android.net.IpSecAlgorithm
//
// Names a crypto algorithm + key for an IPsec transform. Pure value type +
// algorithm-name constants. Mac.apk has no IPsec subsystem, but apps construct
// these to build (no-op) transforms, so the type + constants must resolve.
// Mirrors AOSP android.net.IpSecAlgorithm public API.
package android.net;

public final class IpSecAlgorithm {

    public static final String CRYPT_AES_CBC = "cbc(aes)";
    public static final String CRYPT_AES_CTR = "rfc3686(ctr(aes))";
    public static final String AUTH_HMAC_MD5 = "hmac(md5)";
    public static final String AUTH_HMAC_SHA1 = "hmac(sha1)";
    public static final String AUTH_HMAC_SHA256 = "hmac(sha256)";
    public static final String AUTH_HMAC_SHA384 = "hmac(sha384)";
    public static final String AUTH_HMAC_SHA512 = "hmac(sha512)";
    public static final String AUTH_CRYPT_AES_GCM = "rfc4106(gcm(aes))";
    public static final String AUTH_AES_XCBC = "xcbc(aes)";
    public static final String AUTH_AES_CMAC = "cmac(aes)";

    private final String mName;
    private final byte[] mKey;
    private final int mTruncLenBits;

    public IpSecAlgorithm(String algorithm, byte[] key) {
        this(algorithm, key, key == null ? 0 : key.length * 8);
    }

    public IpSecAlgorithm(String algorithm, byte[] key, int truncLenBits) {
        mName = algorithm;
        mKey = key == null ? null : key.clone();
        mTruncLenBits = truncLenBits;
    }

    public String getName() { return mName; }
    public byte[] getKey() { return mKey == null ? null : mKey.clone(); }
    public int getTruncationLengthBits() { return mTruncLenBits; }
}
