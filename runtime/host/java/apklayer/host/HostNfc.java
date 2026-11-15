package apklayer.host;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whether this host has an NFC controller to offer, decided the same way in every process (the hub serves it, the apps' system features and NfcAdapter follow):
 * the mock directory {@code $APKLAYER_DATA/apklayer/nfc-mock} (docs/NFC.md), or pcscd's socket (a PC/SC reader). Neither: no NFC feature, getDefaultAdapter() is null, as on a phone without NFC.
 */
final class HostNfc {
    private HostNfc() {}

    static Path mockDir() {
        String data = System.getenv("APKLAYER_DATA");
        Path dir = data == null ? null : Path.of(data, "apklayer/nfc-mock");
        return dir != null && Files.isDirectory(dir) ? dir : null;
    }

    static boolean present() { return mockDir() != null || Files.exists(Path.of("/run/pcscd/pcscd.comm")); }
}
