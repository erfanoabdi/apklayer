package apklayer.host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * The runtime permissions the user revoked in this instance (`apklayer permissions revoke`, cli/manage.cpp): lines "package\tpermission" in the
 * instance's permissions-revoked file (beside accounts.json). Every dangerous permission an app asks for is granted unless listed here.
 */
final class HostRevokedPermissions {
    private HostRevokedPermissions() {}

    private static Set<String> entries = Set.of();
    private static long stamp = -1;

    static Path file() {
        String storage = System.getProperty("apklayer.storage");
        return storage == null ? null : Path.of(storage).getParent().resolve("permissions-revoked");
    }

    /** Whether the user revoked the permission of the package (the file is read again when it changed: the hub asks per permission check). */
    static synchronized boolean revoked(String pkg, String permission) {
        Path file = file();
        if (file == null) return false;
        long now;
        try { now = Files.getLastModifiedTime(file).toMillis(); } catch (IOException missing) { now = 0; }
        if (now != stamp) {
            Set<String> read = new HashSet<>();
            if (now != 0) {
                try { read.addAll(Files.readAllLines(file)); } catch (IOException unreadable) { System.out.println("[apklayer] " + file + ": " + unreadable); }
            }
            entries = read;
            stamp = now;
        }
        return entries.contains(pkg + "\t" + permission);
    }
}
