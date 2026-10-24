// android.security.KeyChainException -- thrown by KeyChain.getPrivateKey /
// getCertificateChain when the system keychain service errors out.
// AOSP: frameworks/base/keystore/java/android/security/KeyChainException.java
package android.security;

public class KeyChainException extends Exception {
    public KeyChainException() { super(); }
    public KeyChainException(String detailMessage) { super(detailMessage); }
    public KeyChainException(String message, Throwable cause) { super(message, cause); }
    public KeyChainException(Throwable cause) { super(cause); }
}
