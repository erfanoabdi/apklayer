// android.security.keystore.WrappedKeyEntry -- a KeyStore.Entry describing a key
// that arrives WRAPPED (encrypted under another keystore key) and is meant to be
// unwrapped inside KeyMint so the plaintext never enters the app process.
//
// AOSP source: frameworks/base/keystore/java/android/security/keystore/
// WrappedKeyEntry.java (android16-release). Pure value holder: the wrapped
// bytes, the alias of the wrapping key, the unwrap transformation, and the
// AlgorithmParameterSpec for it.
//
// ⚠ Mac.apk cannot perform secure key import. The whole point of the mechanism
// is that the unwrap happens inside secure hardware; doing it in the JVM would
// expose the plaintext to the very process the feature exists to keep it from,
// while letting the app believe otherwise. So AndroidKeyStoreSpi refuses a
// WrappedKeyEntry with SecureKeyImportUnavailableException -- the exception
// AOSP itself defines for a device that lacks the feature -- rather than
// silently unwrapping. The class exists so that an app which BUILDS one (and
// catches the refusal, as the API requires) links and runs.
package android.security.keystore;

import java.security.KeyStore;
import java.security.spec.AlgorithmParameterSpec;

public class WrappedKeyEntry implements KeyStore.Entry {

    private final byte[] mWrappedKeyBytes;
    private final String mWrappingKeyAlias;
    private final String mTransformation;
    private final AlgorithmParameterSpec mAlgorithmParameterSpec;

    public WrappedKeyEntry(byte[] wrappedKeyBytes, String wrappingKeyAlias, String transformation,
            AlgorithmParameterSpec algorithmParameterSpec) {
        mWrappedKeyBytes = wrappedKeyBytes;
        mWrappingKeyAlias = wrappingKeyAlias;
        mTransformation = transformation;
        mAlgorithmParameterSpec = algorithmParameterSpec;
    }

    public byte[] getWrappedKeyBytes() { return mWrappedKeyBytes; }
    public String getWrappingKeyAlias() { return mWrappingKeyAlias; }
    public String getTransformation() { return mTransformation; }
    public AlgorithmParameterSpec getAlgorithmParameterSpec() { return mAlgorithmParameterSpec; }
}
