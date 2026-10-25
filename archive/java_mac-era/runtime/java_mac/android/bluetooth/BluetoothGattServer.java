// android.bluetooth.BluetoothGattServer -- this device acting as a BLE GATT
// peripheral.
//
// NO PUBLIC CONSTRUCTOR on AOSP either: the only source is
// BluetoothManager.openGattServer(), which returns NULL here because there is
// no Bluetooth bridge (see that method, and BluetoothAdapter). So no instance
// is ever handed out and every method below is unreachable.
//
// The type is here because openGattServer()'s return descriptor names it: a
// caller's invokevirtual site cannot resolve without the class, so a BLE
// peripheral app would fail to link long before it discovered it had no
// Bluetooth.
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/BluetoothGattServer.java.
package android.bluetooth;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class BluetoothGattServer implements BluetoothProfile {

    BluetoothGattServer() {}

    public void close() {}

    public boolean connect(BluetoothDevice device, boolean autoConnect) { return false; }

    public void cancelConnection(BluetoothDevice device) {}

    public void setPreferredPhy(BluetoothDevice device, int txPhy, int rxPhy, int phyOptions) {}

    public void readPhy(BluetoothDevice device) {}

    public boolean sendResponse(BluetoothDevice device, int requestId, int status, int offset,
            byte[] value) {
        return false;
    }

    public boolean notifyCharacteristicChanged(BluetoothDevice device,
            BluetoothGattCharacteristic characteristic, boolean confirm) {
        return false;
    }

    public boolean addService(BluetoothGattService service) { return false; }

    public boolean removeService(BluetoothGattService service) { return false; }

    public void clearServices() {}

    public List<BluetoothGattService> getServices() { return Collections.emptyList(); }

    public BluetoothGattService getService(UUID uuid) { return null; }

    // --- BluetoothProfile ---
    @Override public int getConnectionState(BluetoothDevice device) { return STATE_DISCONNECTED; }

    @Override public List<BluetoothDevice> getConnectedDevices() { return Collections.emptyList(); }

    @Override public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return Collections.emptyList();
    }
}
