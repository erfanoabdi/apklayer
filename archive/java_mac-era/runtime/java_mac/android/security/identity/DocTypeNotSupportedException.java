// android.security.identity.DocTypeNotSupportedException -- Thrown if the given document type is not supported by the store.
//
// Part of the Identity Credential API surface. Mac.apk has no secure element
// and therefore no IdentityCredentialStore, so nothing in this package can
// ever be instantiated here -- but the TYPES must exist and link, because an
// app that merely CHECKS for identity-credential support references them at
// class-load time. Faithful mirror of
// identity/java/android/security/identity/DocTypeNotSupportedException.java (android11-release, API 30).
package android.security.identity;

public class DocTypeNotSupportedException extends IdentityCredentialException {
    public DocTypeNotSupportedException(String message) {
        super(message);
    }

    public DocTypeNotSupportedException(String message, Throwable cause) {
        super(message, cause);
    }
}
