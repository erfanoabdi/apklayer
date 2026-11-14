package apklayer.host;

import android.service.gatekeeper.GateKeeperResponse;
import android.service.gatekeeper.IGateKeeperService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * LockSettingsService's part for keys that need user authentication (system_server, which apklayer does not run): the instance has a lock screen
 * credential, enrolled once with gatekeeperd (the stack's software Gatekeeper), and the instance counts as unlocked by the person who logged in to the
 * desktop: the hub verifies the credential at its start and again when an app starts (at most once a minute, off the start's path), so gatekeeperd
 * hands keystore2 an auth token and keys with an authentication timeout (setUserAuthenticationParameters(seconds, AUTH_DEVICE_CREDENTIAL)) work for
 * that long after the last verification. The credential is a random secret
 * in the instance's /data/system (only the hub reads it; apps share the Linux uid, so this is no barrier against an app's native code).
 * Per-operation authentication (BiometricPrompt with DEVICE_CREDENTIAL, KeyguardManager.createConfirmDeviceCredentialIntent: confirm) asks for the
 * desktop's login password in the hub's dialog, checked by PAM's helper (HubDesktopPassword), then verifies the credential with the operation's
 * challenge as LockSettingsService.verifyCredential does: gatekeeperd hands keystore2 the auth token. The instance is locked while the desktop
 * session is (logind's LockedHint, through HubHostSettings: setLocked), as TrustManagerService tells keystore2.
 */
final class HubLockScreen {
    static final String SERVICE = "android.service.gatekeeper.IGateKeeperService";
    private static final int USER = 0;
    private static volatile long unlocked = Long.MIN_VALUE / 2;  // nanoTime of the last verification
    private static volatile boolean locked;  // the desktop session is locked
    private static final java.util.Map<String, Thread> ASKERS = new java.util.concurrent.ConcurrentHashMap<>();  // the package -> the thread asking its password question
    private static final Object ASKING = new Object();  // one password question at a time
    private static final int TRIES = 5;
    private HubLockScreen() {}

    /** An app registers: the person at the desktop is there; refreshes the auth token when the last one is a minute old. */
    static void appStarted() {
        if (locked || System.nanoTime() - unlocked < 60_000_000_000L) return;
        unlocked = System.nanoTime();
        Thread refresh = new Thread(HubLockScreen::unlockIfProvided, "hub-unlock");
        refresh.setDaemon(true);
        refresh.start();
    }

    /** At the hub's start when the stack runs gatekeeperd: enrolls a credential the first time, then verifies it (the "unlock"). */
    static synchronized void unlockIfProvided() {
        unlocked = System.nanoTime();
        String root = System.getenv("APKLAYER_INSTANCE_ROOT"), data = System.getenv("APKLAYER_DATA");
        if (root == null || data == null || !new java.io.File(root, "vendor/etc/vintf/manifest/apklayer-gatekeeper.xml").exists()) return;
        android.os.IBinder binder = RealBinder.lookup(SERVICE) != null ? RealBinder.lookup(SERVICE) : RealBinder.await(SERVICE, 5000);
        if (binder == null) { System.out.println("[apklayer-hub] gatekeeperd is declared but not running: no lock screen credential"); return; }
        try {
            IGateKeeperService gatekeeper = IGateKeeperService.Stub.asInterface(binder);
            Path dir = Path.of(data, "system", "apklayer-lock"), secretFile = dir.resolve("credential"), handleFile = dir.resolve("handle");
            Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            if (!Files.exists(secretFile)) {
                byte[] secret = new byte[32];
                new java.security.SecureRandom().nextBytes(secret);
                Files.write(secretFile, secret);
                Files.setPosixFilePermissions(secretFile, PosixFilePermissions.fromString("rw-------"));
            }
            byte[] secret = Files.readAllBytes(secretFile), handle = Files.exists(handleFile) ? Files.readAllBytes(handleFile) : null;
            GateKeeperResponse verified = handle == null ? null : gatekeeper.verifyChallenge(USER, 0, handle, secret);
            if (verified == null || verified.getResponseCode() != GateKeeperResponse.RESPONSE_OK || verified.getShouldReEnroll()) {
                // first start, or gatekeeperd lost its state: a new credential and secure user id (keys bound to the old one stay unusable, as on Android)
                GateKeeperResponse enrolled = gatekeeper.enroll(USER, null, null, secret);
                if (enrolled.getResponseCode() != GateKeeperResponse.RESPONSE_OK) { System.out.println("[apklayer-hub] gatekeeper enroll failed: " + enrolled.getResponseCode()); return; }
                Files.write(handleFile, enrolled.getPayload());
                verified = gatekeeper.verifyChallenge(USER, 0, enrolled.getPayload(), secret);
            }
            if (verified.getResponseCode() != GateKeeperResponse.RESPONSE_OK) { System.out.println("[apklayer-hub] lock screen credential not verified: " + verified.getResponseCode()); return; }
            // LockSettingsService's unlock: keystore2's super keys for the user (auth-bound keys are super-encrypted), created once, then unlocked
            android.os.IBinder maintenance = RealBinder.await("android.security.maintenance", 5000), authorization = RealBinder.await("android.security.authorization", 5000);
            if (maintenance == null || authorization == null) { System.out.println("[apklayer-hub] keystore2 has no maintenance/authorization service: still locked"); return; }
            android.security.maintenance.IKeystoreMaintenance.Stub.asInterface(maintenance).initUserSuperKeys(USER, secret, true);
            var keystore = android.security.authorization.IKeystoreAuthorization.Stub.asInterface(authorization);
            keystore.onDeviceUnlocked(USER, secret);
            if (locked) keystore.onDeviceLocked(USER, new long[0], false);  // the hub started under a locked desktop: the keys wait for its unlock
            System.out.println("[apklayer-hub] lock screen credential verified: " + (locked ? "the desktop is locked" : "unlocked"));
        } catch (android.os.RemoteException | IOException | RuntimeException failure) {
            System.out.println("[apklayer-hub] lock screen credential: " + failure);
        }
    }

    /** The desktop session locked or unlocked (TrustManagerService's onDeviceLocked/Unlocked): keystore2 refuses UNLOCKED_DEVICE_REQUIRED keys while it is
     *  locked; the unlock verifies the credential again (a fresh auth token). */
    static void setLocked(boolean now) {
        if (now == locked) return;
        locked = now;
        System.out.println("[apklayer-hub] desktop " + (now ? "locked" : "unlocked"));
        if (!now) { unlockIfProvided(); return; }
        synchronized (HubLockScreen.class) {
            android.os.IBinder authorization = RealBinder.lookup("android.security.authorization");
            if (authorization == null) return;
            try { android.security.authorization.IKeystoreAuthorization.Stub.asInterface(authorization).onDeviceLocked(USER, new long[0], false); }
            catch (android.os.RemoteException | RuntimeException failure) { System.out.println("[apklayer-hub] keystore2 not told of the lock: " + failure); }
        }
    }
    static boolean locked() { return locked; }

    /**
     * ConfirmDeviceCredentialActivity and BiometricPrompt's credential page for {@code pkg}: the person types the desktop's login password into the
     * hub's dialog (up to TRIES times); a right one verifies the instance credential with {@code challenge} (BiometricPrompt's operation id, 0 for
     * the confirm intent), so keystore2 gets the auth token. False when cancelled, wrong too often, or without a credential.
     */
    static boolean confirm(String pkg, String title, String description, long challenge) {
        if (locked) { System.out.println("[apklayer-hub] credential confirmation of " + pkg + " refused: the desktop is locked"); return false; }
        String heading = title == null || title.isEmpty() ? "Confirm it's you" : title;
        String ask = (description == null || description.isEmpty() ? HubInstaller.labelOf(pkg, pkg) + " asks for your password." : description)
                + " Enter the password you log in to this computer with.";
        synchronized (ASKING) {
            ASKERS.put(pkg, Thread.currentThread());
            try { return ask(pkg, heading, ask, challenge); } finally { ASKERS.remove(pkg); }
        }
    }

    /** The app's BiometricPrompt was cancelled (its CancellationSignal): its open password dialog closes, as when the person cancels it. */
    static void cancel(String pkg) { HubInstallDialog.dismiss(ASKERS.get(pkg)); }

    private static boolean ask(String pkg, String heading, String ask, long challenge) {
        for (int attempt = 0; attempt < TRIES; attempt++) {
            String typed = HubInstallDialog.askSecret(heading, attempt == 0 ? ask : "Wrong password. Try again.", "Confirm", "Cancel");
            if (typed == null) { System.out.println("[apklayer-hub] credential confirmation of " + pkg + ": cancelled"); return false; }
            if (!HubDesktopPassword.check(typed)) { System.out.println("[apklayer-hub] credential confirmation of " + pkg + ": wrong password"); continue; }
            boolean verified = verify(challenge);
            System.out.println("[apklayer-hub] credential confirmation of " + pkg + (verified ? ": confirmed" : ": the instance credential was not verified") + (challenge != 0 ? " (an operation's challenge)" : ""));
            return verified;
        }
        return false;
    }

    /** gatekeeperd's verifyChallenge of the enrolled credential: it adds the auth token (with this challenge) to keystore2 itself. */
    private static synchronized boolean verify(long challenge) {
        String data = System.getenv("APKLAYER_DATA");
        android.os.IBinder binder = RealBinder.lookup(SERVICE);
        if (data == null || binder == null) return false;
        Path dir = Path.of(data, "system", "apklayer-lock");
        try {
            GateKeeperResponse verified = IGateKeeperService.Stub.asInterface(binder).verifyChallenge(USER, challenge, Files.readAllBytes(dir.resolve("handle")), Files.readAllBytes(dir.resolve("credential")));
            if (verified.getResponseCode() == GateKeeperResponse.RESPONSE_OK) unlocked = System.nanoTime();
            return verified.getResponseCode() == GateKeeperResponse.RESPONSE_OK;
        } catch (android.os.RemoteException | IOException | RuntimeException failure) {
            System.out.println("[apklayer-hub] lock screen credential: " + failure);
            return false;
        }
    }
}
