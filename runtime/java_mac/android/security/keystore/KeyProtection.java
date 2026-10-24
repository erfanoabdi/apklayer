// android.security.keystore.KeyProtection -- the java.security.KeyStore
// .ProtectionParameter used when IMPORTING a key into the AndroidKeyStore
// (keyStore.setEntry(alias, entry, keyProtection)). It is the import-side twin
// of KeyGenParameterSpec: same authorization vocabulary, no alias (the setEntry
// call supplies that) and no certificate fields.
//
// AOSP source: frameworks/base/keystore/java/android/security/keystore/
// KeyProtection.java (android16-release). Builder defaults mirrored:
// randomizedEncryptionRequired=true, invalidatedByBiometricEnrollment=true,
// userAuthenticationType=AUTH_BIOMETRIC_STRONG, validity duration 0,
// maxUsageCount=UNRESTRICTED_USAGE_COUNT, empty MGF1 digest set.
//
// Note the public API has setIsStrongBoxBacked() but NO getter -- that is
// AOSP's shape, not an omission here. AndroidKeyStoreSpi accepts a
// KeyProtection wherever it accepted a PasswordProtection, since Android
// keystore entries have no per-key password.
package android.security.keystore;

import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class KeyProtection implements KeyStore.ProtectionParameter {

    private final Date mKeyValidityStart;
    private final Date mKeyValidityForOriginationEnd;
    private final Date mKeyValidityForConsumptionEnd;
    private final int mPurposes;
    private final String[] mEncryptionPaddings;
    private final String[] mSignaturePaddings;
    private final String[] mDigests;
    private final boolean mDigestsSpecified;
    private final Set<String> mMgf1Digests;
    private final String[] mBlockModes;
    private final boolean mRandomizedEncryptionRequired;
    private final boolean mUserAuthenticationRequired;
    private final int mUserAuthenticationValidityDurationSeconds;
    private final int mUserAuthenticationType;
    private final boolean mUserPresenceRequired;
    private final boolean mUserAuthenticationValidWhileOnBody;
    private final boolean mInvalidatedByBiometricEnrollment;
    private final boolean mUserConfirmationRequired;
    private final boolean mUnlockedDeviceRequired;
    private final boolean mIsStrongBoxBacked;
    private final int mMaxUsageCount;

    private KeyProtection(Builder b) {
        mKeyValidityStart = cloneOrNull(b.mKeyValidityStart);
        mKeyValidityForOriginationEnd = cloneOrNull(b.mKeyValidityForOriginationEnd);
        mKeyValidityForConsumptionEnd = cloneOrNull(b.mKeyValidityForConsumptionEnd);
        mPurposes = b.mPurposes;
        mEncryptionPaddings = cloneOrEmpty(b.mEncryptionPaddings);
        mSignaturePaddings = cloneOrEmpty(b.mSignaturePaddings);
        mDigests = cloneOrEmpty(b.mDigests);
        mDigestsSpecified = b.mDigests != null;
        mMgf1Digests = Collections.unmodifiableSet(new LinkedHashSet<>(b.mMgf1Digests));
        mBlockModes = cloneOrEmpty(b.mBlockModes);
        mRandomizedEncryptionRequired = b.mRandomizedEncryptionRequired;
        mUserAuthenticationRequired = b.mUserAuthenticationRequired;
        mUserAuthenticationValidityDurationSeconds = b.mUserAuthenticationValidityDurationSeconds;
        mUserAuthenticationType = b.mUserAuthenticationType;
        mUserPresenceRequired = b.mUserPresenceRequired;
        mUserAuthenticationValidWhileOnBody = b.mUserAuthenticationValidWhileOnBody;
        mInvalidatedByBiometricEnrollment = b.mInvalidatedByBiometricEnrollment;
        mUserConfirmationRequired = b.mUserConfirmationRequired;
        mUnlockedDeviceRequired = b.mUnlockedDeviceRequired;
        mIsStrongBoxBacked = b.mIsStrongBoxBacked;
        mMaxUsageCount = b.mMaxUsageCount;
    }

    private static String[] cloneOrEmpty(String[] a) { return a != null ? a.clone() : new String[0]; }
    private static Date cloneOrNull(Date d) { return d != null ? (Date) d.clone() : null; }

    public Date getKeyValidityStart() { return cloneOrNull(mKeyValidityStart); }
    public Date getKeyValidityForConsumptionEnd() {
        return cloneOrNull(mKeyValidityForConsumptionEnd);
    }
    public Date getKeyValidityForOriginationEnd() {
        return cloneOrNull(mKeyValidityForOriginationEnd);
    }
    public int getPurposes() { return mPurposes; }
    public String[] getEncryptionPaddings() { return mEncryptionPaddings.clone(); }
    public String[] getSignaturePaddings() { return mSignaturePaddings.clone(); }
    public String[] getDigests() { return mDigests.clone(); }
    public boolean isDigestsSpecified() { return mDigestsSpecified; }
    public Set<String> getMgf1Digests() { return new HashSet<>(mMgf1Digests); }
    public boolean isMgf1DigestsSpecified() { return !mMgf1Digests.isEmpty(); }
    public String[] getBlockModes() { return mBlockModes.clone(); }
    public boolean isRandomizedEncryptionRequired() { return mRandomizedEncryptionRequired; }
    public boolean isUserAuthenticationRequired() { return mUserAuthenticationRequired; }
    public int getUserAuthenticationValidityDurationSeconds() {
        return mUserAuthenticationValidityDurationSeconds;
    }
    public int getUserAuthenticationType() { return mUserAuthenticationType; }
    public boolean isUserPresenceRequired() { return mUserPresenceRequired; }
    public boolean isUserAuthenticationValidWhileOnBody() {
        return mUserAuthenticationValidWhileOnBody;
    }
    public boolean isInvalidatedByBiometricEnrollment() {
        return mInvalidatedByBiometricEnrollment;
    }
    public boolean isUserConfirmationRequired() { return mUserConfirmationRequired; }
    public boolean isUnlockedDeviceRequired() { return mUnlockedDeviceRequired; }
    public int getMaxUsageCount() { return mMaxUsageCount; }

    /** @hide on AOSP -- there is deliberately no public getter. Our keystore
     *  backend reads it to refuse a StrongBox import honestly. */
    public boolean isStrongBoxBacked() { return mIsStrongBoxBacked; }

    public static final class Builder {
        private final int mPurposes;
        private Date mKeyValidityStart;
        private Date mKeyValidityForOriginationEnd;
        private Date mKeyValidityForConsumptionEnd;
        private String[] mEncryptionPaddings;
        private String[] mSignaturePaddings;
        private String[] mDigests;
        private Set<String> mMgf1Digests = Collections.emptySet();
        private String[] mBlockModes;
        private boolean mRandomizedEncryptionRequired = true;
        private boolean mUserAuthenticationRequired;
        private int mUserAuthenticationValidityDurationSeconds = 0;
        private int mUserAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG;
        private boolean mUserPresenceRequired = false;
        private boolean mUserAuthenticationValidWhileOnBody;
        private boolean mInvalidatedByBiometricEnrollment = true;
        private boolean mUserConfirmationRequired;
        private boolean mUnlockedDeviceRequired = false;
        private boolean mIsStrongBoxBacked = false;
        private int mMaxUsageCount = KeyProperties.UNRESTRICTED_USAGE_COUNT;

        public Builder(int purposes) { mPurposes = purposes; }

        public Builder setKeyValidityStart(Date startDate) {
            mKeyValidityStart = startDate; return this;
        }
        public Builder setKeyValidityEnd(Date endDate) {
            setKeyValidityForOriginationEnd(endDate);
            setKeyValidityForConsumptionEnd(endDate);
            return this;
        }
        public Builder setKeyValidityForOriginationEnd(Date endDate) {
            mKeyValidityForOriginationEnd = endDate; return this;
        }
        public Builder setKeyValidityForConsumptionEnd(Date endDate) {
            mKeyValidityForConsumptionEnd = endDate; return this;
        }
        public Builder setEncryptionPaddings(String... paddings) {
            mEncryptionPaddings = paddings; return this;
        }
        public Builder setSignaturePaddings(String... paddings) {
            mSignaturePaddings = paddings; return this;
        }
        public Builder setDigests(String... digests) { mDigests = digests; return this; }
        public Builder setMgf1Digests(String... mgf1Digests) {
            mMgf1Digests = mgf1Digests != null
                    ? new LinkedHashSet<>(Arrays.asList(mgf1Digests))
                    : Collections.<String>emptySet();
            return this;
        }
        public Builder setBlockModes(String... blockModes) {
            mBlockModes = blockModes; return this;
        }
        public Builder setRandomizedEncryptionRequired(boolean required) {
            mRandomizedEncryptionRequired = required; return this;
        }
        public Builder setUserAuthenticationRequired(boolean required) {
            mUserAuthenticationRequired = required; return this;
        }
        @Deprecated public Builder setUserAuthenticationValidityDurationSeconds(int seconds) {
            mUserAuthenticationValidityDurationSeconds = seconds; return this;
        }
        public Builder setUserAuthenticationParameters(int timeout, int type) {
            if (timeout < 0) throw new IllegalArgumentException("timeout must be 0 or larger");
            mUserAuthenticationValidityDurationSeconds = timeout;
            mUserAuthenticationType = type;
            return this;
        }
        public Builder setUserPresenceRequired(boolean required) {
            mUserPresenceRequired = required; return this;
        }
        public Builder setUserAuthenticationValidWhileOnBody(boolean remainsValid) {
            mUserAuthenticationValidWhileOnBody = remainsValid; return this;
        }
        public Builder setInvalidatedByBiometricEnrollment(boolean invalidate) {
            mInvalidatedByBiometricEnrollment = invalidate; return this;
        }
        public Builder setUserConfirmationRequired(boolean required) {
            mUserConfirmationRequired = required; return this;
        }
        public Builder setUnlockedDeviceRequired(boolean required) {
            mUnlockedDeviceRequired = required; return this;
        }
        public Builder setIsStrongBoxBacked(boolean backed) {
            mIsStrongBoxBacked = backed; return this;
        }
        public Builder setMaxUsageCount(int maxUsageCount) {
            if (maxUsageCount == KeyProperties.UNRESTRICTED_USAGE_COUNT || maxUsageCount > 0) {
                mMaxUsageCount = maxUsageCount;
                return this;
            }
            throw new IllegalArgumentException("maxUsageCount is not valid");
        }
        public KeyProtection build() { return new KeyProtection(this); }
    }
}
