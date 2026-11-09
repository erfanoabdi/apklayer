package apklayer.host;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowBluetoothServerSocket;

/**
 * An RFCOMM server socket (listenUsing[Insecure]RfcommWithServiceRecord): the service is registered with BlueZ by a listener helper the hub starts
 * (bridge/portal/bluez.py listen, BlueZ's Profile API in the server role), which takes one incoming connection and then carries its bytes over its stdin
 * and stdout; accept() hands that over as a connected socket and starts the next listener, close() ends the waiting one.
 */
@Implements(className = "android.bluetooth.BluetoothServerSocket", isInAndroidSdk = false)
public class ShadowHostBluetoothServerSocket extends ShadowBluetoothServerSocket {
    private BluetoothAdapter adapter;
    private String uuid, name;
    private boolean secure;
    private Listener armed;  // the helper waiting for the next connection; null once closed

    /** One listener helper: its stdin, its stdout, the service registered (or why not), then the address of the device that connected. */
    private record Listener(OutputStream in, InputStream out, CompletableFuture<Void> registered, CompletableFuture<String> connected) {}

    static BluetoothServerSocket listen(BluetoothAdapter adapter, String name, UUID uuid, boolean secure) throws IOException {
        HostBluetooth.require(ShadowHostBluetoothAdapter.CONNECT);
        if (!adapter.isEnabled()) throw new IOException("Bluetooth is off");
        BluetoothServerSocket server = Shadow.newInstanceOf(BluetoothServerSocket.class);  // Robolectric's newInstance names a constructor this android-all lacks
        ShadowHostBluetoothServerSocket shadow = Shadow.extract(server);
        shadow.adapter = adapter;
        shadow.uuid = uuid.toString();
        shadow.name = name;
        shadow.secure = secure;
        Listener first = shadow.arm();
        try { first.registered.get(); }
        catch (ExecutionException | InterruptedException failed) { shadow.close(); throw new IOException("Error: " + failed.getCause().getMessage(), failed); }
        return server;
    }

    private synchronized Listener arm() throws IOException {
        ParcelFileDescriptor[] pipes;
        try { pipes = HubClient.current().bluetooth(null, uuid, name, secure); }
        catch (RuntimeException failure) { throw new IOException("listen failed", failure); }
        Listener listener = new Listener(new ParcelFileDescriptor.AutoCloseOutputStream(pipes[0]), new ParcelFileDescriptor.AutoCloseInputStream(pipes[1]),
                new CompletableFuture<>(), new CompletableFuture<>());
        armed = listener;
        Thread waiter = new Thread(() -> {
            try {
                String status = ShadowHostBluetoothSocket.line(listener.out);
                if (!"LISTEN".equals(status)) throw new IOException(status.isEmpty() ? "the listener ended" : status);
                listener.registered.complete(null);
                status = ShadowHostBluetoothSocket.line(listener.out);
                if (!status.startsWith("OK ")) throw new IOException(status.isEmpty() ? "socket closed" : status);
                listener.connected.complete(status.substring(3));
            } catch (IOException failed) {
                listener.registered.completeExceptionally(failed);
                listener.connected.completeExceptionally(failed);
                try { listener.out.close(); listener.in.close(); } catch (IOException ignored) { }
            }
        }, "apklayer-bluetooth-listen");
        waiter.setDaemon(true);
        waiter.start();
        return listener;
    }

    @Implementation @Override protected BluetoothSocket accept(int timeout) throws IOException {
        Listener listener;
        synchronized (this) { listener = armed; }
        if (listener == null) throw new IOException("socket closed");
        String address;
        try { address = timeout < 0 ? listener.connected.get() : listener.connected.get(timeout, TimeUnit.MILLISECONDS); }
        catch (TimeoutException waited) { throw new IOException("Try again"); }  // Android's message for a timed-out accept
        catch (ExecutionException | InterruptedException failed) { throw new IOException(failed.getCause() == null ? failed.toString() : failed.getCause().getMessage()); }
        synchronized (this) {
            if (armed == listener) arm();
        }
        return ShadowHostBluetoothSocket.accepted(adapter.getRemoteDevice(address), uuid, listener.out, listener.in);
    }

    @Implementation @Override protected synchronized void close() throws IOException {
        super.close();
        Listener listener = armed;
        armed = null;
        if (listener == null) return;
        listener.connected.completeExceptionally(new IOException("socket closed"));  // a waiting accept() throws, as on Android
        listener.in.close();  // the helper sees its stdin end and quits (a connection that came but was never accepted ends too)
        listener.out.close();  // the waiter's next read throws
    }
}
