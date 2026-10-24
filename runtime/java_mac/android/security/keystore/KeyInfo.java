// android.security.keystore.KeyInfo
//
// Describes the authorizations of an AndroidKeyStore key. Apps obtain one via
// KeyFactory.getKeySpec(key, KeyInfo.class) and read isInsideSecureHardware() /
// getSecurityLevel() before deciding how much to trust the key (Jetpack
// Security, Tink and several banking SDKs all do exactly that).
//
// AOSP source: frameworks/base/keystore/java/android/security/keystore/
// KeyInfo.java (android16-release). Full public getter surface mirrored; the
// 23-argument constructor matches AOSP's (:95-117), and the older 14-argument
// form is retained so existing in-tree callers keep compiling.
//
// ⚠ THE HONEST ANSWERS, and they matter more than the getters:
//   isInsideSecureHardware()  -> whatever the constructor was told; our backend
//                                is file-backed, so false.
//   getSecurityLevel()        -> SECURITY_LEVEL_SOFTWARE.
//   isUserAuthenticationRequirementEnforcedBySecureHardware() -> false.
// An app that reads these is asking "is this key protected by hardware I can
// rely on?" and the truthful answer here is no. Reporting
// SECURITY_LEVEL_TRUSTED_ENVIRONMENT or STRONGBOX would be exactly the kind of
// lie about attestation-adjacent state that this project refuses to tell.
package android.security.keystore;

import java.security.spec.KeySpec;
import java.util.Date;

public class KeyInfo implements KeySpec {

    private final String mKeystoreAlias;
    private final int mKeySize;
    private final boolean mInsideSecureHardware;
    private final int mOrigin;
    private final int mPurposes;
    private final String[] mEncryptionPaddings;
    private final String[] mSignaturePaddings;
    private final String[] mDigests;
    private final String[] mBlockModes;
    private final boolean mUserAuthenticationRequired;
    private final int mUserAuthenticationValidityDurationSeconds;
    private final int mUserAuthenticationType;
    private final boolean mUserAuthenticationRequirementEnforcedBySecureHardware;
    private final boolean mUserAuthenticationValidWhileOnBody;
    private final boolean mUnlockedDeviceRequired;
    private final boolean mTrustedUserPresenceRequired;
    private final boolean mInvalidatedByBiometricEnrollment;
    private final boolean mUserConfirmationRequired;
    private final int mSecurityLevel;
    private final int mRemainingUsageCount;
    private final Date mKeyValidityStart;
    private final Date mKeyValidityForOriginationEnd;
    private final Date mKeyValidityForConsumptionEnd;

    /** The pre-API-31 shape, kept for existing in-tree callers. Defaults the
     *  fields it does not carry the way a software-backed key really is:
     *  device-credential auth type, no secure-hardware enforcement,
     *  SECURITY_LEVEL_SOFTWARE, unrestricted usage count. */
    public KeyInfo(String keystoreKeyAlias, boolean insideSecureHardware, int origin,
            int keySize, Date keyValidityStart, Date keyValidityForOriginationEnd,
            Date keyValidityForConsumptionEnd, int purposes, String[] encryptionPaddings,
            String[] signaturePaddings, String[] digests, String[] blockModes,
            boolean userAuthenticationRequired,
            int userAuthenticationValidityDurationSeconds) {
        this(keystoreKeyAlias, insideSecureHardware, origin, keySize, keyValidityStart,
                keyValidityForOriginationEnd, keyValidityForConsumptionEnd, purposes,
                encryptionPaddings, signaturePaddings, digests, blockModes,
                userAuthenticationRequired, userAuthenticationValidityDurationSeconds,
                KeyProperties.AUTH_DEVICE_CREDENTIAL, false, false, false, false, true, false,
                KeyProperties.SECURITY_LEVEL_SOFTWARE,
                KeyProperties.UNRESTRICTED_USAGE_COUNT);
    }

    public KeyInfo(String keystoreKeyAlias, boolean insideSecureHardware, int origin,
            int keySize, Date keyValidityStart, Date keyValidityForOriginationEnd,
            Date keyValidityForConsumptionEnd, int purposes, String[] encryptionPaddings,
            String[] signaturePaddings, String[] digests, String[] blockModes,
            boolean userAuthenticationRequired,
            int userAuthenticationValidityDurationSeconds,
            int userAuthenticationType,
            boolean userAuthenticationRequirementEnforcedBySecureHardware,
            boolean userAuthenticationValidWhileOnBody,
            boolean unlockedDeviceRequired,
            boolean trustedUserPresenceRequired,
            boolean invalidatedByBiometricEnrollment,
            boolean userConfirmationRequired,
            int securityLevel,
            int remainingUsageCount) {
        mKeystoreAlias = keystoreKeyAlias;
        mInsideSecureHardware = insideSecureHardware;
        mOrigin = origin;
        mKeySize = keySize;
        mKeyValidityStart = cloneOrNull(keyValidityStart);
        mKeyValidityForOriginationEnd = cloneOrNull(keyValidityForOriginationEnd);
        mKeyValidityForConsumptionEnd = cloneOrNull(keyValidityForConsumptionEnd);
        mPurposes = purposes;
        mEncryptionPaddings = encryptionPaddings != null ? encryptionPaddings.clone()
                                                         : new String[0];
        mSignaturePaddings = signaturePaddings != null ? signaturePaddings.clone()
                                                       : new String[0];
        mDigests = digests != null ? digests.clone() : new String[0];
        mBlockModes = blockModes != null ? blockModes.clone() : new String[0];
        mUserAuthenticationRequired = userAuthenticationRequired;
        mUserAuthenticationValidityDurationSeconds = userAuthenticationValidityDurationSeconds;
        mUserAuthenticationType = userAuthenticationType;
        mUserAuthenticationRequirementEnforcedBySecureHardware =
                userAuthenticationRequirementEnforcedBySecureHardware;
        mUserAuthenticationValidWhileOnBody = userAuthenticationValidWhileOnBody;
        mUnlockedDeviceRequired = unlockedDeviceRequired;
        mTrustedUserPresenceRequired = trustedUserPresenceRequired;
        mInvalidatedByBiometricEnrollment = invalidatedByBiometricEnrollment;
        mUserConfirmationRequired = userConfirmationRequired;
        mSecurityLevel = securityLevel;
        mRemainingUsageCount = remainingUsageCount;
    }

    private static Date cloneOrNull(Date d) { return d != null ? (Date) d.clone() : null; }

    public String getKeystoreAlias() { return mKeystoreAlias; }
    public int getKeySize() { return mKeySize; }
    /** @deprecated superseded by getSecurityLevel(). Mac.apk's keystore is
     *  file-backed, not a TEE/StrongBox -> false. */
    @Deprecated public boolean isInsideSecureHardware() { return mInsideSecureHardware; }
    public int getOrigin() { return mOrigin; }
    public int getPurposes() { return mPurposes; }
    public String[] getEncryptionPaddings() { return mEncryptionPaddings.clone(); }
    public String[] getSignaturePaddings() { return mSignaturePaddings.clone(); }
    public String[] getDigests() { return mDigests.clone(); }
    public String[] getBlockModes() { return mBlockModes.clone(); }
    public boolean isUserAuthenticationRequired() { return mUserAuthenticationRequired; }
    public int getUserAuthenticationValidityDurationSeconds() {
        return mUserAuthenticationValidityDurationSeconds;
    }
    public int getUserAuthenticationType() { return mUserAuthenticationType; }
    public boolean isUserAuthenticationRequirementEnforcedBySecureHardware() {
        return mUserAuthenticationRequirementEnforcedBySecureHardware;
    }
    public boolean isUserAuthenticationValidWhileOnBody() {
        return mUserAuthenticationValidWhileOnBody;
    }
    public boolean isUnlockedDeviceRequired() { return mUnlockedDeviceRequired; }
    public boolean isTrustedUserPresenceRequired() { return mTrustedUserPresenceRequired; }
    public boolean isInvalidatedByBiometricEnrollment() {
        return mInvalidatedByBiometricEnrollment;
    }
    public boolean isUserConfirmationRequired() { return mUserConfirmationRequired; }
    /** KeyProperties.SECURITY_LEVEL_*. Always SOFTWARE on Mac.apk. */
    public int getSecurityLevel() { return mSecurityLevel; }
    /** KeyProperties.UNRESTRICTED_USAGE_COUNT when the key has no usage limit. */
    public int getRemainingUsageCount() { return mRemainingUsageCount; }
    public Date getKeyValidityStart() { return cloneOrNull(mKeyValidityStart); }
    public Date getKeyValidityForOriginationEnd() {
        return cloneOrNull(mKeyValidityForOriginationEnd);
    }
    public Date getKeyValidityForConsumptionEnd() {
        return cloneOrNull(mKeyValidityForConsumptionEnd);
    }
}
