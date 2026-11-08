package apklayer.host;

import android.content.Context;
import android.content.pm.PackageManager;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.robolectric.Shadows;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The build's own system configuration, read like SystemConfig does: system features ({@code etc/permissions/*.xml} and {@code etc/sysconfig},
 * {@code <feature>}), the privileged-permission allowlist ({@code <privapp-permissions>}) and the default grants ({@code etc/default-permissions}).
 * They feed the package manager instead of ad hoc grants: a package listed in the allowlist is a privileged system app and holds exactly what it lists.
 */
final class HostSystemConfig {
    final Set<String> features = new HashSet<>();
    final Map<String, Integer> versions = new HashMap<>();  // <feature name version>: android.hardware.vulkan.version and the like
    final Set<String> unavailable = new HashSet<>();
    final Map<String, Set<String>> privileged = new HashMap<>();
    final Map<String, Set<String>> denied = new HashMap<>();
    final Map<String, Set<String>> defaultGrants = new HashMap<>();
    final Map<String, String> levels = new HashMap<>();  // permission -> normal | dangerous | appop | signature
    final Set<String> implicitBroadcasts = new HashSet<>();  // <allow-implicit-broadcast>: actions that still reach manifest receivers of apps targeting 26+
    final Map<String, Library> libraries = new HashMap<>();  // <library>: the shared libraries apps may use (HostSharedLibraries)
    final Set<String> nativeLibraries = new HashSet<>();  // the native shared libraries <uses-native-library> names (SystemConfig.readPublicNativeLibrariesList)

    /** A {@code <library name file dependency>}: its jar's path on a device, the libraries it needs. */
    record Library(String file, List<String> dependencies) {}

    /** The features of the last configuration applied (ShadowHostPackageManager lists them). */
    static volatile java.util.Set<String> loadedFeatures = java.util.Set.of();
    static volatile java.util.Map<String, Integer> loadedVersions = java.util.Map.of();

    /** A device path under /system ("/system/framework/x.jar") in this build's root (ANDROID_ROOT: <native>/android-root/system), or null. */
    static java.nio.file.Path systemPath(String devicePath) {
        String root = System.getenv("ANDROID_ROOT");
        if (root == null || devicePath == null || !devicePath.startsWith("/system/")) return null;
        return java.nio.file.Path.of(root, devicePath.substring("/system/".length()));
    }

    private static volatile HostSystemConfig cached;

    /** This process's configuration, read once (an app process reads it for its shared libraries, then for its features and grants). */
    static HostSystemConfig cached() {
        HostSystemConfig config = cached;
        if (config == null) cached = config = load();
        return config;
    }

    static HostSystemConfig load() {
        HostSystemConfig config = new HostSystemConfig();
        String root = System.getenv("ANDROID_ROOT");
        if (root == null) return config;
        for (String dir : new String[] {"etc/permissions", "etc/sysconfig"}) config.readDir(new File(root, dir), false);
        config.readDir(new File(new File(root).getParentFile(), "vendor/etc/permissions"), false);  // the vendor half: what this device's host layers provide
        config.readDir(new File(root, "etc/default-permissions"), true);
        // the vendor's public libraries and the partitions' public.libraries-<company>.txt (the plain public.libraries.txt is every app's anyway)
        java.util.List<File> lists = new java.util.ArrayList<>(java.util.List.of(new File(new File(root).getParentFile(), "vendor/etc/public.libraries.txt")));
        for (String dir : new String[] {"etc", "system_ext/etc", "product/etc"}) {
            File[] found = new File(root, dir).listFiles((d, name) -> name.startsWith("public.libraries-") && name.endsWith(".txt"));
            if (found != null) lists.addAll(java.util.List.of(found));
        }
        for (File list : lists) {
            try {
                for (String line : java.nio.file.Files.readAllLines(list.toPath())) if (!line.isBlank() && !line.startsWith("#")) config.nativeLibraries.add(line.trim().split(" ")[0]);
            } catch (java.io.IOException missing) { /* no such list on this build */ }
        }
        String[] adapters = new File("/sys/class/bluetooth").list();  // the host has a Bluetooth adapter: BlueZ through the hub (HubBluetooth)
        if (adapters != null && adapters.length > 0)
            config.features.addAll(java.util.List.of("android.hardware.bluetooth", "android.hardware.bluetooth_le"));
        if (HostCameraShadows.declared())  // the stack runs cameraserver over a camera HAL (providers.toml)
            config.features.addAll(java.util.List.of("android.hardware.camera", "android.hardware.camera.any"));
        if (HostLocation.gnssDeclared())  // the GNSS HAL (gps) and GeoClue's network provider (HostLocation)
            config.features.addAll(java.util.List.of("android.hardware.location", "android.hardware.location.gps", "android.hardware.location.network"));
        String vulkan = System.getenv("APKLAYER_VULKAN_PRESENT") != null ? System.getenv().getOrDefault("APKLAYER_VULKAN_VERSION", "1.1") : null;
        if (vulkan != null) {  // the swapchain is there (cli/env.cpp): what a device's android.hardware.vulkan.*.xml declares, at the host's API version up to 1.3
            String[] v = vulkan.split("\\.");
            int minor = Integer.parseInt(v[0]) > 1 ? 3 : Math.min(3, Integer.parseInt(v[1]));
            config.versions.put("android.hardware.vulkan.version", 1 << 22 | minor << 12);  // VK_MAKE_API_VERSION(0, 1, minor, 0)
            config.versions.put("android.hardware.vulkan.level", 0);
            config.versions.put("android.hardware.vulkan.compute", 0);
            config.features.addAll(config.versions.keySet());
        }
        try {
            for (String line : java.nio.file.Files.readAllLines(new File(root, "etc/apklayer-permission-levels.tsv").toPath())) {
                String[] parts = line.split("\t");
                if (parts.length == 2) config.levels.put(parts[0], parts[1]);
            }
        } catch (java.io.IOException missing) { /* no levels staged: only the allowlists apply */ }
        return config;
    }

    private void readDir(File dir, boolean defaults) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".xml"));
        if (files == null) return;
        for (File file : files) {
            try {
                Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getDocumentElement();
                if (defaults) readDefaults(root); else readConfig(root);
            } catch (Exception failure) { System.out.println("[apklayer] system config " + file.getName() + ": " + failure); }
        }
    }

    private void readConfig(Element root) {
        for (Element feature : children(root, "feature")) {
            features.add(feature.getAttribute("name"));
            if (!feature.getAttribute("version").isEmpty()) versions.put(feature.getAttribute("name"), Integer.decode(feature.getAttribute("version")));
        }
        for (Element feature : children(root, "unavailable-feature")) unavailable.add(feature.getAttribute("name"));
        for (Element broadcast : children(root, "allow-implicit-broadcast")) implicitBroadcasts.add(broadcast.getAttribute("action"));
        for (Element library : children(root, "library")) {
            String dependency = library.getAttribute("dependency");
            libraries.put(library.getAttribute("name"), new Library(library.getAttribute("file"), dependency.isEmpty() ? List.of() : List.of(dependency.split(":"))));
        }
        for (Element app : children(root, "privapp-permissions")) {
            String pkg = app.getAttribute("package");
            for (Element permission : children(app, "permission")) privileged.computeIfAbsent(pkg, k -> new HashSet<>()).add(permission.getAttribute("name"));
            for (Element permission : children(app, "deny-permission")) denied.computeIfAbsent(pkg, k -> new HashSet<>()).add(permission.getAttribute("name"));
        }
    }

    private void readDefaults(Element root) {
        for (Element exception : children(root, "exception"))
            for (Element permission : children(exception, "permission"))
                defaultGrants.computeIfAbsent(exception.getAttribute("package"), k -> new HashSet<>()).add(permission.getAttribute("name"));
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) out.add((Element) nodes.item(i));
        return out;
    }

    /** Declares the features and grants this package what the build gives it. */
    void apply(Context app) {
        loadedFeatures = java.util.Set.copyOf(features);
        loadedVersions = java.util.Map.copyOf(versions);
        org.robolectric.shadows.ShadowPackageManager shadow = Shadows.shadowOf(app.getPackageManager());
        for (String feature : features) {
            shadow.setSystemFeature(feature, true);
            android.content.pm.FeatureInfo info = new android.content.pm.FeatureInfo();  // getSystemAvailableFeatures lists them too (Device Info HW counts them; null there is a crash)
            info.name = feature;
            info.version = versions.getOrDefault(feature, 0);
            shadow.addSystemAvailableFeature(info);
        }
        for (String feature : unavailable) shadow.setSystemFeature(feature, false);
        if (Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] system features: " + features.size() + " available, " + unavailable.size() + " unavailable (ANDROID_ROOT=" + System.getenv("ANDROID_ROOT") + ")");
        // signature spoofing support (the permission microG Services asks for; the spoofing itself is ShadowHostPackageManager.fakeSignature): known to the system and granted at install
        android.content.pm.PermissionInfo spoofing = new android.content.pm.PermissionInfo();
        spoofing.name = "android.permission.FAKE_PACKAGE_SIGNATURE";
        spoofing.packageName = "android";
        spoofing.protectionLevel = android.content.pm.PermissionInfo.PROTECTION_NORMAL;
        shadow.addPermissionInfo(spoofing);
        levels.putIfAbsent(spoofing.name, "normal");
        for (Map.Entry<String, String> known : levels.entrySet()) {  // the framework's permissions exist for getPermissionInfo (microG's self-check asks for each)
            if (known.getKey().equals(spoofing.name)) continue;
            android.content.pm.PermissionInfo info = new android.content.pm.PermissionInfo();
            info.name = known.getKey();
            info.packageName = "android";
            info.protectionLevel = "dangerous".equals(known.getValue()) ? android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
                    : "appop".equals(known.getValue()) ? android.content.pm.PermissionInfo.PROTECTION_SIGNATURE | android.content.pm.PermissionInfo.PROTECTION_FLAG_APPOP
                    : "signature".equals(known.getValue()) ? android.content.pm.PermissionInfo.PROTECTION_SIGNATURE : android.content.pm.PermissionInfo.PROTECTION_NORMAL;
            shadow.addPermissionInfo(info);
        }
        String pkg = app.getPackageName();
        String[] asked;
        try { asked = app.getPackageManager().getPackageInfo(pkg, PackageManager.GET_PERMISSIONS).requestedPermissions; }
        catch (PackageManager.NameNotFoundException e) { System.out.println("[apklayer] system config: " + e); return; }
        if (asked == null) return;
        boolean system = privileged.containsKey(pkg);  // a package in the privileged allowlist is a platform-signed system app
        Set<String> allowed = privileged.getOrDefault(pkg, Set.of());
        Set<String> blocked = denied.getOrDefault(pkg, Set.of());
        Set<String> grant = new HashSet<>();
        for (String permission : asked) {
            String level = levels.getOrDefault(permission, "");
            boolean ok = "normal".equals(level) || "appop".equals(level)  // normal and "appop" (draw over other apps, write settings...) are granted at install
                    || ("dangerous".equals(level) && HostRuntimePermissions.granted(pkg, permission, app.getApplicationInfo().targetSdkVersion))  // runtime ones: as the person answered (HubPermissionPrompt)
                    || (system && ("signature".equals(level) || allowed.contains(permission)))  // platform-signed system apps hold signature permissions
                    || defaultGrants.getOrDefault(pkg, Set.of()).contains(permission);
            if (ok && !blocked.contains(permission) && !("dangerous".equals(level) && HostRuntimePermissions.revoked(pkg, permission))) grant.add(permission);
            if ("dangerous".equals(level) && HostRuntimePermissions.state(pkg, permission) == HostRuntimePermissions.DENIED_ONCE)  // asked again: the app may explain first
                org.robolectric.Shadows.shadowOf(app.getPackageManager()).setShouldShowRequestPermissionRationale(permission, true);
        }
        if (system) System.out.println("[apklayer] privileged system app " + pkg);
        if (!grant.isEmpty()) ((org.robolectric.shadows.ShadowApplication) org.robolectric.shadow.api.Shadow.extract(app)).grantPermissions(grant.toArray(new String[0]));
    }
}
