// android.hardware.usb.UsbRequest
//
// An async USB transfer request paired with a UsbDeviceConnection. Mac.apk
// never opens a device, so initialize() always fails (no endpoint); the type
// + method surface must resolve for async-transfer code to compile.
// Mirrors frameworks/base/core/java/android/hardware/usb/UsbRequest.java.
package android.hardware.usb;

import java.nio.ByteBuffer;

public class UsbRequest {
    private UsbEndpoint mEndpoint;
    private Object mClientData;

    public UsbRequest() {}

    /** AOSP returns false if the connection/endpoint cannot be bound; ours never can. */
    public boolean initialize(UsbDeviceConnection connection, UsbEndpoint endpoint) {
        mEndpoint = endpoint;
        return false;
    }

    public UsbEndpoint getEndpoint() { return mEndpoint; }
    public Object getClientData() { return mClientData; }
    public void setClientData(Object data) { mClientData = data; }

    public boolean queue(ByteBuffer buffer) { return false; }
    public boolean queue(ByteBuffer buffer, int length) { return false; }
    public boolean cancel() { return false; }
    public void close() {}
}
