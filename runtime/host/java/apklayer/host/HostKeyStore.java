package apklayer.host;

import java.security.Security;

/**
 * The "AndroidKeyStore" provider: Android's own (keystore2 and the software KeyMint behind it, started by the stack) when the keystore service is registered, the software
 * provider of this host (HostKeyStoreProvider) when it is not or with -Dapklayer.softkeystore=true.
 */
final class HostKeyStore {
    private HostKeyStore() {}

    /** A service of the device's own (binderfs) service manager, waited for only when the build declares it (the stack stages the declaration with the service). */
    private static android.os.IBinder nativeService(String name) throws android.os.RemoteException {
        android.os.IServiceManager sm = android.os.ServiceManagerNative.asInterface(com.android.internal.os.BinderInternal.getContextObject());
        if (sm == null || !sm.isDeclared(name)) return null;
        for (int i = 0; i < 100; i++) {  // keystore2 registers once it has met KeyMint and negotiated the shared secret
            android.os.IBinder b = sm.checkService(name);
            if (b != null) return b;
            try { Thread.sleep(50); } catch (InterruptedException e) { return null; }
        }
        return null;
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
                    System.out.println("[apklayer] AndroidKeyStore: keystore2");
                    return;
                }
            } catch (Throwable unavailable) { System.out.println("[apklayer] keystore2 not usable, software keystore: " + unavailable); }
        }
        System.out.println("[apklayer] AndroidKeyStore: software provider (keystore2 service not registered)");
        Security.addProvider(new HostKeyStoreProvider());
    }
}
