package apklayer.host;

import android.app.PendingIntent;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.json.JSONObject;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowBluetoothLeScanner;

/**
 * BluetoothLeScanner over BlueZ's LE discovery (HostBluetooth.scan, with the filters' service UUIDs as BlueZ's discovery filter): every advertisement BlueZ
 * reports that matches the app's ScanFilters reaches onScanResult on the main thread, as the framework's scanner delivers it, or, for a PendingIntent scan,
 * the intent's EXTRA_LIST_SCAN_RESULT.
 */
@Implements(className = "android.bluetooth.le.BluetoothLeScanner", isInAndroidSdk = false)
public class ShadowHostBluetoothLeScanner extends ShadowBluetoothLeScanner {
    private final Map<Object, Consumer<JSONObject>> scans = new HashMap<>();  // by ScanCallback or PendingIntent

    @Implementation @Override protected void startScan(List<ScanFilter> filters, ScanSettings settings, ScanCallback callback) {
        if (callback == null) throw new IllegalArgumentException("callback is null");
        if (!begin(callback, filters, result -> callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)))
            HostBluetooth.onMain(() -> callback.onScanFailed(ScanCallback.SCAN_FAILED_ALREADY_STARTED));
    }

    /** As the framework's PendingIntent scan: each match is sent as EXTRA_LIST_SCAN_RESULT (one result) with EXTRA_CALLBACK_TYPE; 0 on success. */
    @Implementation @Override protected int startScan(List<ScanFilter> filters, ScanSettings settings, PendingIntent callbackIntent) {
        if (callbackIntent == null) throw new IllegalArgumentException("callbackIntent is null");
        boolean started = begin(callbackIntent, filters, result -> {
            try {
                callbackIntent.send(RuntimeEnvironment.getApplication(), 0, new Intent().putExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .putParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, new ArrayList<>(List.of(result))));
            } catch (PendingIntent.CanceledException cancelled) { end(callbackIntent); }  // a cancelled PendingIntent ends its scan
        });
        return started ? 0 : ScanCallback.SCAN_FAILED_ALREADY_STARTED;
    }

    /** One scan for a callback or a PendingIntent: false if {@code key} already scans. */
    private boolean begin(Object key, List<ScanFilter> filters, Consumer<ScanResult> deliver) {
        HostBluetooth.require(ShadowHostBluetoothAdapter.SCAN);
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
            HostBluetooth.onMain(() -> { synchronized (scans) { if (scans.get(key) == null) return; } deliver.accept(result); });
        };
        synchronized (scans) { if (scans.putIfAbsent(key, results) != null) return false; }
        session.listen(results);
        session.scan(key, "le", uuids);
        return true;
    }

    @Implementation @Override protected void stopScan(ScanCallback callback) { end(callback); }

    @Implementation @Override protected void stopScan(PendingIntent callbackIntent) { end(callbackIntent); }

    private void end(Object key) {
        HostBluetooth.require(ShadowHostBluetoothAdapter.SCAN);
        Consumer<JSONObject> results;
        synchronized (scans) { results = scans.remove(key); }
        HostBluetooth session = HostBluetooth.get();
        if (results == null || session == null) return;
        session.unlisten(results);
        session.unscan(key);
    }
}
