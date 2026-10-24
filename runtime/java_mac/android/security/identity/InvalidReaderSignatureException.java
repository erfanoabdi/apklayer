// android.security.identity.InvalidReaderSignatureException -- Thrown if the reader signature is invalid, carries no certificate chain, or fails to validate.
//
// Part of the Identity Credential API surface. Mac.apk has no secure element
// and therefore no IdentityCredentialStore, so nothing in this package can
// ever be instantiated here -- but the TYPES must exist and link, because an
// app that merely CHECKS for identity-credential support references them at
// class-load time. Faithful mirror of
// identity/java/android/security/identity/InvalidReaderSignatureException.java (android11-release, API 30).
package android.security.identity;

public class InvalidReaderSignatureException extends IdentityCredentialException {
    public InvalidReaderSignatureException(String message) {
        super(message);
    }

    public InvalidReaderSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
