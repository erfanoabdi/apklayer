package android.credentials;
/** Thrown/delivered when a get-credential call fails. API 34.
 *  Source: frameworks/base/core/java/android/credentials/GetCredentialException.java */
public class GetCredentialException extends Exception {
    public static final String TYPE_NO_CREDENTIAL =
            "android.credentials.GetCredentialException.TYPE_NO_CREDENTIAL";
    public static final String TYPE_USER_CANCELED =
            "android.credentials.GetCredentialException.TYPE_USER_CANCELED";
    public static final String TYPE_INTERRUPTED =
            "android.credentials.GetCredentialException.TYPE_INTERRUPTED";
    public static final String TYPE_UNKNOWN =
            "android.credentials.GetCredentialException.TYPE_UNKNOWN";
    private final String mType;
    public GetCredentialException(String type) { super(type); mType = type; }
    public GetCredentialException(String type, String message) { super(message); mType = type; }
    public GetCredentialException(String type, String message, Throwable cause) {
        super(message, cause); mType = type;
    }
    public GetCredentialException(String type, Throwable cause) { super(cause); mType = type; }
    public String getType() { return mType; }
}
