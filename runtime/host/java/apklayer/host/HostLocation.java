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
 * updates; without it location is off and no provider is enabled (a machine without a GNSS receiver says so instead of inventing a position).
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
        if (network) follow(shadow, LocationManager.NETWORK_PROVIDER, HostProcess.dying("python3", script("geoclue.py")));
        // the GNSS HAL (providers.toml) through its client, which speaks the same lines; shortcut: Robolectric's LocationManager stays the framework side
        // (no LocationManagerService/GnssLocationProvider in the hub), fed with the HAL's fixes; the HAL keeps one callback, so with two apps
        // locating at once only the later one gets fixes, until one client in the hub serves them all
        if (gps) follow(shadow, LocationManager.GPS_PROVIDER, HostProcess.dying(System.getProperty("apklayer.cli", "apklayer"), "exec", "libapklayer_gnss.so", "--client"));
    }

    /** Whether this stack declares the GNSS HAL (cli/stack.cpp stages its manifest when providers.toml configures it). */
    static boolean gnssDeclared() {
        String root = System.getenv("APKLAYER_INSTANCE_ROOT");
        return root != null && new java.io.File(root, "vendor/etc/vintf/manifest/apklayer-gnss.xml").exists();
    }

    /** Runs a location source (`ready`, then `location LAT LON ACCURACY` lines) and reports its fixes as the provider's. */
    private static void follow(ShadowLocationManager shadow, String provider, java.util.List<String> command) {
        try {
            Process helper = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            Runtime.getRuntime().addShutdownHook(new Thread(helper::destroy));
            BufferedReader in = new BufferedReader(new InputStreamReader(helper.getInputStream()));
            String first = in.readLine();  // `ready`, or why the source is not usable
            // btload's own line, and the GNSS client's session status (it may come before its `ready`)
            while (first != null && (first.startsWith("LOADED ") || first.startsWith("status "))) first = in.readLine();
            if (first == null || !first.equals("ready")) {  // it went away since the hub asked: no such provider here (a disabled one would be a settings write)
                System.out.println("[apklayer] location: " + provider + ": " + (first == null ? "no helper" : first) + ", provider removed");
                helper.destroy();
                shadow.removeProvider(provider);
                return;
            }
            Thread follower = new Thread(() -> follow(in, shadow, provider), "apklayer-location-" + provider);
            follower.setDaemon(true);
            follower.start();
        } catch (IOException noHelper) { System.out.println("[apklayer] location: " + provider + ": " + noHelper.getMessage() + ", provider removed"); shadow.removeProvider(provider); }
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
        } catch (IOException ended) { /* GeoClue went away */ }
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
