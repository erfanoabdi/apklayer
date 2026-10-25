package android.credentials;
/** Thrown/delivered when clear-credential-state fails. API 34.
 *  Source: frameworks/base/core/java/android/credentials/ClearCredentialStateException.java
 *  All four AOSP constructors are present; a caller that rethrows with a cause
 *  (the common wrapper idiom in credential-manager SDKs) needs the two
 *  Throwable-taking forms to resolve. */
public class ClearCredentialStateException extends Exception {
    public static final String TYPE_UNKNOWN =
            "android.credentials.ClearCredentialStateException.TYPE_UNKNOWN";
    private final String mType;
    public ClearCredentialStateException(String type) { super(type); mType = type; }
    public ClearCredentialStateException(String type, String message) { super(message); mType = type; }
    public ClearCredentialStateException(String type, String message, Throwable cause) {
        super(message, cause); mType = type;
    }
    public ClearCredentialStateException(String type, Throwable cause) {
        super(cause); mType = type;
    }
    public String getType() { return mType; }
}
