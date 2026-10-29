package apklayer.host;

import java.security.Security;

/**
 * The "AndroidKeyStore" provider: Android's own (keystore2 and the software KeyMint behind it, started by the stack) when the keystore service is registered, the software
 * provider of this host (HostKeyStoreProvider) when it is not or with -Dapklayer.softkeystore=true.
 */
final class HostKeyStore {
    private HostKeyStore() {}

    /** keystore2 of the device's own service manager, waited for only when the stack declares it (it stages the declaration together with the service). */
    private static android.os.IBinder nativeService(String name) {
        String native_ = System.getProperty("apklayer.native");
        if (native_ == null || !new java.io.File(native_, "android-root/system/etc/vintf/manifest/apklayer-keystore2.xml").exists()) return null;
        return RealBinder.await(name, 5000);  // it registers once it has met KeyMint and negotiated the shared secret
    }

    static void install() {
        if (Security.getProvider("AndroidKeyStore") != null) return;
        if (!Boolean.getBoolean("apklayer.softkeystore")) {
            try {
                android.os.IBinder real = nativeService("android.system.keystore2.IKeystoreService/default");
                if (real != null) {
                    // the sandbox's service manager answers from its own table
                    org.robolectric.shadows.ShadowServiceManager.addBinderService("android.system.keystore2.IKeystoreService/default", android.system.keystore2.IKeystoreService.class,
                            android.system.keystore2.IKeystoreService.Stub.asInterface(real));
                    org.robolectric.shadows.ShadowServiceManager.setServiceDeclaredOrNot("android.system.keystore2.IKeystoreService/default", true);
                    android.security.keystore2.AndroidKeyStoreProvider.install();
                    try {  // a first call tells whether keystore2 answers this process at all
                        java.security.KeyStore probe = java.security.KeyStore.getInstance("AndroidKeyStore");
                        probe.load(null);
                        probe.aliases();
                        System.out.println("[apklayer] AndroidKeyStore: keystore2");
                        return;
                    } catch (Exception unanswered) {
                        System.out.println("[apklayer] keystore2 does not answer, software keystore: " + unanswered);
                        Security.removeProvider("AndroidKeyStore");
                    }
                }
            } catch (Throwable unavailable) { System.out.println("[apklayer] keystore2 not usable, software keystore: " + unavailable); }
        }
        System.out.println("[apklayer] AndroidKeyStore: software provider (keystore2 service not registered)");
        Security.addProvider(new HostKeyStoreProvider());
        if (Security.getProvider("AndroidKeyStoreBCWorkaround") == null) Security.addProvider(new HostBcWorkaroundProvider());  // (keystore2's install() brings Android's own)
    }
}
