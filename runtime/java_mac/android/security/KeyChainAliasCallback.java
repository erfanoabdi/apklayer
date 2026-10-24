// android.security.KeyChainAliasCallback -- the callback an app passes to
// KeyChain.choosePrivateKeyAlias to receive the alias the user selected (or
// null if the user cancelled / no system credential UI exists).
// AOSP: frameworks/base/keystore/java/android/security/KeyChainAliasCallback.java
package android.security;

public interface KeyChainAliasCallback {
    void alias(String alias);
}
