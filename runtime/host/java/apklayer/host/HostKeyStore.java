package apklayer.host;

import java.security.Security;

/**
 * The "AndroidKeyStore" provider: Android's own (keystore2 and the software KeyMint behind it, started by the stack) when the keystore service is registered, the software
 * provider of this host (HostKeyStoreProvider) when it is not or with -Dapklayer.softkeystore=true.
 */
final class HostKeyStore {
    private HostKeyStore() {}

    /** keystore2 of the device's own service manager, waited for only when the stack declares it (it stages the declaration together with the service,
     *  in the instance's own root: cli/stack.cpp stageManifest, never in the shared native tree). */
    private static android.os.IBinder nativeService(String name) {
        String root = System.getenv("APKLAYER_INSTANCE_ROOT");
        if (root == null || !new java.io.File(root, "system/etc/vintf/manifest/apklayer-keystore2.xml").exists()) return null;
        return RealBinder.await(name, 5000);  // it registers once it has met KeyMint and negotiated the shared secret
    }

    /**
     * Android's AndroidKeyStoreSpi as Android's libcore runs it: there KeyStoreSpi.engineGetEntry(alias, null) returns a key entry ("Android-removed" password
     * check), the JDK's throws "requested entry requires a password" (Element's SecretStoringUtils.ensureKey, from its Application.onCreate).
     */
    public static final class Spi extends android.security.keystore2.AndroidKeyStoreSpi {
        @Override public java.security.KeyStore.Entry engineGetEntry(String alias, java.security.KeyStore.ProtectionParameter protection)
                throws java.security.KeyStoreException, java.security.NoSuchAlgorithmException, java.security.UnrecoverableEntryException {
            boolean keyEntry = protection == null && engineContainsAlias(alias) && !engineIsCertificateEntry(alias);
            return super.engineGetEntry(alias, keyEntry ? new java.security.KeyStore.PasswordProtection(null) : protection);
        }
    }

    /** gatekeeperd (HubLockScreen enrolled the instance's credential): GateKeeper.getSecureUserId for keys that need user authentication, and
     *  KeyguardManager's secure lock screen when a credential is set. */
    private static void lockScreen() {
        // a stack that runs gatekeeperd (cli/stack.cpp stages the Gatekeeper HAL's declaration with it): an app starting with the stack waits for
        // its registration, as for keystore2's (without the wait the first app of a new stack saw no secure lock screen)
        String root = System.getenv("APKLAYER_INSTANCE_ROOT");
        if (root == null || !new java.io.File(root, "vendor/etc/vintf/manifest/apklayer-gatekeeper.xml").exists()) return;
        android.os.IBinder binder = RealBinder.await(HubLockScreen.SERVICE, 5000);
        if (binder == null) return;
        var gatekeeper = android.service.gatekeeper.IGateKeeperService.Stub.asInterface(binder);
        org.robolectric.shadows.ShadowServiceManager.addBinderService(HubLockScreen.SERVICE, android.service.gatekeeper.IGateKeeperService.class, gatekeeper);
        boolean secure;
        try {
            secure = gatekeeper.getSecureUserId(0) != 0;
            // a new instance: the hub enrolls the credential before it is ready (HubLockScreen.unlockIfProvided), and this app may have started
            // with the hub (its first app start); once enrolled the id is there at once and nothing waits. The hub itself does not wait for itself.
            if (!secure && !AppEnvironment.installing && System.getenv("APKLAYER_HUB_READY_FD") == null && awaitHub()) secure = gatekeeper.getSecureUserId(0) != 0;
        } catch (android.os.RemoteException gone) { return; }
        org.robolectric.util.ReflectionHelpers.setStaticField(org.robolectric.shadows.ShadowKeyguardManager.class, "isDeviceSecure", secure);
        org.robolectric.util.ReflectionHelpers.setStaticField(org.robolectric.shadows.ShadowKeyguardManager.class, "isKeyguardSecure", secure);
        System.out.println("[apklayer] lock screen credential " + (secure ? "set" : "not set") + " (gatekeeperd)");
    }

    /** `apklayer wait hub`: until the instance's hub is ready (at once when it is), at most 10 s (a process whose launcher started no hub). */
    private static boolean awaitHub() {
        String cli = System.getProperty("apklayer.cli");
        if (cli == null) return false;
        try {
            Process wait = new ProcessBuilder(cli, "wait", "hub").inheritIO().start();
            if (wait.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) return wait.exitValue() == 0;
            wait.destroy();
            return false;
        }
        catch (java.io.IOException failed) { return false; }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
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
                    lockScreen();
                    android.security.keystore2.AndroidKeyStoreProvider.install();
                    Security.getProvider("AndroidKeyStore").put("KeyStore.AndroidKeyStore", Spi.class.getName());
                    StartTrace.mark("keystore provider installed");
                    try {  // a first call tells whether keystore2 answers this process at all
                        java.security.KeyStore probe = java.security.KeyStore.getInstance("AndroidKeyStore");
                        probe.load(null);
                        probe.aliases();
                        StartTrace.mark("keystore2 probed");
                        System.out.println("[apklayer] AndroidKeyStore: keystore2");
                        return;
                    } catch (Exception unanswered) {
                        System.out.println("[apklayer] keystore2 does not answer, software keystore: " + unanswered);
                        Security.removeProvider("AndroidKeyStore");
                    }
                }
            } catch (Throwable unavailable) {
                Throwable root = unavailable;
                while (root.getCause() != null) root = root.getCause();
                System.out.println("[apklayer] keystore2 not usable, software keystore: " + unavailable + (root == unavailable ? "" : " (root cause " + root + ")"));
            }
        }
        System.out.println("[apklayer] AndroidKeyStore: software provider (keystore2 service not registered)");
        Security.addProvider(new HostKeyStoreProvider());
        if (Security.getProvider("AndroidKeyStoreBCWorkaround") == null) Security.addProvider(new HostBcWorkaroundProvider());  // (keystore2's install() brings Android's own)
    }
}
