package apklayer.host;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.ParcelUuid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowBluetoothAdapter;

/**
 * BluetoothAdapter from the host's BlueZ (HostBluetooth): state, name, bonded devices, classic discovery with its broadcasts (ACTION_DISCOVERY_STARTED,
 * ACTION_FOUND, ACTION_DISCOVERY_FINISHED after Android's 12 s inquiry) and the legacy LE scan on top of BluetoothLeScanner, as the framework does it.
 */
@Implements(className = "android.bluetooth.BluetoothAdapter", isInAndroidSdk = false)
public class ShadowHostBluetoothAdapter extends ShadowBluetoothAdapter {
    static final String SCAN = "android.permission.BLUETOOTH_SCAN", CONNECT = "android.permission.BLUETOOTH_CONNECT";
    private static final long INQUIRY_MS = 12_000;
    @RealObject private BluetoothAdapter real;
    private volatile Consumer<JSONObject> discovery;
    private final Map<BluetoothAdapter.LeScanCallback, ScanCallback> leScans = new HashMap<>();

    @Implementation @Override protected int getState() { return HostBluetooth.enabled() ? BluetoothAdapter.STATE_ON : BluetoothAdapter.STATE_OFF; }
    @Implementation @Override protected boolean isEnabled() { return getState() == BluetoothAdapter.STATE_ON; }
    @Implementation @Override public int getLeState() { return getState(); }

    @Implementation @Override protected String getName() {
        HostBluetooth.require(CONNECT);
        return HostBluetooth.adapter().optString("name", null);
    }

    /** As Android since 6.0: apps without LOCAL_MAC_ADDRESS (a system permission) see this constant, not the adapter's address. */
    @Implementation @Override protected String getAddress() {
        HostBluetooth.require(CONNECT);
        return "02:00:00:00:00:00";
    }

    @Implementation @Override protected Set<BluetoothDevice> getBondedDevices() {
        HostBluetooth.require(CONNECT);
        Set<BluetoothDevice> bonded = new LinkedHashSet<>();
        if (!isEnabled()) return bonded;  // Android: an empty set while off
        JSONArray devices = HostBluetooth.adapter().optJSONArray("bonded");
        for (int i = 0; devices != null && i < devices.length(); i++) bonded.add(HostBluetooth.device(devices.optJSONObject(i)));
        return bonded;
    }

    /** Apps targeting Android 13+ may not switch Bluetooth (false, as there); older ones switch BlueZ's adapter. */
    @Implementation @Override protected boolean enable() { return power(true); }
    @Implementation @Override protected boolean disable() { return power(false); }
    @Implementation @Override protected boolean disable(boolean persist) { return power(false); }

    private boolean power(boolean on) {
        HostBluetooth.require(CONNECT);
        HostBluetooth session = HostBluetooth.get();
        if (session == null || RuntimeEnvironment.getApplication().getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.TIRAMISU) return false;
        session.send("op", "power", "on", on);
        return true;
    }

    @Implementation @Override protected boolean startDiscovery() {
        HostBluetooth.require(SCAN);
        HostBluetooth session = HostBluetooth.get();
        if (session == null || !isEnabled()) return false;
        if (discovery != null) return true;
        Context app = RuntimeEnvironment.getApplication();
        Consumer<JSONObject> found = event -> {
            if (!"found".equals(event.optString("ev"))) return;
            JSONObject record = event.optJSONObject("device");
            Intent intent = new Intent(BluetoothDevice.ACTION_FOUND).putExtra(BluetoothDevice.EXTRA_DEVICE, HostBluetooth.device(record))
                    .putExtra(BluetoothDevice.EXTRA_RSSI, (short) record.optInt("rssi", -127));
            if (!record.isNull("name")) intent.putExtra(BluetoothDevice.EXTRA_NAME, record.optString("name"));
            app.sendBroadcast(intent);
        };
        discovery = found;
        session.listen(found);
        session.scan(this, "auto", List.of());
        app.sendBroadcast(new Intent(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> { if (discovery == found) cancelDiscovery(); }, INQUIRY_MS);
        return true;
    }

    @Implementation @Override protected boolean cancelDiscovery() {
        HostBluetooth.require(SCAN);
        Consumer<JSONObject> found = discovery;
        HostBluetooth session = HostBluetooth.get();
        if (found == null || session == null) return true;
        discovery = null;
        session.unlisten(found);
        session.unscan(this);
        RuntimeEnvironment.getApplication().sendBroadcast(new Intent(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        return true;
    }

    @Implementation @Override protected boolean isDiscovering() { return discovery != null; }

    /** The deprecated LE scan: a BluetoothLeScanner scan with the UUIDs as filters, its results as onLeScan (BluetoothAdapter.startLeScan does the same). */
    @Implementation @Override protected boolean startLeScan(UUID[] serviceUuids, BluetoothAdapter.LeScanCallback callback) {
        if (!isEnabled() || callback == null) return false;
        List<ScanFilter> filters = new ArrayList<>();
        for (UUID uuid : serviceUuids == null ? new UUID[0] : serviceUuids) filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(uuid)).build());
        ScanCallback wrapper = new ScanCallback() {
            @Override public void onScanResult(int type, ScanResult result) {
                callback.onLeScan(result.getDevice(), result.getRssi(), result.getScanRecord() == null ? null : result.getScanRecord().getBytes());
            }
        };
        synchronized (leScans) { if (leScans.putIfAbsent(callback, wrapper) != null) return false; }
        real.getBluetoothLeScanner().startScan(filters, new ScanSettings.Builder().build(), wrapper);
        return true;
    }

    @Implementation @Override protected boolean startLeScan(BluetoothAdapter.LeScanCallback callback) { return startLeScan(null, callback); }

    @Implementation @Override protected void stopLeScan(BluetoothAdapter.LeScanCallback callback) {
        ScanCallback wrapper;
        synchronized (leScans) { wrapper = leScans.remove(callback); }
        if (wrapper != null && real.getBluetoothLeScanner() != null) real.getBluetoothLeScanner().stopScan(wrapper);
    }
}
