// android.security.keystore.BackendBusyException -- raised when the KeyMint
// backend has no free operation slot; the caller should retry after the hinted
// back-off. AOSP source: frameworks/base/keystore/java/android/security/
// keystore/BackendBusyException.java. Unchecked (ProviderException), so a JCA
// caller sees it through Cipher/Signature init.
//
// Mac.apk never raises this -- there is no KeyMint and no slot pressure -- but
// apps CATCH it around every keystore operation, and a missing class makes that
// catch block fail to link.
package android.security.keystore;

import java.security.ProviderException;

public class BackendBusyException extends ProviderException {
    private final long mBackOffHintMillis;

    public BackendBusyException(long backOffHintMillis) {
        super("Keystore backend busy");
        mBackOffHintMillis = backOffHintMillis;
    }
    public BackendBusyException(long backOffHintMillis, String message) {
        super(message);
        mBackOffHintMillis = backOffHintMillis;
    }
    public BackendBusyException(long backOffHintMillis, String message, Throwable cause) {
        super(message, cause);
        mBackOffHintMillis = backOffHintMillis;
    }

    /** Suggested wait, in milliseconds, before retrying. */
    public long getBackOffHintMillis() { return mBackOffHintMillis; }
}
