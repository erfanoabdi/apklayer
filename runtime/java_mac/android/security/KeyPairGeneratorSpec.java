// android.security.KeyPairGeneratorSpec
//
// The deprecated (pre-M) AndroidKeyStore key-pair generation spec, superseded
// by android.security.keystore.KeyGenParameterSpec. Older libraries still build
// one via its Builder and pass it to KeyPairGenerator.initialize, so the value
// type + full Builder must resolve. Pure data holder. Mirrors AOSP
// android.security.KeyPairGeneratorSpec public API.
package android.security;

import android.content.Context;

import java.math.BigInteger;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

public final class KeyPairGeneratorSpec implements AlgorithmParameterSpec {

    private final Context mContext;
    private final String mKeystoreAlias;
    private final String mKeyType;
    private final int mKeySize;
    private final AlgorithmParameterSpec mSpec;
    private final X500Principal mSubjectDN;
    private final BigInteger mSerialNumber;
    private final Date mStartDate;
    private final Date mEndDate;
    private final int mFlags;

    private KeyPairGeneratorSpec(Context context, String keyStoreAlias, String keyType,
            int keySize, AlgorithmParameterSpec spec, X500Principal subjectDN,
            BigInteger serialNumber, Date startDate, Date endDate, int flags) {
        mContext = context;
        mKeystoreAlias = keyStoreAlias;
        mKeyType = keyType;
        mKeySize = keySize;
        mSpec = spec;
        mSubjectDN = subjectDN;
        mSerialNumber = serialNumber;
        mStartDate = startDate;
        mEndDate = endDate;
        mFlags = flags;
    }

    public Context getContext() { return mContext; }
    public String getKeystoreAlias() { return mKeystoreAlias; }
    public String getKeyType() { return mKeyType; }
    public int getKeySize() { return mKeySize; }
    public AlgorithmParameterSpec getAlgorithmParameterSpec() { return mSpec; }
    public X500Principal getSubjectDN() { return mSubjectDN; }
    public BigInteger getSerialNumber() { return mSerialNumber; }
    public Date getStartDate() { return mStartDate; }
    public Date getEndDate() { return mEndDate; }
    public int getFlags() { return mFlags; }
    public boolean isEncryptionRequired() { return false; }

    public static final class Builder {
        private final Context mContext;
        private String mKeystoreAlias;
        private String mKeyType;
        private int mKeySize = -1;
        private AlgorithmParameterSpec mSpec;
        private X500Principal mSubjectDN;
        private BigInteger mSerialNumber;
        private Date mStartDate;
        private Date mEndDate;
        private int mFlags;

        public Builder(Context context) { mContext = context; }

        public Builder setAlias(String alias) { mKeystoreAlias = alias; return this; }
        public Builder setKeyType(String keyType) { mKeyType = keyType; return this; }
        public Builder setKeySize(int keySize) { mKeySize = keySize; return this; }
        public Builder setAlgorithmParameterSpec(AlgorithmParameterSpec spec) {
            mSpec = spec; return this;
        }
        public Builder setSubject(X500Principal subject) { mSubjectDN = subject; return this; }
        public Builder setSerialNumber(BigInteger serialNumber) {
            mSerialNumber = serialNumber; return this;
        }
        public Builder setStartDate(Date startDate) { mStartDate = startDate; return this; }
        public Builder setEndDate(Date endDate) { mEndDate = endDate; return this; }
        public Builder setEncryptionRequired() { mFlags |= 1; return this; }

        public KeyPairGeneratorSpec build() {
            return new KeyPairGeneratorSpec(mContext, mKeystoreAlias, mKeyType, mKeySize,
                    mSpec, mSubjectDN, mSerialNumber, mStartDate, mEndDate, mFlags);
        }
    }
}
