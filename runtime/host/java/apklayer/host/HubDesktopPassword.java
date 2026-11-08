package apklayer.host;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Whether a password is the desktop user's login password: PAM's own helper for unprivileged callers, unix_chkpwd (pam_unix runs it when the
 * program checking is not root, as a screen locker does). It is setgid shadow and checks only the caller's own user, so the hub needs no privilege.
 * shortcut: the local passwords of pam_unix only (not LDAP, systemd-homed or a fingerprint); a PAM conversation helper when a desktop needs them.
 */
final class HubDesktopPassword {
    private static final String[] HELPERS = {"/usr/sbin/unix_chkpwd", "/sbin/unix_chkpwd", "/usr/bin/unix_chkpwd"};
    private HubDesktopPassword() {}

    /** True when the helper accepts {@code password} for the user the hub runs as; false for a wrong one, or without the helper. */
    static boolean check(String password) {
        String helper = Arrays.stream(HELPERS).filter(path -> Files.isExecutable(Path.of(path))).findFirst().orElse(null);
        if (helper == null || password.isEmpty()) { System.out.println("[apklayer-hub] login password not checked: " + (helper == null ? "no unix_chkpwd" : "empty")); return false; }
        byte[] typed = (password + "\0").getBytes(StandardCharsets.UTF_8);  // the helper reads one NUL-terminated password from its stdin (a pipe, never a tty)
        try {
            Process process = new ProcessBuilder(helper, System.getProperty("user.name"), "nonull").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            try (OutputStream in = process.getOutputStream()) { in.write(typed); }
            return process.waitFor() == 0;
        } catch (IOException failure) {
            System.out.println("[apklayer-hub] login password not checked: " + failure);
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        } finally { Arrays.fill(typed, (byte) 0); }
    }
}
