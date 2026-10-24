// android.security.keystore.KeyNotYetValidException -- the key's validity period has not STARTED.
// AOSP source: frameworks/base/keystore/java/android/security/keystore/
// KeyNotYetValidException.java. Extends java.security.InvalidKeyException, so it
// surfaces from Cipher/Signature/Mac init and callers catch it there.
//
// Mac.apk's keystore is file-backed with no lock-screen binding, so this is
// never thrown from our own code -- but the class must exist for an app's
// catch block to link, and for an app to construct one in its own wrappers.
package android.security.keystore;

import java.security.InvalidKeyException;

public class KeyNotYetValidException extends InvalidKeyException {
    public KeyNotYetValidException() { super("the key's validity period has not STARTED"); }
    public KeyNotYetValidException(String message) { super(message); }
    public KeyNotYetValidException(String message, Throwable cause) { super(message, cause); }
}
