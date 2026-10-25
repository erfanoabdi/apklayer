// android.security.keystore.SecureKeyImportUnavailableException -- secure key import (a wrapped key unwrapped inside KeyMint) is not supported by this device.
// AOSP source: frameworks/base/keystore/java/android/security/keystore/
// SecureKeyImportUnavailableException.java. Extends java.security.ProviderException (unchecked).
package android.security.keystore;

import java.security.ProviderException;

public class SecureKeyImportUnavailableException extends ProviderException {
    public SecureKeyImportUnavailableException() { super("secure key import (a wrapped key unwrapped inside KeyMint) is not supported by this device"); }
    public SecureKeyImportUnavailableException(String message) { super(message); }
    public SecureKeyImportUnavailableException(String message, Throwable cause) { super(message, cause); }
    public SecureKeyImportUnavailableException(Throwable cause) { super(cause); }
}
