package apklayer.host;

import android.content.pm.SharedLibraryInfo;
import android.content.pm.VersionedPackage;
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
 * Installed apps provide libraries too (HostAppLibraries): a system app's {@code <library>} for {@code <uses-library>}, any app's {@code <static-library>}
 * and {@code <sdk-library>} for {@code <uses-static-library>}/{@code <uses-sdk-library>} (name, version and certificate digest must match).
 */
final class HostSharedLibraries {
    private HostSharedLibraries() {}

    // android.R.attr.name, required, version, certDigest, versionMajor
    static final int ATTR_NAME = 0x01010003, ATTR_REQUIRED = 0x0101028e, ATTR_VERSION = 16844057, ATTR_CERT_DIGEST = 16844104, ATTR_VERSION_MAJOR = 16844151;

    /** An element of the manifest's {@code <application>}: its tag and attributes by resource id (shrunk manifests drop the attribute names). */
    record Element(String tag, Map<Integer, String> attributes) {}

    /** The {@code <application>} children of an APK's manifest with one of the tags; empty when it is unreadable. */
    static List<Element> applicationElements(File apk, java.util.Set<String> tags) {
        List<Element> found = new ArrayList<>();
        try {
            ApkAssets assets = ApkAssets.loadFromPath(apk.getPath());
            try (XmlResourceParser xml = assets.openXml("AndroidManifest.xml")) {
                for (int type; (type = xml.next()) != XmlPullParser.END_DOCUMENT; ) {
                    if (type != XmlPullParser.START_TAG || xml.getDepth() != 3 || !tags.contains(xml.getName())) continue;
                    Map<Integer, String> attributes = new java.util.HashMap<>();
                    for (int i = 0; i < xml.getAttributeCount(); i++) attributes.put(xml.getAttributeNameResource(i), xml.getAttributeValue(i));
                    found.add(new Element(xml.getName(), attributes));
                }
            } finally { assets.close(); }
        } catch (Exception unreadable) { System.out.println("[apklayer] libraries of " + apk + ": " + unreadable); }
        return found;
    }

    /** The libraries an app uses after the platform's rules: name -> required, in class path order. */
    static Map<String, Boolean> used(File apk, int targetSdk) {
        Map<String, Boolean> libs = new LinkedHashMap<>();
        for (Element e : applicationElements(apk, java.util.Set.of("uses-library"))) {
            String name = e.attributes().get(ATTR_NAME);
            if (name != null && !libs.containsKey(name)) libs.put(name, !"false".equals(e.attributes().get(ATTR_REQUIRED)));
        }
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

    /**
     * The app's {@code <uses-native-library>}: name -> required. PackageManagerService checks them from target SDK 31 on (ENFORCE_NATIVE_SHARED_LIBRARY_DEPENDENCIES);
     * below that an app sees every public native library and they are not its shared libraries.
     */
    static Map<String, Boolean> usedNative(File apk, int targetSdk) {
        Map<String, Boolean> libs = new LinkedHashMap<>();
        if (targetSdk < 31) return libs;
        for (Element e : applicationElements(apk, java.util.Set.of("uses-native-library"))) {
            String name = e.attributes().get(ATTR_NAME);
            if (name != null && !libs.containsKey(name)) libs.put(name, !"false".equals(e.attributes().get(ATTR_REQUIRED)));
        }
        return libs;
    }

    /**
     * What the libraries resolve to: the jars this runtime has (dependencies first), the required libraries the build does not know, those it lacks, and
     * the app's SharedLibraryInfos (ApplicationInfo.sharedLibraryInfos: the libraries it was given, as SharedLibrariesImpl collects them).
     */
    record Resolved(List<Path> jars, List<String> unknown, List<String> absent, List<SharedLibraryInfo> infos) {}

    static Resolved resolve(Map<String, Boolean> libs) { return resolve(libs, List.of(), Map.of()); }

    /** With the app's {@code <uses-static-library>}/{@code <uses-sdk-library>} (HostAppLibraries.uses): the installed apps that provide them. */
    /** With the app's {@code <uses-native-library>} (usedNative): a required one the build's public native libraries lack is unknown too. */
    static Resolved resolve(Map<String, Boolean> libs, List<HostAppLibraries.Use> statics, Map<String, Boolean> natives) {
        List<Path> jars = new ArrayList<>();
        List<SharedLibraryInfo> infos = new ArrayList<>();
        if (libs.isEmpty() && statics.isEmpty() && natives.isEmpty()) return new Resolved(jars, List.of(), List.of(), infos);
        HostSystemConfig config = HostSystemConfig.cached();
        List<String> unknown = new ArrayList<>(), absent = new ArrayList<>();
        for (HostAppLibraries.Use use : statics) {
            Path jar = HostAppLibraries.providerJar(use);
            if (jar == null) { unknown.add(use.toString()); continue; }
            if (!jars.contains(jar)) jars.add(jar);
            String provider = jar.getParent().getFileName().toString();
            infos.add(new SharedLibraryInfo(null, provider, List.of(jar.toString()), use.name(), use.version(), use.kind().equals("sdk") ? SharedLibraryInfo.TYPE_SDK_PACKAGE : SharedLibraryInfo.TYPE_STATIC,
                    new VersionedPackage(provider, use.version()), null, null, false));
        }
        for (Map.Entry<String, Boolean> lib : libs.entrySet()) add(lib.getKey(), lib.getValue(), config.libraries, jars, unknown, absent, infos, 0);
        for (Map.Entry<String, Boolean> lib : natives.entrySet()) {  // a public native library of the vendor or a partition's list
            if (config.nativeLibraries.contains(lib.getKey()))
                infos.add(new SharedLibraryInfo(lib.getKey(), null, null, lib.getKey(), SharedLibraryInfo.VERSION_UNDEFINED, SharedLibraryInfo.TYPE_BUILTIN, new VersionedPackage("android", 0L), null, null, true));
            else if (lib.getValue() && !config.libraries.isEmpty()) unknown.add(lib.getKey());
        }
        return new Resolved(jars, unknown, absent, infos);
    }

    private static void add(String name, boolean required, Map<String, HostSystemConfig.Library> known, List<Path> jars, List<String> unknown,
            List<String> absent, List<SharedLibraryInfo> infos, int depth) {
        HostSystemConfig.Library lib = known.get(name);
        if (lib == null) {  // a system app's <library> (PackageManagerService takes them from system apps only)
            Path provided = HostAppLibraries.systemAppJar(name);
            if (provided != null) {
                if (!jars.contains(provided)) jars.add(provided);
                String provider = provided.getParent().getFileName().toString();
                infos.add(new SharedLibraryInfo(null, provider, List.of(provided.toString()), name, SharedLibraryInfo.VERSION_UNDEFINED, SharedLibraryInfo.TYPE_DYNAMIC,
                        new VersionedPackage(provider, 0L), null, null, false));
                return;
            }
        }
        // an optional library the device lacks is skipped, as Android does; without a system configuration (no ANDROID_ROOT) nothing is known
        if (lib == null) { if (required && !known.isEmpty()) unknown.add(name); return; }
        if (depth < 8) for (String dependency : lib.dependencies()) add(dependency, required, known, jars, unknown, absent, infos, depth + 1);
        Path jar = HostSystemConfig.systemPath(lib.file());
        if (jar == null || !jar.toFile().isFile()) { if (required) absent.add(name); return; }
        if (!jars.contains(jar)) jars.add(jar);
        if (infos.stream().noneMatch(info -> info.getName().equals(name)))
            infos.add(new SharedLibraryInfo(lib.file(), null, null, name, SharedLibraryInfo.VERSION_UNDEFINED, SharedLibraryInfo.TYPE_BUILTIN, new VersionedPackage("android", 0L), null, null, false));
    }

    /** This process's app's library jars (install), or none. */
    private static volatile String[] files = new String[0];
    private static volatile List<SharedLibraryInfo> infos = List.of();

    /** The app's own ApplicationInfo lists its library jars first in sharedLibraryFiles, and its libraries in sharedLibraryInfos, as PackageManagerService records them. */
    static void record(android.content.pm.ApplicationInfo info) {
        String[] own = files;
        if (info != null && !infos.isEmpty()) info.sharedLibraryInfos = new ArrayList<>(infos);
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
        int target = ApkSdk.target(apk.toFile());
        Resolved resolved = resolve(used(apk.toFile(), target), HostAppLibraries.uses(apk.toFile()), usedNative(apk.toFile(), target));
        for (String name : resolved.unknown()) System.out.println("[apklayer] shared library " + name + ": unknown to this build");
        for (String name : resolved.absent()) System.out.println("[apklayer] shared library " + name + ": this runtime has no classes for it");
        infos = List.copyOf(resolved.infos());
        if (resolved.jars().isEmpty()) {
            try {
                Object bound = org.robolectric.util.ReflectionHelpers.getField(org.robolectric.RuntimeEnvironment.getActivityThread(), "mBoundApplication");
                record(org.robolectric.util.ReflectionHelpers.getField(bound, "appInfo"));
            } catch (RuntimeException notBoundYet) { /* the context path covers it */ }
            return;
        }
        ClassLoader app = AppClassLoader.index;  // the loader Main built, a class of the outer loader: reached by reflection
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
