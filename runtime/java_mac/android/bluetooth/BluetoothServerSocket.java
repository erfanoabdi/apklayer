// android.bluetooth.BluetoothServerSocket -- a listening Bluetooth socket.
//
// Mac.apk exposes no Bluetooth bridge, so nothing can ever be accepted.
// accept() throws IOException, which is AOSP's own documented failure ("on
// error, for example this socket has already been closed") and the only answer
// that does not leave a server thread blocked forever on a link that cannot
// exist. Every factory that would produce one (BluetoothAdapter.listenUsing*)
// already throws for the same reason.
//
// Mirrors frameworks/base/core/java/android/bluetooth/BluetoothServerSocket.java.
package android.bluetooth;

import java.io.Closeable;
import java.io.IOException;

public class BluetoothServerSocket implements Closeable {

    public BluetoothServerSocket() {}

    public BluetoothSocket accept() throws IOException { return accept(-1); }

    public BluetoothSocket accept(int timeout) throws IOException {
        throw new IOException("Mac.apk: no Bluetooth radio");
    }

    /** AOSP returns the assigned L2CAP PSM, or throws when the socket is not
     *  an L2CAP listener. No socket is ever bound, so 0 -- which is not a
     *  valid PSM, so it cannot be mistaken for one. */
    public int getPsm() { return 0; }

    @Override public String toString() {
        return "ServiceName: null Type: RFCOMM ServerSocket: null";
    }

    /** A close of something never opened: genuinely a no-op. */
    @Override public void close() throws IOException {}
}
