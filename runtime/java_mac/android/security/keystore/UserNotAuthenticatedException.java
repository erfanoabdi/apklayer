// android.security.keystore.UserNotAuthenticatedException -- the key requires the user to have authenticated recently and they have not.
// AOSP source: frameworks/base/keystore/java/android/security/keystore/
// UserNotAuthenticatedException.java. Extends java.security.InvalidKeyException, so it
// surfaces from Cipher/Signature/Mac init and callers catch it there.
//
// Mac.apk's keystore is file-backed with no lock-screen binding, so this is
// never thrown from our own code -- but the class must exist for an app's
// catch block to link, and for an app to construct one in its own wrappers.
package android.security.keystore;

import java.security.InvalidKeyException;

public class UserNotAuthenticatedException extends InvalidKeyException {
    public UserNotAuthenticatedException() { super("the key requires the user to have authenticated recently and they have not"); }
    public UserNotAuthenticatedException(String message) { super(message); }
    public UserNotAuthenticatedException(String message, Throwable cause) { super(message, cause); }
}
