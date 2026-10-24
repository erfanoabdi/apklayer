// android.security.KeyStoreException -- the checked exception the Keystore 2.0
// APIs raise, carrying a PUBLIC error code plus three classification predicates
// that tell a caller whether to retry, re-authenticate, or give up.
//
// AOSP source: frameworks/base/keystore/java/android/security/KeyStoreException.java
// (android16-release). The public API is the ERROR_* / RETRY_* constants,
// getNumericErrorCode(), isTransientFailure(), requiresUserAuthentication(),
// isSystemError() and getRetryPolicy(); the constructors are @hide.
//
// IMPLEMENTATION NOTE. AOSP stores the INTERNAL KeyMint/Keystore error code and
// maps it to a public code through a ~200-entry table
// (sErrorCodeToFailureInfo, KeyStoreException.java:481-692). Mac.apk has no
// KeyMint, so there are no internal codes to map: we store the PUBLIC code
// directly and classify it with the same indicator bits AOSP assigns to that
// public code in its table. Observably identical for every caller, which only
// ever sees the public code and the four predicates.
package android.security;

public class KeyStoreException extends Exception {

    public static final int ERROR_OTHER = 1;
    public static final int ERROR_USER_AUTHENTICATION_REQUIRED = 2;
    public static final int ERROR_KEYSTORE_UNINITIALIZED = 3;
    public static final int ERROR_INTERNAL_SYSTEM_ERROR = 4;
    public static final int ERROR_PERMISSION_DENIED = 5;
    public static final int ERROR_KEY_DOES_NOT_EXIST = 6;
    public static final int ERROR_KEY_CORRUPTED = 7;
    public static final int ERROR_ID_ATTESTATION_FAILURE = 8;
    public static final int ERROR_ATTESTATION_CHALLENGE_TOO_LARGE = 9;
    public static final int ERROR_KEYMINT_FAILURE = 10;
    public static final int ERROR_KEYSTORE_FAILURE = 11;
    public static final int ERROR_UNIMPLEMENTED = 12;
    public static final int ERROR_INCORRECT_USAGE = 13;
    public static final int ERROR_KEY_NOT_TEMPORALLY_VALID = 14;
    public static final int ERROR_KEY_OPERATION_EXPIRED = 15;
    public static final int ERROR_ATTESTATION_KEYS_UNAVAILABLE = 16;

    public static final int RETRY_NEVER = 1;
    public static final int RETRY_WITH_EXPONENTIAL_BACKOFF = 2;
    public static final int RETRY_WHEN_CONNECTIVITY_AVAILABLE = 3;
    public static final int RETRY_AFTER_NEXT_REBOOT = 4;

    // AOSP's indicator bits (KeyStoreException.java:250-256).
    private static final int IS_SYSTEM_ERROR = 1 << 1;
    private static final int IS_TRANSIENT_ERROR = 1 << 2;
    private static final int REQUIRES_USER_AUTHENTICATION = 1 << 3;

    private final int mErrorCode;

    /** @hide on AOSP; kept public so framework code can raise one. */
    public KeyStoreException(int errorCode, String message) {
        super(message);
        mErrorCode = errorCode;
    }

    /** @hide on AOSP. */
    public KeyStoreException(int errorCode, String message, String keystoreErrorMessage) {
        super(message + " (internal Keystore code: " + errorCode + " message: "
                + keystoreErrorMessage + ")");
        mErrorCode = errorCode;
    }

    /** @hide on AOSP -- the internal code. Here it is the same value as the
     *  public one, because there is no internal KeyMint layer to differ from. */
    public int getErrorCode() { return mErrorCode; }

    public int getNumericErrorCode() { return mErrorCode; }

    private int indicators() {
        switch (mErrorCode) {
            case ERROR_USER_AUTHENTICATION_REQUIRED:  return REQUIRES_USER_AUTHENTICATION;
            case ERROR_KEYSTORE_UNINITIALIZED:        return IS_SYSTEM_ERROR;
            case ERROR_INTERNAL_SYSTEM_ERROR:         return IS_SYSTEM_ERROR;
            case ERROR_ID_ATTESTATION_FAILURE:        return IS_SYSTEM_ERROR;
            case ERROR_UNIMPLEMENTED:                 return IS_SYSTEM_ERROR;
            case ERROR_ATTESTATION_KEYS_UNAVAILABLE:  return IS_SYSTEM_ERROR;
            default:                                  return 0;
        }
    }

    public boolean isTransientFailure() { return (indicators() & IS_TRANSIENT_ERROR) != 0; }

    public boolean requiresUserAuthentication() {
        return (indicators() & REQUIRES_USER_AUTHENTICATION) != 0;
    }

    public boolean isSystemError() { return (indicators() & IS_SYSTEM_ERROR) != 0; }

    public int getRetryPolicy() {
        return (indicators() & IS_TRANSIENT_ERROR) != 0
                ? RETRY_WITH_EXPONENTIAL_BACKOFF : RETRY_NEVER;
    }

    @Override public String toString() {
        return super.toString() + " (public error code: " + mErrorCode + ")";
    }
}
