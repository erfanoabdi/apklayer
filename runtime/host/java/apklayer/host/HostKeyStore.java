package apklayer.host;

import java.security.Security;

/**
 * The "AndroidKeyStore" provider: Android's own (keystore2 and the software KeyMint behind it, started by the stack) when the keystore service is registered, the software
 * provider of this host (HostKeyStoreProvider) when it is not or with -Dapklayer.softkeystore=true.
 */
final class HostKeyStore {
    private HostKeyStore() {}

    static void install() {
        if (Security.getProvider("AndroidKeyStore") != null) return;
        if (!Boolean.getBoolean("apklayer.softkeystore")) {
            try {
                if (android.os.ServiceManager.checkService("android.system.keystore2.IKeystoreService/default") != null) {
                    android.security.keystore2.AndroidKeyStoreProvider.install();
                    if (Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] AndroidKeyStore: keystore2");
                    return;
                }
            } catch (Throwable unavailable) { System.out.println("[apklayer] keystore2 not usable, software keystore: " + unavailable); }
        }
        Security.addProvider(new HostKeyStoreProvider());
    }
}
