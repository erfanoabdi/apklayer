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

    static void install() {
        String root = System.getenv("ANDROID_ROOT");
        if (root == null) return;
        File dir = new File(root, "etc/compatconfig");
        File[] files = dir.listFiles((d, name) -> name.endsWith(".xml"));
        if (files == null) return;
        Map<Long, Change> changes = new HashMap<>();
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
        Compatibility.setBehaviorChangeDelegate(new Compatibility.BehaviorChangeDelegate() {
            @Override public boolean isChangeEnabled(long id) {
                Change change = changes.get(id);
                if (change == null) return true;
                if (change.disabled) return false;
                int target = RuntimeEnvironment.getApplication().getApplicationInfo().targetSdkVersion;
                if (change.after >= 0) return target > change.after;
                if (change.since >= 0) return target >= change.since;
                return true;
            }
        });
    }

    private static int number(Element e, String attribute) { return e.hasAttribute(attribute) ? Integer.parseInt(e.getAttribute(attribute)) : -1; }
}
