package apklayer.host;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageParser;
import android.provider.Settings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The settings the system writes on every boot before any app runs, written by the hub (the instance's system, uid 1000) into the real
 * SettingsProvider: what PowerManagerService (BOOT_COUNT), LocationManagerService (the location mode, from the host's location service),
 * InputMethodManagerService (the enabled and default input methods) and the Provision app (DEVICE_PROVISIONED, USER_SETUP_COMPLETE: the
 * desktop has no setup wizard) write on a device. Apps may not write Global/Secure settings; the hub may.
 */
final class HubBootSettings {
    private HubBootSettings() {}

    static final String INPUT_METHOD_ACTION = "android.view.InputMethod";

    static void write(Context context, HubDirectory directory) {
        Process location = startLocationProbe();  // GeoClue's answer comes while the settings provider's host starts
        // the hub's own resolver reaches the settings provider in its process like an app's does (RemoteProvider), as the system
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(Settings.AUTHORITY,
                new RemoteProvider(new android.content.ContextWrapper(context) {
                    @Override public android.content.AttributionSource getAttributionSource() {
                        return new android.content.AttributionSource.Builder(android.os.Process.SYSTEM_UID).setPackageName("android").build();
                    }
                }, Settings.AUTHORITY, null, null, () -> directory.providers().system(Settings.AUTHORITY)));
        ContentResolver resolver = context.getContentResolver();
        try {
            int boots = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, 0) + 1;  // Device Info HW reads it with getInt and no default
            Settings.Global.putInt(resolver, Settings.Global.BOOT_COUNT, boots);
            if (Settings.Global.getInt(resolver, Settings.Global.DEVICE_PROVISIONED, 0) == 0) Settings.Global.putInt(resolver, Settings.Global.DEVICE_PROVISIONED, 1);
            if (Settings.Secure.getInt(resolver, Settings.Secure.USER_SETUP_COMPLETE, 0) == 0) Settings.Secure.putInt(resolver, Settings.Secure.USER_SETUP_COMPLETE, 1);
            String enabled = inputMethods(directory.packages());
            put(resolver, Settings.Secure.ENABLED_INPUT_METHODS, enabled);
            String current = Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD);
            List<String> ids = enabled.isEmpty() ? List.of() : List.of(enabled.split(":"));
            if (current == null || !ids.contains(current)) put(resolver, Settings.Secure.DEFAULT_INPUT_METHOD, ids.isEmpty() ? "" : ids.get(0));
            boolean located = locationReady(location);
            // LOCATION_PROVIDERS_ALLOWED: gone from Android's own settings (version 193), but Robolectric's LocationManager keeps provider state in it
            // (HostLocation): the network provider is GeoClue's
            put(resolver, Settings.Secure.LOCATION_MODE, Integer.toString(located ? Settings.Secure.LOCATION_MODE_HIGH_ACCURACY : Settings.Secure.LOCATION_MODE_OFF));
            put(resolver, Settings.Secure.LOCATION_PROVIDERS_ALLOWED, located ? android.location.LocationManager.NETWORK_PROVIDER : "");
            System.out.println("[apklayer-hub] boot count " + boots + ", location " + (located ? "on" : "off") + ", input methods [" + enabled + "]");
        } catch (RuntimeException failed) {
            System.out.println("[apklayer-hub] boot settings not written: " + failed);
        } finally {
            if (location != null) location.destroy();
            BOOTED.countDown();
        }
    }

    private static final java.util.concurrent.CountDownLatch BOOTED = new java.util.concurrent.CountDownLatch(1);

    /** Until the boot settings are written (or BOOT_WAIT_S passed): an app's first provider request waits, so its first launch sees them. */
    static void awaitBoot() {
        try { BOOTED.await(BOOT_WAIT_S, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
    private static final long BOOT_WAIT_S = 10;

    private static void put(ContentResolver resolver, String key, String value) {
        if (!value.equals(Settings.Secure.getString(resolver, key))) Settings.Secure.putString(resolver, key, value);
    }

    /** The host's location service (GeoClue, bridge/portal/geoclue.py) as HostLocation asks it: null without python. */
    private static Process startLocationProbe() {
        try { return new ProcessBuilder(HostProcess.dying("python3", HostLocation.script("geoclue.py"))).redirectError(ProcessBuilder.Redirect.DISCARD).start(); }
        catch (IOException noPython) { return null; }
    }

    private static boolean locationReady(Process probe) {
        if (probe == null) return false;
        try { return "ready".equals(new BufferedReader(new InputStreamReader(probe.getInputStream())).readLine()); }
        catch (IOException gone) { return false; }
    }

    /**
     * ENABLED_INPUT_METHODS as InputMethodManagerService keeps it: the input methods (a service for android.view.InputMethod that requires
     * BIND_INPUT_METHOD) of the installed packages the user enabled (`apklayer ime enable <package>`: one package or IME id per line of the
     * instance's input-methods file). An uninstalled one drops out. An IME sees every key typed, so none is enabled unasked (as on a device).
     */
    static String inputMethods(HubPackages packages) {
        List<String> chosen;
        try { chosen = Files.readAllLines(chosenFile()).stream().map(String::trim).filter(line -> !line.isEmpty()).toList(); }
        catch (IOException none) { chosen = List.of(); }
        List<String> enabled = new ArrayList<>();
        for (String name : packages.packageNames()) {
            PackageParser.Package pkg;
            try { pkg = packages.get(name); } catch (RuntimeException unreadable) { continue; }
            if (pkg == null) continue;
            for (PackageParser.Service service : pkg.services) {
                if (!"android.permission.BIND_INPUT_METHOD".equals(service.info.permission)) continue;
                if (service.intents.stream().noneMatch(filter -> filter.hasAction(INPUT_METHOD_ACTION))) continue;
                String id = new ComponentName(name, service.info.name).flattenToShortString();
                if (chosen.contains(name) || chosen.contains(id)) enabled.add(id);
            }
        }
        return String.join(":", enabled);
    }

    /** The user's input method choice for the instance (beside accounts.json): written by the CLI (cli/ime.cpp). */
    static Path chosenFile() { return Path.of(System.getProperty("apklayer.storage")).getParent().resolve("input-methods"); }
}
