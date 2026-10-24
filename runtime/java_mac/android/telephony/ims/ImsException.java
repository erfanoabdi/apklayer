package android.telephony.ims;

/**
 * Thrown when an IMS operation cannot be carried out.
 *
 * Mac.apk has no IMS stack, so any app that reaches an IMS API gets
 * CODE_ERROR_UNSUPPORTED_OPERATION -- AOSP's own code for "the device does not
 * support IMS", which is the truthful answer for a machine with no radio.
 *
 * Source: frameworks/base/telephony/java/android/telephony/ims/ImsException.java
 */
public final class ImsException extends Exception {

    public static final int CODE_ERROR_UNSPECIFIED = 0;
    public static final int CODE_ERROR_SERVICE_UNAVAILABLE = 1;
    public static final int CODE_ERROR_UNSUPPORTED_OPERATION = 2;
    public static final int CODE_ERROR_INVALID_SUBSCRIPTION = 3;

    private final int mCode;

    public ImsException(String message) {
        super(getMessage(message, CODE_ERROR_UNSPECIFIED));
        mCode = CODE_ERROR_UNSPECIFIED;
    }

    public ImsException(String message, int code) {
        super(getMessage(message, code));
        mCode = code;
    }

    public ImsException(String message, int code, Throwable cause) {
        super(getMessage(message, code), cause);
        mCode = code;
    }

    public int getCode() { return mCode; }

    private static String getMessage(String message, int code) {
        StringBuilder builder = new StringBuilder();
        if (message != null) {
            builder.append(message);
            builder.append(" (code: ");
            builder.append(code);
            builder.append(")");
            return builder.toString();
        }
        return "code: " + code;
    }
}
