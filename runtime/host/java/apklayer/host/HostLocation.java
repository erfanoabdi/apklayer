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
        for (String provider : new String[] {LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) shadow.setProviderEnabled(provider, false);
        shadow.setLocationEnabled(false);
        try {
            Process helper = new ProcessBuilder("python3", script()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            Runtime.getRuntime().addShutdownHook(new Thread(helper::destroy));
            BufferedReader in = new BufferedReader(new InputStreamReader(helper.getInputStream()));
            String first = in.readLine();  // `ready`, or why GeoClue is not usable
            if (first == null || !first.equals("ready")) { System.out.println("[apklayer] location: " + (first == null ? "no helper" : first) + ", location is off"); helper.destroy(); return; }
            shadow.setLocationEnabled(true);
            shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
            Thread follower = new Thread(() -> follow(in, shadow), "apklayer-location");
            follower.setDaemon(true);
            follower.start();
        } catch (IOException noPython) { System.out.println("[apklayer] location: " + noPython.getMessage() + ", location is off"); }
    }

    private static void follow(BufferedReader in, ShadowLocationManager shadow) {
        Pattern update = Pattern.compile("location (\\S+) (\\S+) (\\S+)");
        try {
            for (String line; (line = in.readLine()) != null; ) {
                Matcher m = update.matcher(line);
                if (!m.matches()) continue;
                Location location = new Location(LocationManager.NETWORK_PROVIDER);
                location.setLatitude(Double.parseDouble(m.group(1)));
                location.setLongitude(Double.parseDouble(m.group(2)));
                location.setAccuracy((float) Double.parseDouble(m.group(3)));
                location.setTime(System.currentTimeMillis());
                location.setElapsedRealtimeNanos(android.os.SystemClock.elapsedRealtimeNanos());
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> shadow.simulateLocation(location));
            }
        } catch (IOException ended) { /* GeoClue went away */ }
    }

    private static String script() {
        java.io.File cli = new java.io.File(System.getProperty("apklayer.cli", ""));
        for (java.io.File dir = cli.getAbsoluteFile().getParentFile(); dir != null; dir = dir.getParentFile()) {
            java.io.File candidate = new java.io.File(dir, "bridge/portal/geoclue.py");
            if (candidate.exists()) return candidate.getPath();
        }
        return "bridge/portal/geoclue.py";
    }
}
