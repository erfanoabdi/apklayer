// android.security.identity.AlreadyPersonalizedException -- Thrown if a credential with the given name already exists.
//
// Part of the Identity Credential API surface. Mac.apk has no secure element
// and therefore no IdentityCredentialStore, so nothing in this package can
// ever be instantiated here -- but the TYPES must exist and link, because an
// app that merely CHECKS for identity-credential support references them at
// class-load time. Faithful mirror of
// identity/java/android/security/identity/AlreadyPersonalizedException.java (android11-release, API 30).
package android.security.identity;

public class AlreadyPersonalizedException extends IdentityCredentialException {
    public AlreadyPersonalizedException(String message) {
        super(message);
    }

    public AlreadyPersonalizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
