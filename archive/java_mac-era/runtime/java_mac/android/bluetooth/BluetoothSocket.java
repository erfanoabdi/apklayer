// android.bluetooth.BluetoothSocket -- an RFCOMM/L2CAP/SCO socket to a remote
// Bluetooth device.
//
// Mac.apk exposes no Bluetooth bridge, so no socket can ever carry traffic.
// connect() therefore THROWS IOException rather than returning silently:
// AOSP's own contract is "@throws IOException on error, for example
// connection failure", and a silent success would leave an app reading from a
// stream that will never produce a byte while it believes the link is up.
//
// (This file began life as an auto-generated stub whose connect() was an empty
// no-op and whose getInputStream() returned an already-empty ByteArrayInputStream
// -- so a caller saw "connected fine, peer sent nothing / closed", which is a
// different and more misleading story than "there is no Bluetooth".)
//
// Mirrors frameworks/base/core/java/android/bluetooth/BluetoothSocket.java.
package android.bluetooth;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BluetoothSocket implements Closeable {

    public static final int TYPE_RFCOMM = 1;
    public static final int TYPE_SCO = 2;
    public static final int TYPE_L2CAP = 3;

    private static final String NO_RADIO = "Mac.apk: no Bluetooth radio";

    public BluetoothSocket() {}

    /** @throws IOException always -- there is no radio to connect over. */
    public void connect() throws IOException {
        throw new IOException(NO_RADIO);
    }

    /**
     * AOSP hands back the socket's stream whether or not it is connected, and
     * the READ is what fails. Mirrored: the stream exists, and every read
     * raises the same IOException connect() would have -- rather than
     * reporting a clean end-of-stream, which an app reads as "the peer closed
     * normally".
     */
    public InputStream getInputStream() throws IOException {
        return new InputStream() {
            @Override public int read() throws IOException {
                throw new IOException(NO_RADIO);
            }
        };
    }

    /** @see #getInputStream -- same reasoning on the write side. */
    public OutputStream getOutputStream() throws IOException {
        return new OutputStream() {
            @Override public void write(int b) throws IOException {
                throw new IOException(NO_RADIO);
            }
        };
    }

    public boolean isConnected() { return false; }

    public BluetoothDevice getRemoteDevice() { return null; }

    /** AOSP: RFCOMM / SCO / L2CAP. Nothing is connected, and RFCOMM is the
     *  type every socket factory on this class creates. */
    public int getConnectionType() { return TYPE_RFCOMM; }

    /** AOSP returns the negotiated L2CAP MTU, and throws IOException when the
     *  socket is not connected. Ours never is. */
    public int getMaxTransmitPacketSize() { return 0; }

    /** @see #getMaxTransmitPacketSize */
    public int getMaxReceivePacketSize() { return 0; }

    /** A close of something never opened: genuinely a no-op. */
    @Override public void close() throws IOException {}
}
