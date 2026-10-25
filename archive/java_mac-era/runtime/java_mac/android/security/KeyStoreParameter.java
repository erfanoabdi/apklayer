// android.security.KeyStoreParameter
//
// The deprecated (pre-M) AndroidKeyStore protection parameter for importing
// symmetric/private keys, superseded by KeyProtection. Older libraries build
// one via its Builder and pass it to KeyStore.setEntry, so the value type +
// Builder must resolve. Pure data holder. Mirrors AOSP
// android.security.KeyStoreParameter public API.
package android.security;

import android.content.Context;

import java.security.KeyStore;

public final class KeyStoreParameter implements KeyStore.ProtectionParameter {

    private final boolean mEncryptionRequired;

    private KeyStoreParameter(boolean encryptionRequired) {
        mEncryptionRequired = encryptionRequired;
    }

    public boolean isEncryptionRequired() { return mEncryptionRequired; }

    public static final class Builder {
        private boolean mEncryptionRequired;

        public Builder(Context context) {}

        public Builder setEncryptionRequired(boolean required) {
            mEncryptionRequired = required; return this;
        }

        public KeyStoreParameter build() {
            return new KeyStoreParameter(mEncryptionRequired);
        }
    }
}
