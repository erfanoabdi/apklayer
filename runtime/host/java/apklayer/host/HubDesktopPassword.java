package apklayer.host;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Whether a password is the desktop user's login password: apklayer-pamcheck runs the PAM stack of the service APKLAYER_PAM_SERVICE (config
 * `keystore-pam-service`, default "login") with a conversation that answers every prompt with the password, so sssd, LDAP or FreeIPA users unlock
 * as well; the hub needs no privilege (pam_unix runs its setgid-shadow unix_chkpwd). Without the helper or libpam: unix_chkpwd directly (local
 * passwords only). A module that waits for a fingerprint or a token before it asks for the password waits here too: name a service without it.
 */
final class HubDesktopPassword {
    private static final String[] HELPERS = {"/usr/sbin/unix_chkpwd", "/sbin/unix_chkpwd", "/usr/bin/unix_chkpwd"};
    private HubDesktopPassword() {}

    /** True when PAM accepts {@code password} for the user the hub runs as; false for a wrong one, or without any checker. */
    static boolean check(String password) {
        if (password.isEmpty()) return false;
        String nativeDir = System.getProperty("apklayer.native"), service = System.getenv("APKLAYER_PAM_SERVICE"), user = System.getProperty("user.name");
        if (nativeDir != null && Files.isExecutable(Path.of(nativeDir, "bin/apklayer-pamcheck"))) {
            int status = run(password, Path.of(nativeDir, "bin/apklayer-pamcheck").toString(), user, service == null || service.isEmpty() ? "login" : service);
            if (status == 0 || status == 1) return status == 0;  // 2: no libpam; -1 or a crash: the helper did not answer
        }
        String helper = Arrays.stream(HELPERS).filter(path -> Files.isExecutable(Path.of(path))).findFirst().orElse(null);
        if (helper == null) { System.out.println("[apklayer-hub] login password not checked: no PAM helper"); return false; }
        return run(password, helper, user, "nonull") == 0;  // the same NUL-terminated password on stdin
    }

    /** The helper's exit status with {@code password} (NUL-terminated) on its stdin, a pipe, never a tty; -1 when it did not run. */
    private static int run(String password, String... command) {
        byte[] typed = (password + "\0").getBytes(StandardCharsets.UTF_8);
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            try (OutputStream in = process.getOutputStream()) { in.write(typed); }
            return process.waitFor();
        } catch (IOException failure) {
            System.out.println("[apklayer-hub] login password not checked: " + failure);
            return -1;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return -1;
        } finally { Arrays.fill(typed, (byte) 0); }
    }
}
