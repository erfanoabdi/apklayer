// android.security.identity.AccessControlProfileId -- a numeric handle for an
// AccessControlProfile within one credential.
//
// A REAL implementation: it is a plain int wrapper with no device dependency,
// so there is nothing to degrade. Apps build these before ever asking whether
// a credential store exists.
//
// Mirrors identity/java/android/security/identity/AccessControlProfileId.java
// (android11-release, API 30).
package android.security.identity;

public class AccessControlProfileId {
    private int mId = 0;

    public AccessControlProfileId(int id) {
        mId = id;
    }

    public int getId() {
        return this.mId;
    }
}
