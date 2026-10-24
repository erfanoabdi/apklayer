// android.security.keystore.UserPresenceUnavailableException -- the key requires a hardware test of user presence that is unavailable.
// AOSP source: frameworks/base/keystore/java/android/security/keystore/
// UserPresenceUnavailableException.java. Extends java.security.InvalidKeyException, so it
// surfaces from Cipher/Signature/Mac init and callers catch it there.
//
// Mac.apk's keystore is file-backed with no lock-screen binding, so this is
// never thrown from our own code -- but the class must exist for an app's
// catch block to link, and for an app to construct one in its own wrappers.
package android.security.keystore;

import java.security.InvalidKeyException;

public class UserPresenceUnavailableException extends InvalidKeyException {
    public UserPresenceUnavailableException() { super("the key requires a hardware test of user presence that is unavailable"); }
    public UserPresenceUnavailableException(String message) { super(message); }
    public UserPresenceUnavailableException(String message, Throwable cause) { super(message, cause); }
}
