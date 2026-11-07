package apklayer.host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The runtime (dangerous) permissions of this instance's apps, as PermissionManagerService keeps them: the person's answers in the prompt
 * (HubPermissionPrompt) and the manager's grant/revoke (`apklayer permissions`, cli/manage.cpp), in two files beside accounts.json:
 * permissions-granted ("package\tpermission") and permissions-revoked ("package\tpermission": denied for good, after a revoke or a second
 * "Don't allow"; "package\tpermission\tonce": denied once, asked again). A permission in neither is not held until the app asks, except by apps
 * that target API 22 or lower (granted at install, as Android does) and with APKLAYER_PERMISSIONS=grant (no prompts, every runtime permission held
 * unless revoked: the tests, tests/lib/vm.py).
 */
final class HostRuntimePermissions {
    private HostRuntimePermissions() {}

    static final int GRANTED = 0, UNASKED = 1, DENIED_ONCE = 2, DENIED = 3;
    static final String GRANTED_FILE = "permissions-granted", REVOKED_FILE = "permissions-revoked";
    private static final boolean GRANT_ALL = "grant".equals(System.getenv("APKLAYER_PERMISSIONS"));
    private static final Map<String, Object[]> cache = new HashMap<>();  // file -> {mtime and size, lines}

    static Path file(String name) {
        String storage = System.getProperty("apklayer.storage");
        return storage == null ? null : Path.of(storage).getParent().resolve(name);
    }

    /** GRANTED, UNASKED, DENIED_ONCE or DENIED: what is recorded (the files are read again when they changed: the hub asks per permission check). */
    static synchronized int state(String pkg, String permission) {
        String key = pkg + "\t" + permission;
        if (lines(GRANTED_FILE).contains(key)) return GRANTED;
        Set<String> revoked = lines(REVOKED_FILE);
        return revoked.contains(key) ? DENIED : revoked.contains(key + "\tonce") ? DENIED_ONCE : UNASKED;
    }

    /** Whether the package holds the runtime permission now. */
    static boolean granted(String pkg, String permission, int targetSdk) {
        int state = state(pkg, permission);
        return state == GRANTED || (state == UNASKED && (GRANT_ALL || targetSdk < 23));
    }

    /** The person said no (in the manager or the prompt). */
    static boolean revoked(String pkg, String permission) { return state(pkg, permission) >= DENIED_ONCE; }

    /** Records the prompt's answer: allowed, denied once, or (a second no) denied for good. Returns the new state. */
    static synchronized int answer(String pkg, String permission, boolean allowed) {
        String key = pkg + "\t" + permission;
        int next = allowed ? GRANTED : state(pkg, permission) == DENIED_ONCE ? DENIED : DENIED_ONCE;
        Set<String> granted = new LinkedHashSet<>(lines(GRANTED_FILE)), revoked = new LinkedHashSet<>(lines(REVOKED_FILE));
        granted.remove(key); revoked.remove(key); revoked.remove(key + "\tonce");
        if (next == GRANTED) granted.add(key); else revoked.add(next == DENIED ? key : key + "\tonce");
        write(GRANTED_FILE, granted);
        write(REVOKED_FILE, revoked);
        return next;
    }

    private static Set<String> lines(String name) {
        Path file = file(name);
        if (file == null) return Set.of();
        String stamp;
        try { stamp = Files.getLastModifiedTime(file).toMillis() + "/" + Files.size(file); } catch (IOException missing) { stamp = "none"; }
        Object[] cached = cache.get(name);
        if (cached != null && cached[0].equals(stamp)) { @SuppressWarnings("unchecked") Set<String> lines = (Set<String>) cached[1]; return lines; }
        Set<String> read = new LinkedHashSet<>();
        if (!stamp.equals("none")) {
            try { for (String line : Files.readAllLines(file)) if (!line.isEmpty()) read.add(line); }
            catch (IOException unreadable) { System.out.println("[apklayer] " + file + ": " + unreadable); }
        }
        cache.put(name, new Object[] {stamp, read});
        return read;
    }

    private static void write(String name, Set<String> lines) {
        Path file = file(name);
        if (file == null) return;
        try {
            Path tmp = file.resolveSibling(name + ".tmp");
            Files.write(tmp, lines.stream().map(l -> l + "\n").reduce("", String::concat).getBytes());
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) { System.out.println("[apklayer] cannot write " + file + ": " + failure); }
        cache.remove(name);
    }
}
