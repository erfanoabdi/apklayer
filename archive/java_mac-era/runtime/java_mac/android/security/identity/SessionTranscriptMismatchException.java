// android.security.identity.SessionTranscriptMismatchException -- Thrown if the session transcript passed to getEntries() differs from a previous call.
//
// Part of the Identity Credential API surface. Mac.apk has no secure element
// and therefore no IdentityCredentialStore, so nothing in this package can
// ever be instantiated here -- but the TYPES must exist and link, because an
// app that merely CHECKS for identity-credential support references them at
// class-load time. Faithful mirror of
// identity/java/android/security/identity/SessionTranscriptMismatchException.java (android11-release, API 30).
package android.security.identity;

public class SessionTranscriptMismatchException extends IdentityCredentialException {
    public SessionTranscriptMismatchException(String message) {
        super(message);
    }

    public SessionTranscriptMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
