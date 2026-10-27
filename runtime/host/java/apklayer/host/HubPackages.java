package apklayer.host;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageParser;
import android.content.pm.ServiceInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
