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

    @Implementation @Override protected BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback) { return gatt(callback); }
    @Implementation @Override protected BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback, int transport) { return gatt(callback); }
    @Implementation @Override protected BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback, int transport, int phy, Handler handler) {
        return gatt(callback);
    }

    // shortcut: autoConnect is a direct connection (BlueZ's Connect); upgrade to BlueZ's background connection (Device1.Connect on advertisement) when an app needs it
    private BluetoothGatt gatt(BluetoothGattCallback callback) {
        HostBluetooth.require(ShadowHostBluetoothAdapter.CONNECT);
        BluetoothGatt gatt = ShadowHostBluetoothGatt.create(real, callback);
        ((ShadowHostBluetoothGatt) Shadow.extract(gatt)).open(real.getAddress(), callback);
        return gatt;
    }

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

    @Implementation @Override protected BluetoothSocket createRfcommSocketToServiceRecord(UUID uuid) {
        HostBluetooth.require(ShadowHostBluetoothAdapter.CONNECT);
        return ShadowHostBluetoothSocket.create(real, uuid);
    }

    @Implementation protected BluetoothSocket createInsecureRfcommSocketToServiceRecord(UUID uuid) { return createRfcommSocketToServiceRecord(uuid); }
}
