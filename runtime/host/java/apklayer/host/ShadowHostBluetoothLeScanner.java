package apklayer.host;

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.json.JSONObject;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowBluetoothLeScanner;

/**
 * BluetoothLeScanner over BlueZ's LE discovery (HostBluetooth.scan, with the filters' service UUIDs as BlueZ's discovery filter): every advertisement BlueZ
 * reports that matches the app's ScanFilters reaches onScanResult on the main thread, as the framework's scanner delivers it.
 */
@Implements(className = "android.bluetooth.le.BluetoothLeScanner", isInAndroidSdk = false)
public class ShadowHostBluetoothLeScanner extends ShadowBluetoothLeScanner {
    private final Map<ScanCallback, Consumer<JSONObject>> scans = new HashMap<>();

    @Implementation @Override protected void startScan(List<ScanFilter> filters, ScanSettings settings, ScanCallback callback) {
        HostBluetooth.require(ShadowHostBluetoothAdapter.SCAN);
        if (callback == null) throw new IllegalArgumentException("callback is null");
        HostBluetooth session = HostBluetooth.get();
        if (session == null || !HostBluetooth.enabled()) throw new IllegalStateException("BT Adapter is not turned ON");
        List<ScanFilter> matching = filters == null ? List.of() : List.copyOf(filters);
        List<String> uuids = new ArrayList<>();
        for (ScanFilter filter : matching) if (filter.getServiceUuid() != null) uuids.add(filter.getServiceUuid().toString());
        if (uuids.size() != matching.size()) uuids.clear();  // a filter without a service UUID: BlueZ reports everything, the filters below choose
        Consumer<JSONObject> results = event -> {
            if (!"found".equals(event.optString("ev")) || "bredr".equals(event.optJSONObject("device").optString("type"))) return;
            ScanResult result = HostBluetooth.scanResult(event.optJSONObject("device"));
            if (!matching.isEmpty() && matching.stream().noneMatch(filter -> filter.matches(result))) return;
            HostBluetooth.onMain(() -> { synchronized (scans) { if (scans.get(callback) == null) return; } callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result); });
        };
        synchronized (scans) {
            if (scans.putIfAbsent(callback, results) != null) { HostBluetooth.onMain(() -> callback.onScanFailed(ScanCallback.SCAN_FAILED_ALREADY_STARTED)); return; }
        }
        session.listen(results);
        session.scan(callback, "le", uuids);
    }

    // shortcut: scans delivered by PendingIntent keep Robolectric's bookkeeping (no results); upgrade when an app needs background scans
    @Implementation @Override protected void stopScan(ScanCallback callback) {
        HostBluetooth.require(ShadowHostBluetoothAdapter.SCAN);
        Consumer<JSONObject> results;
        synchronized (scans) { results = scans.remove(callback); }
        HostBluetooth session = HostBluetooth.get();
        if (results == null || session == null) return;
        session.unlisten(results);
        session.unscan(callback);
    }
}
