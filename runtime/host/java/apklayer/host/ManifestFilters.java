package apklayer.host;

import android.app.Application;
import android.content.res.XmlResourceParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.xmlpull.v1.XmlPullParserException;

/**
 * The intent filters of an app's activities, read from its AndroidManifest.xml with the framework's own binary XML
 * parser. Written at install time (info.properties) so that other apps' hosts can route an intent to an installed
 * app without starting it (PLAN P6, apklayerd's registry).
 */
final class ManifestFilters {
    private ManifestFilters() {}

    private static final String NS = "http://schemas.android.com/apk/res/android";

    /** One activity intent filter; lists are empty when the filter does not name any. */
    record Filter(String activity, List<String> actions, List<String> categories, List<String> mimeTypes, List<String> schemes) {
        /** "activity;a,b;c;m;s": the form stored in info.properties. */
        String encode() {
            return activity + ";" + String.join(",", actions) + ";" + String.join(",", categories) + ";"
                    + String.join(",", mimeTypes) + ";" + String.join(",", schemes);
        }

        static Filter decode(String line) {
            String[] p = line.split(";", -1);
            return new Filter(p[0], split(p[1]), split(p[2]), split(p[3]), split(p[4]));
        }

        private static List<String> split(String s) { return s.isEmpty() ? List.of() : List.of(s.split(",")); }
    }

    static List<Filter> read(Application app) {
        List<Filter> out = new ArrayList<>();
        try (XmlResourceParser xml = app.getAssets().openXmlResourceParser("AndroidManifest.xml")) {
            String activity = null;
            Filter current = null;
            List<String> actions = null, categories = null, mimes = null, schemes = null;
            for (int ev = xml.getEventType(); ev != XmlResourceParser.END_DOCUMENT; ev = xml.next()) {
                String tag = xml.getName();
                if (ev == XmlResourceParser.START_TAG) {
                    switch (tag) {
                        case "activity", "activity-alias" -> activity = qualified(app, xml.getAttributeValue(NS, "name"));
                        case "intent-filter" -> {
                            actions = new ArrayList<>(); categories = new ArrayList<>(); mimes = new ArrayList<>(); schemes = new ArrayList<>();
                        }
                        case "action" -> { if (actions != null) actions.add(xml.getAttributeValue(NS, "name")); }
                        case "category" -> { if (categories != null) categories.add(xml.getAttributeValue(NS, "name")); }
                        case "data" -> {
                            if (mimes != null) {
                                String m = xml.getAttributeValue(NS, "mimeType"), s = xml.getAttributeValue(NS, "scheme");
                                if (m != null) mimes.add(m);
                                if (s != null) schemes.add(s);
                            }
                        }
                        default -> { }
                    }
                } else if (ev == XmlResourceParser.END_TAG) {
                    if ("intent-filter".equals(tag) && activity != null && actions != null) {
                        out.add(new Filter(activity, actions, categories, mimes, schemes));
                        actions = null;
                    } else if ("activity".equals(tag) || "activity-alias".equals(tag)) {
                        activity = null;
                    }
                }
            }
        } catch (IOException | XmlPullParserException | RuntimeException e) {
            System.out.println("[apklayer] cannot read the manifest's intent filters: " + e);
        }
        return out;
    }

    private static String qualified(Application app, String name) {
        return name == null ? null : name.startsWith(".") ? app.getPackageName() + name : name;
    }
}
