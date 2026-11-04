package apklayer.host;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Bluetooth for the instance's apps (docs/PLAN.md "Bluetooth and USB", no Android Bluetooth stack): each app's session is a BlueZ client of its own
 * (bridge/portal/bluez.py) started here, where the system bus is reachable (a sandboxed app has none), with its stdin and stdout handed to the app as two
 * pipes (HostBluetooth). The hub's own client follows the adapter and sends ACTION_STATE_CHANGED to every app, as the Bluetooth service does.
 */
final class HubBluetooth {
    private static final Pattern ADDRESS = Pattern.compile("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}"),
            UUID = Pattern.compile("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}");
    private final HubDirectory directory;
    private Process watcher;

    HubBluetooth(HubDirectory directory) { this.directory = directory; }

    /** A session (address and uuid null) or an RFCOMM connection to the device's service: {the helper's stdin, its stdout} for the caller. */
    ParcelFileDescriptor[] open(HubDirectory.Peer caller, String address, String uuid) throws IOException {
        HubPackages packages = directory.packages();
        if (!List.of("android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH").stream().anyMatch(p -> packages.holds(caller.name(), p)))
            throw new SecurityException(caller.name() + " holds no Bluetooth permission");
        if ((address == null) != (uuid == null) || address != null && (!ADDRESS.matcher(address).matches() || !UUID.matcher(uuid).matches()))
            throw new IllegalArgumentException("Bad RFCOMM target " + address + " " + uuid);
        watch();
        List<String> command = HostProcess.dying("python3", HostLocation.script("bluez.py"));
        if (address != null) command.addAll(List.of("rfcomm", address, uuid));
        ParcelFileDescriptor[] in = ParcelFileDescriptor.createPipe(), out = ParcelFileDescriptor.createPipe();
        try {  // the pipes' far ends become the helper's stdin and stdout (a pipe reopens through /proc; ProcessBuilder passes no other descriptor)
            new ProcessBuilder(command).redirectInput(new File("/proc/self/fd/" + in[0].getFd())).redirectOutput(new File("/proc/self/fd/" + out[1].getFd()))
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        } finally { in[0].close(); out[1].close(); }
        return new ParcelFileDescriptor[] {in[1], out[0]};
    }

    /** The adapter's power as ACTION_STATE_CHANGED broadcasts; from the first session on (no Bluetooth user, no BlueZ client). */
    private synchronized void watch() throws IOException {
        if (watcher != null) return;
        watcher = new ProcessBuilder(HostProcess.dying("python3", HostLocation.script("bluez.py"))).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        BufferedReader events = new BufferedReader(new InputStreamReader(watcher.getInputStream()));
        Thread follower = new Thread(() -> {
            int state = -1;
            try {
                for (String line; (line = events.readLine()) != null; ) {
                    JSONObject event = new JSONObject(line);
                    if (!"adapter".equals(event.optString("ev"))) continue;
                    int now = event.optBoolean("powered") ? BluetoothAdapter.STATE_ON : BluetoothAdapter.STATE_OFF;
                    if (state >= 0 && now != state) {
                        System.out.println("[apklayer-hub] bluetooth " + (now == BluetoothAdapter.STATE_ON ? "on" : "off"));
                        directory.sendSystem(new Intent(BluetoothAdapter.ACTION_STATE_CHANGED).putExtra(BluetoothAdapter.EXTRA_STATE, now)
                                .putExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, state).addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND), false);
                    }
                    state = now;
                }
            } catch (IOException | JSONException ended) { System.out.println("[apklayer-hub] bluetooth watcher ended: " + ended); }
        }, "hub-bluetooth");
        follower.setDaemon(true);
        follower.start();
    }
}
