// android.bluetooth.BluetoothGatt
//
// The GATT client returned by BluetoothDevice.connectGatt. Mac.apk has no
// radio, so the client immediately reports a failed connection to the supplied
// callback (status=GATT_FAILURE, newState=STATE_DISCONNECTED) and every
// subsequent operation returns false / empty. This is the honest "could not
// connect" path real BLE apps already handle. Mirrors
// frameworks/base/core/java/android/bluetooth/BluetoothGatt.java.
package android.bluetooth;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class BluetoothGatt implements BluetoothProfile {

    public static final int GATT_SUCCESS = 0;
    public static final int GATT_READ_NOT_PERMITTED = 0x2;
    public static final int GATT_WRITE_NOT_PERMITTED = 0x3;
    public static final int GATT_INSUFFICIENT_AUTHENTICATION = 0x5;
    public static final int GATT_REQUEST_NOT_SUPPORTED = 0x6;
    public static final int GATT_INSUFFICIENT_ENCRYPTION = 0xf;
    public static final int GATT_INVALID_OFFSET = 0x7;
    public static final int GATT_INVALID_ATTRIBUTE_LENGTH = 0xd;
    public static final int GATT_CONNECTION_CONGESTED = 0x8f;
    public static final int GATT_FAILURE = 0x101;

    public static final int CONNECTION_PRIORITY_BALANCED = 0;
    public static final int CONNECTION_PRIORITY_HIGH = 1;
    public static final int CONNECTION_PRIORITY_LOW_POWER = 2;

    private final BluetoothDevice mDevice;
    private final BluetoothGattCallback mCallback;

    private BluetoothGatt(BluetoothDevice device, BluetoothGattCallback callback) {
        mDevice = device;
        mCallback = callback;
    }

    /** Factory used by BluetoothDevice.connectGatt: builds a client and tells
     *  the callback the connection failed (no radio) on a best-effort basis.
     *  AOSP posts the callback asynchronously; we invoke synchronously after
     *  returning, but since there is no host thread loop guaranteed here we
     *  notify immediately -- the observable result (a FAILURE/DISCONNECTED
     *  event) is identical. */
    static BluetoothGatt createFailed(BluetoothDevice device, BluetoothGattCallback callback) {
        BluetoothGatt gatt = new BluetoothGatt(device, callback);
        if (callback != null) {
            callback.onConnectionStateChange(gatt, GATT_FAILURE, STATE_DISCONNECTED);
        }
        return gatt;
    }

    public BluetoothDevice getDevice() { return mDevice; }

    public boolean connect() { return false; }
    public void disconnect() {}
    public void close() {}

    public boolean discoverServices() { return false; }
    public List<BluetoothGattService> getServices() { return Collections.emptyList(); }
    public BluetoothGattService getService(UUID uuid) { return null; }

    public boolean readCharacteristic(BluetoothGattCharacteristic characteristic) { return false; }
    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) { return false; }
    public int writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] value,
            int writeType) { return GATT_FAILURE; }
    public boolean readDescriptor(BluetoothGattDescriptor descriptor) { return false; }
    public boolean writeDescriptor(BluetoothGattDescriptor descriptor) { return false; }
    public int writeDescriptor(BluetoothGattDescriptor descriptor, byte[] value) {
        return GATT_FAILURE;
    }

    public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
            boolean enable) { return false; }

    public boolean readRemoteRssi() { return false; }
    public boolean requestMtu(int mtu) { return false; }
    public boolean requestConnectionPriority(int connectionPriority) { return false; }
    public boolean beginReliableWrite() { return false; }
    public boolean executeReliableWrite() { return false; }
    public void abortReliableWrite() {}

    /** @deprecated on AOSP in favour of the no-arg abortReliableWrite(); the
     *  device argument was always ignored there too. */
    @Deprecated
    public void abortReliableWrite(BluetoothDevice mDevice) { abortReliableWrite(); }

    /**
     * AOSP asks the controller for the connection's current PHYs and answers
     * through BluetoothGattCallback.onPhyRead. There is no connection, so
     * there is nothing to read and no callback fires -- consistent with every
     * other operation on this class, all of which fail after
     * connectGatt() already reported GATT_FAILURE/STATE_DISCONNECTED.
     */
    public void readPhy() {}

    /** @see #readPhy -- there is no link whose PHY could be changed. */
    public void setPreferredPhy(int txPhy, int rxPhy, int phyOptions) {}

    // --- BluetoothProfile ---
    @Override public int getConnectionState(BluetoothDevice device) { return STATE_DISCONNECTED; }
    @Override public List<BluetoothDevice> getConnectedDevices() { return Collections.emptyList(); }
    @Override public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return Collections.emptyList();
    }
}
