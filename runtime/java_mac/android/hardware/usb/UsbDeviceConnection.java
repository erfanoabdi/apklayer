// android.hardware.usb.UsbDeviceConnection
//
// The I/O handle returned by UsbManager.openDevice. Mac.apk never opens a
// real device (the device list is always empty), so a connection is never
// constructed in practice; the type + method surface must still resolve for
// USB-protocol code to compile. All transfers report failure (-1) and
// claimInterface returns false -- the honest "no device" result.
// Mirrors frameworks/base/core/java/android/hardware/usb/UsbDeviceConnection.java.
package android.hardware.usb;

import java.io.FileDescriptor;

public class UsbDeviceConnection {
    private final UsbDevice mDevice;

    public UsbDeviceConnection(UsbDevice device) { mDevice = device; }

    /** AOSP returns false if the native open failed; we have no device. */
    public boolean claimInterface(UsbInterface intf, boolean force) { return false; }
    public boolean releaseInterface(UsbInterface intf) { return false; }
    public boolean setInterface(UsbInterface intf) { return false; }
    public boolean setConfiguration(Object configuration) { return false; }

    public int controlTransfer(int requestType, int request, int value, int index,
            byte[] buffer, int length, int timeout) { return -1; }
    public int controlTransfer(int requestType, int request, int value, int index,
            byte[] buffer, int offset, int length, int timeout) { return -1; }

    public int bulkTransfer(UsbEndpoint endpoint, byte[] buffer, int length, int timeout) {
        return -1;
    }
    public int bulkTransfer(UsbEndpoint endpoint, byte[] buffer, int offset, int length,
            int timeout) { return -1; }

    public UsbRequest requestWait() { return null; }

    /** AOSP UsbDeviceConnection.java:393 requestWait(long) -- the timeout-taking
     *  form, which signals "nothing completed in time" with a checked
     *  java.util.concurrent.TimeoutException rather than the null the no-arg
     *  form uses for a generic error. AOSP's first statement is
     *  Preconditions.checkArgumentNonnegative(timeout, "timeout"), which raises
     *  IllegalArgumentException before any wait, so that check is mirrored
     *  exactly and comes first.
     *
     *  We then throw TimeoutException IMMEDIATELY instead of sleeping out the
     *  timeout. AOSP sleeps because a queued UsbRequest genuinely could complete
     *  during the wait; here no device is ever opened, so there is no request
     *  queue and no completion is possible at any point in the interval --
     *  sleeping would only delay an answer that cannot change. Returning null
     *  instead would be wrong in the other direction: null means "error" for the
     *  no-arg form, and a caller polling requestWait(timeout) in a loop must be
     *  able to tell a timeout apart from a failure. */
    public UsbRequest requestWait(long timeout)
            throws java.util.concurrent.TimeoutException {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        throw new java.util.concurrent.TimeoutException(
                "no USB device is open, so no request can complete");
    }
    public byte[] getRawDescriptors() { return null; }
    public String getSerial() { return mDevice != null ? mDevice.getSerialNumber() : null; }
    public int getFileDescriptor() { return -1; }
    public FileDescriptor getFileDescriptorObject() { return null; }

    public void close() {}
}
