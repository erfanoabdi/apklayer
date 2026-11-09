package apklayer.host;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowBluetoothSocket;

/**
 * An RFCOMM BluetoothSocket: connect asks the hub for a BlueZ helper connected to the device's service (BlueZ's Profile API looks up its channel) or
 * channel, whose stdin and stdout carry the connection's bytes (bridge/portal/bluez.py rfcomm); a server socket's accepted one comes connected.
 */
@Implements(className = "android.bluetooth.BluetoothSocket", isInAndroidSdk = false)
public class ShadowHostBluetoothSocket extends ShadowBluetoothSocket {
    private String address;
    private BluetoothDevice device;
    private String target;  // the service's UUID or the channel
    private boolean secure;
    private InputStream in;
    private OutputStream out;

    static BluetoothSocket create(BluetoothDevice device, String target, boolean secure) {
        BluetoothSocket socket = Shadow.newInstanceOf(BluetoothSocket.class);
        ShadowHostBluetoothSocket shadow = Shadow.extract(socket);
        shadow.device = device;  // getRemoteDevice: Android 16's BluetoothSocket keeps it in an Optional the constructor fills
        shadow.address = device.getAddress();
        shadow.target = target;
        shadow.secure = secure;
        return socket;
    }

    /** A connection a server socket accepted: the listener helper's pipes (ShadowHostBluetoothServerSocket). */
    static BluetoothSocket accepted(BluetoothDevice device, String service, InputStream in, OutputStream out) {
        BluetoothSocket socket = create(device, service, true);
        ShadowHostBluetoothSocket shadow = Shadow.extract(socket);
        shadow.in = in;
        shadow.out = out;
        return socket;
    }

    /** A helper's status line (read byte by byte: what follows is the connection's). */
    static String line(InputStream in) throws IOException {
        StringBuilder status = new StringBuilder();
        for (int c; (c = in.read()) >= 0 && c != '\n'; ) status.append((char) c);
        return status.toString();
    }

    @Implementation @Override protected synchronized void connect() throws IOException {
        if (address == null) { super.connect(); return; }  // a socket Robolectric made (a server socket's)
        if (in != null) return;
        ParcelFileDescriptor[] pipes;
        try { pipes = HubClient.current().bluetooth(address, target, null, secure); }
        catch (RuntimeException failure) { throw new IOException("read failed, socket might closed or timeout", failure); }
        out = new ParcelFileDescriptor.AutoCloseOutputStream(pipes[0]);
        in = new ParcelFileDescriptor.AutoCloseInputStream(pipes[1]);
        String status = line(in);
        if (!"OK".equals(status)) { close(); throw new IOException("read failed, socket might closed or timeout (" + status + ")"); }
    }

    @Implementation protected BluetoothDevice getRemoteDevice() { return device; }
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
