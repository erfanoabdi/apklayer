package android.credentials;
/** Thrown/delivered when a create-credential call fails. API 34.
 *  Source: frameworks/base/core/java/android/credentials/CreateCredentialException.java */
public class CreateCredentialException extends Exception {
    public static final String TYPE_NO_CREATE_OPTIONS =
            "android.credentials.CreateCredentialException.TYPE_NO_CREATE_OPTIONS";
    public static final String TYPE_USER_CANCELED =
            "android.credentials.CreateCredentialException.TYPE_USER_CANCELED";
    public static final String TYPE_INTERRUPTED =
            "android.credentials.CreateCredentialException.TYPE_INTERRUPTED";
    public static final String TYPE_UNKNOWN =
            "android.credentials.CreateCredentialException.TYPE_UNKNOWN";
    private final String mType;
    public CreateCredentialException(String type) { super(type); mType = type; }
    public CreateCredentialException(String type, String message) { super(message); mType = type; }
    public CreateCredentialException(String type, String message, Throwable cause) {
        super(message, cause); mType = type;
    }
    public CreateCredentialException(String type, Throwable cause) { super(cause); mType = type; }
    public String getType() { return mType; }
}
