// android.security.identity.NoAuthenticationKeyAvailableException -- Thrown if no dynamic authentication keys are provisioned, or they are expired or use-exhausted.
//
// Part of the Identity Credential API surface. Mac.apk has no secure element
// and therefore no IdentityCredentialStore, so nothing in this package can
// ever be instantiated here -- but the TYPES must exist and link, because an
// app that merely CHECKS for identity-credential support references them at
// class-load time. Faithful mirror of
// identity/java/android/security/identity/NoAuthenticationKeyAvailableException.java (android11-release, API 30).
package android.security.identity;

public class NoAuthenticationKeyAvailableException extends IdentityCredentialException {
    public NoAuthenticationKeyAvailableException(String message) {
        super(message);
    }

    public NoAuthenticationKeyAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
