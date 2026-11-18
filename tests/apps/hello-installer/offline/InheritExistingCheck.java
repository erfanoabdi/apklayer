package apklayer.host;

import android.content.pm.PackageInstaller.SessionParams;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** Run on a JVM with the host classes and Android framework on its class path. */
public final class InheritExistingCheck {
    private static final String PKG = "hello.payload";
    private static final long VERSION = (1L << 32) | 7;

    public static void main(String[] args) throws IOException {
        var base = apk("base", null);
        var density = apk("density", "config.xxhdpi");
        var language = apk("language", "config.nl");
        var replacement = apk("updated-density", density.split());

        check(List.of(base.path(), density.path(), language.path()),
                HubInstaller.mergeInherited(PKG, List.of(base, density), List.of(language), List.of()));
        check(List.of(base.path(), replacement.path()),
                HubInstaller.mergeInherited(PKG, List.of(base, density), List.of(replacement), List.of()));
        check(List.of(base.path()), HubInstaller.mergeInherited(PKG, List.of(base, density), List.of(), List.of(density.split())));
        check(List.of(base.path(), density.path()),
                HubInstaller.mergeInherited(PKG, List.of(), List.of(base, density), List.of()));

        rejects("No base APK", List.of(), List.of(density), List.of());
        rejects("Mismatched version", List.of(base), List.of(new HubInstaller.InstallApk(density.path(), PKG, density.split(), 7)), List.of());
        rejects("Unexpected package", List.of(base), List.of(new HubInstaller.InstallApk(density.path(), "other.payload", density.split(), VERSION)), List.of());
        rejects("duplicate split", List.of(base), List.of(density, replacement), List.of());
        rejects("Cannot remove split", List.of(base), List.of(), List.of(density.split()));
        rejects("Cannot remove split", List.of(base, density), List.of(replacement), List.of(density.split()));

        Properties legacy = new Properties();
        check(SessionParams.MODE_FULL_INSTALL, HubInstallSessions.mode(legacy));
        legacy.setProperty("mode", Integer.toString(SessionParams.MODE_INHERIT_EXISTING));
        check(SessionParams.MODE_INHERIT_EXISTING, HubInstallSessions.mode(legacy));
        System.out.println("PASS inherit existing merge and session mode");
    }

    private static HubInstaller.InstallApk apk(String file, String split) {
        return new HubInstaller.InstallApk(Path.of(file + ".apk"), PKG, split, VERSION);
    }

    private static void rejects(String message, List<HubInstaller.InstallApk> installed, List<HubInstaller.InstallApk> staged, List<String> removals) throws IOException {
        try {
            HubInstaller.mergeInherited(PKG, installed, staged, removals);
        } catch (IOException failure) {
            if (failure.getMessage().contains(message)) return;
            throw failure;
        }
        throw new AssertionError("Expected rejection: " + message);
    }

    private static void check(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
