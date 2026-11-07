package apklayer.host;

import android.app.Application;
import android.location.Location;
import android.location.LocationManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLocationManager;

/**
 * LocationManager from the host's location service (GeoClue over D-Bus, bridge/portal/geoclue.py): with GeoClue the network provider reports its location and follows its
 * updates; without it location is off and no provider is enabled (a machine without a GNSS receiver says so instead of inventing a position). The gps provider
 * is the instance's GNSS HAL (providers.toml; the geoclue backend on a desktop) through the hub's one client of it (HubGnss).
 */
final class HostLocation {
    private HostLocation() {}

    static void install(Application app) {
        LocationManager manager = app.getSystemService(LocationManager.class);
        ShadowLocationManager shadow = Shadows.shadowOf(manager);
        shadow.setGnssCapabilities(new android.location.GnssCapabilities.Builder().build());  // never null on Android (none here: no GNSS receiver); Robolectric's default is null
        // Robolectric's location fake keeps its state in Settings.Secure (LOCATION_MODE, LOCATION_PROVIDERS_ALLOWED), which only the system writes once the
        // real SettingsProvider answers: the hub writes them at boot from the host's location service (HubBootSettings). The app's own provider state
        // follows them, so the fake writes nothing (it writes only a state the settings do not have yet).
        String allowed = android.provider.Settings.Secure.getString(app.getContentResolver(), android.provider.Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        if (allowed == null) {  // the hub did not write them within its boot wait (HubBootSettings.awaitBoot): location is off for this launch
            FakeLog.hit("location", "HostLocation: no location state from the system yet");
            shadow.removeProvider(LocationManager.GPS_PROVIDER);
            shadow.removeProvider(LocationManager.NETWORK_PROVIDER);
            return;
        }
        // gps also needs this stack's GNSS HAL: the settings persist across stacks, and the hub may not have rewritten them yet
        boolean network = allowed.contains(LocationManager.NETWORK_PROVIDER), gpsAllowed = allowed.contains(LocationManager.GPS_PROVIDER), gps = gpsAllowed && gnssDeclared();
        // settings that allow gps on a stack without a GNSS HAL: no gps provider here, and nothing written (Robolectric's fake writes LOCATION_PROVIDERS_ALLOWED
        // on a change, which only the system may: the SettingsProvider host died on that denial)
        if (gpsAllowed && !gps) shadow.removeProvider(LocationManager.GPS_PROVIDER);
        else shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, gps);
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, network);
        if (!manager.isLocationEnabled()) return;  // the host has no location service
        if (network) follow(shadow, LocationManager.NETWORK_PROVIDER, helper("geoclue.py"));
        // the GNSS HAL (providers.toml) through the hub's one client of it (HubGnss), whose pipe speaks the same lines, while this app has a gps
        // request (LocationRequests, robolectric 0018); Robolectric's LocationManager stays the framework side (no LocationManagerService or
        // GnssLocationProvider in the hub), fed with the HAL's fixes
        if (gps) org.robolectric.shadows.LocationRequests.listener = (provider, active) -> {
            if (LocationManager.GPS_PROVIDER.equals(provider)) GPS.execute(() -> gps(shadow, active));  // the listener runs under the provider's lock
        };
    }

    private static final java.util.concurrent.ExecutorService GPS = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "apklayer-location-gps-requests");
        t.setDaemon(true);
        return t;
    });
    private static boolean gpsFollowed;  // whether the hub sends this process gps fixes (the GPS thread only)

    /** The first gps request: the hub's fixes; the last one gone: the hub ends the pipe (its reader sees the end and closes it, on its own thread). */
    private static void gps(ShadowLocationManager shadow, boolean active) {
        if (active == gpsFollowed) return;
        android.os.ParcelFileDescriptor pipe;
        try { pipe = HubClient.current().gnss(active); }
        catch (RuntimeException none) { System.out.println("[apklayer] location: gps: " + none.getMessage()); return; }  // no permission or no hub: no fixes
        gpsFollowed = active;
        if (!active) return;
        java.io.InputStream fixes = new android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe);
        follow(shadow, LocationManager.GPS_PROVIDER, fixes, () -> { try { fixes.close(); } catch (IOException ignored) { } });
    }

    /** Whether this stack declares the GNSS HAL (cli/stack.cpp stages its manifest when providers.toml configures it). */
    static boolean gnssDeclared() {
        String root = System.getenv("APKLAYER_INSTANCE_ROOT");
        return root != null && new java.io.File(root, "vendor/etc/vintf/manifest/apklayer-gnss.xml").exists();
    }

    /** Runs a location helper and follows it. */
    private static void follow(ShadowLocationManager shadow, String provider, java.util.List<String> command) {
        try {
            Process helper = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            Runtime.getRuntime().addShutdownHook(new Thread(helper::destroy));
            follow(shadow, provider, helper.getInputStream(), helper::destroy);
        } catch (IOException noHelper) { System.out.println("[apklayer] location: " + provider + ": " + noHelper.getMessage() + ", provider removed"); shadow.removeProvider(provider); }
    }

    /** Reports a location source's fixes (`ready`, then `location LAT LON ACCURACY` lines) as the provider's. */
    private static void follow(ShadowLocationManager shadow, String provider, java.io.InputStream source, Runnable close) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(source));
            String first = in.readLine();  // `ready`, or why the source is not usable
            // btload's own line, and the GNSS client's session status (it may come before its `ready`)
            // shortcut: a fix that comes before `ready` is dropped (a backend that sends one fix only would lose it; the fakes and GeoClue repeat)
            while (first != null && (first.startsWith("LOADED ") || first.startsWith("status ") || first.startsWith("location "))) first = in.readLine();
            if (first == null || !first.equals("ready")) {  // it went away since the hub asked: no such provider here (a disabled one would be a settings write)
                System.out.println("[apklayer] location: " + provider + ": " + (first == null ? "no helper" : first) + ", provider removed");
                close.run();
                shadow.removeProvider(provider);
                return;
            }
            Thread follower = new Thread(() -> follow(in, shadow, provider), "apklayer-location-" + provider);
            follower.setDaemon(true);
            follower.start();
        } catch (IOException ended) { System.out.println("[apklayer] location: " + provider + ": " + ended.getMessage() + ", provider removed"); close.run(); shadow.removeProvider(provider); }
    }

    private static void follow(BufferedReader in, ShadowLocationManager shadow, String provider) {
        Pattern update = Pattern.compile("location (\\S+) (\\S+) (\\S+)");
        try {
            for (String line; (line = in.readLine()) != null; ) {
                Matcher m = update.matcher(line);
                if (!m.matches()) continue;
                Location location = new Location(provider);
                location.setLatitude(Double.parseDouble(m.group(1)));
                location.setLongitude(Double.parseDouble(m.group(2)));
                location.setAccuracy((float) Double.parseDouble(m.group(3)));
                location.setTime(System.currentTimeMillis());
                location.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> shadow.simulateLocation(location));
            }
        } catch (IOException ended) { /* the source went away */ }
        try { in.close(); } catch (IOException ignored) { }
    }

    /**
     * The command that runs a D-Bus helper of bridge/portal (geoclue.py, bluez.py, settings.py) with these arguments, ending with this process. In the sandbox
     * through apklayerd (`apklayer portal helper`), as the user outside the instance's user namespace: the system and session buses know no app uid, and
     * not even the namespace's 1000 when the user's own uid is another.
     */
    static java.util.List<String> helper(String name, String... args) {
        java.util.List<String> command = "1".equals(System.getenv("APKLAYER_SANDBOX"))
                ? HostProcess.dying(System.getProperty("apklayer.cli"), "portal", "helper", name) : HostProcess.dying("python3", script(name));
        command.addAll(java.util.List.of(args));
        return command;
    }

    /** A helper of bridge/portal (geoclue.py, settings.py) above the CLI, as installed or in the tree. */
    static String script(String name) {
        java.io.File cli = new java.io.File(System.getProperty("apklayer.cli", ""));
        for (java.io.File dir = cli.getAbsoluteFile().getParentFile(); dir != null; dir = dir.getParentFile()) {
            java.io.File candidate = new java.io.File(dir, "bridge/portal/" + name);
            if (candidate.exists()) return candidate.getPath();
        }
        return "bridge/portal/" + name;
    }
}
