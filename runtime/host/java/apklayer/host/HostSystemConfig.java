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

    static HostSystemConfig load() {
        HostSystemConfig config = new HostSystemConfig();
        String root = System.getenv("ANDROID_ROOT");
        if (root == null) return config;
        for (String dir : new String[] {"etc/permissions", "etc/sysconfig"}) config.readDir(new File(root, dir), false);
        config.readDir(new File(new File(root).getParentFile(), "vendor/etc/permissions"), false);  // the vendor half: what this device's host layers provide
        config.readDir(new File(root, "etc/default-permissions"), true);
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
        for (String feature : features) shadow.setSystemFeature(feature, true);
        for (String feature : unavailable) shadow.setSystemFeature(feature, false);
        String pkg = app.getPackageName();
        Set<String> grant = new HashSet<>();
        if (privileged.containsKey(pkg)) {
            grant.addAll(privileged.get(pkg));
            grant.removeAll(denied.getOrDefault(pkg, Set.of()));
            System.out.println("[apklayer] privileged system app " + pkg + ": " + grant.size() + " privileged permissions");
        }
        grant.addAll(defaultGrants.getOrDefault(pkg, Set.of()));
        if (grant.isEmpty()) return;
        try {
            String[] asked = app.getPackageManager().getPackageInfo(pkg, PackageManager.GET_PERMISSIONS).requestedPermissions;
            Set<String> requested = asked == null ? Set.of() : Set.of(asked);
            grant.retainAll(requested);
            ((org.robolectric.shadows.ShadowApplication) org.robolectric.shadow.api.Shadow.extract(app)).grantPermissions(grant.toArray(new String[0]));
        } catch (PackageManager.NameNotFoundException e) { System.out.println("[apklayer] system config: " + e); }
    }
}
