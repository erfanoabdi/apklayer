package apklayer.host;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageParser;
import android.content.pm.ServiceInfo;
import android.content.pm.PermissionInfo;
import android.content.pm.PackageManager;
import org.robolectric.RuntimeEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.robolectric.shadows.ShadowPackageParser;

/** Parse installed APKs with the framework parser, retaining original component intent filters. */
final class HubPackages {
    private record Cached(long modified, long size, PackageParser.Package parsed) {}
    private final Path root;
    private final Map<String, Cached> cache = new HashMap<>();
    HubPackages(Path root) { this.root = root.toAbsolutePath().normalize(); }
    synchronized PackageParser.Package get(String name) {
        if (name == null || name.isEmpty()) return null;
        Path directory = root.resolve(name).normalize();
        if (!root.equals(directory.getParent())) throw new SecurityException("Invalid package name");
        Path apk = directory.resolve("base.apk");
        if (!Files.isRegularFile(apk)) return null;
        try {
            long modified = Files.getLastModifiedTime(apk).toMillis(), size = Files.size(apk);
            Cached old = cache.get(name);
            if (old != null && old.modified == modified && old.size == size) return old.parsed;
            PackageParser.Package parsed = ShadowPackageParser.callParsePackage(apk);
            if (!name.equals(parsed.packageName)) throw new SecurityException("Installed APK package mismatch");
            cache.put(name, new Cached(modified, size, parsed)); return parsed;
        } catch (IOException failure) { throw new IllegalStateException("Cannot read installed package " + name, failure); }
    }
    record Receiver(ActivityInfo info, IntentFilter filter) {}

    /** Installed package names (every directory with an APK). */
    List<String> installed() {
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(root)) {
            stream.filter(directory -> Files.isRegularFile(directory.resolve("base.apk"))).forEach(directory -> names.add(directory.getFileName().toString()));
        } catch (IOException failure) { throw new IllegalStateException("Cannot list installed packages", failure); }
        return names;
    }

    /** Manifest receivers whose intent filter matches the broadcast (all packages, or the one named). */
    List<Receiver> receivers(Intent intent, String onlyPackage) {
        List<Receiver> found = new ArrayList<>();
        ComponentName explicit = intent.getComponent();
        for (String name : onlyPackage != null ? List.of(onlyPackage) : installed()) {
            PackageParser.Package pkg;
            try { pkg = get(name); } catch (RuntimeException unreadable) { continue; }
            if (pkg == null) continue;
            for (PackageParser.Activity receiver : pkg.receivers) {
                if (explicit != null) {
                    if (explicit.getPackageName().equals(name) && explicit.getClassName().equals(receiver.info.name))
                        found.add(new Receiver(receiver.info, receiver.intents.isEmpty() ? null : receiver.intents.get(0)));
                    continue;
                }
                for (IntentFilter filter : receiver.intents) {
                    if (filter.match(null, intent, false, "apklayer-hub") >= 0) { found.add(new Receiver(receiver.info, filter)); break; }
                }
            }
        }
        return found;
    }

    /** Whether the package holds the permission: it declares it itself, or requests a permission whose protection is normal (other grants are not modelled yet). */
    boolean holds(String name, String permission) {
        if (permission == null || permission.isEmpty()) return true;
        PackageParser.Package source = get(name);
        if (source == null) return false;
        if (source.permissions.stream().anyMatch(declared -> permission.equals(declared.info.name))) return true;
        if (!source.requestedPermissions.contains(permission)) return false;
        PermissionInfo definition = null;
        for (String other : installed()) {
            PackageParser.Package owner = get(other);
            if (owner == null) continue;
            definition = owner.permissions.stream().map(declared -> declared.info).filter(info -> permission.equals(info.name)).findFirst().orElse(null);
            if (definition != null) break;
        }
        if (definition == null) {
            try { definition = RuntimeEnvironment.getApplication().getPackageManager().getPermissionInfo(permission, 0); }
            catch (PackageManager.NameNotFoundException missing) { return false; }
        }
        return (definition.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_NORMAL;
    }

    void enforceServicePermission(String caller, ServiceInfo service) {
        if (caller.equals(service.packageName) || service.permission == null || service.permission.isEmpty()) return;
        PackageParser.Package source = get(caller), owner = get(service.packageName);
        if (source == null || !source.requestedPermissions.contains(service.permission))
            throw new SecurityException("Service requires " + service.permission);
        PermissionInfo definition = owner.permissions.stream().map(permission -> permission.info)
                .filter(permission -> service.permission.equals(permission.name)).findFirst().orElse(null);
        if (definition == null) {
            try { definition = RuntimeEnvironment.getApplication().getPackageManager().getPermissionInfo(service.permission, 0); }
            catch (PackageManager.NameNotFoundException missing) { throw new SecurityException("Unknown permission " + service.permission); }
        }
        if ((definition.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE) != PermissionInfo.PROTECTION_NORMAL)
            throw new SecurityException("Permission is not granted: " + service.permission);
    }
    ServiceInfo service(Intent intent) {
        ComponentName explicit = intent.getComponent();
        if (explicit != null) {
            PackageParser.Package pkg = get(explicit.getPackageName());
            if (pkg == null) return null;
            return pkg.services.stream().filter(service -> explicit.getClassName().equals(service.info.name))
                    .map(service -> service.info).filter(info -> info.enabled && info.applicationInfo.enabled).findFirst().orElse(null);
        }
        String target = intent.getPackage();
        if (target == null) throw new IllegalArgumentException("Service intents must name a package or component");
        PackageParser.Package pkg = get(target);
        if (pkg == null) return null;
        return pkg.services.stream().filter(service -> service.info.enabled && service.info.applicationInfo.enabled &&
                service.intents.stream().anyMatch(filter -> filter.match(intent.getAction(), intent.getType(), intent.getScheme(),
                        intent.getData(), intent.getCategories(), "apklayer-hub") >= 0)).map(service -> service.info).findFirst().orElse(null);
    }
}
