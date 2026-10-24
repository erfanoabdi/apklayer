package android.content.pm;

/** android.content.pm.SigningInfoException (API 36) -- thrown by
 *  PackageManager.getVerifiedSigningInfo when an APK's signature cannot be
 *  verified. Carries the platform's own parse error code.
 *
 *  Mirrors frameworks/base/core/java/android/content/pm/SigningInfoException.java
 *  (android16-release): a checked Exception with one public accessor. The
 *  constructor is @hide upstream (only the platform verifier throws it); public
 *  here so this tree can construct one. */
public class SigningInfoException extends Exception {
    private final int mCode;

    public SigningInfoException(int code, String message, Throwable cause) {
        super(message, cause);
        mCode = code;
    }

    /** AOSP SigningInfoException.getCode(): "the error code", one of the
     *  PackageManager.INSTALL_PARSE_FAILED_* values. */
    public int getCode() { return mCode; }
}
