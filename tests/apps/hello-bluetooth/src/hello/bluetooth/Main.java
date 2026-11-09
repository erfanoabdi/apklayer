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
import android.bluetooth.BluetoothServerSocket;
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
 * off and on (ACTION_STATE_CHANGED from the system), an RFCOMM socket to the paired device's Serial Port service (the mock echoes), one straight to a
 * channel (the hidden createRfcommSocket by reflection: a real AF_BLUETOOTH socket, which fails without that device), a server socket its own client
 * socket connects to, then three pairings: one the app answers itself (ACTION_PAIRING_REQUEST, setPin 0000), a legacy device whose PIN the person types into
 * the hub's dialog (the test types 1234), one whose passkey the person compares there (the test answers yes). Each step prints a "[bt]" line; "[bt] PASS" at the end.
 */
public final class Main extends Activity {
    private static final UUID BATTERY = uuid("180f"), LEVEL = uuid("2a19"), CCC = uuid("2902"), SPP = uuid("1101");
    private static final UUID ECHO = UUID.fromString("a1b2c3d4-0000-1000-8000-00805f9b34fb");
    private static final String PIN_DEVICE = "AA:BB:CC:00:00:01", SETPIN_DEVICE = "AA:BB:CC:00:00:02", COMPARE_DEVICE = "AA:BB:CC:00:00:03";
    private BluetoothAdapter adapter;
    private boolean found, sawOff;

    private static UUID uuid(String short16) { return UUID.fromString("0000" + short16 + "-0000-1000-8000-00805f9b34fb"); }
    private static void log(String line) { System.out.println("[bt] " + line); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (System.getProperty("bt.real") != null) { Real.start(this, System.getProperty("bt.real")); return; }  // against real devices (Real.java)
        adapter = getSystemService(BluetoothManager.class).getAdapter();
        log("feature le=" + getPackageManager().hasSystemFeature("android.hardware.bluetooth_le") + " scan-granted="
                + (checkSelfPermission("android.permission.BLUETOOTH_SCAN") == android.content.pm.PackageManager.PERMISSION_GRANTED));
        log("adapter state=" + adapter.getState() + " enabled=" + adapter.isEnabled() + " name=" + adapter.getName() + " address=" + adapter.getAddress());
        for (BluetoothDevice d : adapter.getBondedDevices()) log("bonded " + d.getAddress() + " " + d.getName() + " type=" + d.getType() + " bond=" + d.getBondState());
        IntentFilter filter = new IntentFilter();
        for (String a : new String[] {BluetoothDevice.ACTION_FOUND, BluetoothAdapter.ACTION_DISCOVERY_STARTED, BluetoothAdapter.ACTION_DISCOVERY_FINISHED,
                BluetoothDevice.ACTION_BOND_STATE_CHANGED, BluetoothAdapter.ACTION_STATE_CHANGED, BluetoothDevice.ACTION_PAIRING_REQUEST}) filter.addAction(a);
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                received(intent);
                if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(intent.getAction()) && SETPIN_DEVICE.equals(((BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)).getAddress())) {
                    log("setpin answered=" + ((BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)).setPin("0000".getBytes(StandardCharsets.UTF_8)));
                    abortBroadcast();  // the system's pairing dialog is not shown
                }
            }
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
                if (bond != BluetoothDevice.BOND_BONDED) return;
                switch (device.getAddress()) {
                    case SETPIN_DEVICE -> log("pin bond started=" + adapter.getRemoteDevice(PIN_DEVICE).createBond());
                    case PIN_DEVICE -> log("compare bond started=" + adapter.getRemoteDevice(COMPARE_DEVICE).createBond());
                    case COMPARE_DEVICE -> log("PASS");
                    default -> log("power off=" + adapter.disable());
                }
            }
            case BluetoothDevice.ACTION_PAIRING_REQUEST -> log("pairing-request " + device.getAddress() + " variant=" + intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1)
                    + " key=" + intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, -1));
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
    private static int pipes() {  // this process's open pipes: a closed socket's pipes must go
        int n = 0;
        for (java.io.File f : new java.io.File("/proc/self/fd").listFiles()) {
            try { if (java.nio.file.Files.readSymbolicLink(f.toPath()).toString().startsWith("pipe:")) n++; } catch (IOException gone) { }
        }
        return n;
    }

    private void rfcomm() {
        int before = pipes();
        try (BluetoothSocket socket = adapter.getRemoteDevice("11:22:33:44:55:66").createRfcommSocketToServiceRecord(SPP)) {
            socket.connect();
            log("rfcomm connected=" + socket.isConnected());
            socket.getOutputStream().write("ping\n".getBytes(StandardCharsets.UTF_8));
            byte[] got = new byte[64];
            int n = socket.getInputStream().read(got);
            log("rfcomm read " + (n > 0 ? new String(got, 0, n, StandardCharsets.UTF_8).trim() : "nothing"));
        } catch (IOException failed) { log("rfcomm failed " + failed); }
        BluetoothDevice paired = adapter.getRemoteDevice("11:22:33:44:55:66");
        try (BluetoothSocket socket = (BluetoothSocket) paired.getClass().getMethod("createRfcommSocket", int.class).invoke(paired, 3)) {
            socket.connect();
            log("rfcomm channel connected=" + socket.isConnected());
        } catch (IOException failed) { log("rfcomm channel 3 failed " + failed.getMessage()); }
        catch (ReflectiveOperationException missing) { log("rfcomm channel missing " + missing); }
        server();
        log("rfcomm pipes left=" + (pipes() - before));
        log("setpin bond started=" + adapter.getRemoteDevice(SETPIN_DEVICE).createBond());
    }

    /** A server socket as chat and serial-bridge apps use it: a timed-out accept, the connection its own client socket makes, close waking an accept. */
    private void server() {
        try {
            BluetoothServerSocket server = adapter.listenUsingInsecureRfcommWithServiceRecord("apklayer-echo", ECHO);
            try { server.accept(300); log("server accept returned"); } catch (IOException timeout) { log("server accept timeout " + timeout.getMessage()); }
            Thread client = new Thread(() -> {
                try (BluetoothSocket socket = adapter.getRemoteDevice("11:22:33:44:55:66").createRfcommSocketToServiceRecord(ECHO)) {
                    socket.connect();
                    socket.getOutputStream().write("hello\n".getBytes(StandardCharsets.UTF_8));
                    byte[] got = new byte[64];
                    int n = socket.getInputStream().read(got);
                    log("server client read " + (n > 0 ? new String(got, 0, n, StandardCharsets.UTF_8).trim() : "nothing"));
                } catch (IOException failed) { log("server client failed " + failed); }
            });
            client.start();
            try (BluetoothSocket peer = server.accept()) {
                log("server accepted " + peer.getRemoteDevice().getAddress() + " connected=" + peer.isConnected());
                byte[] got = new byte[64];
                int n = peer.getInputStream().read(got);
                peer.getOutputStream().write(("got:" + new String(got, 0, Math.max(n, 0), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
                client.join();
            }
            Thread waiting = new Thread(() -> {
                try { server.accept(); log("server accept after close returned"); } catch (IOException closed) { log("server close woke accept " + closed.getMessage()); }
            });
            waiting.start();
            Thread.sleep(300);
            server.close();
            waiting.join(5000);
        } catch (IOException | InterruptedException failed) { log("server failed " + failed); }
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
