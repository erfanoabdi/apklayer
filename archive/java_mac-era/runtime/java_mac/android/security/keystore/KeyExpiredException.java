// android.security.keystore.KeyExpiredException -- the key's validity period has ENDED.
// AOSP source: frameworks/base/keystore/java/android/security/keystore/
// KeyExpiredException.java. Extends java.security.InvalidKeyException, so it
// surfaces from Cipher/Signature/Mac init and callers catch it there.
//
// Mac.apk's keystore is file-backed with no lock-screen binding, so this is
// never thrown from our own code -- but the class must exist for an app's
// catch block to link, and for an app to construct one in its own wrappers.
package android.security.keystore;

import java.security.InvalidKeyException;

public class KeyExpiredException extends InvalidKeyException {
    public KeyExpiredException() { super("the key's validity period has ENDED"); }
    public KeyExpiredException(String message) { super(message); }
    public KeyExpiredException(String message, Throwable cause) { super(message, cause); }
}
