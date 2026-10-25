// android.security.identity.CredentialDataRequest -- what a reader is asking a
// PresentationSession for.
//
// A REAL implementation: a pure builder-populated value object, no device
// dependency. The three boolean DEFAULTS are AOSP's and are load-bearing, so
// they are copied rather than zero-initialized: allowUsingExhaustedKeys=true,
// allowUsingExpiredKeys=false, incrementUseCount=true. Defaulting them to
// Java's `false` would silently change an app's request semantics.
//
// LinkedHashMap for the same reason as PersonalizationData: the CBOR request
// encoding is order-sensitive.
//
// Mirrors identity/java/android/security/identity/CredentialDataRequest.java
// (android13-release, API 33).
package android.security.identity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class CredentialDataRequest {
    CredentialDataRequest() {}

    public Map<String, Collection<String>> getDeviceSignedEntriesToRequest() {
        return mDeviceSignedEntriesToRequest;
    }

    public Map<String, Collection<String>> getIssuerSignedEntriesToRequest() {
        return mIssuerSignedEntriesToRequest;
    }

    public boolean isAllowUsingExhaustedKeys() {
        return mAllowUsingExhaustedKeys;
    }

    public boolean isAllowUsingExpiredKeys() {
        return mAllowUsingExpiredKeys;
    }

    public boolean isIncrementUseCount() {
        return mIncrementUseCount;
    }

    public byte[] getRequestMessage() {
        return mRequestMessage;
    }

    public byte[] getReaderSignature() {
        return mReaderSignature;
    }

    Map<String, Collection<String>> mDeviceSignedEntriesToRequest = new LinkedHashMap<>();
    Map<String, Collection<String>> mIssuerSignedEntriesToRequest = new LinkedHashMap<>();
    boolean mAllowUsingExhaustedKeys = true;
    boolean mAllowUsingExpiredKeys = false;
    boolean mIncrementUseCount = true;
    byte[] mRequestMessage = null;
    byte[] mReaderSignature = null;

    public static final class Builder {
        private CredentialDataRequest mData;

        public Builder() {
            mData = new CredentialDataRequest();
        }

        public Builder setDeviceSignedEntriesToRequest(
                Map<String, Collection<String>> entriesToRequest) {
            mData.mDeviceSignedEntriesToRequest = entriesToRequest;
            return this;
        }

        public Builder setIssuerSignedEntriesToRequest(
                Map<String, Collection<String>> entriesToRequest) {
            mData.mIssuerSignedEntriesToRequest = entriesToRequest;
            return this;
        }

        public Builder setAllowUsingExhaustedKeys(boolean allowUsingExhaustedKeys) {
            mData.mAllowUsingExhaustedKeys = allowUsingExhaustedKeys;
            return this;
        }

        public Builder setAllowUsingExpiredKeys(boolean allowUsingExpiredKeys) {
            mData.mAllowUsingExpiredKeys = allowUsingExpiredKeys;
            return this;
        }

        public Builder setIncrementUseCount(boolean incrementUseCount) {
            mData.mIncrementUseCount = incrementUseCount;
            return this;
        }

        public Builder setRequestMessage(byte[] requestMessage) {
            mData.mRequestMessage = requestMessage;
            return this;
        }

        public Builder setReaderSignature(byte[] readerSignature) {
            mData.mReaderSignature = readerSignature;
            return this;
        }

        public CredentialDataRequest build() {
            return mData;
        }
    }
}
