package apklayer.host;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowBluetoothSocket;
import org.robolectric.util.ReflectionHelpers;

/**
 * An RFCOMM BluetoothSocket: connect asks the hub for a BlueZ helper connected to the device's service (BlueZ's Profile API looks up its channel), whose
 * stdin and stdout carry the connection's bytes (bridge/portal/bluez.py rfcomm).
 */
@Implements(className = "android.bluetooth.BluetoothSocket", isInAndroidSdk = false)
public class ShadowHostBluetoothSocket extends ShadowBluetoothSocket {
    private String address;
    private UUID uuid;
    private InputStream in;
    private OutputStream out;

    static BluetoothSocket create(BluetoothDevice device, UUID uuid) {
        BluetoothSocket socket = Shadow.newInstanceOf(BluetoothSocket.class);
        if (ReflectionHelpers.hasField(BluetoothSocket.class, "mDevice")) ReflectionHelpers.setField(socket, "mDevice", device);
        ShadowHostBluetoothSocket shadow = Shadow.extract(socket);
        shadow.address = device.getAddress();
        shadow.uuid = uuid;
        return socket;
    }

    @Implementation @Override protected synchronized void connect() throws IOException {
        if (address == null) { super.connect(); return; }  // a socket Robolectric made (a server socket's)
        if (in != null) return;
        ParcelFileDescriptor[] pipes;
        try { pipes = HubClient.current().bluetooth(address, uuid.toString()); }
        catch (RuntimeException failure) { throw new IOException("read failed, socket might closed or timeout", failure); }
        out = new ParcelFileDescriptor.AutoCloseOutputStream(pipes[0]);
        in = new ParcelFileDescriptor.AutoCloseInputStream(pipes[1]);
        StringBuilder status = new StringBuilder();
        for (int c; (c = in.read()) >= 0 && c != '\n'; ) status.append((char) c);
        if (!"OK".contentEquals(status)) { close(); throw new IOException("read failed, socket might closed or timeout (" + status + ")"); }
    }

    @Implementation @Override protected InputStream getInputStream() { return address == null ? super.getInputStream() : in; }
    @Implementation @Override protected OutputStream getOutputStream() { return address == null ? super.getOutputStream() : out; }
    @Implementation @Override protected boolean isConnected() { return address == null ? super.isConnected() : in != null; }

    @Implementation @Override protected synchronized void close() throws IOException {
        if (address == null) { super.close(); return; }
        if (in != null) in.close();
        if (out != null) out.close();
        in = null;
        out = null;
    }
}
