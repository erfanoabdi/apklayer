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
 * shortcut: no per-operation authentication (timeout 0, BiometricPrompt) and no lock while the desktop session runs; upgrade with a host prompt.
 */
final class HubLockScreen {
    static final String SERVICE = "android.service.gatekeeper.IGateKeeperService";
    private static final int USER = 0;
    private static volatile long unlocked = Long.MIN_VALUE / 2;  // nanoTime of the last verification
    private HubLockScreen() {}

    /** An app registers: the person at the desktop is there; refreshes the auth token when the last one is a minute old. */
    static void appStarted() {
        if (System.nanoTime() - unlocked < 60_000_000_000L) return;
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
            System.out.println("[apklayer-hub] lock screen credential " + (verified.getResponseCode() == GateKeeperResponse.RESPONSE_OK ? "verified: unlocked" : "not verified: " + verified.getResponseCode()));
        } catch (android.os.RemoteException | IOException | RuntimeException failure) {
            System.out.println("[apklayer-hub] lock screen credential: " + failure);
        }
    }
}
