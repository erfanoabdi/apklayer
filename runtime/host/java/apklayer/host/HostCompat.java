package apklayer.host;

import android.compat.Compatibility;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.robolectric.RuntimeEnvironment;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * App compatibility changes (Compatibility.isChangeEnabled, e.g. "PendingIntent needs FLAG_IMMUTABLE") by the app's target SDK, from the build's own change list
 * ({@code $ANDROID_ROOT/etc/compatconfig/*.xml}): the platform compat service decides this on a device; the default here would enable every change, which breaks apps
 * that target an older SDK. A change with neither attribute is on, {@code disabled} is off, {@code enableAfterTargetSdk=N} is on above N, {@code enableSinceTargetSdk=N} from N.
 */
final class HostCompat {
    private HostCompat() {}

    private record Change(boolean disabled, int after, int since) {}

    private static final Map<Long, Change> changes = new HashMap<>();

    static void install() {
        String root = System.getenv("ANDROID_ROOT");
        if (root == null && System.getProperty("apklayer.native") != null) root = System.getProperty("apklayer.native") + "/android-root/system";
        if (root == null) return;
        File dir = new File(root, "etc/compatconfig");
        File[] files = dir.listFiles((d, name) -> name.endsWith(".xml"));
        if (files == null) return;
        for (File file : files) {
            try {
                NodeList list = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("compat-change");
                for (int i = 0; i < list.getLength(); i++) {
                    Element e = (Element) list.item(i);
                    changes.put(Long.parseLong(e.getAttribute("id")), new Change("true".equals(e.getAttribute("disabled")),
                            number(e, "enableAfterTargetSdk"), number(e, "enableSinceTargetSdk")));
                }
            } catch (Exception unreadable) { System.out.println("[apklayer] compat config " + file + ": " + unreadable); }
        }
    }

    /** Reached through ShadowHostCompatibility (the framework installs its own delegate when the application is bound). */
    static boolean enabled(long id) {
        Change change = changes.get(id);
        if (change == null) return true;
        if (change.disabled) return false;
        int target = targetSdk();
        if (change.after >= 0) return target > change.after;
        if (change.since >= 0) return target >= change.since;
        return true;
    }

    private static int declaredTarget = -2;
    /** The app's target SDK from its install record: the Application itself may be under construction (getApplication would recurse into its creation). */
    private static int targetSdk() {
        if (declaredTarget == -2) {
            declaredTarget = -1;
            String apps = System.getProperty("apklayer.apps"), pkg = System.getProperty("apklayer.package");
            if (apps != null && pkg != null) {
                try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.FileReader(new File(new File(apps, pkg), "info.properties")))) {
                    for (String line; (line = in.readLine()) != null; ) if (line.startsWith("targetSdk=")) declaredTarget = Integer.parseInt(line.substring(10).trim());
                } catch (java.io.IOException | NumberFormatException none) { /* falls back below */ }
            }
        }
        // no install record yet (`apklayer install` parses the APK while its Application is being created: getApplication would recurse into that creation)
        return declaredTarget >= 0 ? declaredTarget : Integer.getInteger("apklayer.sdk", 36);
    }

    private static int number(Element e, String attribute) { return e.hasAttribute(attribute) ? Integer.parseInt(e.getAttribute(attribute)) : -1; }
}
