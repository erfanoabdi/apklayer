// android.bluetooth.BluetoothGattServerCallback -- the callback an app
// subclasses to receive GATT SERVER events (a remote central reading or
// writing this device's characteristics).
//
// Never invoked here: BluetoothManager.openGattServer() returns null because
// there is no Bluetooth bridge, so no server exists to raise events. The class
// is present so an app's subclass compiles and LINKS -- which is the whole
// reason the type is needed, since the subclass is defined at class-load time.
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/BluetoothGattServerCallback.java.
package android.bluetooth;

public abstract class BluetoothGattServerCallback {

    public BluetoothGattServerCallback() {}

    public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {}

    public void onServiceAdded(int status, BluetoothGattService service) {}

    public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
            BluetoothGattCharacteristic characteristic) {}

    public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
            BluetoothGattCharacteristic characteristic, boolean preparedWrite,
            boolean responseNeeded, int offset, byte[] value) {}

    public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
            BluetoothGattDescriptor descriptor) {}

    public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
            BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded,
            int offset, byte[] value) {}

    public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {}

    public void onNotificationSent(BluetoothDevice device, int status) {}

    public void onMtuChanged(BluetoothDevice device, int mtu) {}

    public void onPhyUpdate(BluetoothDevice device, int txPhy, int rxPhy, int status) {}

    public void onPhyRead(BluetoothDevice device, int txPhy, int rxPhy, int status) {}
}
