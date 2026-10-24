package android.security.keystore;

import java.math.BigInteger;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

// android.security.keystore.KeyGenParameterSpec: the configuration object passed
// to a KeyPairGenerator/KeyGenerator initialized for the AndroidKeyStore. It is
// a pure data holder built via the inner Builder.
//
// AOSP source: frameworks/base/keystore/java/android/security/keystore/
// KeyGenParameterSpec.java (android16-release). The constructor defaults
// (:288-295, :380-398), the Builder field defaults (:955-982) and the argument
// validation are mirrored, including the one that is easy to miss:
// certificateNotAfter < certificateNotBefore is an IllegalArgumentException at
// build() time, not a silent acceptance.
//
// ⚠ ATTESTATION -- READ BEFORE CHANGING ANYTHING HERE.
// setAttestationChallenge()/getAttestationChallenge() and
// setDevicePropertiesAttestationIncluded()/isDevicePropertiesAttestationIncluded()
// are honest on THIS class: a spec is a REQUEST, and faithfully recording what
// the app asked for is not a claim that we can satisfy it. What Mac.apk must
// never do is MINT the answer. A hardware key attestation is a certificate
// chain, rooted in a Google-provisioned attestation key inside certified secure
// hardware, asserting that the private key never left that hardware. macOS
// cannot produce that chain and a self-signed substitute presented as one would
// be a forgery -- explicitly out of scope for this project. So there is no
// KeyPairGenerator service on the AndroidKeyStore provider at all
// (AndroidKeyStoreProvider registers KeyStore + two KeyGenerator services and
// nothing else): an app that asks for an attested key gets a clean
// NoSuchAlgorithmException from JCA, never a fabricated attestation record.
//
// ⚠ STRONGBOX. isStrongBoxBacked() now returns what was requested instead of
// being discarded, and AndroidKeyStoreKeyGeneratorSpi consequently throws
// StrongBoxUnavailableException for such a request -- the AOSP-documented
// behaviour on hardware without a StrongBox, and what androidx.security / Tink
// already catch and retry without. Silently ignoring the request would be
// telling the app its key is in a secure element when it is a file.
public final class KeyGenParameterSpec implements AlgorithmParameterSpec {
    private static final X500Principal DEFAULT_ATTESTATION_CERT_SUBJECT =
            new X500Principal("CN=Android Keystore Key");
    private static final X500Principal DEFAULT_SELF_SIGNED_CERT_SUBJECT =
            new X500Principal("CN=Fake");
    private static final BigInteger DEFAULT_CERT_SERIAL_NUMBER = new BigInteger("1");
    private static final Date DEFAULT_CERT_NOT_BEFORE = new Date(0L);            // Jan 1 1970
    private static final Date DEFAULT_CERT_NOT_AFTER = new Date(2461449600000L); // Jan 1 2048

    private final String keystoreAlias;
    private final int purposes;
    private final int keySize;
    private final String[] blockModes;
    private final String[] encryptionPaddings;
    private final String[] signaturePaddings;
    private final String[] digests;
    private final boolean digestsSpecified;
    private final Set<String> mgf1Digests;
    private final boolean randomizedEncryptionRequired;
    private final boolean userAuthenticationRequired;
    private final int userAuthenticationValidityDurationSeconds;
    private final int userAuthenticationType;
    private final boolean userAuthenticationValidWhileOnBody;
    private final boolean userPresenceRequired;
    private final boolean userConfirmationRequired;
    private final boolean unlockedDeviceRequired;
    private final boolean invalidatedByBiometricEnrollment;
    private final boolean strongBoxBacked;
    private final int maxUsageCount;
    private final String attestKeyAlias;
    private final byte[] attestationChallenge;
    private final boolean devicePropertiesAttestationIncluded;
    private final Date keyValidityStart;
    private final Date keyValidityForOriginationEnd;
    private final Date keyValidityForConsumptionEnd;
    private final X500Principal certificateSubject;
    private final BigInteger certificateSerialNumber;
    private final Date certificateNotBefore;
    private final Date certificateNotAfter;
    private final AlgorithmParameterSpec algorithmParameterSpec;

    private KeyGenParameterSpec(Builder b) {
        X500Principal subject = b.certificateSubject;
        if (subject == null) {
            subject = (b.attestationChallenge == null)
                    ? DEFAULT_SELF_SIGNED_CERT_SUBJECT : DEFAULT_ATTESTATION_CERT_SUBJECT;
        }
        Date notBefore = b.certificateNotBefore != null
                ? b.certificateNotBefore : DEFAULT_CERT_NOT_BEFORE;
        Date notAfter = b.certificateNotAfter != null
                ? b.certificateNotAfter : DEFAULT_CERT_NOT_AFTER;
        BigInteger serial = b.certificateSerialNumber != null
                ? b.certificateSerialNumber : DEFAULT_CERT_SERIAL_NUMBER;
        if (notAfter.before(notBefore)) {
            throw new IllegalArgumentException("certificateNotAfter < certificateNotBefore");
        }

        this.keystoreAlias = b.keystoreAlias;
        this.purposes = b.purposes;
        this.keySize = b.keySize;
        this.blockModes = cloneOrEmpty(b.blockModes);
        this.encryptionPaddings = cloneOrEmpty(b.encryptionPaddings);
        this.signaturePaddings = cloneOrEmpty(b.signaturePaddings);
        this.digests = cloneOrEmpty(b.digests);
        this.digestsSpecified = b.digests != null;
        this.mgf1Digests = Collections.unmodifiableSet(new LinkedHashSet<>(b.mgf1Digests));
        this.randomizedEncryptionRequired = b.randomizedEncryptionRequired;
        this.userAuthenticationRequired = b.userAuthenticationRequired;
        this.userAuthenticationValidityDurationSeconds =
                b.userAuthenticationValidityDurationSeconds;
        this.userAuthenticationType = b.userAuthenticationType;
        this.userAuthenticationValidWhileOnBody = b.userAuthenticationValidWhileOnBody;
        this.userPresenceRequired = b.userPresenceRequired;
        this.userConfirmationRequired = b.userConfirmationRequired;
        this.unlockedDeviceRequired = b.unlockedDeviceRequired;
        this.invalidatedByBiometricEnrollment = b.invalidatedByBiometricEnrollment;
        this.strongBoxBacked = b.strongBoxBacked;
        this.maxUsageCount = b.maxUsageCount;
        this.attestKeyAlias = b.attestKeyAlias;
        this.attestationChallenge = b.attestationChallenge != null
                ? b.attestationChallenge.clone() : null;
        this.devicePropertiesAttestationIncluded = b.devicePropertiesAttestationIncluded;
        this.keyValidityStart = cloneOrNull(b.keyValidityStart);
        this.keyValidityForOriginationEnd = cloneOrNull(b.keyValidityForOriginationEnd);
        this.keyValidityForConsumptionEnd = cloneOrNull(b.keyValidityForConsumptionEnd);
        this.certificateSubject = subject;
        this.certificateSerialNumber = serial;
        this.certificateNotBefore = (Date) notBefore.clone();
        this.certificateNotAfter = (Date) notAfter.clone();
        this.algorithmParameterSpec = b.algorithmParameterSpec;
    }

    private static String[] cloneOrEmpty(String[] a) {
        return a != null ? a.clone() : new String[0];
    }
    private static Date cloneOrNull(Date d) { return d != null ? (Date) d.clone() : null; }

    public String getKeystoreAlias() { return keystoreAlias; }
    public int getPurposes() { return purposes; }
    public int getKeySize() { return keySize; }
    public String[] getBlockModes() { return blockModes.clone(); }
    public String[] getEncryptionPaddings() { return encryptionPaddings.clone(); }
    public String[] getSignaturePaddings() { return signaturePaddings.clone(); }
    public String[] getDigests() { return digests.clone(); }
    /** AOSP: true only when setDigests() was actually called. */
    public boolean isDigestsSpecified() { return digestsSpecified; }
    public Set<String> getMgf1Digests() { return new HashSet<>(mgf1Digests); }
    public boolean isMgf1DigestsSpecified() { return !mgf1Digests.isEmpty(); }
    public boolean isRandomizedEncryptionRequired() { return randomizedEncryptionRequired; }
    public boolean isUserAuthenticationRequired() { return userAuthenticationRequired; }
    public int getUserAuthenticationValidityDurationSeconds() {
        return userAuthenticationValidityDurationSeconds;
    }
    public int getUserAuthenticationType() { return userAuthenticationType; }
    public boolean isUserAuthenticationValidWhileOnBody() {
        return userAuthenticationValidWhileOnBody;
    }
    public boolean isUserPresenceRequired() { return userPresenceRequired; }
    public boolean isUserConfirmationRequired() { return userConfirmationRequired; }
    public boolean isUnlockedDeviceRequired() { return unlockedDeviceRequired; }
    public boolean isInvalidatedByBiometricEnrollment() { return invalidatedByBiometricEnrollment; }
    /** What the caller REQUESTED. Mac.apk has no StrongBox, so a true here makes
     *  key generation fail with StrongBoxUnavailableException rather than
     *  silently producing a software key the app believes is in a secure
     *  element. See the class header. */
    public boolean isStrongBoxBacked() { return strongBoxBacked; }
    public int getMaxUsageCount() { return maxUsageCount; }
    public String getAttestKeyAlias() { return attestKeyAlias; }
    /** The challenge the caller supplied, echoed back. Mac.apk never produces an
     *  attestation certificate for it -- see the class header. */
    public byte[] getAttestationChallenge() {
        return attestationChallenge != null ? attestationChallenge.clone() : null;
    }
    public boolean isDevicePropertiesAttestationIncluded() {
        return devicePropertiesAttestationIncluded;
    }
    public Date getKeyValidityStart() { return cloneOrNull(keyValidityStart); }
    public Date getKeyValidityForOriginationEnd() {
        return cloneOrNull(keyValidityForOriginationEnd);
    }
    public Date getKeyValidityForConsumptionEnd() {
        return cloneOrNull(keyValidityForConsumptionEnd);
    }
    public X500Principal getCertificateSubject() { return certificateSubject; }
    public BigInteger getCertificateSerialNumber() { return certificateSerialNumber; }
    public Date getCertificateNotBefore() { return (Date) certificateNotBefore.clone(); }
    public Date getCertificateNotAfter() { return (Date) certificateNotAfter.clone(); }
    public AlgorithmParameterSpec getAlgorithmParameterSpec() { return algorithmParameterSpec; }

    public static final class Builder {
        private final String keystoreAlias;
        private final int purposes;
        private int keySize = -1;
        private String[] blockModes;
        private String[] encryptionPaddings;
        private String[] signaturePaddings;
        private String[] digests;
        private Set<String> mgf1Digests = Collections.emptySet();
        private boolean randomizedEncryptionRequired = true;
        private boolean userAuthenticationRequired;
        private int userAuthenticationValidityDurationSeconds = 0;
        private int userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG;
        private boolean userAuthenticationValidWhileOnBody;
        private boolean userPresenceRequired = false;
        private boolean userConfirmationRequired;
        private boolean unlockedDeviceRequired = false;
        private boolean invalidatedByBiometricEnrollment = true;
        private boolean strongBoxBacked = false;
        private int maxUsageCount = KeyProperties.UNRESTRICTED_USAGE_COUNT;
        private String attestKeyAlias = null;
        private byte[] attestationChallenge = null;
        private boolean devicePropertiesAttestationIncluded = false;
        private Date keyValidityStart;
        private Date keyValidityForOriginationEnd;
        private Date keyValidityForConsumptionEnd;
        private X500Principal certificateSubject;
        private BigInteger certificateSerialNumber;
        private Date certificateNotBefore;
        private Date certificateNotAfter;
        private AlgorithmParameterSpec algorithmParameterSpec;

        public Builder(String keystoreAlias, int purposes) {
            this.keystoreAlias = keystoreAlias;
            this.purposes = purposes;
        }

        public Builder setKeySize(int keySize) { this.keySize = keySize; return this; }
        public Builder setBlockModes(String... blockModes) {
            this.blockModes = blockModes; return this;
        }
        public Builder setEncryptionPaddings(String... paddings) {
            this.encryptionPaddings = paddings; return this;
        }
        public Builder setSignaturePaddings(String... paddings) {
            this.signaturePaddings = paddings; return this;
        }
        public Builder setDigests(String... digests) { this.digests = digests; return this; }
        public Builder setMgf1Digests(String... mgf1Digests) {
            this.mgf1Digests = mgf1Digests != null
                    ? new LinkedHashSet<>(java.util.Arrays.asList(mgf1Digests))
                    : Collections.<String>emptySet();
            return this;
        }
        public Builder setRandomizedEncryptionRequired(boolean required) {
            this.randomizedEncryptionRequired = required; return this;
        }
        public Builder setUserAuthenticationRequired(boolean required) {
            this.userAuthenticationRequired = required; return this;
        }
        @Deprecated public Builder setUserAuthenticationValidityDurationSeconds(int seconds) {
            this.userAuthenticationValidityDurationSeconds = seconds; return this;
        }
        /** AOSP validates timeout >= 0 and records the auth-type flags. */
        public Builder setUserAuthenticationParameters(int timeout, int type) {
            if (timeout < 0) throw new IllegalArgumentException("timeout must be 0 or larger");
            this.userAuthenticationValidityDurationSeconds = timeout;
            this.userAuthenticationType = type;
            return this;
        }
        public Builder setUserAuthenticationValidWhileOnBody(boolean remainsValid) {
            this.userAuthenticationValidWhileOnBody = remainsValid; return this;
        }
        public Builder setUserPresenceRequired(boolean required) {
            this.userPresenceRequired = required; return this;
        }
        public Builder setUserConfirmationRequired(boolean required) {
            this.userConfirmationRequired = required; return this;
        }
        public Builder setUnlockedDeviceRequired(boolean required) {
            this.unlockedDeviceRequired = required; return this;
        }
        public Builder setInvalidatedByBiometricEnrollment(boolean invalidate) {
            this.invalidatedByBiometricEnrollment = invalidate; return this;
        }
        public Builder setIsStrongBoxBacked(boolean backed) {
            this.strongBoxBacked = backed; return this;
        }
        /** AOSP accepts UNRESTRICTED_USAGE_COUNT or a positive count, and
         *  throws IllegalArgumentException otherwise. */
        public Builder setMaxUsageCount(int maxUsageCount) {
            if (maxUsageCount == KeyProperties.UNRESTRICTED_USAGE_COUNT || maxUsageCount > 0) {
                this.maxUsageCount = maxUsageCount;
                return this;
            }
            throw new IllegalArgumentException("maxUsageCount is not valid");
        }
        public Builder setAttestKeyAlias(String attestKeyAlias) {
            this.attestKeyAlias = attestKeyAlias; return this;
        }
        /** Records the challenge. Mac.apk produces no attestation certificate
         *  for it; see the class header. */
        public Builder setAttestationChallenge(byte[] attestationChallenge) {
            this.attestationChallenge = attestationChallenge; return this;
        }
        public Builder setDevicePropertiesAttestationIncluded(
                boolean devicePropertiesAttestationIncluded) {
            this.devicePropertiesAttestationIncluded = devicePropertiesAttestationIncluded;
            return this;
        }
        public Builder setKeyValidityStart(Date date) { this.keyValidityStart = date; return this; }
        public Builder setKeyValidityEnd(Date date) {
            setKeyValidityForOriginationEnd(date);
            setKeyValidityForConsumptionEnd(date);
            return this;
        }
        public Builder setKeyValidityForOriginationEnd(Date date) {
            this.keyValidityForOriginationEnd = date; return this;
        }
        public Builder setKeyValidityForConsumptionEnd(Date date) {
            this.keyValidityForConsumptionEnd = date; return this;
        }
        public Builder setCertificateSubject(X500Principal subject) {
            this.certificateSubject = subject; return this;
        }
        public Builder setCertificateSerialNumber(BigInteger serialNumber) {
            this.certificateSerialNumber = serialNumber; return this;
        }
        public Builder setCertificateNotBefore(Date date) {
            this.certificateNotBefore = date; return this;
        }
        public Builder setCertificateNotAfter(Date date) {
            this.certificateNotAfter = date; return this;
        }
        public Builder setAlgorithmParameterSpec(AlgorithmParameterSpec spec) {
            this.algorithmParameterSpec = spec; return this;
        }
        public KeyGenParameterSpec build() { return new KeyGenParameterSpec(this); }
    }
}
