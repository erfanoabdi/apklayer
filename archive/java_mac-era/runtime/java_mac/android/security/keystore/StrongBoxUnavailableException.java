// android.security.keystore.StrongBoxUnavailableException -- a StrongBox-backed key was requested and this device has no StrongBox.
// AOSP source: frameworks/base/keystore/java/android/security/keystore/
// StrongBoxUnavailableException.java. Extends java.security.ProviderException (unchecked).
package android.security.keystore;

import java.security.ProviderException;

public class StrongBoxUnavailableException extends ProviderException {
    public StrongBoxUnavailableException() { super("a StrongBox-backed key was requested and this device has no StrongBox"); }
    public StrongBoxUnavailableException(String message) { super(message); }
    public StrongBoxUnavailableException(String message, Throwable cause) { super(message, cause); }
    public StrongBoxUnavailableException(Throwable cause) { super(cause); }
}
