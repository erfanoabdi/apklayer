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
        if (name == null || name.isEmpty() || name.startsWith(".")) return null;
        Path directory = root.resolve(name).normalize();
        if (!root.equals(directory.getParent())) throw new SecurityException("Invalid package name");
        Path apk = directory.resolve("base.apk");
        if (!Files.isRegularFile(apk) || !Files.isRegularFile(directory.resolve("info.properties"))) return null;  // info.properties comes last: an install in progress is not one yet
        try {
            // info.properties is written last by every install, splits included
            long modified = Files.getLastModifiedTime(directory.resolve("info.properties")).toMillis(), size = Files.size(apk);
            Cached old = cache.get(name);
            if (old != null && old.modified == modified && old.size == size) return old.parsed;
            boolean splits;
            try (var files = Files.list(directory)) { splits = files.anyMatch(f -> f.getFileName().toString().matches("split_.*\\.apk")); }
            // with splits the directory is parsed as a cluster, as PackageManagerService scans /data/app/<package>: splitNames and the split paths come with it
            PackageParser.Package parsed = ShadowPackageParser.callParsePackage(splits ? directory : apk);
            if (!name.equals(parsed.packageName)) throw new SecurityException("Installed APK package mismatch: " + name + " holds " + parsed.packageName);
            parsed.applicationInfo.uid = uid(name);  // the app id the scanner assigns (PackageManagerService did this at scan time); every component shares this ApplicationInfo and the generators copy it
            if (system(name)) parsed.applicationInfo.flags |= android.content.pm.ApplicationInfo.FLAG_SYSTEM;  // as the scanner marks the system partition's packages
            cache.put(name, new Cached(modified, size, parsed)); return parsed;
        } catch (IOException failure) { throw new IllegalStateException("Cannot read installed package " + name, failure); }
    }
    record Receiver(ActivityInfo info, IntentFilter filter) {}

    /** Every installed package's name (what an app's PackageManager is told about). */
    List<String> packageNames() { return installed().stream().sorted().toList(); }

    /**
     * The package as the package manager reports it to another app: components, metadata and permissions (signatures are the host's business, ShadowHostPackageManager).
     * Null when the package cannot be read.
     */
    android.content.pm.PackageInfo packageInfo(String name) {
        PackageParser.Package pkg;
        try { pkg = get(name); } catch (RuntimeException unreadable) { return null; }
        if (pkg == null) return null;
        int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS
                | PackageManager.GET_META_DATA | PackageManager.GET_PERMISSIONS | PackageManager.GET_CONFIGURATIONS
                | PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;  // ComputerEngine.updateFlags adds these for an unlocked user; without them no component matches the user state
        return PackageParser.generatePackageInfo(pkg, new int[0], flags, 0L, 0L, null, android.content.pm.pkg.FrameworkPackageUserState.DEFAULT);
    }

    /** The Android app uid of an installed package (info.properties), or -1. */
    int uid(String name) {
        try { return Integer.parseInt(property(name, "uid")); } catch (NumberFormatException none) { return -1; }
    }

    /** A key of the package's info.properties (written by `apklayer install`), "" when absent. */
    private String property(String name, String key) {
        try {
            for (String line : Files.readAllLines(root.resolve(name).resolve("info.properties"))) if (line.startsWith(key + "=")) return line.substring(key.length() + 1).trim();
        } catch (IOException | RuntimeException none) { /* not an app directory */ }
        return "";
    }

    /** The intent filters of the package's activities (services: {@code services}), by component class name. */
    java.util.Map<String, List<IntentFilter>> filters(String name, boolean services) {
        java.util.Map<String, List<IntentFilter>> found = new java.util.LinkedHashMap<>();
        PackageParser.Package pkg;
        try { pkg = get(name); } catch (RuntimeException unreadable) { return found; }
        if (pkg == null) return found;
        if (services) for (PackageParser.Service service : pkg.services) found.put(service.info.name, service.intents.stream().map(IntentFilter::new).toList());
        else for (PackageParser.Activity activity : pkg.activities) found.put(activity.info.name, activity.intents.stream().map(IntentFilter::new).toList());
        return found;
    }

    /** Every provider of every installed package (the manifest's authorities, split on ';'). */
    List<android.content.pm.ProviderInfo> providers() {
        List<android.content.pm.ProviderInfo> found = new ArrayList<>();
        for (String name : installed()) {
            PackageParser.Package pkg;
            try { pkg = get(name); } catch (RuntimeException unreadable) { continue; }
            if (pkg == null) continue;
            for (PackageParser.Provider provider : pkg.providers) if (provider.info.authority != null && provider.info.enabled) found.add(provider.info);
        }
        return found;
    }

    /** The same providers with their intent filters (a document provider is found by its DOCUMENTS_PROVIDER filter). */
    List<PackageParser.Provider> providerComponents() {
        List<PackageParser.Provider> found = new ArrayList<>();
        for (String name : installed()) {
            PackageParser.Package pkg;
            try { pkg = get(name); } catch (RuntimeException unreadable) { continue; }
            if (pkg == null) continue;
            for (PackageParser.Provider provider : pkg.providers) if (provider.info.authority != null && provider.info.enabled) found.add(provider);
        }
        return found;
    }

    /**
     * The activity an intent starts for the caller: the named component, or the first (by package name) whose intent filter matches the intent (as startActivity
     * resolves it, with CATEGORY_DEFAULT). Not exported ones are for their own package only. Null when nothing matches.
     */
    android.content.pm.ActivityInfo activity(android.content.Intent intent, String callerPackage) {
        android.content.Intent probe = new android.content.Intent(intent).addCategory(android.content.Intent.CATEGORY_DEFAULT);
        for (String name : installed().stream().sorted().toList()) {
            if (intent.getComponent() != null && !intent.getComponent().getPackageName().equals(name)) continue;
            PackageParser.Package pkg;
            try { pkg = get(name); } catch (RuntimeException unreadable) { continue; }
            if (pkg == null) continue;
            for (PackageParser.Activity activity : pkg.activities) {
                android.content.pm.ActivityInfo info = activity.info;
                if (intent.getComponent() != null) { if (!info.name.equals(intent.getComponent().getClassName())) continue; }
                else if (activity.intents.stream().noneMatch(filter -> filter.match(null, probe, false, "apklayer") >= 0)) continue;
                if (!info.exported && !name.equals(callerPackage)) continue;
                if (info.permission != null && !name.equals(callerPackage) && !holds(callerPackage, info.permission)) continue;
                return info;
            }
        }
        return null;
    }

    android.content.pm.ProviderInfo provider(String authority) {
        for (android.content.pm.ProviderInfo info : providers())
            for (String one : info.authority.split(";")) if (one.equals(authority)) return info;
        return null;
    }

    /** Installed package names (every directory with an APK). */
    List<String> installed() {
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(root)) {
            // an install in progress stages in apps/.install.XXXXXX (cli/apps.cpp) before its rename: no package name starts with a dot
            stream.filter(directory -> !directory.getFileName().toString().startsWith(".") && Files.isRegularFile(directory.resolve("base.apk")) && Files.isRegularFile(directory.resolve("info.properties"))).forEach(directory -> names.add(directory.getFileName().toString()));
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

    private Map<String, PermissionInfo> framework;

    /** The framework's permission definitions (name, base protection level), generated from the tree's manifest (tools/gen/framework-permissions.py). */
    private synchronized PermissionInfo frameworkPermission(String name) {
        if (framework == null) {
            framework = new HashMap<>();
            try (java.io.InputStream in = HubPackages.class.getClassLoader().getResourceAsStream("framework-permissions.txt")) {
                if (in != null) {
                    for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                        String[] parts = line.trim().split(" ");
                        if (parts.length != 2 || parts[0].startsWith("#")) continue;
                        PermissionInfo info = new PermissionInfo();
                        info.name = parts[0];
                        info.protectionLevel = switch (parts[1]) {
                            case "dangerous" -> PermissionInfo.PROTECTION_DANGEROUS;
                            case "signature" -> PermissionInfo.PROTECTION_SIGNATURE;
                            case "internal" -> PermissionInfo.PROTECTION_INTERNAL;
                            default -> PermissionInfo.PROTECTION_NORMAL;
                        };
                        framework.put(info.name, info);
                    }
                }
            } catch (IOException failure) { System.out.println("[apklayer-hub] cannot read the framework's permissions: " + failure); }
        }
        return framework.get(name);
    }

    /** Whether the package holds the permission: it declares it itself, or requests a permission whose protection is normal (other grants are not modelled yet). */
    boolean holds(String name, String permission) {
        if (permission == null || permission.isEmpty() || "android".equals(name)) return true;  // the system holds every permission
        PackageParser.Package source = get(name);
        if (source == null) return false;
        if (source.permissions.stream().anyMatch(declared -> permission.equals(declared.info.name))) return true;
        if (!source.requestedPermissions.contains(permission)) return false;
        PermissionInfo definition = definition(permission);
        if (definition == null) { System.out.println("[apklayer-hub] permission " + permission + " has no definition"); return false; }
        switch (definition.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE) {
            case PermissionInfo.PROTECTION_NORMAL: return true;
            case PermissionInfo.PROTECTION_DANGEROUS:  // the person's answer (HubPermissionPrompt, the manager), or a system app's default grant
                return HostRuntimePermissions.granted(name, permission, source.applicationInfo.targetSdkVersion) || (SYSTEM.defaultGrants.getOrDefault(name, java.util.Set.of()).contains(permission) && !HostRuntimePermissions.revoked(name, permission));
            case PermissionInfo.PROTECTION_SIGNATURE: return SYSTEM.privileged.containsKey(name);  // platform-signed system apps (the privileged allowlist)
            default: return false;
        }
    }

    /** The permission's definition: an installed package's, the framework's, or null. */
    PermissionInfo definition(String permission) {
        for (String other : installed()) {
            PackageParser.Package owner = get(other);
            if (owner == null) continue;
            PermissionInfo found = owner.permissions.stream().map(declared -> declared.info).filter(info -> permission.equals(info.name)).findFirst().orElse(null);
            if (found != null) return found;
        }
        PermissionInfo found = frameworkPermission(permission);
        if (found != null) return found;
        try { return RuntimeEnvironment.getApplication().getPackageManager().getPermissionInfo(permission, 0); }
        catch (PackageManager.NameNotFoundException missing) { return null; }
    }

    /** BroadcastSkipPolicy.isSignaturePerm: every permission given is defined, signature-protected and not privileged (none given: false). */
    boolean allSignature(String[] permissions) {
        if (permissions == null || permissions.length == 0) return false;
        for (String permission : permissions) {
            PermissionInfo info = permission == null ? null : definition(permission);
            if (info == null || (info.protectionLevel & (PermissionInfo.PROTECTION_MASK_BASE | PermissionInfo.PROTECTION_FLAG_PRIVILEGED)) != PermissionInfo.PROTECTION_SIGNATURE) return false;
        }
        return true;
    }

    /** The build's allow-implicit-broadcast list (SystemConfig.getAllowImplicitBroadcasts). */
    boolean implicitAllowed(String action) { return action != null && SYSTEM.implicitBroadcasts.contains(action); }

    /** Whether the uid holds the permission: a system uid every one, an app uid what one of its packages holds. */
    boolean uidHolds(int uid, String permission) {
        int appId = android.os.UserHandle.getAppId(uid);
        return appId < android.os.Process.FIRST_APPLICATION_UID
                || installed().stream().anyMatch(name -> android.os.UserHandle.getAppId(uid(name)) == appId && holds(name, permission));
    }

    private static final HostSystemConfig SYSTEM = HostSystemConfig.load();

    /** A system app (installed with --system: system=true in info.properties) or a platform-signed one (the privileged allowlist): FLAG_SYSTEM, the package installer does not remove it. */
    boolean system(String name) { return SYSTEM.privileged.containsKey(name) || "true".equals(property(name, "system")); }

    /** A service's android:permission, checked with the same grants as every other permission (holds): a requested dangerous one is granted too. */
    void enforceServicePermission(String caller, ServiceInfo service) {
        if (caller.equals(service.packageName) || service.permission == null || service.permission.isEmpty()) return;
        if (!holds(caller, service.permission)) throw new SecurityException("Service requires " + service.permission);
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
