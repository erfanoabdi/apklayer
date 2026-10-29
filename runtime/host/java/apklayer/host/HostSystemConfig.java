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
    final Set<String> unavailable = new HashSet<>();
    final Map<String, Set<String>> privileged = new HashMap<>();
    final Map<String, Set<String>> denied = new HashMap<>();
    final Map<String, Set<String>> defaultGrants = new HashMap<>();
    final Map<String, String> levels = new HashMap<>();  // permission -> normal | dangerous | appop | signature

    static HostSystemConfig load() {
        HostSystemConfig config = new HostSystemConfig();
        String root = System.getenv("ANDROID_ROOT");
        if (root == null) return config;
        for (String dir : new String[] {"etc/permissions", "etc/sysconfig"}) config.readDir(new File(root, dir), false);
        config.readDir(new File(new File(root).getParentFile(), "vendor/etc/permissions"), false);  // the vendor half: what this device's host layers provide
        config.readDir(new File(root, "etc/default-permissions"), true);
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
        for (Element feature : children(root, "feature")) features.add(feature.getAttribute("name"));
        for (Element feature : children(root, "unavailable-feature")) unavailable.add(feature.getAttribute("name"));
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
        org.robolectric.shadows.ShadowPackageManager shadow = Shadows.shadowOf(app.getPackageManager());
        for (String feature : features) {
            shadow.setSystemFeature(feature, true);
            android.content.pm.FeatureInfo info = new android.content.pm.FeatureInfo();  // getSystemAvailableFeatures lists them too (Device Info HW counts them; null there is a crash)
            info.name = feature;
            shadow.addSystemAvailableFeature(info);
        }
        for (String feature : unavailable) shadow.setSystemFeature(feature, false);
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
            boolean ok = "normal".equals(level) || "dangerous".equals(level) || "appop".equals(level)  // normal, dangerous and "appop" (draw over other apps, write settings...) are granted at install: the desktop has no prompts
                    || (system && ("signature".equals(level) || allowed.contains(permission)))  // platform-signed system apps hold signature permissions
                    || defaultGrants.getOrDefault(pkg, Set.of()).contains(permission);
            if (ok && !blocked.contains(permission)) grant.add(permission);
        }
        if (system) System.out.println("[apklayer] privileged system app " + pkg);
        if (!grant.isEmpty()) ((org.robolectric.shadows.ShadowApplication) org.robolectric.shadow.api.Shadow.extract(app)).grantPermissions(grant.toArray(new String[0]));
    }
}
