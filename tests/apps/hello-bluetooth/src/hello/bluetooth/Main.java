package hello.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.ParcelUuid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Bluetooth as an app uses it, against the bluetooth group's mock BlueZ (tests/vm/bluez-mock.py): adapter state and bonded devices, classic discovery,
 * an LE scan filtered by the Battery service, a GATT connection that reads Battery Level and gets its notification, bonding, then the adapter switched
 * off and on (ACTION_STATE_CHANGED from the system), an RFCOMM socket to the paired device's Serial Port service (the mock echoes), and bonding a
 * legacy device whose PIN the person types into the hub's dialog (the test types 1234). Each step prints a "[bt]" line; "[bt] PASS" at the end.
 */
public final class Main extends Activity {
    private static final UUID BATTERY = uuid("180f"), LEVEL = uuid("2a19"), CCC = uuid("2902"), SPP = uuid("1101");
    private static final String PIN_DEVICE = "AA:BB:CC:00:00:01";
    private BluetoothAdapter adapter;
    private boolean found, sawOff;

    private static UUID uuid(String short16) { return UUID.fromString("0000" + short16 + "-0000-1000-8000-00805f9b34fb"); }
    private static void log(String line) { System.out.println("[bt] " + line); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        adapter = getSystemService(BluetoothManager.class).getAdapter();
        log("feature le=" + getPackageManager().hasSystemFeature("android.hardware.bluetooth_le") + " scan-granted="
                + (checkSelfPermission("android.permission.BLUETOOTH_SCAN") == android.content.pm.PackageManager.PERMISSION_GRANTED));
        log("adapter state=" + adapter.getState() + " enabled=" + adapter.isEnabled() + " name=" + adapter.getName() + " address=" + adapter.getAddress());
        for (BluetoothDevice d : adapter.getBondedDevices()) log("bonded " + d.getAddress() + " " + d.getName() + " type=" + d.getType() + " bond=" + d.getBondState());
        IntentFilter filter = new IntentFilter();
        for (String a : new String[] {BluetoothDevice.ACTION_FOUND, BluetoothAdapter.ACTION_DISCOVERY_STARTED, BluetoothAdapter.ACTION_DISCOVERY_FINISHED,
                BluetoothDevice.ACTION_BOND_STATE_CHANGED, BluetoothAdapter.ACTION_STATE_CHANGED}) filter.addAction(a);
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { received(intent); }
        }, filter, Context.RECEIVER_EXPORTED);
        try {
            log("discovery started=" + adapter.startDiscovery());
        } catch (SecurityException denied) { log("discovery SecurityException " + denied.getMessage()); }
    }

    private void received(Intent intent) {
        String action = intent.getAction();
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        switch (action) {
            case BluetoothAdapter.ACTION_DISCOVERY_STARTED -> log("discovery-started");
            case BluetoothDevice.ACTION_FOUND -> {
                log("found " + device.getAddress() + " name=" + intent.getStringExtra(BluetoothDevice.EXTRA_NAME) + " rssi=" + intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (short) 0));
                if (!found) { found = true; adapter.cancelDiscovery(); }
            }
            case BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> { log("discovery-finished discovering=" + adapter.isDiscovering()); scan(); }
            case BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                int bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                log("bond " + device.getAddress() + " " + intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1) + "->" + bond);
                if (bond == BluetoothDevice.BOND_BONDED && PIN_DEVICE.equals(device.getAddress())) log("PASS");
                else if (bond == BluetoothDevice.BOND_BONDED) log("power off=" + adapter.disable());
            }
            case BluetoothAdapter.ACTION_STATE_CHANGED -> {
                int now = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                log("state " + intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1) + "->" + now + " enabled=" + adapter.isEnabled());
                if (now == BluetoothAdapter.STATE_OFF) { sawOff = true; log("power on=" + adapter.enable()); }
                if (now == BluetoothAdapter.STATE_ON && sawOff) new Thread(this::rfcomm).start();
            }
            default -> { }
        }
    }

    /** A blocking socket, as serial-port apps use it: connect, write, read the echo; then the PIN pairing. */
    private void rfcomm() {
        try (BluetoothSocket socket = adapter.getRemoteDevice("11:22:33:44:55:66").createRfcommSocketToServiceRecord(SPP)) {
            socket.connect();
            log("rfcomm connected=" + socket.isConnected());
            socket.getOutputStream().write("ping\n".getBytes(StandardCharsets.UTF_8));
            byte[] got = new byte[64];
            int n = socket.getInputStream().read(got);
            log("rfcomm read " + (n > 0 ? new String(got, 0, n, StandardCharsets.UTF_8).trim() : "nothing"));
        } catch (IOException failed) { log("rfcomm failed " + failed); }
        log("pin bond started=" + adapter.getRemoteDevice(PIN_DEVICE).createBond());
    }

    private void scan() {
        ScanFilter battery = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(BATTERY)).build();
        adapter.getBluetoothLeScanner().startScan(List.of(battery), new ScanSettings.Builder().build(), new ScanCallback() {
            @Override public void onScanResult(int type, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                byte[] mfr = result.getScanRecord().getManufacturerSpecificData(0xffff);
                log("scan " + device.getAddress() + " name=" + result.getScanRecord().getDeviceName() + " rssi=" + result.getRssi() + " type=" + device.getType()
                        + " mfr=" + (mfr == null ? null : java.util.HexFormat.of().formatHex(mfr)));
                adapter.getBluetoothLeScanner().stopScan(this);
                device.connectGatt(Main.this, false, gatt, BluetoothDevice.TRANSPORT_LE);
            }
            @Override public void onScanFailed(int error) { log("scan failed " + error); }
        });
    }

    private final BluetoothGattCallback gatt = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int state) {
            log("gatt state=" + state + " status=" + status);
            if (state == BluetoothProfile.STATE_CONNECTED) g.discoverServices();
        }
        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            log("services " + g.getServices().size() + " battery=" + (g.getService(BATTERY) != null));
            log("read started=" + g.readCharacteristic(g.getService(BATTERY).getCharacteristic(LEVEL)));
        }
        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value, int status) {
            log("read " + c.getUuid() + " value=" + (value.length > 0 ? value[0] : -1) + " status=" + status);
            g.setCharacteristicNotification(c, true);
            g.writeDescriptor(c.getDescriptor(CCC), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        }
        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) { log("ccc written status=" + status); }
        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
            log("notify " + c.getUuid() + " value=" + value[0]);
            g.setCharacteristicNotification(c, false);
            log("bond started=" + g.getDevice().createBond());
        }
    };
}
