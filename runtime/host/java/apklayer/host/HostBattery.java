package apklayer.host;

import android.app.Application;
import android.content.Intent;
import android.os.BatteryManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.robolectric.Shadows;

/**
 * The host's power supplies for BatteryManager and the sticky ACTION_BATTERY_CHANGED: the battery and mains entries of /sys/class/power_supply, or "no battery,
 * powered" (what a desktop is) without one. Changes arrive from `udevadm monitor` (events, no polling).
 */
final class HostBattery {
    private HostBattery() {}

    record State(boolean present, int level, int status, int plugged, int voltageMv, int tempTenthsC) {}

    private static String read(File dir, String name) {
        try { return Files.readString(Path.of(dir.getPath(), name)).trim(); } catch (IOException none) { return null; }
    }

    static State read() {
        boolean present = false, mains = false, usb = false;
        int level = 100, status = BatteryManager.BATTERY_STATUS_UNKNOWN, voltage = 0, temp = 0;
        File[] supplies = new File("/sys/class/power_supply").listFiles();
        if (supplies != null) for (File supply : supplies) {
            String type = read(supply, "type");
            if ("Battery".equals(type) && !"0".equals(read(supply, "present"))) {
                present = true;
                String capacity = read(supply, "capacity"), state = read(supply, "status"), volts = read(supply, "voltage_now"), t = read(supply, "temp");
                if (capacity != null) level = Integer.parseInt(capacity);
                if (volts != null) voltage = (int) (Long.parseLong(volts) / 1000);
                if (t != null) temp = Integer.parseInt(t);
                status = state == null ? status : switch (state) {
                    case "Charging" -> BatteryManager.BATTERY_STATUS_CHARGING;
                    case "Discharging" -> BatteryManager.BATTERY_STATUS_DISCHARGING;
                    case "Full" -> BatteryManager.BATTERY_STATUS_FULL;
                    case "Not charging" -> BatteryManager.BATTERY_STATUS_NOT_CHARGING;
                    default -> BatteryManager.BATTERY_STATUS_UNKNOWN;
                };
            } else if ("Mains".equals(type) && "1".equals(read(supply, "online"))) mains = true;
            else if ("USB".equals(type) && "1".equals(read(supply, "online"))) usb = true;
        }
        int plugged = mains ? BatteryManager.BATTERY_PLUGGED_AC : usb ? BatteryManager.BATTERY_PLUGGED_USB : 0;
        if (!present) plugged = BatteryManager.BATTERY_PLUGGED_AC;  // a machine without a battery runs on mains
        return new State(present, level, status, plugged, voltage, temp);
    }

    private static State current;

    /** Per app JVM: BatteryManager's properties follow the host (the sticky broadcast is the hub's, see installHub). */
    static void install(Application app) {
        applyProperties(app, read());
        watch(() -> applyProperties(app, read()), "apklayer-battery");
    }

    /** In the hub: the sticky ACTION_BATTERY_CHANGED every app's registerReceiver(null, filter) gets, and the power connected / disconnected broadcasts. */
    static void installHub(java.util.function.BiConsumer<Intent, Boolean> send) {
        publish(send, read());
        watch(() -> publish(send, read()), "apklayer-hub-battery");
    }

    private static void watch(Runnable changed, String name) {
        try {
            Process monitor = new ProcessBuilder(HostProcess.dying("udevadm", "monitor", "--udev", "--subsystem-match=power_supply")).redirectErrorStream(true).start();
            Thread thread = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(monitor.getInputStream()))) {
                    for (String line; (line = in.readLine()) != null; ) if (line.contains("power_supply")) changed.run();
                } catch (IOException ended) { /* stopped with the JVM */ }
            }, name);
            thread.setDaemon(true);
            thread.start();
            Runtime.getRuntime().addShutdownHook(new Thread(monitor::destroy));
        } catch (IOException noUdev) { System.out.println("[apklayer] battery: no udevadm, the state is read once"); }
    }

    private static synchronized void applyProperties(Application app, State next) {
        BatteryManager manager = app.getSystemService(BatteryManager.class);
        org.robolectric.shadows.ShadowBatteryManager shadow = Shadows.shadowOf(manager);
        shadow.setIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY, next.level());
        shadow.setIsCharging(next.status() == BatteryManager.BATTERY_STATUS_CHARGING);
    }

    private static synchronized void publish(java.util.function.BiConsumer<Intent, Boolean> send, State next) {
        State previous = current;
        if (next.equals(previous)) return;
        current = next;
        send.accept(new Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PRESENT, next.present()).putExtra(BatteryManager.EXTRA_LEVEL, next.level())
                .putExtra(BatteryManager.EXTRA_SCALE, 100).putExtra(BatteryManager.EXTRA_STATUS, next.status()).putExtra(BatteryManager.EXTRA_PLUGGED, next.plugged())
                .putExtra(BatteryManager.EXTRA_VOLTAGE, next.voltageMv()).putExtra(BatteryManager.EXTRA_TEMPERATURE, next.tempTenthsC())
                .putExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD).putExtra(BatteryManager.EXTRA_TECHNOLOGY, "Li-ion"), true);
        if (previous != null && previous.plugged() == 0 && next.plugged() != 0) send.accept(new Intent(Intent.ACTION_POWER_CONNECTED), false);
        if (previous != null && previous.plugged() != 0 && next.plugged() == 0) send.accept(new Intent(Intent.ACTION_POWER_DISCONNECTED), false);
    }
}
