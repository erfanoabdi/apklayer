package apklayer.host;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Shared libraries that installed apps provide, as PackageManagerService keeps them: a system app's {@code <library name>} (for other apps'
 * {@code <uses-library>}), and any app's {@code <static-library name version>} or {@code <sdk-library name versionMajor>} (for
 * {@code <uses-static-library name version certDigest>} and {@code <uses-sdk-library name versionMajor certDigest>}: the name, the version and the
 * provider's signing certificate digest must match, else install refuses the user with INSTALL_FAILED_MISSING_SHARED_LIBRARY). The provider's code
 * (its converted classes.jar) joins the user's class path. Recorded at the provider's install in its info.properties ("libraries", "staticLibraries").
 * shortcut: classes only, the provider's resources are not added to the user's (Android adds its APK to sharedLibraryFiles); uninstalling a provider
 * an installed app uses is not refused. Upgrade when an app ships a static library with resources.
 */
final class HostAppLibraries {
    private HostAppLibraries() {}

    /** A {@code <uses-static-library>} ("static") or {@code <uses-sdk-library>} ("sdk"): name, version, certificate digest (lower-case hex, no colons). */
    record Use(String kind, String name, long version, String certDigest) {
        @Override public String toString() { return name + " (" + kind + " library version " + version + ")"; }
    }

    static List<Use> uses(File apk) {
        List<Use> uses = new ArrayList<>();
        for (HostSharedLibraries.Element e : HostSharedLibraries.applicationElements(apk, Set.of("uses-static-library", "uses-sdk-library"))) {
            boolean sdk = e.tag().equals("uses-sdk-library");
            String name = e.attributes().get(HostSharedLibraries.ATTR_NAME), version = e.attributes().get(sdk ? HostSharedLibraries.ATTR_VERSION_MAJOR : HostSharedLibraries.ATTR_VERSION);
            String digest = e.attributes().getOrDefault(HostSharedLibraries.ATTR_CERT_DIGEST, "");
            if (name != null) uses.add(new Use(sdk ? "sdk" : "static", name, number(version), digest.replace(":", "").toLowerCase(Locale.ROOT)));
        }
        return uses;
    }

    /** What the APK provides, into its install metadata: "libraries" (its {@code <library>} names), "staticLibraries" (kind:name:version). */
    static void recordProvided(Properties info, File apk) {
        List<String> libraries = new ArrayList<>(), statics = new ArrayList<>();
        for (HostSharedLibraries.Element e : HostSharedLibraries.applicationElements(apk, Set.of("library", "static-library", "sdk-library"))) {
            String name = e.attributes().get(HostSharedLibraries.ATTR_NAME);
            if (name == null) continue;
            if (e.tag().equals("library")) libraries.add(name);
            else if (e.tag().equals("sdk-library")) statics.add("sdk:" + name + ":" + number(e.attributes().get(HostSharedLibraries.ATTR_VERSION_MAJOR)));
            else statics.add("static:" + name + ":" + (number(e.attributes().get(HostSharedLibraries.ATTR_VERSION_MAJOR)) << 32 | number(e.attributes().get(HostSharedLibraries.ATTR_VERSION))));
        }
        info.setProperty("libraries", String.join(";", libraries));
        info.setProperty("staticLibraries", String.join(";", statics));
    }

    /** The jar of the installed app that provides the static or SDK library with that version and signer; null without one. */
    static Path providerJar(Use use) {
        for (Path app : apps()) {
            Properties info = info(app);
            if (!List.of(info.getProperty("staticLibraries", "").split(";")).contains(use.kind() + ":" + use.name() + ":" + use.version())) continue;
            // the digest the user names is one of the provider's signers (HostCertificate.digest: comma-separated upper-case SHA-256 of each)
            if (!use.certDigest().isEmpty() && !List.of(info.getProperty("signer", "").toLowerCase(Locale.ROOT).split(",")).contains(use.certDigest())) continue;
            return jar(app);
        }
        return null;
    }

    /** The jar of the installed system app whose {@code <library>} has that name; null without one. */
    static Path systemAppJar(String name) {
        for (Path app : apps()) {
            Properties info = info(app);
            boolean system = "true".equals(info.getProperty("system")) || HostSystemConfig.cached().privileged.containsKey(app.getFileName().toString());
            if (system && List.of(info.getProperty("libraries", "").split(";")).contains(name)) return jar(app);
        }
        return null;
    }

    private static Path jar(Path app) {
        Path jar = app.resolve("classes.jar");
        return Files.isRegularFile(jar) ? jar : null;
    }

    private static List<Path> apps() {
        String dir = System.getProperty("apklayer.apps");
        if (dir == null) return List.of();
        try (var list = Files.list(Path.of(dir))) { return list.filter(Files::isDirectory).sorted().toList(); }
        catch (IOException unreadable) { return List.of(); }
    }

    private static Properties info(Path app) {
        Properties info = new Properties();
        try (InputStream in = Files.newInputStream(app.resolve("info.properties"))) { info.load(in); }
        catch (IOException none) { /* not installed completely */ }
        return info;
    }

    private static long number(String value) {
        if (value == null) return 0;
        try { return Long.decode(value); } catch (NumberFormatException notNumber) { return 0; }
    }
}
