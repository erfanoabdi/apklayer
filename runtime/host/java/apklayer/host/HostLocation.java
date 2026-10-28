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
 * LocationManager from the host's location service (GeoClue over D-Bus, through `gdbus`): with GeoClue the network provider reports its location and follows its
 * updates; without it location is off and no provider is enabled (a machine without a GNSS receiver says so instead of inventing a position).
 */
final class HostLocation {
    private HostLocation() {}

    private static final String GEOCLUE = "org.freedesktop.GeoClue2";

    private static String gdbus(String... args) {
        String[] command = new String[args.length + 3];
        command[0] = "gdbus"; command[1] = "call"; command[2] = "--system";
        System.arraycopy(args, 0, command, 3, args.length);
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            return p.waitFor() == 0 ? out : null;
        } catch (IOException | InterruptedException failure) { return null; }
    }

    static void install(Application app) {
        LocationManager manager = app.getSystemService(LocationManager.class);
        ShadowLocationManager shadow = Shadows.shadowOf(manager);
        String client = clientPath();
        shadow.setLocationEnabled(client != null);
        for (String provider : new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) shadow.setProviderEnabled(provider, false);
        if (client == null) { System.out.println("[apklayer] location: no GeoClue, location is off"); return; }
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        Thread follower = new Thread(() -> follow(app, shadow, client), "apklayer-location");
        follower.setDaemon(true);
        follower.start();
    }

    private static String clientPath() {
        String made = gdbus("--dest", GEOCLUE, "--object-path", "/org/freedesktop/GeoClue2/Manager", "--method", "org.freedesktop.GeoClue2.Manager.GetClient");
        if (made == null) return null;
        Matcher m = Pattern.compile("'(/[^']+)'").matcher(made);
        if (!m.find()) return null;
        String client = m.group(1);
        gdbus("--dest", GEOCLUE, "--object-path", client, "--method", "org.freedesktop.DBus.Properties.Set", "org.freedesktop.GeoClue2.Client", "DesktopId", "<'apklayer'>");
        gdbus("--dest", GEOCLUE, "--object-path", client, "--method", "org.freedesktop.DBus.Properties.Set", "org.freedesktop.GeoClue2.Client", "RequestedAccuracyLevel", "<uint32 8>");
        return client;
    }

    private static void follow(Application app, ShadowLocationManager shadow, String client) {
        try {
            Process monitor = new ProcessBuilder("gdbus", "monitor", "--system", "--dest", GEOCLUE, "--object-path", client).redirectErrorStream(true).start();
            Runtime.getRuntime().addShutdownHook(new Thread(monitor::destroy));
            gdbus("--dest", GEOCLUE, "--object-path", client, "--method", "org.freedesktop.GeoClue2.Client.Start");
            Pattern updated = Pattern.compile("LocationUpdated \\('[^']*', '(/[^']+)'\\)");
            try (BufferedReader in = new BufferedReader(new InputStreamReader(monitor.getInputStream()))) {
                for (String line; (line = in.readLine()) != null; ) {
                    Matcher m = updated.matcher(line);
                    if (m.find()) publish(shadow, m.group(1));
                }
            }
        } catch (IOException ended) { /* GeoClue went away */ }
    }

    private static void publish(ShadowLocationManager shadow, String path) {
        String all = gdbus("--dest", GEOCLUE, "--object-path", path, "--method", "org.freedesktop.DBus.Properties.GetAll", "org.freedesktop.GeoClue2.Location");
        if (all == null) return;
        Location location = new Location(LocationManager.NETWORK_PROVIDER);
        location.setLatitude(value(all, "Latitude"));
        location.setLongitude(value(all, "Longitude"));
        location.setAccuracy((float) value(all, "Accuracy"));
        location.setTime(System.currentTimeMillis());
        location.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> shadow.simulateLocation(location));
    }

    private static double value(String text, String key) {
        Matcher m = Pattern.compile("'" + key + "': <(-?[0-9.eE+-]+)>").matcher(text);
        return m.find() ? Double.parseDouble(m.group(1)) : 0;
    }
}
