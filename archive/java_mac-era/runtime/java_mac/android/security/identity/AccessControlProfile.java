// android.security.identity.AccessControlProfile -- the conditions under which
// a credential data element may be released (user auth required, auth timeout,
// and/or a reader certificate that must have signed the request).
//
// A REAL implementation: it is a builder-populated value object with no device
// dependency. Note the AOSP shape is unusual and is copied deliberately --
// the class has a PRIVATE constructor and PACKAGE-PRIVATE getters, so the only
// public way in is Builder and the only consumer is the package itself.
// Widening the getters would add surface android.jar does not expose.
//
// AOSP default worth preserving: mUserAuthenticationRequired starts TRUE, so a
// profile built without calling setUserAuthenticationRequired is the strict
// one. Defaulting it to false would silently weaken an app's access policy.
//
// Mirrors identity/java/android/security/identity/AccessControlProfile.java
// (android11-release, API 30).
package android.security.identity;

import java.security.cert.X509Certificate;

public class AccessControlProfile {
    private AccessControlProfileId mAccessControlProfileId = new AccessControlProfileId(0);
    private X509Certificate mReaderCertificate = null;
    private boolean mUserAuthenticationRequired = true;
    private long mUserAuthenticationTimeout = 0;

    private AccessControlProfile() {
    }

    AccessControlProfileId getAccessControlProfileId() {
        return mAccessControlProfileId;
    }

    long getUserAuthenticationTimeout() {
        return mUserAuthenticationTimeout;
    }

    boolean isUserAuthenticationRequired() {
        return mUserAuthenticationRequired;
    }

    X509Certificate getReaderCertificate() {
        return mReaderCertificate;
    }

    public static final class Builder {
        private AccessControlProfile mProfile;

        public Builder(AccessControlProfileId accessControlProfileId) {
            mProfile = new AccessControlProfile();
            mProfile.mAccessControlProfileId = accessControlProfileId;
        }

        public Builder setUserAuthenticationRequired(boolean userAuthenticationRequired) {
            mProfile.mUserAuthenticationRequired = userAuthenticationRequired;
            return this;
        }

        public Builder setUserAuthenticationTimeout(long userAuthenticationTimeoutMillis) {
            mProfile.mUserAuthenticationTimeout = userAuthenticationTimeoutMillis;
            return this;
        }

        public Builder setReaderCertificate(X509Certificate readerCertificate) {
            mProfile.mReaderCertificate = readerCertificate;
            return this;
        }

        public AccessControlProfile build() {
            return mProfile;
        }
    }
}
