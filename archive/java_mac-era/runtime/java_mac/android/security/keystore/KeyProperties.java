package android.security.keystore;
// android.security.keystore.KeyProperties: the constant set used to configure
// AndroidKeyStore key generation. Pure constants (no platform state), so this
// is a faithful, complete mirror of the AOSP class.
// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class KeyProperties {
    private KeyProperties() {}
    public static final int PURPOSE_ENCRYPT = 1;
    public static final int PURPOSE_DECRYPT = 2;
    public static final int PURPOSE_SIGN    = 4;
    public static final int PURPOSE_VERIFY  = 8;
    public static final int PURPOSE_WRAP_KEY = 32;
    public static final int PURPOSE_AGREE_KEY = 64;
    public static final int PURPOSE_ATTEST_KEY = 128;

    public static final String KEY_ALGORITHM_RSA = "RSA";
    public static final String KEY_ALGORITHM_EC  = "EC";
    public static final String KEY_ALGORITHM_AES = "AES";
    public static final String KEY_ALGORITHM_3DES = "DESede";
    public static final String KEY_ALGORITHM_HMAC_SHA1   = "HmacSHA1";
    public static final String KEY_ALGORITHM_HMAC_SHA224 = "HmacSHA224";
    public static final String KEY_ALGORITHM_HMAC_SHA256 = "HmacSHA256";
    public static final String KEY_ALGORITHM_HMAC_SHA384 = "HmacSHA384";
    public static final String KEY_ALGORITHM_HMAC_SHA512 = "HmacSHA512";

    public static final String BLOCK_MODE_ECB = "ECB";
    public static final String BLOCK_MODE_CBC = "CBC";
    public static final String BLOCK_MODE_CTR = "CTR";
    public static final String BLOCK_MODE_GCM = "GCM";

    public static final String ENCRYPTION_PADDING_NONE     = "NoPadding";
    public static final String ENCRYPTION_PADDING_PKCS7    = "PKCS7Padding";
    public static final String ENCRYPTION_PADDING_RSA_PKCS1 = "PKCS1Padding";
    public static final String ENCRYPTION_PADDING_RSA_OAEP = "OAEPPadding";

    public static final String SIGNATURE_PADDING_RSA_PKCS1 = "PKCS1";
    public static final String SIGNATURE_PADDING_RSA_PSS   = "PSS";

    public static final String DIGEST_NONE   = "NONE";
    public static final String DIGEST_MD5    = "MD5";
    public static final String DIGEST_SHA1   = "SHA-1";
    public static final String DIGEST_SHA224 = "SHA-224";
    public static final String DIGEST_SHA256 = "SHA-256";
    public static final String DIGEST_SHA384 = "SHA-384";
    public static final String DIGEST_SHA512 = "SHA-512";

    // User-authentication types (KeyGenParameterSpec.Builder
    // .setUserAuthenticationParameters). Mac.apk has no lock-screen auth, but
    // the constants must exist for callers that OR them into the spec.
    public static final int AUTH_BIOMETRIC_STRONG = 2;
    public static final int AUTH_DEVICE_CREDENTIAL = 1;

    // Key origin (KeyInfo.getOrigin()). Our generated keys are ORIGIN_GENERATED.
    public static final int ORIGIN_GENERATED = 1;
    public static final int ORIGIN_IMPORTED = 2;
    public static final int ORIGIN_UNKNOWN = 4;
    public static final int ORIGIN_SECURELY_IMPORTED = 8;

    // Security level (KeyInfo.getSecurityLevel()). macOS has no TEE/StrongBox,
    // so generated keys are SOFTWARE; the higher levels exist for completeness
    // and for callers that compare against them.
    public static final int SECURITY_LEVEL_UNKNOWN = -2;
    public static final int SECURITY_LEVEL_UNKNOWN_SECURE = -1;
    public static final int SECURITY_LEVEL_SOFTWARE = 0;
    public static final int SECURITY_LEVEL_TRUSTED_ENVIRONMENT = 1;
    public static final int SECURITY_LEVEL_STRONGBOX = 2;

    // KeyGenParameterSpec.Builder.setMaxUsageCount() "no limit" sentinel.
    public static final int UNRESTRICTED_USAGE_COUNT = -1;
}
