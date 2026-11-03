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
        boolean network = allowed.contains(LocationManager.NETWORK_PROVIDER);
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, allowed.contains(LocationManager.GPS_PROVIDER));
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, network);
        if (!network || !manager.isLocationEnabled()) return;  // the host has no location service
        try {
            Process helper = new ProcessBuilder(HostProcess.dying("python3", script())).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            Runtime.getRuntime().addShutdownHook(new Thread(helper::destroy));
            BufferedReader in = new BufferedReader(new InputStreamReader(helper.getInputStream()));
            String first = in.readLine();  // `ready`, or why GeoClue is not usable
            if (first == null || !first.equals("ready")) {  // GeoClue went away since the hub asked: no network provider here (a disabled one would be a settings write)
                System.out.println("[apklayer] location: " + (first == null ? "no helper" : first) + ", location is off");
                helper.destroy();
                shadow.removeProvider(LocationManager.NETWORK_PROVIDER);
                return;
            }
            Thread follower = new Thread(() -> follow(in, shadow), "apklayer-location");
            follower.setDaemon(true);
            follower.start();
        } catch (IOException noPython) { System.out.println("[apklayer] location: " + noPython.getMessage() + ", location is off"); shadow.removeProvider(LocationManager.NETWORK_PROVIDER); }
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

    static String script() {
        java.io.File cli = new java.io.File(System.getProperty("apklayer.cli", ""));
        for (java.io.File dir = cli.getAbsoluteFile().getParentFile(); dir != null; dir = dir.getParentFile()) {
            java.io.File candidate = new java.io.File(dir, "bridge/portal/geoclue.py");
            if (candidate.exists()) return candidate.getPath();
        }
        return "bridge/portal/geoclue.py";
    }
}
