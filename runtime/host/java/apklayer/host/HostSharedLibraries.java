package apklayer.host;

import android.content.res.ApkAssets;
import android.content.res.XmlResourceParser;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.xmlpull.v1.XmlPullParser;

/**
 * Android's optional shared libraries (org.apache.http.legacy, android.test.*): an app gets a library's classes on its class path when its manifest
 * declares {@code <uses-library>}, or implicitly by its target SDK, as PackageManagerService's PackageBackwardCompatibility adds them. The libraries the
 * build knows are its system configuration's {@code <library name file>} (etc/permissions, HostSystemConfig); the runtime carries their classes as JVM
 * jars at the path that names ({@code android-root/system/framework/<name>.jar}, tools/stage root). Their classes win over the "Stub!" copies the
 * sandbox's jars have ({@link AppClassLoader#addLibraries}). Install refuses an app that requires a library the build does not know, as Android does.
 */
final class HostSharedLibraries {
    private HostSharedLibraries() {}

    private static final int ATTR_NAME = 0x01010003, ATTR_REQUIRED = 0x0101028e;  // android.R.attr.name, android.R.attr.required

    /** The libraries an app uses after the platform's rules: name -> required, in class path order. */
    static Map<String, Boolean> used(File apk, int targetSdk) {
        Map<String, Boolean> libs = new LinkedHashMap<>();
        try {
            ApkAssets assets = ApkAssets.loadFromPath(apk.getPath());
            try (XmlResourceParser xml = assets.openXml("AndroidManifest.xml")) {
                for (int type; (type = xml.next()) != XmlPullParser.END_DOCUMENT; ) {
                    if (type != XmlPullParser.START_TAG || xml.getDepth() != 3 || !"uses-library".equals(xml.getName())) continue;
                    String name = null;
                    boolean required = true;
                    for (int i = 0; i < xml.getAttributeCount(); i++) {  // by resource id: shrunk manifests drop the attribute names
                        if (xml.getAttributeNameResource(i) == ATTR_NAME) name = xml.getAttributeValue(i);
                        else if (xml.getAttributeNameResource(i) == ATTR_REQUIRED) required = xml.getAttributeBooleanValue(i, true);
                    }
                    if (name != null && !libs.containsKey(name)) libs.put(name, required);
                }
            } finally { assets.close(); }
        } catch (Exception unreadable) { System.out.println("[apklayer] uses-library of " + apk + ": " + unreadable); }
        // PackageBackwardCompatibility's updaters, in their order (frameworks/base/services/core/java/com/android/server/pm/parsing/library)
        for (String onBootClassPath : new String[] {"wear-sdk", "android.net.ipsec.ike", "com.google.android.maps", "android.hidl.base", "android.hidl.manager"})
            libs.remove(onBootClassPath);  // on the boot class path, or dropped for apps (maps; HIDL for non-system apps)
        if (targetSdk > 0 && targetSdk < 28) prefixRequired(libs, "org.apache.http.legacy");  // OrgApacheHttpLegacyUpdater: targets below P
        prefixImplicit(libs, "android.test.runner", "android.test.mock");  // AndroidTestRunnerSplitUpdater
        if (targetSdk > 0 && targetSdk <= 29) prefixRequired(libs, "android.test.base");  // AndroidTestBaseUpdater: targets up to Q
        else prefixImplicit(libs, "android.test.runner", "android.test.base");
        return libs;
    }

    private static void prefixRequired(Map<String, Boolean> libs, String name) {
        Map<String, Boolean> copy = new LinkedHashMap<>(libs);
        libs.clear();
        libs.put(name, true);
        copy.remove(name);
        libs.putAll(copy);
    }

    private static void prefixImplicit(Map<String, Boolean> libs, String existing, String implicit) {
        Boolean required = libs.get(existing);
        if (required == null) return;
        if (required) prefixRequired(libs, implicit);
        else if (!libs.containsKey(implicit)) {
            Map<String, Boolean> copy = new LinkedHashMap<>(libs);
            libs.clear();
            libs.put(implicit, false);
            libs.putAll(copy);
        }
    }

    /** What the libraries resolve to: the jars this runtime has (dependencies first), the required libraries the build does not know, those it lacks. */
    record Resolved(List<Path> jars, List<String> unknown, List<String> absent) {}

    static Resolved resolve(Map<String, Boolean> libs) {
        Map<String, HostSystemConfig.Library> known = HostSystemConfig.load().libraries;
        List<Path> jars = new ArrayList<>();
        List<String> unknown = new ArrayList<>(), absent = new ArrayList<>();
        for (Map.Entry<String, Boolean> lib : libs.entrySet()) add(lib.getKey(), lib.getValue(), known, jars, unknown, absent, 0);
        return new Resolved(jars, unknown, absent);
    }

    private static void add(String name, boolean required, Map<String, HostSystemConfig.Library> known, List<Path> jars, List<String> unknown,
            List<String> absent, int depth) {
        HostSystemConfig.Library lib = known.get(name);
        if (lib == null) { if (required) unknown.add(name); return; }  // an optional library the device lacks is skipped, as Android does
        if (depth < 8) for (String dependency : lib.dependencies()) add(dependency, required, known, jars, unknown, absent, depth + 1);
        Path jar = HostSystemConfig.systemPath(lib.file());
        if (jar == null || !jar.toFile().isFile()) { if (required) absent.add(name); return; }
        if (!jars.contains(jar)) jars.add(jar);
    }

    /** This process's app's library jars (install), or none. */
    private static volatile String[] files = new String[0];

    /** The app's own ApplicationInfo lists its library jars first in sharedLibraryFiles, as PackageManagerService records them. */
    static void record(android.content.pm.ApplicationInfo info) {
        String[] own = files;
        if (info == null || own.length == 0) return;
        List<String> all = new ArrayList<>(List.of(own));
        if (info.sharedLibraryFiles != null) for (String f : info.sharedLibraryFiles) if (!all.contains(f)) all.add(f);
        info.sharedLibraryFiles = all.toArray(new String[0]);
    }

    /**
     * Before any class of the app loads: the libraries' jars join the app's class loader and its ApplicationInfo.sharedLibraryFiles (LoadedApk and
     * apps read them; ResourcesManager skips .jar files). A required library the runtime lacks is reported: the app may still run without it.
     */
    static void install(Path apk) {
        if (apk == null) return;
        Resolved resolved = resolve(used(apk.toFile(), ApkSdk.target(apk.toFile())));
        for (String name : resolved.unknown()) System.out.println("[apklayer] shared library " + name + ": unknown to this build");
        for (String name : resolved.absent()) System.out.println("[apklayer] shared library " + name + ": this runtime has no classes for it");
        if (resolved.jars().isEmpty()) return;
        ClassLoader app = AppClassLoader.app;  // the loader Main built, a class of the outer loader: reached by reflection
        try {
            if (app != null) app.getClass().getMethod("addLibraries", Path[].class).invoke(app, (Object) resolved.jars().toArray(new Path[0]));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        files = resolved.jars().stream().map(Path::toString).toArray(String[]::new);
        try {
            Object bound = org.robolectric.util.ReflectionHelpers.getField(org.robolectric.RuntimeEnvironment.getActivityThread(), "mBoundApplication");
            record(org.robolectric.util.ReflectionHelpers.getField(bound, "appInfo"));
        } catch (RuntimeException notBoundYet) { /* the context path (ShadowHostContextImpl.getApplicationInfo) covers it */ }
        if (Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] shared libraries: " + resolved.jars());
    }
}
