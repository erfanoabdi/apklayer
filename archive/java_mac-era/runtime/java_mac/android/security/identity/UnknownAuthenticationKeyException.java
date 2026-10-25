// android.security.identity.UnknownAuthenticationKeyException -- Thrown if the given authentication key is not recognized.
//
// Part of the Identity Credential API surface. Mac.apk has no secure element
// and therefore no IdentityCredentialStore, so nothing in this package can
// ever be instantiated here -- but the TYPES must exist and link, because an
// app that merely CHECKS for identity-credential support references them at
// class-load time. Faithful mirror of
// identity/java/android/security/identity/UnknownAuthenticationKeyException.java (android11-release, API 30).
package android.security.identity;

public class UnknownAuthenticationKeyException extends IdentityCredentialException {
    public UnknownAuthenticationKeyException(String message) {
        super(message);
    }

    public UnknownAuthenticationKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
