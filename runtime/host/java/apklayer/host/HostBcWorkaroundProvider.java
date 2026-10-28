package apklayer.host;

import java.security.Provider;
import java.security.Security;

/**
 * "AndroidKeyStoreBCWorkaround": Android's Cipher implementations that accept AndroidKeyStore keys (Flutter's secure storage asks for its RSA cipher by this provider name).
 * The software keys of HostKeyStoreProvider are ordinary JCE keys, so every algorithm is served by the other providers.
 */
public final class HostBcWorkaroundProvider extends Provider {
    public HostBcWorkaroundProvider() { super("AndroidKeyStoreBCWorkaround", "1.0", "apklayer: ciphers for software AndroidKeyStore keys"); }

    @Override public synchronized Provider.Service getService(String type, String algorithm) {
        for (Provider other : Security.getProviders()) {
            if (other == this || other.getName().startsWith("AndroidKeyStore")) continue;
            Provider.Service service = other.getService(type, algorithm);
            if (service != null) return service;
        }
        return null;
    }
}
