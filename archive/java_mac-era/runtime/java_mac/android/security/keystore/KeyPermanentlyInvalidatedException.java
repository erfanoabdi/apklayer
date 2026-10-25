// android.security.keystore.KeyPermanentlyInvalidatedException -- the key is irrecoverably invalid (the lock screen or biometric enrolment it was bound to changed).
// AOSP source: frameworks/base/keystore/java/android/security/keystore/
// KeyPermanentlyInvalidatedException.java. Extends java.security.InvalidKeyException, so it
// surfaces from Cipher/Signature/Mac init and callers catch it there.
//
// Mac.apk's keystore is file-backed with no lock-screen binding, so this is
// never thrown from our own code -- but the class must exist for an app's
// catch block to link, and for an app to construct one in its own wrappers.
package android.security.keystore;

import java.security.InvalidKeyException;

public class KeyPermanentlyInvalidatedException extends InvalidKeyException {
    public KeyPermanentlyInvalidatedException() { super("the key is irrecoverably invalid (the lock screen or biometric enrolment it was bound to changed)"); }
    public KeyPermanentlyInvalidatedException(String message) { super(message); }
    public KeyPermanentlyInvalidatedException(String message, Throwable cause) { super(message, cause); }
}
