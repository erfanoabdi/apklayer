package hello.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The same app against real devices (no mock): a role from -Dbt.real (APKLAYER_JOPTS), its peer from -Dbt.peer, its target (a UUID or an RFCOMM channel)
 * from -Dbt.target, -Dbt.secure=false for the insecure calls. Roles: scan (classic discovery, then an unfiltered LE scan), pair (createBond; -Dbt.pin=N
 * answers ACTION_PAIRING_REQUEST with setPin, -Dbt.abort=true aborts it so no dialog asks), server (accepts -Dbt.accepts connections, echoes a line each),
 * client (connects, sends a line, reads the echo; -Dbt.retry=<ms> retries meanwhile), gatt (-Dbt.auto=true: autoConnect; services, MTU, reads, a write, a notification). "[bt]" lines as Main.
 */
final class Real {
    private static void log(String line) { System.out.println("[bt] " + line); }

    static void start(Activity activity, String role) {
        BluetoothAdapter adapter = activity.getSystemService(BluetoothManager.class).getAdapter();
        String peer = System.getProperty("bt.peer", ""), target = System.getProperty("bt.target", "");
        boolean secure = !"false".equals(System.getProperty("bt.secure"));
        log("real " + role + " adapter=" + adapter.getAddress() + " state=" + adapter.getState() + " peer=" + peer + " target=" + target + " secure=" + secure);
        IntentFilter filter = new IntentFilter();
        for (String a : new String[] {BluetoothDevice.ACTION_FOUND, BluetoothAdapter.ACTION_DISCOVERY_FINISHED, BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                BluetoothDevice.ACTION_PAIRING_REQUEST}) filter.addAction(a);
        Set<String> seen = new HashSet<>();
        activity.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (intent.getAction()) {
                    case BluetoothDevice.ACTION_FOUND -> {
                        if (seen.add(device.getAddress())) log("found " + device.getAddress() + " name=" + intent.getStringExtra(BluetoothDevice.EXTRA_NAME) + " type=" + device.getType());
                    }
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> { log("discovery-finished found=" + seen.size()); if (role.equals("scan")) le(adapter); }
                    case BluetoothDevice.ACTION_BOND_STATE_CHANGED -> log("bond " + device.getAddress() + " " + intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                            + "->" + intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1));
                    case BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                        log("pairing-request " + device.getAddress() + " variant=" + intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
                                + " key=" + intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1));
                        String pin = System.getProperty("bt.pin");
                        if (pin != null) log("setpin answered=" + device.setPin(pin.getBytes(StandardCharsets.UTF_8)));
                        if (pin != null || "true".equals(System.getProperty("bt.abort"))) abortBroadcast();
                    }
                    default -> { }
                }
            }
        }, filter, Context.RECEIVER_EXPORTED);
        new Thread(() -> {  // once the app's start settled (a cold start on a phone takes seconds): its own broadcasts reach its receiver
            try { Thread.sleep(Integer.getInteger("bt.delay", 5000)); } catch (InterruptedException ignored) { }
            activity.runOnUiThread(() -> act(activity, adapter, role, peer, target, secure));
        }).start();
    }

    private static void act(Activity activity, BluetoothAdapter adapter, String role, String peer, String target, boolean secure) {
        switch (role) {
            case "scan" -> log("discovery started=" + adapter.startDiscovery());
            case "pair" -> log("bond started=" + adapter.getRemoteDevice(peer).createBond());
            case "server" -> new Thread(() -> server(adapter, UUID.fromString(target), secure)).start();
            case "client" -> new Thread(() -> client(adapter.getRemoteDevice(peer), target, secure)).start();
            case "gatt" -> adapter.getRemoteDevice(peer).connectGatt(activity, "true".equals(System.getProperty("bt.auto")), gatt(), BluetoothDevice.TRANSPORT_LE);
            default -> log("unknown role " + role);
        }
    }

    private static void le(BluetoothAdapter adapter) {
        Set<String> seen = new HashSet<>();
        ScanCallback callback = new ScanCallback() {
            @Override public void onScanResult(int type, ScanResult result) {
                if (seen.add(result.getDevice().getAddress())) log("scan " + result.getDevice().getAddress() + " name=" + result.getScanRecord().getDeviceName()
                        + " rssi=" + result.getRssi() + " uuids=" + result.getScanRecord().getServiceUuids());
            }
            @Override public void onScanFailed(int error) { log("scan failed " + error); }
        };
        adapter.getBluetoothLeScanner().startScan(callback);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> { adapter.getBluetoothLeScanner().stopScan(callback); log("scan-done le=" + seen.size()); }, 10000);
    }

    private static void server(BluetoothAdapter adapter, UUID uuid, boolean secure) {
        int accepts = Integer.getInteger("bt.accepts", 1);
        try (BluetoothServerSocket server = secure ? adapter.listenUsingRfcommWithServiceRecord("apklayer-real", uuid)
                : adapter.listenUsingInsecureRfcommWithServiceRecord("apklayer-real", uuid)) {
            log("server listening");
            for (int i = 0; i < accepts; i++) {
                try (BluetoothSocket peer = server.accept(60000)) {
                    log("server accepted " + peer.getRemoteDevice().getAddress() + " connected=" + peer.isConnected());
                    byte[] got = new byte[256];
                    int n = peer.getInputStream().read(got);
                    String line = n > 0 ? new String(got, 0, n, StandardCharsets.UTF_8).trim() : "";
                    log("server read " + line);
                    peer.getOutputStream().write(("echo:" + line + "\n").getBytes(StandardCharsets.UTF_8));
                    peer.getInputStream().read(got);  // until the client closes
                }
            }
        } catch (IOException failed) { log("server failed " + failed.getMessage()); }
        log("server done");
    }

    private static void client(BluetoothDevice device, String target, boolean secure) {
        try {
            BluetoothSocket socket;
            if (target.matches("\\d+")) socket = (BluetoothSocket) device.getClass().getMethod(secure ? "createRfcommSocket" : "createInsecureRfcommSocket", int.class)
                    .invoke(device, Integer.parseInt(target));
            else socket = secure ? device.createRfcommSocketToServiceRecord(UUID.fromString(target)) : device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(target));
            try (socket) {
                socket.connect();
                log("client connected=" + socket.isConnected());
                socket.getOutputStream().write(("ping " + Integer.getInteger("bt.n", 1) + "\n").getBytes(StandardCharsets.UTF_8));
                byte[] got = new byte[256];
                int n = socket.getInputStream().read(got);
                log("client read " + (n > 0 ? new String(got, 0, n, StandardCharsets.UTF_8).trim() : "nothing"));
            }
        } catch (IOException failed) {
            log("client failed " + failed.getMessage());
            if (System.currentTimeMillis() < retryUntil) {  // -Dbt.retry=<ms>: the server on the other phone may not be up yet (a cold start there)
                try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
                client(device, target, secure);
                return;
            }
        }
        catch (ReflectiveOperationException missing) { log("client missing " + missing); }
        log("client done");
    }

    private static final long retryUntil = System.currentTimeMillis() + Integer.getInteger("bt.retry", 0);

    private static BluetoothGattCallback gatt() {
        ArrayDeque<BluetoothGattCharacteristic> reads = new ArrayDeque<>();
        return new BluetoothGattCallback() {
            BluetoothGattCharacteristic notified;
            @Override public void onConnectionStateChange(BluetoothGatt g, int status, int state) {
                log("gatt state=" + state + " status=" + status);
                if (state == BluetoothProfile.STATE_CONNECTED) log("mtu requested=" + g.requestMtu(517));
            }
            @Override public void onMtuChanged(BluetoothGatt g, int mtu, int status) { log("mtu " + mtu + " status=" + status); g.discoverServices(); }
            @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
                for (BluetoothGattService s : g.getServices()) {
                    log("service " + s.getUuid() + " chars=" + s.getCharacteristics().size());
                    for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                        log("char " + c.getUuid() + " props=" + c.getProperties());
                        if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) != 0) reads.add(c);
                        if ((c.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 && notified == null) notified = c;
                    }
                }
                next(g);
            }
            private void next(BluetoothGatt g) {
                BluetoothGattCharacteristic c = reads.poll();
                if (c != null) { g.readCharacteristic(c); return; }
                String write = System.getProperty("bt.write");  // <char uuid>=<text>
                if (write != null) {
                    for (BluetoothGattService s : g.getServices()) {
                        BluetoothGattCharacteristic w = s.getCharacteristic(UUID.fromString(write.split("=")[0]));
                        if (w != null) { log("write started=" + g.writeCharacteristic(w, write.split("=")[1].getBytes(StandardCharsets.UTF_8), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)); return; }
                    }
                }
                subscribe(g);
            }
            private void subscribe(BluetoothGatt g) {
                if (notified == null) { log("gatt done"); g.disconnect(); return; }
                g.setCharacteristicNotification(notified, true);
                BluetoothGattDescriptor ccc = notified.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                log("ccc started=" + (ccc == null ? "none" : g.writeDescriptor(ccc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)));
            }
            @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value, int status) {
                log("read " + c.getUuid() + " status=" + status + " value=" + new String(value, StandardCharsets.UTF_8).replaceAll("\\p{Cntrl}", "."));
                next(g);
            }
            @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
                log("write " + c.getUuid() + " status=" + status);
                System.clearProperty("bt.write");
                next(g);
            }
            @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) { log("ccc written status=" + status); }
            @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
                log("notify " + c.getUuid() + " value=" + new String(value, StandardCharsets.UTF_8));
                g.setCharacteristicNotification(c, false);
                log("gatt done");
                g.disconnect();
            }
        };
    }
}
