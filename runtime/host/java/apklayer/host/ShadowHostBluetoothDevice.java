package apklayer.host;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import java.util.UUID;
import java.util.function.Consumer;
import org.json.JSONObject;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowBluetoothDevice;

/** BluetoothDevice over BlueZ: GATT connections (ShadowHostBluetoothGatt), bonding through BlueZ's Pair with ACTION_BOND_STATE_CHANGED, RFCOMM sockets. */
@Implements(className = "android.bluetooth.BluetoothDevice", isInAndroidSdk = false)
public class ShadowHostBluetoothDevice extends ShadowBluetoothDevice {
    @RealObject private BluetoothDevice real;
    private Consumer<JSONObject> bonding;

    @Implementation @Override protected BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback) { return gatt(callback, autoConnect); }
    @Implementation @Override protected BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback, int transport) { return gatt(callback, autoConnect); }
    @Implementation @Override protected BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback, int transport, int phy, Handler handler) {
        return gatt(callback, autoConnect);
    }

    /** autoConnect: the helper connects whenever the device is seen advertising (an LE discovery runs meanwhile), with no timeout; a direct connection gives up after 30 s. */
    private BluetoothGatt gatt(BluetoothGattCallback callback, boolean auto) {
        HostBluetooth.require(ShadowHostBluetoothAdapter.CONNECT);
        BluetoothGatt gatt = ShadowHostBluetoothGatt.create(real, callback);
        ((ShadowHostBluetoothGatt) Shadow.extract(gatt)).open(real.getAddress(), callback, auto);
        return gatt;
    }

    /** A device unparcelled from an intent (ACTION_FOUND's EXTRA_DEVICE) is a new object: it takes what BlueZ last told of its address (HostBluetooth.KNOWN). */
    private void fill() {
        JSONObject record = super.getType() == BluetoothDevice.DEVICE_TYPE_UNKNOWN ? HostBluetooth.KNOWN.get(real.getAddress()) : null;
        if (record != null) HostBluetooth.fill(this, record);
    }
    @Implementation @Override protected String getName() { fill(); return super.getName(); }
    @Implementation @Override protected int getType() { fill(); return super.getType(); }
    @Implementation @Override protected int getBondState() { fill(); return super.getBondState(); }
    @Implementation @Override public android.bluetooth.BluetoothClass getBluetoothClass() { fill(); return super.getBluetoothClass(); }

    @Implementation @Override protected boolean createBond() {
        HostBluetooth.require(ShadowHostBluetoothAdapter.CONNECT);
        HostBluetooth session = HostBluetooth.get();
        if (session == null || !HostBluetooth.enabled()) return false;
        synchronized (this) {
            if (bonding == null) {
                bonding = event -> {
                    if (!"bond".equals(event.optString("ev")) || !real.getAddress().equalsIgnoreCase(event.optString("address"))) return;
                    int previous = getBondState(), state = event.optInt("state");
                    if (state == previous) return;
                    setBondState(state);
                    RuntimeEnvironment.getApplication().sendBroadcast(new Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED).putExtra(BluetoothDevice.EXTRA_DEVICE, real)
                            .putExtra(BluetoothDevice.EXTRA_BOND_STATE, state).putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, previous));
                };
                session.listen(bonding);
            }
        }
        session.send("op", "bond", "address", real.getAddress());
        return true;
    }

    @Implementation @Override protected boolean createBond(int transport) { return createBond(); }

    @Implementation @Override protected boolean removeBond() {
        HostBluetooth.require(ShadowHostBluetoothAdapter.CONNECT);
        HostBluetooth session = HostBluetooth.get();
        if (session == null) return false;
        session.send("op", "unbond", "address", real.getAddress());
        return true;
    }

    @Implementation @Override protected BluetoothSocket createRfcommSocketToServiceRecord(UUID uuid) { return socket(uuid.toString(), true); }

    @Implementation protected BluetoothSocket createInsecureRfcommSocketToServiceRecord(UUID uuid) { return socket(uuid.toString(), false); }

    /** Secure: BlueZ authenticates (pairs first) and encrypts the link (BT_SECURITY medium); insecure: neither is asked (low), as Android's. */
    private BluetoothSocket socket(String target, boolean secure) {
        HostBluetooth.require(ShadowHostBluetoothAdapter.CONNECT);
        return ShadowHostBluetoothSocket.create(real, target, secure);
    }

    /** The hidden calls serial apps fall back to (by reflection) when a device's SDP record is missing: straight to the RFCOMM channel. */
    @Implementation protected BluetoothSocket createRfcommSocket(int channel) { return socket(Integer.toString(channel), true); }

    @Implementation protected BluetoothSocket createInsecureRfcommSocket(int channel) { return socket(Integer.toString(channel), false); }

    /** An app's own answer to ACTION_PAIRING_REQUEST (HostBluetooth.pairingRequest): the PIN or passkey BlueZ waits for. */
    @Implementation @Override protected boolean setPin(byte[] pin) {
        HostBluetooth.require(ShadowHostBluetoothAdapter.CONNECT);
        super.setPin(pin);
        HostBluetooth session = HostBluetooth.get();
        return session != null && pin != null && session.answer(real.getAddress(), new String(pin, java.nio.charset.StandardCharsets.UTF_8));
    }
}
