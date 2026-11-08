package apklayer.host;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowBluetoothDevice;

/**
 * The app's Bluetooth session: a BlueZ client (bridge/portal/bluez.py) the hub starts for this process (HubBluetooth), spoken to in JSON lines. Opened on the
 * app's first Bluetooth call; the shadows (ShadowHostBluetooth*) send commands and follow its events. Without a hub or BlueZ the adapter is off.
 */
final class HostBluetooth {
    private static HostBluetooth session;
    private static boolean failed;
    private final OutputStream commands;
    private final List<Consumer<JSONObject>> listeners = new CopyOnWriteArrayList<>();
    private volatile JSONObject adapter;
    private final Map<Object, String> scans = new HashMap<>();  // owner (a ScanCallback, the discovery) -> its transport

    private HostBluetooth(ParcelFileDescriptor[] pipes) throws IOException, JSONException {
        commands = new ParcelFileDescriptor.AutoCloseOutputStream(pipes[0]);
        BufferedReader events = new BufferedReader(new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(pipes[1]), StandardCharsets.UTF_8));
        String first = events.readLine();  // the helper starts with the adapter's state
        if (first == null) throw new IOException("the BlueZ helper ended");
        adapter = new JSONObject(first);
        Thread reader = new Thread(() -> {
            try {
                for (String line; (line = events.readLine()) != null; ) {
                    JSONObject event = new JSONObject(line);
                    if ("adapter".equals(event.optString("ev"))) adapter = event;
                    if ("error".equals(event.optString("ev"))) System.out.println("[apklayer] bluetooth: " + event.optString("message"));
                    if ("pin".equals(event.optString("ev"))) askPin(event.optString("address"), event.isNull("name") ? null : event.optString("name"), event.optBoolean("passkey"));
                    for (Consumer<JSONObject> listener : listeners) listener.accept(event);
                }
            } catch (IOException | JSONException ended) { System.out.println("[apklayer] bluetooth session ended: " + ended); }
        }, "apklayer-bluetooth");
        reader.setDaemon(true);
        reader.start();
    }

    /** BlueZ waits for a pairing's PIN: the hub asks the person (not on this reader: the session's other events go on meanwhile). */
    private void askPin(String address, String name, boolean passkey) {
        Thread asking = new Thread(() -> {
            String pin = null;
            try { pin = HubClient.current().bluetoothPin(address, name, passkey); }
            catch (RuntimeException failure) { System.out.println("[apklayer] bluetooth PIN not asked: " + failure); }
            send("op", "pin", "address", address, "pin", pin == null ? JSONObject.NULL : pin);
        }, "apklayer-bluetooth-pin");
        asking.setDaemon(true);
        asking.start();
    }

    /** The session, opened on first use; null without a hub or when the hub refused (no Bluetooth permission). */
    static synchronized HostBluetooth get() {
        if (session != null || failed) return session;
        try {
            HubClient hub = HubClient.current();
            session = new HostBluetooth(hub.bluetooth(null, null));
        } catch (IOException | JSONException | RuntimeException none) {
            failed = true;
            System.out.println("[apklayer] bluetooth unavailable: " + none);
        }
        return session;
    }

    /** The adapter's last state (an absent one when there is no session). */
    static JSONObject adapter() {
        HostBluetooth s = get();
        return s == null ? new JSONObject() : s.adapter;
    }

    static boolean enabled() { JSONObject a = adapter(); return a.optBoolean("present") && a.optBoolean("powered"); }

    void listen(Consumer<JSONObject> listener) { listeners.add(listener); }
    void unlisten(Consumer<JSONObject> listener) { listeners.remove(listener); }

    synchronized void send(Object... keyValues) {
        try {
            JSONObject command = new JSONObject();
            for (int i = 0; i < keyValues.length; i += 2) command.put((String) keyValues[i], keyValues[i + 1]);
            commands.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            commands.flush();
        } catch (IOException | JSONException failure) { System.out.println("[apklayer] bluetooth command failed: " + failure); }
    }

    /** One BlueZ discovery for all the app's scans (BlueZ keeps one per client): LE only while every scan is LE; filtered by UUIDs only for a lone scan. */
    synchronized void scan(Object owner, String transport, List<String> uuids) {
        boolean was = !scans.isEmpty();
        scans.put(owner, transport);
        String merged = scans.values().stream().allMatch("le"::equals) ? "le" : "auto";
        if (was && scans.size() > 1) uuids = List.of();
        send("op", "scan", "transport", merged, "uuids", new JSONArray(uuids));
    }

    synchronized void unscan(Object owner) {
        if (scans.remove(owner) != null && scans.isEmpty()) send("op", "stopscan");
    }

    /** Android 12+: the runtime permission a call needs (apps targeting older releases hold the install-time BLUETOOTH/BLUETOOTH_ADMIN). */
    static void require(String permission) {
        Context app = RuntimeEnvironment.getApplication();
        if (targetSdk() < Build.VERSION_CODES.S) return;
        if (app.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Need " + permission + " permission for " + app.getAttributionSource() + ": Bluetooth");
    }

    private static int target = -1;
    // shortcut: ApplicationInfo.targetSdkVersion is Robolectric's manifest default (23) for every apklayer app (AppEnvironment's AndroidManifest has no
    // manifest file): the APK's own value is read here; drop this once AppEnvironment sets the real one
    static synchronized int targetSdk() {
        if (target < 0) {
            Context app = RuntimeEnvironment.getApplication();
            try { target = android.content.pm.PackageParser.parseApkLite(new java.io.File(app.getPackageCodePath()), 0).targetSdkVersion; }
            catch (Exception unreadable) { target = app.getApplicationInfo().targetSdkVersion; }
        }
        return target;
    }

    static void onMain(Runnable action) { new Handler(Looper.getMainLooper()).post(action); }

    static byte[] hex(String text) {
        byte[] out = new byte[text.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(text.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes == null ? new byte[0] : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }

    /** The BluetoothDevice of a helper's device record, with what BlueZ knows of it (name, type, class, bond state). */
    static BluetoothDevice device(JSONObject record) {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(record.optString("address").toUpperCase());
        ShadowBluetoothDevice shadow = Shadow.extract(device);
        if (!record.isNull("name")) shadow.setName(record.optString("name"));
        shadow.setType(switch (record.optString("type")) { case "le" -> BluetoothDevice.DEVICE_TYPE_LE; case "dual" -> BluetoothDevice.DEVICE_TYPE_DUAL; default -> BluetoothDevice.DEVICE_TYPE_CLASSIC; });
        if (!record.isNull("class")) shadow.setBluetoothClass(org.robolectric.util.ReflectionHelpers.callConstructor(android.bluetooth.BluetoothClass.class,
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class, record.optInt("class"))));
        if (record.has("paired")) shadow.setBondState(record.optBoolean("paired") ? BluetoothDevice.BOND_BONDED : BluetoothDevice.BOND_NONE);
        return device;
    }

    /** A scan result as the controller reports it: the advertisement rebuilt from BlueZ's fields (flags, service UUIDs, name, TX power, manufacturer and service data). */
    static ScanResult scanResult(JSONObject record) {
        ByteArrayOutputStream ad = new ByteArrayOutputStream();
        field(ad, 0x01, new byte[] {0x06});
        ByteArrayOutputStream short16 = new ByteArrayOutputStream(), long128 = new ByteArrayOutputStream();
        JSONArray uuids = record.optJSONArray("uuids");
        for (int i = 0; uuids != null && i < uuids.length(); i++) {
            UUID uuid = UUID.fromString(uuids.optString(i));
            if (isShort(uuid)) le(short16, (int) (uuid.getMostSignificantBits() >>> 32), 2); else le128(long128, uuid);
        }
        if (short16.size() > 0) field(ad, 0x03, short16.toByteArray());
        if (long128.size() > 0) field(ad, 0x07, long128.toByteArray());
        if (!record.isNull("name")) field(ad, 0x09, record.optString("name").getBytes(StandardCharsets.UTF_8));
        if (!record.isNull("tx")) field(ad, 0x0a, new byte[] {(byte) record.optInt("tx")});
        JSONObject mfr = record.optJSONObject("mfr"), sdata = record.optJSONObject("sdata");
        for (String id : mfr == null ? List.<String>of() : (Iterable<String>) mfr::keys) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            le(body, Integer.parseInt(id), 2); body.writeBytes(hex(mfr.optString(id)));
            field(ad, 0xff, body.toByteArray());
        }
        for (String id : sdata == null ? List.<String>of() : (Iterable<String>) sdata::keys) {
            UUID uuid = UUID.fromString(id);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            if (isShort(uuid)) le(body, (int) (uuid.getMostSignificantBits() >>> 32), 2); else le128(body, uuid);
            body.writeBytes(hex(sdata.optString(id)));
            field(ad, isShort(uuid) ? 0x16 : 0x21, body.toByteArray());
        }
        ScanRecord scanRecord = ScanRecord.parseFromBytes(ad.toByteArray());
        int tx = record.isNull("tx") ? ScanResult.TX_POWER_NOT_PRESENT : record.optInt("tx");
        return new ScanResult(device(record), 0x1b /* legacy, connectable, scannable */, 1, 0, ScanResult.SID_NOT_PRESENT, tx,
                record.optInt("rssi", -127), ScanResult.PERIODIC_INTERVAL_NOT_PRESENT, scanRecord, android.os.SystemClock.elapsedRealtimeNanos());
    }

    private static boolean isShort(UUID uuid) {
        return (uuid.getMostSignificantBits() & 0xFFFF0000FFFFFFFFL) == 0x1000L && uuid.getLeastSignificantBits() == 0x800000805F9B34FBL;
    }

    private static void field(ByteArrayOutputStream ad, int type, byte[] data) { ad.write(data.length + 1); ad.write(type); ad.writeBytes(data); }

    private static void le(ByteArrayOutputStream out, long value, int bytes) { for (int i = 0; i < bytes; i++) out.write((int) (value >>> (8 * i)) & 0xff); }

    private static void le128(ByteArrayOutputStream out, UUID uuid) { le(out, uuid.getLeastSignificantBits(), 8); le(out, uuid.getMostSignificantBits(), 8); }
}
